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
  metadataBase: new URL("https://kat-music.vercel.app"),
  title: {
    default: "KatMusic — Your music. Your universe.",
    template: "%s · KatMusic",
  },
  description:
    "A native Android experience for YouTube Music, with word-synced lyrics, offline downloads, a native audio engine, Automix and six customisable player styles.",
  applicationName: "KatMusic",
  authors: [{ name: "ItzFallenMe", url: "https://github.com/ItzFallenMe" }],
  creator: "ItzFallenMe",
  keywords: [
    "KatMusic",
    "YouTube Music Android",
    "Android music player",
    "synced lyrics",
    "offline music",
    "parametric equalizer",
    "Automix",
    "APK download",
  ],
  openGraph: {
    title: "KatMusic — Your music. Your universe.",
    description:
      "A native Android experience for YouTube Music. Explore synced lyrics, offline listening, a native audio engine and six player styles.",
    siteName: "KatMusic",
    type: "website",
    url: "https://kat-music.vercel.app",
    images: [{ url: "/icon.png", width: 512, height: 512, alt: "KatMusic app icon" }],
  },
  twitter: {
    card: "summary",
    title: "KatMusic — Your music. Your universe.",
    description:
      "A native Android music experience with synced lyrics, offline downloads and a powerful audio engine.",
    images: ["/icon.png"],
  },
  icons: { icon: "/katmusic_logo.svg", apple: "/icon.png" },
  robots: { index: true, follow: true },
};

export const viewport: Viewport = {
  themeColor: "#05080e",
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
