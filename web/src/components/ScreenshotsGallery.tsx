"use client";

import Image from "next/image";
import { useMediaRail } from "./useMediaRail";
import { MediaRailControls } from "./MediaRailControls";

const screens = [
  ["Home & Mixes", "home", "Browse song mixes, mood categories and community playlists."],
  ["Audio player", "player", "Play music with artwork-based colors, playback controls and a sleep timer."],
  ["Synced lyrics", "lyrics", "Follow lyrics when available, request translations and share selected lines."],
  ["Your library", "library", "Find saved music, supported downloads and audio stored on your phone."],
  ["Search & genres", "search", "Search tracks, albums and artists with suggestions and genre charts."],
  ["Sound equalizer", "equalizer", "Adjust available equalizer bands and effects supported by your device."],
  ["Podcasts", "podcasts", "Browse shows and listen to supported episodes."],
] as const;

export function ScreenshotsGallery() {
  const { rail, stops, active, syncActive, goTo, handleKey } = useMediaRail();
  return <section id="gallery" className="page-shell section-space border-t border-line" aria-labelledby="screenshots-heading">
    <h2 id="screenshots-heading" className="section-title">Inside HyperMusic</h2>
    <p className="mt-3 text-base text-muted">The app, from discovery to your next track.</p>
    <div id="screenshot-carousel" ref={rail} tabIndex={0} role="region" aria-label="HyperMusic screenshots. Use Left, Right, Home and End to browse."
      onScroll={syncActive} onKeyDown={handleKey} className="no-scrollbar relative mt-8 flex snap-x snap-mandatory gap-8 overflow-x-auto p-1">
      {screens.map(([title, image, description]) => <figure key={image} className="w-60 shrink-0 snap-start sm:w-65 lg:w-[calc((100%-6rem)/4)]">
        <Image src={`/mockups/mockup_${image}.webp`} alt={`${title} in HyperMusic for Android`} width={700} height={1373} sizes="(min-width: 1240px) 268px, (min-width: 1024px) calc((100vw - 168px) / 4), (min-width: 640px) 260px, 240px" className="h-auto w-full" />
        <figcaption className="mt-5"><h3 className="text-base font-semibold text-ink">{title}</h3><p className="mt-2 text-sm leading-relaxed text-muted">{description}</p></figcaption>
      </figure>)}
    </div>
    <MediaRailControls id="screenshot-carousel" label="screenshots" count={stops.length} active={active} goTo={goTo} />
  </section>;
}
