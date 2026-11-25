import React from "react";
import Link from "next/link";
import { BrandIcon } from "./icons";

export function Brand({ className = "" }: { className?: string }) {
  return (
    <Link
      href="/"
      className={`inline-flex min-h-11 items-center gap-1.5 ${className}`}
      aria-label="HyperMusic home"
    >
      <BrandIcon className="size-6 shrink-0" color="#DC143C" strokeWidth={2.4} />
      <div className="flex items-baseline tracking-tight font-sans">
        <span className="font-semibold text-[19px] text-ink tracking-[-0.03em]">
          Hyper
        </span>
        <span className="font-normal text-[19px] text-metadata tracking-[-0.03em]">
          Music
        </span>
      </div>
    </Link>
  );
}
