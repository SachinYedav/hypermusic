import { Search, Headphones, Languages, CodeXml } from "lucide-react";

const featureGroups = [
  {
    icon: Search,
    title: "Discovery & library",
    description: "Find music on YouTube Music and keep your favorites together.",
    features: [
      ["Search & discovery", "Browse songs, artists, albums, podcasts, charts and mood categories."],
      ["Playlists & offline music", "Create playlists, save favorites, download supported tracks and play files on your phone."],
    ],
  },
  {
    icon: Headphones,
    title: "Playback controls",
    description: "Keep listening with your screen off or while using another app.",
    features: [
      ["Queue & sleep timer", "Reorder upcoming tracks. Stop after a chosen time or when the current track ends."],
      ["Sound & Android controls", "Use notification playback controls, plus equalizer and effects where your device supports them."],
    ],
  },
  {
    icon: Languages,
    title: "Lyrics & sharing",
    description: "Follow the words and share music with the people you listen with.",
    features: [
      ["Synced lyrics & translation", "Read lyrics in time with the track when available, and request a translation."],
      ["Tracks & lyric cards", "Send music links, selected lines or lyric cards through Android sharing."],
    ],
  },
] as const;

export function FeaturesGrid() {
  return <section id="features" aria-labelledby="features-heading" className="page-shell section-space border-t border-line">
    <div className="flex flex-col justify-between gap-4 md:flex-row md:items-end md:gap-10">
      <h2 id="features-heading" className="section-title">Explore HyperMusic features</h2>
      <p className="max-w-sm text-base leading-relaxed text-muted">From finding a track to making it part of your library.</p>
    </div>

    <div className="mt-8 grid divide-y divide-line rounded-panel border border-line bg-surface md:mt-10 md:grid-cols-3 md:divide-x md:divide-y-0">
      {featureGroups.map(({ icon: Icon, title, description, features }) => <article key={title} className="min-w-0 p-6 sm:p-8 md:p-6 lg:p-8">
        <Icon aria-hidden="true" className="mb-5 size-6 text-ink" strokeWidth={1.75} />
        <h3 className="text-xl font-semibold leading-tight tracking-tight text-ink">{title}</h3>
        <p className="mt-3 text-base leading-relaxed text-muted">{description}</p>
        <dl className="mt-7 space-y-6 border-t border-line pt-6">
          {features.map(([name, detail]) => <div key={name}>
            <dt className="text-sm font-semibold text-ink">{name}</dt>
            <dd className="mt-2 text-sm leading-relaxed text-muted">{detail}</dd>
          </div>)}
        </dl>
      </article>)}
    </div>

    <p className="mt-5 flex items-start gap-2.5 text-sm leading-relaxed text-metadata">
      <CodeXml aria-hidden="true" className="mt-0.5 size-4 shrink-0" />
      <span>Open source. Inspect the code, build HyperMusic yourself or report a problem on GitHub.</span>
    </p>
  </section>;
}
