import publishedManifest from "../../../public/releases.json";
import type { ReleaseData } from "@/types/release";

const manifest = publishedManifest;

const httpUrl = (value: unknown): value is string => typeof value === "string" && /^https:\/\//.test(value);

const isStringList = (val: unknown): boolean =>
  val === undefined || (Array.isArray(val) && val.every((item) => typeof item === "string"));

function isRelease(value: unknown): value is ReleaseData {
  if (!value || typeof value !== "object") return false;
  const r = value as Record<string, unknown>;
  const a = r.advisory as Record<string, unknown> | undefined;
  const d = r.details as Record<string, unknown> | undefined;
  const apk = r.apk as Record<string, unknown> | undefined;

  return (
    typeof r.version === "string" &&
    r.version.trim().length > 0 &&
    Number.isInteger(r.versionCode) &&
    Number(r.versionCode) > 0 &&
    typeof r.date === "string" &&
    /^\d{4}-\d{2}-\d{2}$/.test(r.date) &&
    Number.isFinite(Date.parse(r.date)) &&
    (r.channel === "stable" || r.channel === "preview") &&
    httpUrl(r.url) &&
    typeof r.summary === "string" &&
    r.summary.trim().length > 0 &&
    !!apk &&
    typeof apk.name === "string" &&
    typeof apk.bytes === "number" &&
    Number.isSafeInteger(apk.bytes) &&
    apk.bytes > 0 &&
    typeof apk.sha256 === "string" &&
    /^[a-f0-9]{64}$/.test(apk.sha256) &&
    httpUrl(apk.url) &&
    (!a ||
      (typeof a.title === "string" &&
        a.title.trim().length > 0 &&
        typeof a.description === "string" &&
        a.description.trim().length > 0)) &&
    (!!d &&
      (isStringList(d.features) &&
        isStringList(d.improvements) &&
        isStringList(d.fixes)))
  );
}

const records: unknown[] = manifest.releases;
if (manifest.schemaVersion !== 1 || !records.every(isRelease)) {
  throw new Error("Invalid published release manifest");
}
export const releases = (records as ReleaseData[])
  .slice()
  .sort((a, b) => b.versionCode - a.versionCode);
export const latestRelease = releases.find((release) => release.channel === "stable") ?? releases[0];
export function releaseDate(date: string) {
  return new Intl.DateTimeFormat("en", { year: "numeric", month: "long", day: "numeric", timeZone: "UTC" }).format(new Date(date));
}
