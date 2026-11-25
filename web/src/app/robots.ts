import type { MetadataRoute } from "next";
import { siteUrl } from "@/lib/site";
export const dynamic = "force-static";
export default function robots(): MetadataRoute.Robots {
  return {
    rules: { userAgent: "*", allow: "/" },
    ...(siteUrl && process.env.VERCEL_ENV !== "preview" ? { sitemap: `${siteUrl}/sitemap.xml` } : {}),
  };
}
