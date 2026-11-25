import type { NextConfig } from "next";
import { PHASE_PRODUCTION_BUILD } from "next/constants";
import { siteUrl } from "./src/lib/site";

const nextConfig: NextConfig = {
  output: "export",
  images: {
    unoptimized: true,
  },
};

export default function config(phase: string) {
  if (phase === PHASE_PRODUCTION_BUILD && !siteUrl) {
    throw new Error("Set NEXT_PUBLIC_SITE_URL to the confirmed HTTPS origin before building.");
  }
  return nextConfig;
}
