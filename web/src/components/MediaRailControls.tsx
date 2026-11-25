"use client";

import type { ReactNode } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

export function MediaRailControls({ id, label, count, active, goTo, children }: {
  id: string; label: string; count: number; active: number; goTo: (index: number) => void; children?: ReactNode;
}) {
  return <div className="mt-5 flex min-h-11 items-center justify-between gap-4">
    {children ?? <p className="text-sm text-metadata">{count > 1 ? `Position ${active + 1} of ${count}` : "All previews shown"}</p>}
    <div className="flex gap-2" role="group" aria-label={`${label} navigation`}>
      {[-1, 1].map(direction => {
        const Icon = direction < 0 ? ChevronLeft : ChevronRight;
        return <button key={direction} type="button" aria-label={`${direction < 0 ? "Previous" : "Next"} ${label}`}
          aria-controls={id} disabled={active + direction < 0 || active + direction >= count}
          onClick={() => goTo(active + direction)}
          className="flex size-11 items-center justify-center rounded-control border border-control bg-canvas text-ink transition-colors enabled:hover:bg-surface disabled:opacity-40">
          <Icon aria-hidden="true" className="size-5" />
        </button>;
      })}
    </div>
  </div>;
}
