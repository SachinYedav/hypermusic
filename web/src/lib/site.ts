export const repository = "https://github.com/SachinYedav/hypermusic";

function readSiteUrl(value: string | undefined) {
  const origin = value?.trim();
  if (!origin) return undefined;

  const url = URL.canParse(origin) ? new URL(origin) : undefined;
  if (!/^https:\/\/[^/\\?#@\s]+\/?$/i.test(origin) || !url) {
    throw new Error("NEXT_PUBLIC_SITE_URL must be an HTTPS origin without a path, credentials, query, or fragment.");
  }
  return url.origin;
}

export const siteUrl = readSiteUrl(process.env.NEXT_PUBLIC_SITE_URL);
