# HyperMusic Website

The website for [HyperMusic](../README.md) provides Android APK downloads, release notes, and project information. It is built with **Next.js**, **React**, and **Tailwind CSS** and exported as a static site.

## Getting Started

Use **Node.js 22** to match CI. From the repository root, run:

```bash
cd web
npm ci
npm run dev
```

Open [http://localhost:3000](http://localhost:3000).

## Build & Preview

Run these commands from `web/`. Set [`NEXT_PUBLIC_SITE_URL`](#environment-variables) before running the build.

- **Type Check** — `npm run typecheck` checks TypeScript.
- **Static Build** — `npm run build` generates the site in `web/out/` and checks the export.
- **Local Preview** — After building, run `npm run preview` and open [http://127.0.0.1:4173](http://127.0.0.1:4173).

## Shared Files

- **[Release Feed](../public/releases.json)** — Provides release data for the homepage, releases page, and changelog.
- **[Legal Content](../legal/content.json)** — Shared by the Android app and the website’s Privacy, Terms, and Copyright pages.
- **[Third-Party Notices](public/THIRD_PARTY_NOTICES.txt)** — Published with the static site.

## Environment Variables

For local builds, set these in `web/.env.local`. For hosted builds, use GitHub Actions repository variables or the hosting provider’s build environment.

| Variable | Description | Required |
| :--- | :--- | :--- |
| `NEXT_PUBLIC_SITE_URL` | HTTPS site origin used for canonical URLs, social metadata, and the sitemap. | Yes, for static builds |
| `NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION` | Content value of the Google Search Console verification tag. | Optional |

## Release Pipeline

Changelogs are written in [`releases/<tag>.json`](../releases/). The [release workflow](../.github/workflows/build-release.yml) runs on `v*` tag pushes and checks that the tagged commit is in the history of `main`. It publishes the verified APK and updates the release feed.

[Web CI](../.github/workflows/web-ci.yml) rebuilds the site with the updated feed and requests deployment when the Vercel deploy hook is configured.
