/**
 * © 2026 ItzFallenMe — All Rights Reserved
 * github ── https://github.com/ItzFallenMe
 */

"use client";

import { useEffect, useMemo, useState } from "react";
import {
  ArrowUpRight,
  CheckCircle2,
  Download,
  FileCheck2,
  Github,
  Headphones,
  ListMusic,
  Mic2,
  MonitorSmartphone,
  Music4,
  Radio,
  Repeat,
  ShieldCheck,
  Sliders,
  Sparkles,
  Timer,
  Compass,
  Waves,
} from "lucide-react";

import {
  RELEASES_PAGE,
  REPO_PAGE,
  fetchReleases,
  formatBytes,
  formatDate,
  isApk,
  recommendFor,
  type Release,
} from "@/lib/releases";

const FEATURES = [
  {
    icon: Radio,
    title: "Streams the whole catalogue",
    body: "Songs, albums, playlists, artist pages, mixes and podcasts — with background playback, a media notification and lock-screen controls.",
  },
  {
    icon: Waves,
    title: "Automix and a parametric EQ",
    body: "BPM and phase detection, a transition planner and WSOLA time stretching blend one track into the next inside the native audio engine.",
  },
  {
    icon: Sliders,
    title: "Tuned to your headphones",
    body: "A parametric equaliser with AutoEQ device profiles, loudness normalisation and a studio-clarity curve you can dial back.",
  },
  {
    icon: Mic2,
    title: "Word-by-word lyrics",
    body: "Syllable-timed lyrics from a dozen sources, with translation, size and offset controls, plus a share card that renders a verse as an image.",
  },
  {
    icon: Download,
    title: "Downloads that behave",
    body: "One transfer at a time with a visible queue, Wi-Fi-only pausing that actually works, and FLAC, Opus or AAC files tagged on save.",
  },
  {
    icon: Headphones,
    title: "Built for better hardware",
    body: "USB exclusive output for a DAC, a direct internal path that bypasses system mixing, and sample-rate matching for bit-perfect playback.",
  },
  {
    icon: Sparkles,
    title: "A theme that follows the music",
    body: "Material 3 expression built from the current artwork, liquid-glass surfaces, expressive motion and a home-screen widget.",
  },
  {
    icon: Compass,
    title: "Discovery that knows you",
    body: "Genre explorer, discovery radar, smart mixes and AI picks that read your history — plus friends, stats and listen-together rooms.",
  },
];

const HIGHLIGHTS = [
  { icon: ShieldCheck, label: "No ads, no trackers" },
  { icon: Timer, label: "Sleep timer & speed control" },
  { icon: Repeat, label: "Queue, shuffle, autoplay radio" },
  { icon: ListMusic, label: "Playlists, likes, local files" },
  { icon: MonitorSmartphone, label: "Widget & Android Auto" },
  { icon: Music4, label: "Last.fm, Discord, Canvas" },
];

function Pill({ children }: { children: React.ReactNode }) {
  return (
    <span className="inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/[0.04] px-3 py-1 text-xs font-medium text-slate-300">
      {children}
    </span>
  );
}

function AssetButton({ asset }: { asset: Release["assets"][number] }) {
  return (
    <a
      href={asset.downloadUrl}
      className="group flex items-center justify-between gap-4 rounded-2xl border border-white/10 bg-white/[0.03] px-4 py-3 transition hover:border-sky-400/50 hover:bg-sky-400/[0.08]"
    >
      <span className="min-w-0">
        <span className="block truncate text-sm font-semibold text-white">{asset.name}</span>
        <span className="mt-0.5 block text-xs text-slate-400">
          {isApk(asset.name) ? `${recommendFor(asset.name)} · ` : ""}
          {formatBytes(asset.size)}
        </span>
      </span>
      <span className="flex shrink-0 items-center gap-1 rounded-full bg-sky-500/15 px-3 py-1.5 text-xs font-semibold text-sky-300 transition group-hover:bg-sky-500/25">
        <Download className="h-3.5 w-3.5" />
        Get
      </span>
    </a>
  );
}

