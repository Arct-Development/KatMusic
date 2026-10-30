/**
 * © 2026 ItzFallenMe — All Rights Reserved
 * github ── https://github.com/ItzFallenMe
 */

import type { Metadata, Viewport } from "next";
import { Inter, Outfit } from "next/font/google";
import "./globals.css";

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" });
const outfit = Outfit({ subsets: ["latin"], variable: "--font-outfit" });

export const metadata: Metadata = {
  title: "KatMusic — YouTube Music, on your terms",
  description:
    "A native Android client for YouTube Music: ad-free streaming, offline downloads, word-by-word lyrics, a parametric equaliser and automix. Download the latest APK directly.",
  applicationName: "KatMusic",
  authors: [{ name: "ItzFallenMe", url: "https://github.com/ItzFallenMe" }],
  keywords: [
    "KatMusic",
    "YouTube Music client",
    "Android music player",
    "APK download",
    "lossless",
    "lyrics",
  ],
  openGraph: {
    title: "KatMusic — YouTube Music, on your terms",
    description:
      "Native Android client for YouTube Music. Stream, download, read synced lyrics and blend tracks with automix.",
    siteName: "KatMusic",
    type: "website",
  },
  icons: { icon: "/icon.png", apple: "/icon.png" },
};

export const viewport: Viewport = {
  themeColor: "#050B18",
  width: "device-width",
  initialScale: 1,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" className={`${inter.variable} ${outfit.variable}`}>
      <body className="font-sans antialiased text-slate-200">{children}</body>
    </html>
  );
}
