import Image from "next/image";
import Link from "next/link";
import { Download } from "lucide-react";
import { repository } from "@/lib/site";
import { GithubIcon } from "./icons";

export function Hero() {
  return <section id="intro" className="page-shell grid items-center gap-10 pb-12 pt-28 sm:pt-32 lg:grid-cols-[1.1fr_0.9fr] lg:pb-16">
    <div className="max-w-xl">
      <p className="mb-5 text-sm font-medium text-metadata">Open source · Android 8.0 and later</p>
      <h1 className="text-[clamp(2.5rem,5vw,4rem)] font-semibold leading-[1.08] tracking-[-0.035em] text-ink">A music player<br />for your Android.</h1>
      <p className="mt-6 max-w-lg text-lg leading-relaxed text-muted">Discover music from YouTube Music, play files on your phone, and follow synced lyrics when available. Your library stays on your device.</p>
      <div className="mt-8 flex flex-wrap gap-3">
        <Link href="/download" className="button button-primary"><Download aria-hidden="true" className="size-4" />Download for Android</Link>
        <a href={repository} className="button button-secondary"><GithubIcon className="size-4" />View source code</a>
      </div>
      <p className="mt-5 text-sm text-metadata">No HyperMusic account required.</p>
    </div>
    <div className="relative isolate mx-auto aspect-[1.05] w-full max-w-lg lg:max-w-xl">
      <Image
        src="/mockups/mockup_home.webp"
        alt="HyperMusic home screen with music discovery and mixes"
        width={700}
        height={1373}
        loading="eager"
        sizes="(min-width: 1024px) 280px, (min-width: 640px) 240px, 46vw"
        className="absolute left-[3%] top-[12%] z-10 h-auto w-[49%] rotate-[8deg] drop-shadow-md sm:left-[5%] sm:top-[10%] sm:w-[47%]"
      />
      <Image
        src="/mockups/mockup_player.webp"
        alt="HyperMusic player with album artwork, playback controls and a sleep timer"
        width={700}
        height={1373}
        priority
        sizes="(min-width: 1024px) 310px, (min-width: 640px) 270px, 51vw"
        className="absolute left-[39%] top-[2%] z-0 h-auto w-[53%] rotate-[8deg] drop-shadow-md sm:left-[41%] sm:w-[51%]"
      />
    </div>
  </section>;
}
