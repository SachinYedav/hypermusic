import { releases } from "@/lib/release";
import { pageMetadata } from "@/lib/metadata";
import { PageHeader } from "@/components/PageHeader";
import { ProductCatalog } from "@/components/ProductCatalog";

export const metadata = pageMetadata("/changelog", "Android Release Notes | HyperMusic", "See what changed in each HyperMusic Android release, including features, improvements and fixes.");

export default function ChangelogPage() {
  return (
    <main id="main" tabIndex={-1}>
      <div className="page-shell page-content">
        <PageHeader
          title="Changelog"
          description="Explore detailed release notes, new features, performance improvements, and bug fixes for HyperMusic."
          actionLabel="View releases"
          actionHref="/releases"
        />

        <section aria-label="HyperMusic Release Notes">
          <ProductCatalog releases={releases} />
        </section>
      </div>
    </main>
  );
}
