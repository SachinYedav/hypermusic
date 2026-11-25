"use client";

import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { Menu, X } from "lucide-react";
import { Brand } from "./Brand";

const links = [
  ["Features", "/#features"], ["Widgets", "/#widgets"], ["Screenshots", "/#gallery"],
  ["Releases", "/releases"], ["Changelog", "/changelog"], ["FAQ", "/#faq"],
] as const;

export function Navbar() {
  const pathname = usePathname();
  const [open, setOpen] = useState(false);
  const [hash, setHash] = useState("");
  const toggle = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    setOpen(false);
    const update = () => { setHash(window.location.hash); setOpen(false); };
    update();
    window.addEventListener("hashchange", update);
    window.addEventListener("popstate", update);
    const desktop = window.matchMedia("(min-width: 1024px)");
    const closeDesktop = () => { if (desktop.matches) setOpen(false); };
    desktop.addEventListener("change", closeDesktop);
    return () => { window.removeEventListener("hashchange", update); window.removeEventListener("popstate", update); desktop.removeEventListener("change", closeDesktop); };
  }, [pathname]);
  const navigate = (href: string) => { setHash(href.includes("#") ? href.slice(href.indexOf("#")) : ""); setOpen(false); };
  const current = (href: string) => href.includes("#") ? pathname === "/" && href === `/${hash}` : pathname === href;
  return <header className="fixed inset-x-0 top-0 z-50 border-b border-line bg-canvas" onKeyDown={event => {
    if (event.key === "Escape" && open) { setOpen(false); toggle.current?.focus(); }
  }}>
    <div className="page-shell flex h-16 items-center justify-between gap-3">
      <Brand />
      <nav aria-label="Main navigation" className="hidden items-center gap-5 text-sm font-medium text-muted lg:flex">
        {links.map(([label, href]) => <Link key={href} href={href} onNavigate={() => navigate(href)} aria-current={current(href) ? href.includes("#") ? "location" : "page" : undefined}
          className="flex min-h-11 items-center hover:text-ink aria-[current]:text-ink aria-[current]:underline underline-offset-4">{label}</Link>)}
      </nav>
      <div className="flex items-center gap-2">
        <Link href="/download" aria-current={pathname === "/download" ? "page" : undefined} className="button button-primary hidden sm:inline-flex">Download for Android</Link>
        <button ref={toggle} type="button" onClick={() => setOpen(!open)} aria-label={open ? "Close navigation menu" : "Open navigation menu"}
          aria-expanded={open} aria-controls="mobile-navigation" className="flex size-11 items-center justify-center rounded-control hover:bg-surface lg:hidden">
          {open ? <X aria-hidden="true" className="size-5" /> : <Menu aria-hidden="true" className="size-5" />}
        </button>
      </div>
    </div>
    <nav id="mobile-navigation" aria-label="Mobile navigation" hidden={!open}
      className="page-shell max-h-[calc(100dvh-4rem)] overflow-y-auto border-t border-line pb-5 lg:hidden">
      {links.map(([label, href]) => <Link key={href} href={href} onNavigate={() => navigate(href)} aria-current={current(href) ? href.includes("#") ? "location" : "page" : undefined}
        className="flex min-h-12 items-center text-sm font-medium text-muted hover:text-ink aria-[current]:text-ink aria-[current]:underline underline-offset-4">{label}</Link>)}
      <Link href="/download" onClick={() => setOpen(false)} className="button button-primary mt-3 w-full">Download for Android</Link>
    </nav>
  </header>;
}
