import Link from "next/link";
import type { ReleaseData } from "@/types/release";
import { ApkDownloadLink, ReleaseBadge, ReleaseMetadata } from "./ReleaseSummary";
import { ReleaseEmptyState } from "./ReleaseEmptyState";

export function ProductVersionsTable({ releases }: { releases: ReleaseData[] }) {
  if (!releases.length) return <ReleaseEmptyState />;
  return <ul className="divide-y divide-line border-b border-line">
    {releases.map(release => <li id={`v${release.version}`} key={release.version} className="flex flex-col justify-between gap-5 py-7 sm:flex-row sm:items-center">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-3"><h2 className="text-lg font-semibold text-ink">v{release.version}</h2><ReleaseBadge release={release} /></div>
        <ReleaseMetadata release={release} />
      </div>
      <div className="flex flex-wrap items-center gap-5">
        <ApkDownloadLink release={release} secondary />
        <Link href={`/changelog#v${release.version}`} aria-label={`Release notes for HyperMusic ${release.version}`} className="action-link text-sm font-medium">Release notes</Link>
      </div>
    </li>)}
  </ul>;
}
