import { repository } from "@/lib/site";

export function ReleaseEmptyState({ headingLevel = 2 }: { headingLevel?: 2 | 3 }) {
  const Heading = headingLevel === 2 ? "h2" : "h3";
  return <div className="border-b border-line pb-8">
    <Heading className="text-lg font-semibold text-ink">No Android release available yet</Heading>
    <p className="mt-3 max-w-xl text-base text-muted">Published builds and release notes will appear here when available. No APK is available to download from this site yet.</p>
    <a href={`${repository}/releases`} className="action-link mt-4 text-sm font-medium">Check releases on GitHub</a>
    <p className="mt-2 text-sm text-metadata">You can also <a href={repository} className="text-link">view the source code</a>.</p>
  </div>;
}
