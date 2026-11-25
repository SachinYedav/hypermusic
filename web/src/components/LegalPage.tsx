import { PageHeader } from "./PageHeader";
import { ResourceText, type ResourceLink } from "./ResourceText";
import references from "../../../legal/references.json";

interface LegalPageProps {
  document: { title: string; lastUpdated: string; sections: { title: string; paragraphs: string[] }[] };
  description: string;
}

export function LegalPage({ document, description }: LegalPageProps) {
  const links = (references as Record<string, ResourceLink[]>)[document.title] ?? [];
  return <main id="main" tabIndex={-1} className="page-shell page-content">
    <PageHeader title={document.title} description={description} />
    <div className="reading-width">
      <p className="mb-6 text-sm text-metadata">Last updated: {document.lastUpdated}</p>
      <nav aria-label={`${document.title} sections`} className="mb-10 border-y border-line py-5">
        <p className="mb-3 text-sm font-semibold text-ink">On this page</p>
        <ol className="list-decimal space-y-2 pl-5 text-sm text-muted">{document.sections.map((section, index) => <li key={section.title}><a className="text-link" href={`#legal-${index + 1}`}>{section.title}</a></li>)}</ol>
      </nav>
      <div className="space-y-10">{document.sections.map((section, index) => <section key={section.title} aria-labelledby={`legal-${index + 1}`}>
        <h2 id={`legal-${index + 1}`} className="mb-4 text-xl font-semibold tracking-tight text-ink">{section.title}</h2>
        <div className="space-y-4">{section.paragraphs.map(paragraph => <p key={paragraph} className="text-base leading-[1.75] text-muted"><ResourceText text={paragraph} links={links} /></p>)}</div>
      </section>)}</div>
    </div>
  </main>;
}
