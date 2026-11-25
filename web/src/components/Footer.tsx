import Link from "next/link";
import { repository } from "@/lib/site";

export function Footer() {
  return <footer className="border-t border-line bg-canvas pb-6 pt-10 sm:pt-14">
    <div className="page-shell">
      <div className="flex flex-col justify-between gap-8 md:flex-row md:gap-12">
        <p className="max-w-xs text-base font-normal leading-relaxed text-muted md:pt-2.5">An independent, open-source music player for Android.</p>
        <div className="flex flex-wrap gap-x-12 gap-y-4 text-sm font-normal text-muted sm:gap-x-16">
          <nav aria-label="Downloads and releases" className="flex flex-col">
            <Link className="action-link w-fit no-underline decoration-1 underline-offset-4 hover:underline" href="/download">Download for Android</Link>
            <Link className="action-link w-fit no-underline decoration-1 underline-offset-4 hover:underline" href="/releases">Releases</Link>
            <Link className="action-link w-fit no-underline decoration-1 underline-offset-4 hover:underline" href="/changelog">Changelog</Link>
          </nav>
          <nav aria-label="About the project" className="flex flex-col">
            <Link className="action-link w-fit no-underline decoration-1 underline-offset-4 hover:underline" href="/#about">About HyperMusic</Link>
            <a className="action-link w-fit no-underline decoration-1 underline-offset-4 hover:underline" href={repository}>Source code on GitHub</a>
            <a className="action-link w-fit no-underline decoration-1 underline-offset-4 hover:underline" href={`${repository}/issues`}>Report an issue</a>
          </nav>
        </div>
      </div>

      <div aria-hidden="true" className="@container mx-auto mt-8 w-full max-w-6xl select-none sm:mt-10">
        <div className="whitespace-nowrap pb-[0.08em] pt-[0.16em] text-center font-sans text-[clamp(2rem,19cqw,15rem)] font-medium leading-[0.98] tracking-[-0.045em] text-ink">
          HyperMus<span className="relative -top-[0.12em] inline-block">i</span>c
        </div>
      </div>

      <nav aria-label="Legal information" className="mt-4 flex flex-wrap justify-end gap-x-6 pt-2 text-sm font-normal text-muted">
        <Link className="action-link no-underline decoration-1 underline-offset-4 hover:underline" href="/privacy">Privacy</Link>
        <Link className="action-link no-underline decoration-1 underline-offset-4 hover:underline" href="/terms">Terms</Link>
        <Link className="action-link no-underline decoration-1 underline-offset-4 hover:underline" href="/copyright">Copyright &amp; contact</Link>
      </nav>
    </div>
  </footer>;
}
