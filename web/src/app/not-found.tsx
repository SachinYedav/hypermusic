import Link from "next/link";

export default function NotFound() {
  return <main id="main" tabIndex={-1} className="page-shell page-content">
    <p className="mb-3 text-sm font-medium text-metadata">404 · Page not found</p>
    <h1 className="page-title">That page is not here.</h1>
    <p className="mt-4 max-w-xl text-muted">Return to HyperMusic or browse published Android releases.</p>
    <div className="mt-8 flex flex-wrap gap-3">
      <Link href="/" className="button button-primary">Back to HyperMusic</Link>
      <Link href="/releases" className="button button-secondary">Browse releases</Link>
    </div>
  </main>;
}
