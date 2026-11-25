"use client";

import Image from "next/image";
import { useMediaRail } from "./useMediaRail";
import { MediaRailControls } from "./MediaRailControls";

const widgets = [
  { name: "Expanded Player", grid: "4 × 4", description: "See full cover art and control playback from a larger widget.", image: "large-player.webp", width: 558, height: 720 },
  { name: "Search & Play", grid: "4 × 2", description: "Open search or control the current track from your launcher.", image: "search-and-play.webp", width: 720, height: 263 },
  { name: "Player & Shortcuts", grid: "4 × 2", description: "Keep playback controls and library shortcuts in one place.", image: "recommended-player.webp", width: 720, height: 228 },
  { name: "Compact Capsule", grid: "4 × 1", description: "Play, pause, or skip from a single row.", image: "capsule.webp", width: 674, height: 174 },
];

export function WidgetsShowcase() {
  const { rail, stops, active, syncActive, goTo, handleKey } = useMediaRail();
  const isScrollable = stops.length > 1;
  return <section id="widgets" aria-labelledby="widget-heading" className="page-shell section-space border-t border-line">
    <h2 id="widget-heading" className="section-title">Four layouts for your home screen</h2>
    <p className="mt-3 max-w-xl text-base text-muted">Keep playback controls, search, and library shortcuts on your Android launcher.</p>
    <div id="widget-carousel" ref={rail} tabIndex={isScrollable ? 0 : undefined} role="region" aria-label={isScrollable ? "Android widget previews. Use Left, Right, Home and End to browse." : "Android widget previews"}
      onScroll={syncActive} onKeyDown={isScrollable ? handleKey : undefined} className="no-scrollbar relative mt-8 flex snap-x snap-mandatory gap-6 overflow-x-auto p-1 lg:grid lg:grid-cols-2 lg:snap-none lg:gap-x-8 lg:gap-y-10 lg:overflow-visible">
      {widgets.map(widget => <article key={widget.name} className="flex shrink-0 basis-full snap-start flex-col sm:basis-[calc(50%-0.75rem)]">
        <div className="flex h-64 items-center justify-center rounded-panel bg-surface px-6">
          <Image src={`/images/widgets/${widget.image}`} alt={`${widget.name} Android widget preview`} width={widget.width} height={widget.height} className="h-auto max-h-[90%] w-auto max-w-full object-contain" />
        </div>
        <div className="mt-4 flex flex-wrap items-baseline gap-x-3"><h3 className="text-lg font-semibold text-ink">{widget.name}</h3><span className="text-sm text-metadata" title="Target Android launcher grid size">{widget.grid}</span></div>
        <p className="mt-2 text-base text-muted">{widget.description}</p>
      </article>)}
    </div>
    {isScrollable && <MediaRailControls id="widget-carousel" label="widgets" count={stops.length} active={active} goTo={goTo}>
      <div className="flex flex-wrap" role="group" aria-label="Widget carousel positions">
        {stops.map((stop, index) => <button key={stop} type="button" aria-label={`Show widgets starting with ${widgets[index].name}`} aria-controls="widget-carousel" aria-current={active === index ? "true" : undefined} onClick={() => goTo(index)} className="flex size-11 items-center justify-center rounded-control">
          <span aria-hidden="true" className={`size-2 rounded-full ${active === index ? "bg-ink" : "bg-control"}`} />
        </button>)}
      </div>
    </MediaRailControls>}
  </section>;
}
