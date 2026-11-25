import type { Metadata } from "next";
import { Inter } from "next/font/google";
import { Navbar } from "@/components/Navbar";
import { Footer } from "@/components/Footer";
import { siteUrl } from "@/lib/site";
import "./globals.css";

const inter = Inter({
  subsets: ["latin"],
  display: "swap",
  variable: "--font-inter",
});

const googleVerification = process.env.NEXT_PUBLIC_GOOGLE_SITE_VERIFICATION?.trim();

export const metadata: Metadata = {
  ...(siteUrl ? { metadataBase: new URL(siteUrl) } : {}),
  ...(googleVerification ? { verification: { google: googleVerification } } : {}),
  robots: { index: Boolean(siteUrl) && process.env.VERCEL_ENV !== "preview", follow: true },
};
export default function RootLayout({children}: Readonly<{children: React.ReactNode}>) {
  return (
    <html lang="en" className={inter.variable}>
      <body id="top">
        <a className="skip" href="#main">Skip to content</a>
        <Navbar/>
        {children}
        <Footer/>
      </body>
    </html>
  );
}
