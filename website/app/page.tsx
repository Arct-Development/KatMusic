/**
 * © 2026 ItzFallenMe — All Rights Reserved
 * github ── https://github.com/ItzFallenMe
 */

"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import {
  Activity,
  ArrowUpRight,
  CheckCircle2,
  ChevronRight,
  Compass,
  Download,
  FileCheck2,
  Github,
  Headphones,
  ListMusic,
  Mic2,
  Music4,
  Repeat,
  ShieldCheck,
  Sliders,
  Sparkles,
  Timer,
  Waves,
  Zap,
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

/* ── Scroll reveal ─────────────────────────────────────────── */
function Reveal({
  children,
  delay = 0,
  className = "",
}: {
  children: React.ReactNode;
  delay?: number;
  className?: string;
}) {
  const ref = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const obs = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisible(true);
          obs.disconnect();
        }
      },
      { threshold: 0.12 },
    );
    obs.observe(el);
    return () => obs.disconnect();
  }, []);

  return (
    <div
      ref={ref}
      style={{ transitionDelay: `${delay}ms` }}
      className={`reveal ${visible ? "reveal-visible" : ""} ${className}`}
    >
      {children}
    </div>
  );
}

/* ── Animated counter ──────────────────────────────────────── */
function Counter({ to, suffix = "" }: { to: number; suffix?: string }) {
  const ref = useRef<HTMLSpanElement>(null);
  const started = useRef(false);
  const [val, setVal] = useState(0);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const obs = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting && !started.current) {
          started.current = true;
          const t0 = performance.now();
          const tick = (t: number) => {
            const p = Math.min(1, (t - t0) / 1600);
            setVal(to * (1 - Math.pow(1 - p, 3)));
            if (p < 1) requestAnimationFrame(tick);
          };
          requestAnimationFrame(tick);
          obs.disconnect();
        }
      },
      { threshold: 0.4 },
    );
    obs.observe(el);
    return () => obs.disconnect();
  }, [to]);

  return (
    <span ref={ref}>
      {Math.round(val).toLocaleString()}
      {suffix}
    </span>
  );
}

const FEATURES = [
  {
    icon: Waves,
    tag: "Engine",
    title: "Automix that blends",
    body: "BPM and phase detection, a transition planner and WSOLA time stretching crossfade one track into the next in native code.",
  },
  {
    icon: Sliders,
    tag: "Audio",
    title: "Parametric equaliser",
    body: "AutoEQ profiles for your headphones, loudness normalisation and a studio-clarity curve you can dial back or turn off.",
  },
  {
    icon: Mic2,
    tag: "Lyrics",
    title: "Word-by-word karaoke",
    body: "Syllable-timed lyrics raced across a dozen public providers, with translation, size and offset controls.",
  },
  {
    icon: Headphones,
    tag: "Output",
    title: "USB DAC ready",
    body: "Exclusive USB output, a direct internal path that bypasses system mixing, and sample-rate matching for bit-perfect playback.",
  },
  {
    icon: Download,
    tag: "Offline",
    title: "Downloads with a queue",
    body: "One transfer at a time, Wi-Fi-only pausing that resumes itself, and FLAC, Opus or AAC files tagged with artwork and lyrics.",
  },
  {
    icon: Compass,
    tag: "Discover",
    title: "A feed that learns",
    body: "Genre explorer, discovery radar, smart mixes and AI picks drawn from what you actually play — plus friends and stats.",
  },
  {
    icon: Sparkles,
    tag: "Design",
    title: "Themed by the artwork",
    body: "Material 3 expression rebuilt from the current sleeve, liquid-glass surfaces, expressive motion and adaptive home-screen widgets.",
  },
  {
    icon: ShieldCheck,
    tag: "Privacy",
    title: "No ads, no trackers",
    body: "No analytics SDKs and no account required. Keys and cookies stay on the device and out of exported backups.",
  },
];

const HIGHLIGHTS = [
  { icon: Timer, label: "Sleep timer" },
  { icon: Repeat, label: "Queue & autoplay" },
  { icon: ListMusic, label: "Playlists & likes" },
  { icon: Music4, label: "Last.fm scrobbling" },
  { icon: Headphones, label: "Adaptive home widgets" },
  { icon: Activity, label: "Stats" },
  { icon: Zap, label: "0.75× – 2× speed" },
];

