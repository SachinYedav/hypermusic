import type { ReleaseData } from "@/types/release";
import { latestRelease, releaseDate } from "@/lib/release";

export function releaseLabel(release: ReleaseData) {
  if (release.version === latestRelease?.version) return release.channel === "stable" ? "Recommended stable" : "Latest preview";
  if (release.channel === "preview" && latestRelease?.channel === "stable" && release.versionCode > latestRelease.versionCode) return "Newer preview";
  return release.channel === "stable" ? "Stable" : "Preview";
}

export function ReleaseBadge({ release }: { release: ReleaseData }) {
  return <span className="rounded-control bg-surface px-2.5 py-1 text-xs font-medium text-muted">{releaseLabel(release)}</span>;
}

export function ReleaseMetadata({ release }: { release: ReleaseData }) {
  return <p className="mt-3 text-sm text-metadata"><time dateTime={release.date}>{releaseDate(release.date)}</time><span aria-hidden="true"> · </span>{(release.apk.bytes / 1_000_000).toFixed(2)} MB<span aria-hidden="true"> · </span>Android 8.0+</p>;
}

export function ApkDownloadLink({ release, secondary = false }: { release: ReleaseData; secondary?: boolean }) {
  return <a href={release.apk.url} className={`button ${secondary ? "button-secondary" : "button-primary"}`}
    aria-label={`Download APK for HyperMusic ${release.version}, ${releaseLabel(release)}, ${(release.apk.bytes / 1_000_000).toFixed(2)} MB`}>Download APK</a>;
}
