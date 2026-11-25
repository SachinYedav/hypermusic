import { repository } from "@/lib/site";

export function AboutSection() {
  return <section id="about" aria-labelledby="about-heading" className="page-shell section-space border-t border-line">
    <h2 id="about-heading" className="section-title">About HyperMusic</h2>
    <div className="mt-5 max-w-3xl space-y-4 text-base leading-relaxed text-muted">
      <p>HyperMusic is an independent, open-source music player for Android, developed by <a href={repository} className="text-link">Sachin Yedav</a>. It brings YouTube Music discovery and audio stored on your phone into one app, with playlists, background playback, supported downloads and synced lyrics when available.</p>
      <p>The <a href={repository} className="text-link">source code is available on GitHub</a>, where you can follow development, build the app yourself or report a problem. HyperMusic is an independent project and is not affiliated with Google or YouTube.</p>
    </div>
  </section>;
}
