import { releases } from "@/lib/release";
import { pageMetadata } from "@/lib/metadata";
import { PageHeader } from "@/components/PageHeader";
import { ProductVersionsTable } from "@/components/ProductVersionsTable";

export const metadata = pageMetadata("/releases", "Release Archive | HyperMusic", "Browse published HyperMusic Android versions, download their APKs, and read their release notes.");

export default function ReleasesPage() {
  return (
    <main id="main" tabIndex={-1}>
      <div className="page-shell page-content">
        <PageHeader
          title="Releases"
          description="Browse published versions of HyperMusic for Android."
          actionLabel="Download for Android"
          actionHref="/download"
        />

        <section aria-label="Available HyperMusic Versions">
          <ProductVersionsTable releases={releases} />
        </section>
      </div>
    </main>
  );
}
