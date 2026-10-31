"use client";

import { useEffect, useMemo, useState } from "react";
import {
  ArrowDown, ArrowRight, ArrowUpRight, AudioLines, Check, ChevronRight,
  CirclePlay, Download, Github, Headphones, Heart, ListMusic, Mic2,
  Music2, Pause, Radio, Repeat2, ShieldCheck, SlidersHorizontal, Sparkles,
  Waves, X,
} from "lucide-react";
import {
  RELEASES_PAGE, REPO_PAGE, fetchReleases, formatBytes, formatDate, isApk,
  type Release,
} from "@/lib/releases";

function Phone({ variant, className = "" }: { variant: "home" | "player" | "lyrics"; className?: string }) {
  return (
    <div className={`phone-shell ${className}`}>
      <div className="phone-screen">
        <div className="phone-island" />
        <div className="phone-status"><span>9:41</span><span>● ● ▰</span></div>
        {variant === "home" && <>
          <div className="phone-topline"><span className="phone-brand">kat<span>music</span></span><span className="phone-avatar">V</span></div>
          <p className="phone-eyebrow">MONDAY, OCTOBER 26</p>
          <h3 className="phone-heading">Good evening.</h3>
          <div className="phone-search">⌕ <span>Search songs, artists...</span></div>
          <div className="phone-section"><b>Made for you</b><span>SEE ALL</span></div>
          <div className="phone-albums">
            <div className="album-art art-one"><Waves /></div><div className="album-art art-two"><AudioLines /></div>
          </div>
          <div className="phone-section"><b>Recently played</b><span>•••</span></div>
          {["After Hours","Nights","The Colour Violet"].map((song,i)=><div className="phone-track" key={song}><span className={`track-thumb track-${i}`}>{i===0?"✦":i===1?"◒":"◈"}</span><span><b>{song}</b><small>{["The Weeknd","Frank Ocean","Tory Lanez"][i]}</small></span><span className="track-more">•••</span></div>)}
          <div className="phone-mini-player"><span className="mini-art">✦</span><span><b>After Hours</b><small>The Weeknd</small></span><Pause size={13}/></div>
          <div className="phone-nav"><span>⌂</span><span>⌕</span><span>◉</span><span>☷</span></div>
        </>}
        {variant === "player" && <>
          <div className="phone-player-nav"><span>⌄</span><span>NOW PLAYING</span><span>•••</span></div>
          <div className="player-art"><div className="art-orbit"><div className="art-core">K</div></div><span className="art-stamp">KAT<br/>MUSIC</span></div>
          <div className="player-title"><div><h3>Midnight City</h3><p>M83 · Hurry Up, We're Dreaming</p></div><Heart size={19}/></div>
          <div className="player-progress"><i/><span><small>2:18</small><small>4:03</small></span></div>
          <div className="player-controls"><Repeat2 size={17}/><ChevronRight className="flip" size={24}/><span><Pause size={19} fill="currentColor"/></span><ChevronRight size={24}/><ListMusic size={18}/></div>
          <div className="phone-eq"><AudioLines size={17}/><span>Audio engine</span><b>ON</b></div>
        </>}
        {variant === "lyrics" && <>
          <div className="phone-player-nav"><span>⌄</span><span>LYRICS</span><span>•••</span></div>
          <div className="lyrics-art"><div className="lyrics-disc">✳</div></div>
          <div className="lyrics-song"><b>Space Song</b><span>Beach House</span></div>
          <div className="lyrics-lines"><p>Fall back into place</p><h3>It was late at night</h3><h3 className="lyric-active">You held on tight</h3><p>From an empty seat</p><p>A flash of light</p></div>
          <div className="lyrics-tools"><span>文  Translate</span><span>◉  Timing</span></div>
          <div className="phone-progress-mini"><i/></div>
        </>}
      </div>
    </div>
  );
}

const FEATURES = [
  { icon: AudioLines, label: "AUDIO ENGINE", title: "Hear every detail.", body: "A native C++ audio engine, parametric EQ, AutoEQ headphone profiles and advanced output options." },
  { icon: Mic2, label: "LIVE LYRICS", title: "Every word, in time.", body: "Line-synced and word-by-word karaoke lyrics, with translation, romanisation and timing controls." },
  { icon: Download, label: "OFFLINE", title: "Your music goes with you.", body: "A proper download queue with progress, retry and Wi-Fi-only controls. Your library, ready when you are." },
  { icon: SlidersHorizontal, label: "PERSONALISATION", title: "Make it feel like yours.", body: "Six player styles, dynamic colour palettes, light and dark themes, and controls that fit your taste." },
];

