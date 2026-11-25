import { Hero } from "@/components/Hero";
import { FeaturesGrid } from "@/components/FeaturesGrid";
import { WidgetsShowcase } from "@/components/WidgetsShowcase";
import { ScreenshotsGallery } from "@/components/ScreenshotsGallery";
import { AboutSection } from "@/components/AboutSection";
import { FaqSection } from "@/components/FaqSection";
import { ReleasesSection } from "@/components/ReleasesSection";
import { pageMetadata } from "@/lib/metadata";
import { latestRelease } from "@/lib/release";
import { repository, siteUrl } from "@/lib/site";

const description = "HyperMusic is an open-source music player for Android.  Discover millions of tracks from YouTube Music, play files from your device, and follow synced lyrics when available. Find APK releases and source code.";
export const metadata = pageMetadata("/", "HyperMusic — Open-source music player for Android", description);

export default function Home() {
  const schema = siteUrl ? {
    "@context": "https://schema.org",
    "@graph": [
      { "@type": "WebSite", name: "HyperMusic", url: siteUrl },
      {
        "@type": "MobileApplication",
        name: "HyperMusic",
        description,
        applicationCategory: "MultimediaApplication",
        operatingSystem: "Android 8.0 or later",
        url: new URL("/releases", siteUrl).href,
        license: `${repository}/blob/main/LICENSE`,
        ...(latestRelease ? { downloadUrl: latestRelease.apk.url, softwareVersion: latestRelease.version } : {}),
      },
    ],
  } : undefined;

  return (
    <main id="main" tabIndex={-1}>
      {schema && <script type="application/ld+json" dangerouslySetInnerHTML={{
        __html: JSON.stringify(schema).replace(/</g, "\\u003c"),
      }} />}
      <Hero />
      <FeaturesGrid />
      <WidgetsShowcase />
      <ScreenshotsGallery />
      <AboutSection />
      <FaqSection />
      <ReleasesSection />
    </main>
  );
}
