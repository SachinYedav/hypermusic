"use client";

import { useEffect, useRef, useState, type KeyboardEvent } from "react";

export function useMediaRail() {
  const rail = useRef<HTMLDivElement>(null);
  const [stops, setStops] = useState<number[]>([]);
  const [active, setActive] = useState(0);
  const nearest = (positions: number[], left: number) => positions.reduce((best, stop, index) =>
    Math.abs(stop - left) < Math.abs(positions[best] - left) ? index : best, 0);

  useEffect(() => {
    const track = rail.current;
    if (!track) return;
    const measure = () => {
      const max = Math.max(0, track.scrollWidth - track.clientWidth);
      const positions: number[] = [];
      for (const card of Array.from(track.children) as HTMLElement[]) {
        const left = Math.min(card.offsetLeft, max);
        if (!positions.length || left - positions[positions.length - 1] > 1) positions.push(left);
      }
      setStops(positions);
      setActive(nearest(positions, track.scrollLeft));
    };
    const observer = new ResizeObserver(measure);
    observer.observe(track);
    for (const child of track.children) observer.observe(child);
    measure();
    return () => observer.disconnect();
  }, []);

  const syncActive = () => { if (rail.current && stops.length) setActive(nearest(stops, rail.current.scrollLeft)); };
  const goTo = (index: number) => {
    const left = stops[index];
    if (left !== undefined) rail.current?.scrollTo({ left, behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "auto" : "smooth" });
  };
  const handleKey = (event: KeyboardEvent<HTMLDivElement>) => {
    const target = { ArrowLeft: active - 1, ArrowRight: active + 1, Home: 0, End: stops.length - 1 }[event.key];
    if (target === undefined || event.target !== event.currentTarget) return;
    event.preventDefault();
    goTo(Math.max(0, Math.min(target, stops.length - 1)));
  };
  return { rail, stops, active, syncActive, goTo, handleKey };
}
