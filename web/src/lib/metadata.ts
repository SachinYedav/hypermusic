import type { Metadata } from "next";
import { siteUrl } from "./site";

export function pageMetadata(path: string, title: string, description: string): Metadata {
  const url = siteUrl ? path === "/" ? siteUrl : new URL(path, siteUrl).href : undefined;
  const images = siteUrl ? [{
    url: new URL("/images/hypermusic-cover.jpg", siteUrl).href,
    width: 1440,
    height: 670,
    alt: "HyperMusic for Android, showing the home, player and synced lyrics screens",
  }] : undefined;

  return {
    title,
    description,
    alternates: url ? { canonical: url } : undefined,
    openGraph: { type: "website", siteName: "HyperMusic", title, description, url, images },
    twitter: { card: "summary_large_image", title, description, images },
  };
}