export default function HomePage() {
  const [releases, setReleases] = useState<Release[] | null>(null);
  const [error, setError] = useState(false);
  const [scrolled, setScrolled] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    let active = true;
    fetchReleases(8).then((items) => active && setReleases(items)).catch(() => active && setError(true));
    const onScroll = () => setScrolled(window.scrollY > 16);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => { active = false; window.removeEventListener("scroll", onScroll); };
  }, []);

  const latest = releases?.[0] ?? null;
  const apks = useMemo(() => latest?.assets.filter((asset) => isApk(asset.name)) ?? [], [latest]);
  const recommended = apks.find((asset) => !asset.name.toLowerCase().includes("android7")) ?? apks[0];

  return <div className="site-root">
    <div className="ambient ambient-a"/><div className="ambient ambient-b"/>
    <header className={`site-header ${scrolled ? "header-scrolled" : ""}`}>
      <div className="nav-inner">
        <a className="wordmark" href="#top"><img src="/icon.png" alt=""/> <span>katmusic<span className="wordmark-dot">.</span></span></a>
        <nav className={menuOpen ? "nav-links nav-open" : "nav-links"}>
          <a href="#experience" onClick={()=>setMenuOpen(false)}>Experience</a><a href="#features" onClick={()=>setMenuOpen(false)}>Features</a><a href="#download" onClick={()=>setMenuOpen(false)}>Download</a><a href={REPO_PAGE} target="_blank" rel="noreferrer">GitHub <ArrowUpRight size={13}/></a>
        </nav>
        <a className="nav-cta" href="#download">Get the app <ArrowUpRight size={14}/></a>
        <button className="mobile-menu" aria-label="Toggle navigation" onClick={()=>setMenuOpen(v=>!v)}>{menuOpen?<X/>:<span>☰</span>}</button>
      </div>
    </header>

    <main id="top">
      <section className="hero-section">
        <div className="hero-copy">
          <div className="eyebrow-pill"><span/> YOUR MUSIC. YOUR RULES.</div>
          <h1>Find your<br/><em>frequency.</em></h1>
          <p className="hero-description">A music player that gets out of the way and lets the music take over. Built for Android, designed around you.</p>
          <div className="hero-actions">
            <a className="button-primary" href={recommended?.downloadUrl ?? RELEASES_PAGE}><Download size={17}/> Download for Android <ArrowUpRight size={16}/></a>
            <a className="button-quiet" href="#experience">Discover KatMusic <ArrowDown size={15}/></a>
          </div>
          <div className="hero-meta"><span><ShieldCheck size={15}/> Open source · GPL-3.0</span><i/><span><Music2 size={15}/> Android 10+</span></div>
        </div>
        <div className="hero-visual" aria-label="KatMusic app interface previews">
          <div className="visual-glow"/>
          <div className="visual-orbit orbit-one"/><div className="visual-orbit orbit-two"/>
          <Phone variant="home" className="phone-back phone-left"/>
          <Phone variant="lyrics" className="phone-back phone-right"/>
          <Phone variant="player" className="phone-front"/>
          <div className="floating-note note-top"><span className="note-icon"><Waves size={16}/></span><span><b>Pure playback</b><small>Made for listening</small></span></div>
          <div className="floating-note note-bottom"><span className="note-icon note-purple"><Mic2 size={16}/></span><span><b>Lyrics in sync</b><small>Right on the beat</small></span></div>
        </div>
        <div className="hero-index"><span>01</span><i/> A DIFFERENT KIND OF PLAYER</div>
      </section>

      <section className="proof-strip"><div><strong>6</strong><span>PLAYER STYLES</span></div><i/><div><strong>Native</strong><span>AUDIO ENGINE</span></div><i/><div><strong>Open</strong><span>SOURCE PROJECT</span></div><i/><div><strong>Yours</strong><span>TO PERSONALISE</span></div></section>

      <section id="experience" className="experience-section section-wrap">
        <div className="section-kicker">THE LISTENING EXPERIENCE <span>— 01</span></div>
        <div className="experience-grid">
          <div><h2>More than<br/>press <em>play.</em></h2><p className="section-lede">From the first tap to the last note, every part of KatMusic is made to feel considered. Explore your library, settle into a track, and let the lyrics carry you along.</p><a className="text-link" href="#features">Explore what makes it different <ArrowUpRight size={16}/></a></div>
          <div className="experience-aside"><div className="aside-line"/><p>DESIGNED FOR THE MOMENT</p><h3>Less managing<br/>your music.<br/><em>More feeling it.</em></h3></div>
        </div>
      </section>

      <section id="features" className="features-section">
        <div className="section-wrap">
          <div className="features-heading"><div><div className="section-kicker">THOUGHTFULLY BUILT <span>— 02</span></div><h2>The details<br/>make the <em>difference.</em></h2></div><p>Powerful where it matters. Calm where it counts. All the tools you want, without the clutter you don't.</p></div>
          <div className="feature-grid">{FEATURES.map((f,i)=><article className="feature-card" key={f.title}><div className="feature-top"><span>0{i+1}</span><f.icon size={20}/></div><p>{f.label}</p><h3>{f.title}</h3><div className="feature-bottom"><span>{f.body}</span><ArrowUpRight size={17}/></div></article>)}</div>
        </div>
      </section>

      <section className="styles-section section-wrap">
        <div className="styles-copy"><div className="section-kicker">A PLAYER THAT FEELS LIKE YOU <span>— 03</span></div><h2>One app.<br/><em>Six moods.</em></h2><p>Switch the whole feel of your player. Go artwork-first, get lost in the lyrics, or keep every control close at hand.</p><div className="style-tags">{["Classic","Poster","Timeline","Lyrics","Artwork","Canvas"].map((s)=><span key={s}>{s}</span>)}</div></div>
        <div className="style-showcase"><div className="style-halo"/><Phone variant="player" className="style-phone"/><div className="style-caption"><Sparkles size={14}/> YOUR PLAYER, YOUR WAY</div></div>
      </section>

      <section id="download" className="download-section section-wrap">
        <div className="download-panel"><div className="download-copy"><div className="section-kicker">READY WHEN YOU ARE <span>— 04</span></div><h2>Make room<br/>for <em>good music.</em></h2><p>Get the latest KatMusic release and make your phone sound a little more like yours.</p><div className="download-buttons"><a className="button-primary" href={recommended?.downloadUrl ?? RELEASES_PAGE}><Download size={17}/> Download APK <ArrowUpRight size={16}/></a><a className="button-quiet" href={RELEASES_PAGE}>All releases <ChevronRight size={15}/></a></div></div>
          <div className="release-card"><div className="release-card-top"><span>THE LATEST RELEASE</span><span className="stable-dot">● STABLE</span></div><div className="release-version">{latest?.tag ?? "v2.1.0"}</div><p>{latest ? formatDate(latest.publishedAt) : "Latest stable release"}</p><div className="release-divider"/>{(apks.length ? apks : [{name:"View all APKs",size:0,downloadUrl:RELEASES_PAGE} as any]).map((asset:any)=><a className="release-asset" href={asset.downloadUrl} key={asset.name}><span><b>{asset.name}</b><small>{asset.size ? formatBytes(asset.size) : "Choose a device build"}</small></span><Download size={16}/></a>)}{error&&<small className="release-error">Could not load live release data. View all releases on GitHub.</small>}</div>
        </div>
      </section>
      <section className="closing-section"><div className="section-kicker">BUILT IN THE OPEN</div><h2>Good music.<br/><em>No compromises.</em></h2><a href={REPO_PAGE} target="_blank" rel="noreferrer" className="github-link"><Github size={17}/> Explore KatMusic on GitHub <ArrowUpRight size={16}/></a></section>
    </main>
    <footer className="site-footer"><a className="wordmark" href="#top"><img src="/icon.png" alt=""/><span>katmusic<span className="wordmark-dot">.</span></span></a><span>Independent Android music player · GPL-3.0</span><span>© {new Date().getFullYear()} ItzFallenMe</span></footer>
  </div>;
}
