import Link from "next/link";

export function PageHeader({ title, description, actionLabel, actionHref, actionVariant = "button" }: {
  title: string; description: string; actionLabel?: string; actionHref?: string; actionVariant?: "button" | "link";
}) {
  return <header className="mb-8 flex flex-col items-start justify-between gap-5 border-b border-line pb-8 sm:flex-row">
    <div className="max-w-2xl">
      <h1 className="page-title">{title}</h1>
      <p className="mt-3 text-base text-muted">{description}</p>
    </div>
    {actionLabel && actionHref && (
      <Link href={actionHref} className={actionVariant === "link" ? "action-link shrink-0 text-sm font-medium" : "button button-tonal shrink-0"}>
        {actionLabel}
      </Link>
    )}
  </header>;
}
