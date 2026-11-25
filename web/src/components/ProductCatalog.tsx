import Link from "next/link";
import { releaseDate } from "@/lib/release";
import type { ReleaseData } from "@/types/release";
import { ReleaseEmptyState } from "./ReleaseEmptyState";
import { ReleaseBadge } from "./ReleaseSummary";

export function ProductCatalog({ releases }: { releases: ReleaseData[] }) {
  if (!releases.length) return <ReleaseEmptyState />;
  return <div className="divide-y divide-line">
    {releases.map(release => <article id={`v${release.version}`} key={release.version} aria-labelledby={`release-${release.version}`} className="grid gap-6 py-10 first:pt-0 lg:grid-cols-[15rem_minmax(0,1fr)] lg:gap-10">
      <header>
        <h2 id={`release-${release.version}`} className="text-2xl font-semibold tracking-tight text-ink">v{release.version}</h2>
        <div className="mt-3"><ReleaseBadge release={release} /></div>
        <p className="mt-3 text-sm text-metadata"><time dateTime={release.date}>{releaseDate(release.date)}</time></p>
        <Link href={`/releases#v${release.version}`} aria-label={`View HyperMusic ${release.version} release`} className="action-link mt-3 text-sm font-medium">View release</Link>
      </header>
      <div className="min-w-0 max-w-3xl">
        {release.advisory && <aside aria-label={`Advisory for version ${release.version}`} className="mb-6 border-l-2 border-warning bg-surface p-4">
          <h3 className="text-base font-semibold text-ink">{release.advisory.title}</h3>
          <p className="mt-2 text-sm leading-relaxed text-muted">{release.advisory.description}</p>
        </aside>}
        <p className="text-lg font-medium leading-relaxed text-ink">{release.summary}</p>
        {([['features', 'New features'], ['improvements', 'Improvements'], ['fixes', 'Fixes']] as const).map(([key, label]) => {
          const changes = release.details[key] ?? [];
          return changes.length ? <section key={key} className="mt-7" aria-labelledby={`v${release.version}-${key}`}>
            <h3 id={`v${release.version}-${key}`} className="text-base font-semibold text-ink">{label}</h3>
            <ul className="mt-3 list-disc space-y-2 pl-5 text-base leading-relaxed text-muted">{changes.map((change, index) => <li key={index}>{change}</li>)}</ul>
          </section> : null;
        })}
      </div>
    </article>)}
  </div>;
}