export default function HomePage() {
  const [releases, setReleases] = useState<Release[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    let cancelled = false;
    fetchReleases(12)
      .then((list) => !cancelled && setReleases(list))
      .catch((err: Error) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 24);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const latest = releases?.[0] ?? null;
  const apk = useMemo(() => latest?.assets.filter((a) => isApk(a.name)) ?? [], [latest]);
  const primary = apk.find((a) => !a.name.toLowerCase().includes("android7")) ?? apk[0];
  const totalDownloads = useMemo(
    () => (releases ?? []).reduce((sum, r) => sum + r.assets.reduce((s, a) => s + a.downloaded, 0), 0),
    [releases],
  );

  return (
    <div className="relative min-h-screen bg-[#05070d]">
      {/* backdrop */}
      <div className="pointer-events-none fixed inset-0 z-0">
        <div className="grid-backdrop absolute inset-0" />
        <div className="absolute left-1/2 top-[-22rem] h-[44rem] w-[44rem] -translate-x-1/2 rounded-full bg-lime-500/20 blur-[130px]" />
        <div className="absolute bottom-[-18rem] right-[-12rem] h-[34rem] w-[34rem] rounded-full bg-blue-700/20 blur-[150px]" />
      </div>

      {/* nav */}
      <header
        className={`fixed inset-x-0 top-0 z-50 transition-all duration-500 ${
          scrolled ? "border-b border-white/[0.06] bg-[#05070d]/80 backdrop-blur-xl" : ""
        }`}
      >
        <div className="mx-auto flex max-w-7xl items-center justify-between px-6 py-4">
          <a href="#top" className="flex items-center gap-3">
            {/* eslint-disable-next-line @next/next/no-img-element */}
            <img src="/icon.png" alt="KatMusic" className="h-9 w-9 rounded-xl" />
            <span className="font-[family-name:var(--font-outfit)] text-lg font-bold tracking-tight text-white">
              KatMusic
            </span>
          </a>
          <nav className="hidden items-center gap-8 text-[11px] font-black uppercase tracking-[0.22em] text-slate-400 md:flex">
            <a href="#features" className="transition-colors hover:text-white">
              Features
            </a>
            <a href="#download" className="transition-colors hover:text-white">
              Downloads
            </a>
            <a href={REPO_PAGE} target="_blank" rel="noreferrer" className="transition-colors hover:text-white">
              Source
            </a>
          </nav>
          <a
            href="#download"
            className="btn-shine inline-flex items-center gap-2 rounded-full bg-white px-5 py-2.5 text-xs font-black uppercase tracking-[0.16em] text-black transition hover:bg-slate-200"
          >
            <Download className="h-3.5 w-3.5" />
            Get the app
          </a>
        </div>
      </header>

      <main id="top" className="relative z-10">
        {/* hero */}
        <section className="mx-auto max-w-7xl px-6 pb-20 pt-36 text-center md:pt-44">
          <Reveal>
            <div className="mb-9 inline-flex items-center gap-2.5 rounded-full border border-white/10 bg-white/[0.04] px-4 py-2 text-[10px] font-black uppercase tracking-[0.3em] text-white/70">
              <span className="dot-live h-1.5 w-1.5 rounded-full bg-lime-400 text-lime-400" />
              {latest ? `v${latest.tag.replace(/^v/, "")} is live` : "Open source on GitHub"}
            </div>
          </Reveal>

          <Reveal delay={80}>
            <h1 className="mx-auto max-w-5xl font-[family-name:var(--font-outfit)] text-[13vw] font-black uppercase leading-[0.86] tracking-tighter text-white sm:text-7xl md:text-[6.5rem]">
              Your music
              <br />
              <span className="text-shimmer bg-gradient-to-r from-lime-400 via-white to-lime-400 bg-clip-text italic text-transparent">
                without the noise
              </span>
            </h1>
          </Reveal>

          <Reveal delay={160}>
            <p className="mx-auto mt-8 max-w-2xl text-base font-medium leading-relaxed text-slate-400 md:text-lg">
              A native Android client for YouTube Music. Ad-free streaming, real downloads,
              syllable-timed lyrics and an audio engine that crossfades your queue. Install it
              straight from this page — no store, no account.
            </p>
          </Reveal>

          <Reveal delay={240}>
            <div className="mt-11 flex flex-col items-center justify-center gap-4 sm:flex-row">
              {primary ? (
                <a
                  href={primary.downloadUrl}
                  className="btn-shine inline-flex items-center gap-2.5 rounded-2xl bg-white px-9 py-5 text-sm font-black uppercase tracking-[0.14em] text-black shadow-[0_20px_60px_-20px_rgba(255,255,255,0.5)] transition hover:scale-[1.03] active:scale-95"
                >
                  <Download className="h-4 w-4" />
                  Download {primary.name}
                </a>
              ) : (
                <a
                  href={RELEASES_PAGE}
                  className="btn-shine inline-flex items-center gap-2.5 rounded-2xl bg-white px-9 py-5 text-sm font-black uppercase tracking-[0.14em] text-black"
                >
                  <Download className="h-4 w-4" />
                  Latest build
                </a>
              )}
              <a
                href="#download"
                className="inline-flex items-center gap-2 rounded-2xl border border-white/15 px-7 py-5 text-sm font-black uppercase tracking-[0.14em] text-white/80 transition hover:border-white/35 hover:text-white"
              >
                All releases
                <ChevronRight className="h-4 w-4" />
              </a>
            </div>
          </Reveal>

          <Reveal delay={320}>
            <div className="mx-auto mt-16 grid max-w-4xl grid-cols-2 gap-3 sm:grid-cols-4">
              {[
                { value: releases?.length ?? 0, label: "Releases", suffix: "" },
                { value: totalDownloads, label: "Downloads", suffix: "" },
                { value: 14, label: "Lyric sources", suffix: "" },
                { value: 10, label: "Min Android", suffix: "+" },
              ].map((stat) => (
                <div key={stat.label} className="glass rounded-3xl px-4 py-6">
                  <div className="font-[family-name:var(--font-outfit)] text-3xl font-black text-white">
                    <Counter to={stat.value} suffix={stat.suffix} />
                  </div>
                  <div className="mt-1.5 text-[10px] font-black uppercase tracking-[0.22em] text-slate-500">
                    {stat.label}
                  </div>
                </div>
              ))}
            </div>
          </Reveal>

          <Reveal delay={400}>
            <div className="mt-10 flex flex-wrap items-center justify-center gap-x-6 gap-y-3 text-[10px] font-black uppercase tracking-[0.2em] text-slate-600">
              {HIGHLIGHTS.map((item) => (
                <span key={item.label} className="inline-flex items-center gap-2">
                  <item.icon className="h-3.5 w-3.5 text-slate-500" />
                  {item.label}
                </span>
              ))}
            </div>
          </Reveal>
        </section>

        {/* features */}
        <section id="features" className="mx-auto max-w-7xl px-6 py-20">
          <Reveal>
            <div className="mb-12 flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
              <div>
                <span className="text-[10px] font-black uppercase tracking-[0.3em] text-lime-400">
                  What it does
                </span>
                <h2 className="mt-3 font-[family-name:var(--font-outfit)] text-4xl font-black uppercase leading-[0.95] tracking-tighter text-white md:text-5xl">
                  Built like a player,
                  <br />
                  not a webview
                </h2>
              </div>
              <p className="max-w-sm text-sm font-medium leading-relaxed text-slate-500">
                Compose UI, Media3 streaming and a C++ audio engine — the parts that usually get
                skipped in a YouTube Music client.
              </p>
            </div>
          </Reveal>

          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {FEATURES.map((feature, index) => (
              <Reveal key={feature.title} delay={index * 60}>
                <div className="group h-full rounded-[1.75rem] border border-white/[0.07] bg-gradient-to-br from-white/[0.05] via-white/[0.015] to-transparent p-6 transition hover:border-lime-400/30 hover:from-lime-400/[0.08]">
                  <div className="flex items-center justify-between">
                    <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-white/[0.06] text-lime-300">
                      <feature.icon className="h-5 w-5" />
                    </span>
                    <span className="text-[9px] font-black uppercase tracking-[0.26em] text-slate-600">
                      {feature.tag}
                    </span>
                  </div>
                  <h3 className="mt-5 font-[family-name:var(--font-outfit)] text-lg font-bold tracking-tight text-white">
                    {feature.title}
                  </h3>
                  <p className="mt-2 text-[13px] font-medium leading-relaxed text-slate-400">
                    {feature.body}
                  </p>
                </div>
              </Reveal>
            ))}
          </div>
        </section>

        {/* downloads */}
        <section id="download" className="mx-auto max-w-7xl px-6 py-20">
          <Reveal>
            <div className="mb-12 text-center">
              <span className="text-[10px] font-black uppercase tracking-[0.3em] text-lime-400">
                Install
              </span>
              <h2 className="mt-3 font-[family-name:var(--font-outfit)] text-4xl font-black uppercase leading-[0.95] tracking-tighter text-white md:text-5xl">
                Every build, direct
              </h2>
              <p className="mx-auto mt-4 max-w-xl text-sm font-medium leading-relaxed text-slate-500">
                Files are served straight from GitHub releases. Pick a version, tap, install.
              </p>
            </div>
          </Reveal>

          {error ? (
            <Reveal>
              <div className="rounded-[2rem] border border-amber-400/25 bg-amber-400/[0.06] p-8 text-center text-sm font-medium text-amber-200">
                {error}{" "}
                <a href={RELEASES_PAGE} className="underline underline-offset-4">
                  Open the releases page
                </a>
                .
              </div>
            </Reveal>
          ) : releases === null ? (
            <div className="grid gap-4 sm:grid-cols-2">
              {[0, 1].map((i) => (
                <div key={i} className="h-36 animate-pulse rounded-[2rem] border border-white/5 bg-white/[0.03]" />
              ))}
            </div>
          ) : releases.length === 0 ? (
            <Reveal>
              <div className="rounded-[2rem] border border-white/10 bg-white/[0.03] p-8 text-center text-sm text-slate-300">
                No published builds yet —{" "}
                <a href={RELEASES_PAGE} className="underline underline-offset-4">
                  watch the repository
                </a>
                .
              </div>
            </Reveal>
          ) : (
            <div className="space-y-4">
              <Reveal>
                <div className="conic-border rounded-[2rem] bg-gradient-to-br from-white/[0.08] via-white/[0.02] to-transparent p-7 shadow-[0_40px_100px_rgba(0,0,0,0.6)] md:p-9">
                  <div className="relative z-10">
                    <div className="flex flex-wrap items-center gap-3">
                      <span className="rounded-full bg-lime-400/15 px-3 py-1 text-[10px] font-black uppercase tracking-[0.2em] text-lime-300">
                        Latest
                      </span>
                      <span className="text-xs font-bold uppercase tracking-[0.16em] text-slate-400">
                        {latest?.name} · {formatDate(latest?.publishedAt ?? "")}
                      </span>
                    </div>

                    <div className="mt-6 grid gap-3 sm:grid-cols-2">
                      {apk.map((asset) => (
                        <a
                          key={asset.name}
                          href={asset.downloadUrl}
                          className="group flex items-center justify-between gap-4 rounded-2xl border border-white/[0.08] bg-black/30 px-5 py-4 transition hover:border-lime-400/40 hover:bg-lime-400/[0.07]"
                        >
                          <span className="min-w-0">
                            <span className="block truncate text-sm font-bold text-white">
                              {asset.name}
                            </span>
                            <span className="mt-1 block text-[10px] font-black uppercase tracking-[0.18em] text-slate-500">
                              {recommendFor(asset.name)} · {formatBytes(asset.size)}
                            </span>
                          </span>
                          <span className="flex shrink-0 items-center gap-1.5 rounded-full bg-white/10 px-3.5 py-2 text-[10px] font-black uppercase tracking-[0.16em] text-white transition group-hover:bg-white group-hover:text-black">
                            <Download className="h-3.5 w-3.5" />
                            Get
                          </span>
                        </a>
                      ))}
                    </div>

                    {latest && latest.assets.some((a) => !isApk(a.name)) ? (
                      <div className="mt-4 flex flex-wrap gap-2">
                        {latest.assets
                          .filter((a) => !isApk(a.name))
                          .map((asset) => (
                            <a
                              key={asset.name}
                              href={asset.downloadUrl}
                              className="inline-flex items-center gap-2 rounded-full border border-white/10 px-3.5 py-2 text-[10px] font-black uppercase tracking-[0.16em] text-slate-400 transition hover:border-white/30 hover:text-white"
                            >
                              <FileCheck2 className="h-3.5 w-3.5" />
                              {asset.name}
                            </a>
                          ))}
                      </div>
                    ) : null}

                    <p className="mt-5 text-[11px] font-medium leading-relaxed text-slate-500">
                      Standard build for Android 10 and newer. The Android 7 build exists for
                      older phones with classic media controls; the uncompressed variant is a
                      debug-style package.
                    </p>
                  </div>
                </div>
              </Reveal>

              {releases.slice(1).map((release, index) => (
                <Reveal key={release.tag} delay={index * 50}>
                  <details className="group rounded-[1.5rem] border border-white/[0.07] bg-white/[0.02] px-6 py-5 transition hover:border-white/15">
                    <summary className="flex cursor-pointer list-none items-center justify-between gap-4">
                      <span className="min-w-0">
                        <span className="block truncate text-sm font-bold text-white">
                          {release.name}
                        </span>
                        <span className="mt-1 block text-[10px] font-black uppercase tracking-[0.18em] text-slate-600">
                          {formatDate(release.publishedAt)} · {release.apkCount} APK
                          {release.apkCount === 1 ? "" : "s"}
                          {release.prerelease ? " · pre-release" : ""}
                        </span>
                      </span>
                      <span className="shrink-0 text-[10px] font-black uppercase tracking-[0.2em] text-lime-300 group-open:hidden">
                        Show
                      </span>
                      <span className="hidden shrink-0 text-[10px] font-black uppercase tracking-[0.2em] text-lime-300 group-open:inline">
                        Hide
                      </span>
                    </summary>
                    <div className="mt-4 grid gap-3 sm:grid-cols-2">
                      {release.assets.map((asset) => (
                        <a
                          key={asset.name}
                          href={asset.downloadUrl}
                          className="group/asset flex items-center justify-between gap-4 rounded-xl border border-white/[0.07] bg-black/25 px-4 py-3 transition hover:border-lime-400/40"
                        >
                          <span className="min-w-0">
                            <span className="block truncate text-xs font-bold text-white">
                              {asset.name}
                            </span>
                            <span className="mt-0.5 block text-[9px] font-black uppercase tracking-[0.18em] text-slate-600">
                              {formatBytes(asset.size)}
                            </span>
                          </span>
                          <Download className="h-4 w-4 shrink-0 text-slate-500 transition group-hover/asset:text-lime-300" />
                        </a>
                      ))}
                    </div>
                  </details>
                </Reveal>
              ))}
            </div>
          )}

          <Reveal delay={80}>
            <div className="mt-6 grid gap-4 sm:grid-cols-3">
              {[
                { step: "01", title: "Pick a build", body: "Standard APK for Android 10+. Around 20–30 MB." },
                { step: "02", title: "Allow the install", body: "Android asks once to trust your browser. Normal sideload prompt." },
                { step: "03", title: "Open and play", body: "No account needed. Sign in only for your own library." },
              ].map((item) => (
                <div key={item.step} className="glass rounded-[1.5rem] p-6">
                  <span className="font-[family-name:var(--font-outfit)] text-xs font-black tracking-[0.3em] text-lime-400">
                    {item.step}
                  </span>
                  <h3 className="mt-3 text-sm font-bold uppercase tracking-[0.08em] text-white">
                    {item.title}
                  </h3>
                  <p className="mt-2 text-[12px] font-medium leading-relaxed text-slate-400">
                    {item.body}
                  </p>
                </div>
              ))}
            </div>
          </Reveal>
        </section>

        {/* closing band */}
        <section className="mx-auto max-w-5xl px-6 pb-24">
          <Reveal>
            <div className="conic-border relative overflow-hidden rounded-[2.5rem] bg-gradient-to-br from-white/[0.07] via-white/[0.02] to-transparent px-8 py-16 text-center">
              <div className="relative z-10">
                <div className="mb-7 inline-flex items-center gap-2 rounded-full border border-white/10 bg-white/5 px-4 py-1.5 text-[9px] font-black uppercase tracking-[0.3em] text-white/60">
                  <span className="dot-live h-1.5 w-1.5 rounded-full bg-white text-white" />
                  Free · GPL-3.0
                </div>
                <h2 className="font-[family-name:var(--font-outfit)] text-4xl font-black uppercase leading-[0.9] tracking-tighter text-white md:text-6xl">
                  Put it on
                  <br />
                  <span className="text-shimmer bg-gradient-to-r from-white via-lime-300 to-white bg-clip-text italic text-transparent">
                    your phone
                  </span>
                </h2>
                <div className="mt-10 flex flex-col items-center justify-center gap-4 sm:flex-row">
                  <a
                    href={primary?.downloadUrl ?? RELEASES_PAGE}
                    className="btn-shine inline-flex items-center gap-2.5 rounded-2xl bg-white px-8 py-4 text-xs font-black uppercase tracking-[0.18em] text-black transition hover:scale-[1.03] active:scale-95"
                  >
                    <Download className="h-4 w-4" />
                    Download the APK
                  </a>
                  <a
                    href={REPO_PAGE}
                    target="_blank"
                    rel="noreferrer"
                    className="inline-flex items-center gap-2 rounded-2xl border border-white/15 px-7 py-4 text-xs font-black uppercase tracking-[0.18em] text-white/80 transition hover:border-white/35 hover:text-white"
                  >
                    <Github className="h-4 w-4" />
                    Star on GitHub
                  </a>
                </div>
              </div>
            </div>
          </Reveal>
        </section>
      </main>

      {/* footer */}
      <footer className="relative z-10 border-t border-white/[0.05] bg-black/60 pt-16 pb-8">
        <div className="mx-auto max-w-7xl px-6">
          <div className="grid gap-10 md:grid-cols-4">
            <div className="md:col-span-2">
              <div className="flex items-center gap-3">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img src="/icon.png" alt="" className="h-9 w-9 rounded-xl" />
                <span className="font-[family-name:var(--font-outfit)] text-lg font-bold lowercase tracking-tight text-white">
                  katmusic
                </span>
              </div>
              <p className="mt-4 max-w-xs text-sm font-medium leading-relaxed text-slate-500">
                A native Android client for YouTube Music, designed and built by ItzFallenMe.
              </p>
              <div className="mt-5 inline-flex items-center gap-2 text-[9px] font-black uppercase tracking-[0.26em] text-slate-500">
                <CheckCircle2 className="h-3.5 w-3.5 text-lime-400" />
                {latest ? `v${latest.tag.replace(/^v/, "")}` : "Latest build"} · GPL-3.0
              </div>
            </div>
            <div>
              <h4 className="text-[10px] font-black uppercase tracking-[0.3em] text-slate-600">App</h4>
              <ul className="mt-4 space-y-3 text-sm font-medium text-slate-400">
                <li>
                  <a href="#download" className="transition-colors hover:text-white">
                    Downloads
                  </a>
                </li>
                <li>
                  <a href={RELEASES_PAGE} className="transition-colors hover:text-white">
                    Release notes
                  </a>
                </li>
                <li>
                  <a href={REPO_PAGE} className="transition-colors hover:text-white">
                    Source code
                  </a>
                </li>
              </ul>
            </div>
            <div>
              <h4 className="text-[10px] font-black uppercase tracking-[0.3em] text-slate-600">More</h4>
              <ul className="mt-4 space-y-3 text-sm font-medium text-slate-400">
                <li>
                  <a
                    href="https://github.com/ItzFallenMe"
                    target="_blank"
                    rel="noreferrer"
                    className="transition-colors hover:text-white"
                  >
                    ItzFallenMe
                  </a>
                </li>
                <li>
                  <a
                    href={`${REPO_PAGE}#readme`}
                    target="_blank"
                    rel="noreferrer"
                    className="transition-colors hover:text-white"
                  >
                    Readme
                  </a>
                </li>
                <li>
                  <a
                    href={`${REPO_PAGE}/blob/main/LICENSE`}
                    target="_blank"
                    rel="noreferrer"
                    className="transition-colors hover:text-white"
                  >
                    License
                  </a>
                </li>
              </ul>
            </div>
          </div>
          <div className="mt-12 flex flex-col items-center justify-between gap-4 border-t border-white/5 pt-6 md:flex-row">
            <p className="text-[9px] font-black uppercase tracking-[0.3em] text-slate-700">
              © {new Date().getFullYear()} ItzFallenMe — All rights reserved
            </p>
            <a
              href={REPO_PAGE}
              className="inline-flex items-center gap-2 text-[9px] font-black uppercase tracking-[0.26em] text-white"
            >
              <span className="dot-live h-1.5 w-1.5 rounded-full bg-lime-400 text-lime-400" />
              github.com/ItzFallenMe
              <ArrowUpRight className="h-3 w-3" />
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
}
