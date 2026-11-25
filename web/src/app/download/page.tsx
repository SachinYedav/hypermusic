import Link from "next/link";
import { ChevronDown } from "lucide-react";
import { PageHeader } from "@/components/PageHeader";
import { ReleaseEmptyState } from "@/components/ReleaseEmptyState";
import { ApkDownloadLink, ReleaseBadge, ReleaseMetadata } from "@/components/ReleaseSummary";
import { pageMetadata } from "@/lib/metadata";
import { latestRelease as latest } from "@/lib/release";

export const metadata = pageMetadata("/download", "Download HyperMusic for Android", "Find published HyperMusic APKs for Android 8.0 and later, version details and installation instructions.");

export default function DownloadPage() {
  return <main id="main" tabIndex={-1} className="page-shell page-content">
    <PageHeader title="Download HyperMusic" description="An open-source music player for Android." actionLabel="Browse releases" actionHref="/releases" />
    <div className="max-w-3xl">
      {latest ? <>
        <section aria-labelledby="android-download" className="panel p-6 sm:p-8">
          <h2 id="android-download" className="text-2xl font-semibold tracking-tight text-ink">HyperMusic for Android</h2>
          <p className="mt-3 text-base text-muted">Android 8.0 or later. One APK for supported Android devices.</p>
          <div className="mt-6 flex flex-wrap items-center gap-3"><p className="text-lg font-semibold text-ink">v{latest.version}</p><ReleaseBadge release={latest} /></div>
          <ReleaseMetadata release={latest} />
          <div className="mt-6 flex flex-wrap items-center gap-5"><ApkDownloadLink release={latest} /><Link href={`/changelog#v${latest.version}`} className="action-link text-sm font-medium">Release notes</Link></div>
          <p className="mt-4 break-all text-sm text-metadata">{latest.apk.name}</p>
          <details className="group mt-5 border-t border-line pt-3">
            <summary className="flex min-h-11 cursor-pointer list-none items-center justify-between gap-3 text-sm font-medium [&::-webkit-details-marker]:hidden"><span>Verify this download (SHA-256)</span><ChevronDown aria-hidden="true" className="size-4 shrink-0 transition-transform group-open:rotate-180" /></summary>
            <p className="mt-2 text-sm text-muted">Compare the checksum of your downloaded file with this published value.</p>
            <code className="mt-3 block select-all break-all rounded-control bg-canvas p-3 text-sm text-ink">{latest.apk.sha256}</code>
            <a href={latest.url} className="action-link mt-2 text-sm">View this release on GitHub</a>
          </details>
        </section>
        {latest.advisory && <aside aria-labelledby="download-advisory" className="mt-6 border-l-2 border-warning bg-surface p-5">
          <h2 id="download-advisory" className="text-base font-semibold text-ink">{latest.advisory.title}</h2><p className="mt-2 text-base leading-relaxed text-muted">{latest.advisory.description}</p>
        </aside>}
        <section aria-labelledby="install-android" className="mt-10 border-t border-line pt-8">
          <h2 id="install-android" className="text-xl font-semibold text-ink">Install on Android</h2>
          <ol className="mt-4 list-decimal space-y-3 pl-5 text-base leading-relaxed text-muted">
            <li>Download the APK and open it from your browser’s Downloads or your file manager.</li>
            <li>If Android asks, allow “Install unknown apps” for the browser or file manager you used.</li>
            <li>Follow Android’s prompts to install the app, then open HyperMusic.</li>
          </ol>
        </section>
      </> : <ReleaseEmptyState />}
      <p className="mt-6 text-sm text-metadata">Available for Android. Native iOS, Windows and macOS apps are unavailable.</p>
    </div>
  </main>;
}