export default function HomePage() {
  const [releases, setReleases] = useState<Release[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchReleases(12)
      .then((list) => {
        if (!cancelled) setReleases(list);
      })
      .catch((err: Error) => {
        if (!cancelled) setError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const latest = releases?.[0] ?? null;
  const apk = useMemo(() => latest?.assets.filter((asset) => isApk(asset.name)) ?? [], [latest]);
  const primary = apk.find((asset) => !asset.name.toLowerCase().includes("android7")) ?? apk[0];

  return (
    <main className="relative min-h-screen overflow-hidden">
      <div className="pointer-events-none absolute inset-0 -z-10">
        <div className="absolute left-1/2 top-[-18rem] h-[36rem] w-[36rem] -translate-x-1/2 rounded-full bg-sky-500/20 blur-[120px]" />
        <div className="absolute bottom-[-14rem] right-[-10rem] h-[30rem] w-[30rem] rounded-full bg-blue-700/20 blur-[140px]" />
      </div>

      <header className="mx-auto flex max-w-6xl items-center justify-between px-6 py-6">
        <div className="flex items-center gap-3">
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img src="/icon.png" alt="KatMusic" className="h-10 w-10 rounded-xl" />
          <span className="font-[family-name:var(--font-outfit)] text-lg font-semibold tracking-tight text-white">
            KatMusic
          </span>
        </div>
        <nav className="flex items-center gap-2 text-sm">
          <a
            href="#download"
            className="rounded-full px-4 py-2 text-slate-300 transition hover:bg-white/5 hover:text-white"
          >
            Download
          </a>
          <a
            href={REPO_PAGE}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-2 rounded-full border border-white/10 px-4 py-2 text-slate-200 transition hover:border-white/25 hover:text-white"
          >
            <Github className="h-4 w-4" />
            GitHub
          </a>
        </nav>
      </header>

      <section className="mx-auto max-w-6xl px-6 pt-10 pb-16 text-center sm:pt-16">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img
          src="/icon.png"
          alt="KatMusic logo"
          className="mx-auto mb-7 h-24 w-24 rounded-[28px] shadow-[0_25px_80px_-25px_rgba(14,165,233,0.8)] sm:h-28 sm:w-28"
        />

        <div className="mx-auto mb-6 flex flex-wrap items-center justify-center gap-2">
          <Pill>
            <CheckCircle2 className="h-3.5 w-3.5 text-sky-400" />
            {latest ? `v${latest.tag.replace(/^v/, "")} is out` : "Free and open source"}
          </Pill>
          <Pill>Android 10+</Pill>
          <Pill>No account needed</Pill>
        </div>

        <h1 className="font-[family-name:var(--font-outfit)] text-4xl font-semibold leading-[1.05] tracking-tight text-white sm:text-6xl">
          YouTube Music,
          <br className="hidden sm:block" />
          <span className="bg-gradient-to-r from-sky-300 via-sky-400 to-blue-500 bg-clip-text text-transparent">
            {" "}
            without the noise.
          </span>
        </h1>

        <p className="mx-auto mt-6 max-w-2xl text-base leading-relaxed text-slate-400 sm:text-lg">
          KatMusic is a native Android client for YouTube Music: ad-free streaming, real
          downloads, syllable-timed lyrics and an audio engine that crossfades your queue.
          Install it straight from this page.
        </p>

        <div className="mt-9 flex flex-col items-center justify-center gap-3 sm:flex-row">
          {primary ? (
            <a
              href={primary.downloadUrl}
              className="inline-flex items-center gap-2 rounded-full bg-sky-500 px-6 py-3 text-sm font-semibold text-slate-950 shadow-[0_10px_40px_-10px_rgba(14,165,233,0.9)] transition hover:bg-sky-400"
            >
              <Download className="h-4 w-4" />
              Download {primary.name}
            </a>
          ) : (
            <a
              href={RELEASES_PAGE}
              className="inline-flex items-center gap-2 rounded-full bg-sky-500 px-6 py-3 text-sm font-semibold text-slate-950 transition hover:bg-sky-400"
            >
              <Download className="h-4 w-4" />
              Get the latest build
            </a>
          )}
          <a
            href="#download"
            className="inline-flex items-center gap-2 rounded-full border border-white/15 px-6 py-3 text-sm font-semibold text-white transition hover:border-white/30 hover:bg-white/5"
          >
            Every release
            <ArrowUpRight className="h-4 w-4" />
          </a>
        </div>

        <div className="mt-8 flex flex-wrap items-center justify-center gap-x-5 gap-y-2 text-xs text-slate-500">
          {HIGHLIGHTS.map((item) => (
            <span key={item.label} className="inline-flex items-center gap-1.5">
              <item.icon className="h-3.5 w-3.5 text-slate-400" />
              {item.label}
            </span>
          ))}
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-6 pb-20">
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          {FEATURES.map((feature) => (
            <div
              key={feature.title}
              className="rounded-3xl border border-white/10 bg-white/[0.025] p-5 transition hover:border-white/20 hover:bg-white/[0.045]"
            >
              <feature.icon className="h-5 w-5 text-sky-400" />
              <h3 className="mt-4 font-[family-name:var(--font-outfit)] text-base font-semibold text-white">
                {feature.title}
              </h3>
              <p className="mt-2 text-sm leading-relaxed text-slate-400">{feature.body}</p>
            </div>
          ))}
        </div>
      </section>

      <section id="download" className="mx-auto max-w-6xl px-6 pb-24">
        <div className="mb-8 flex flex-col gap-2 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 className="font-[family-name:var(--font-outfit)] text-3xl font-semibold tracking-tight text-white">
              Downloads
            </h2>
            <p className="mt-2 text-sm text-slate-400">
              Direct APKs, hosted on GitHub releases. No redirects, no waiting.
            </p>
          </div>
          <a
            href={RELEASES_PAGE}
            target="_blank"
            rel="noreferrer"
            className="inline-flex items-center gap-1.5 text-sm font-medium text-sky-300 hover:text-sky-200"
          >
            Release notes on GitHub
            <ArrowUpRight className="h-4 w-4" />
          </a>
        </div>

        {error ? (
          <div className="rounded-3xl border border-amber-400/30 bg-amber-400/5 p-6 text-sm text-amber-200">
            {error}{" "}
            <a href={RELEASES_PAGE} className="underline underline-offset-4">
              Open the releases page instead
            </a>
            .
          </div>
        ) : releases === null ? (
          <div className="grid gap-3 sm:grid-cols-2">
            {[0, 1].map((key) => (
              <div
                key={key}
                className="h-32 animate-pulse rounded-3xl border border-white/5 bg-white/[0.03]"
              />
            ))}
          </div>
        ) : releases.length === 0 ? (
          <div className="rounded-3xl border border-white/10 bg-white/[0.03] p-6 text-sm text-slate-300">
            No published builds yet.{" "}
            <a href={RELEASES_PAGE} className="underline underline-offset-4">
              Watch the repository
            </a>{" "}
            to be notified.
          </div>
        ) : (
          <div className="space-y-4">
            <div className="rounded-3xl border border-sky-400/25 bg-gradient-to-br from-sky-500/[0.10] to-transparent p-6">
              <div className="flex flex-wrap items-center gap-2 text-xs">
                <span className="rounded-full bg-sky-500/20 px-2.5 py-1 font-semibold text-sky-200">
                  Latest
                </span>
                <span className="text-slate-400">
                  {latest?.name} · {formatDate(latest?.publishedAt ?? "")}
                </span>
              </div>

              <div className="mt-4 grid gap-3 sm:grid-cols-2">
                {apk.map((asset) => (
                  <AssetButton key={asset.name} asset={asset} />
                ))}
              </div>

              {latest?.assets.some((asset) => !isApk(asset.name)) ? (
                <div className="mt-3 flex flex-wrap gap-2 text-xs text-slate-400">
                  {latest?.assets
                    .filter((asset) => !isApk(asset.name))
                    .map((asset) => (
                      <a
                        key={asset.name}
                        href={asset.downloadUrl}
                        className="inline-flex items-center gap-1.5 rounded-full border border-white/10 px-3 py-1.5 transition hover:border-white/25 hover:text-white"
                      >
                        <FileCheck2 className="h-3.5 w-3.5" />
                        {asset.name}
                      </a>
                    ))}
                </div>
              ) : null}

              <p className="mt-4 text-xs leading-relaxed text-slate-500">
                Recommended: the standard build for Android 10 and newer. The Android 7 build
                exists for older phones and uses classic media controls; the uncompressed
                variant is only useful for debugging.
              </p>
            </div>

            {releases.slice(1).map((release) => (
              <details
                key={release.tag}
                className="group rounded-3xl border border-white/10 bg-white/[0.02] p-5"
              >
                <summary className="flex cursor-pointer list-none items-center justify-between gap-4">
                  <span>
                    <span className="block text-sm font-semibold text-white">{release.name}</span>
                    <span className="mt-0.5 block text-xs text-slate-500">
                      {formatDate(release.publishedAt)} · {release.apkCount} APK
                      {release.apkCount === 1 ? "" : "s"}
                      {release.prerelease ? " · pre-release" : ""}
                    </span>
                  </span>
                  <span className="text-xs font-medium text-sky-300 group-open:hidden">Show</span>
                  <span className="hidden text-xs font-medium text-sky-300 group-open:inline">
                    Hide
                  </span>
                </summary>
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                  {release.assets.map((asset) => (
                    <AssetButton key={asset.name} asset={asset} />
                  ))}
                </div>
              </details>
            ))}
          </div>
        )}

        <div className="mt-10 grid gap-4 sm:grid-cols-3">
          {[
            {
              step: "1",
              title: "Download an APK",
              body: "Pick the standard build above. The file is roughly 20–30 MB.",
            },
            {
              step: "2",
              title: "Allow the install",
              body: "Your phone will ask to allow installs from the browser once. That is Android's normal sideload prompt.",
            },
            {
              step: "3",
              title: "Open and play",
              body: "No account required. Sign in to YouTube Music only if you want your own library and recommendations.",
            },
          ].map((item) => (
            <div key={item.step} className="rounded-3xl border border-white/10 bg-white/[0.02] p-5">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-white/10 text-sm font-semibold text-white">
                {item.step}
              </span>
              <h3 className="mt-3 text-sm font-semibold text-white">{item.title}</h3>
              <p className="mt-1.5 text-xs leading-relaxed text-slate-400">{item.body}</p>
            </div>
          ))}
        </div>
      </section>

      <footer className="border-t border-white/[0.06] bg-black/30">
        <div className="mx-auto flex max-w-6xl flex-col items-center justify-between gap-4 px-6 py-8 text-xs text-slate-500 sm:flex-row">
          <div className="flex items-center gap-3">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/icon.png" alt="" className="h-6 w-6 rounded-md" />
            <span>
              KatMusic · Designed and built by{" "}
              <a
                href="https://github.com/ItzFallenMe"
                className="text-slate-300 underline-offset-4 hover:underline"
              >
                ItzFallenMe
              </a>
            </span>
          </div>
          <div className="flex items-center gap-4">
            <a href={REPO_PAGE} className="hover:text-slate-300">
              Source
            </a>
            <a href={RELEASES_PAGE} className="hover:text-slate-300">
              Releases
            </a>
            <span>GPL-3.0</span>
          </div>
        </div>
      </footer>
    </main>
  );
}
