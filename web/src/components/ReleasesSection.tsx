import Link from "next/link";
import { latestRelease } from "@/lib/release";
import { repository } from "@/lib/site";
import { releaseLabel } from "./ReleaseSummary";

export function ReleasesSection() {
  return <section id="releases">
    <div className="inverse py-12 sm:py-16">
      <div className="page-shell">
        <p className="mb-4 text-sm text-neutral-300">{latestRelease ? `${releaseLabel(latestRelease)} · v${latestRelease.version}` : "Android releases"}</p>
        <h2 className="section-title">{latestRelease ? "Get HyperMusic for Android" : "The first Android release is on its way"}</h2>
        <p className="mt-4 max-w-xl text-base text-neutral-300">{latestRelease ? "Review the version, requirements and release notes before downloading." : "No APK is available on this site yet. Published builds will appear here and on GitHub."}</p>
        <div className="mt-7 flex flex-wrap gap-4">
          <Link href="/download" className="button button-primary">Download for Android</Link>
          <a href={`${repository}/releases`} className="action-link text-sm">Releases on GitHub</a>
        </div>
      </div>
    </div>
  </section>;
}
