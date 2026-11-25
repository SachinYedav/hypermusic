import React from "react";
import { ResourceText } from "./ResourceText";
import { repository } from "@/lib/site";

const questions = [
  [
    "Do I need an account to use the app?",
    "No HyperMusic account is required. The app connects directly to YouTube Music and lyric services for online streaming and metadata. Your library, playlists, and settings remain entirely on your device. See the privacy page for details.",
  ],
  [
    "Can I listen without an internet connection?",
    "Yes. You can play tracks you have downloaded or audio files already stored on your phone. Online search, streaming and fetching new lyrics require an active connection.",
  ],
  [
    "Which Android versions are supported?",
    "Android 8.0 (Oreo) and later. A single universal APK is provided for all supported devices. Audio effects and equalizer availability depend on your device firmware.",
  ],
  [
    "Can I create and manage playlists?",
    "Yes. You can create local playlists in your library, organize tracks, and add songs as you discover them across mixes and charts.",
  ],
  [
    "Where can I get updates or report a problem?",
    "Use the Download page for the latest build, Releases for other published versions, and Changelog for release notes. Report reproducible problems through the GitHub issue tracker; remove private information from logs before sharing.",
  ],
] as const;

export function FaqSection() {
  return (
    <section id="faq" className="page-shell section-space border-t border-line">
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8 lg:gap-14 items-start">
        <div className="lg:col-span-4">
          <h2 className="section-title">
            Questions about HyperMusic
          </h2>
          <p className="text-[15px] text-muted mt-2.5 leading-relaxed">
            Quick clarity on accounts, offline music, updates, and open-source licensing.
          </p>
        </div>

        <div className="lg:col-span-8 divide-y divide-line border-y border-line">
          {questions.map(([q, a]) => (
            <details key={q} className="group py-4 sm:py-5 transition-colors">
              <summary className="flex min-h-11 justify-between items-center cursor-pointer list-none text-[16px] sm:text-[17px] font-medium text-ink group-open:text-black">
                <span>{q}</span>
                <span aria-hidden="true" className="text-xl font-light text-metadata transition-transform duration-200 group-open:rotate-45 ml-4 shrink-0">
                  +
                </span>
              </summary>
              <p className="mt-2.5 text-[14px] sm:text-[15px] text-muted leading-[1.75] pr-6">
                <ResourceText text={a} links={[
                  { text: "privacy page", url: "/privacy" }, { text: "Download page", url: "/download" },
                  { text: "Releases", url: "/releases" }, { text: "Changelog", url: "/changelog" },
                  { text: "GitHub issue tracker", url: `${repository}/issues` }
                ]} />
              </p>
            </details>
          ))}
        </div>
      </div>
    </section>
  );
}
