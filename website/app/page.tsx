"use client";

import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import {
  ArrowDownRight, ArrowRight, ArrowUpRight, AudioLines, Check, ChevronDown,
  ChevronLeft, ChevronRight, Download, Github, Heart, ListMusic,
  Mic2, Music2, Pause, Radio, Repeat2, ShieldCheck, SlidersHorizontal,
  Sparkles, Waves, X,
} from "lucide-react";
import {
  RELEASES_PAGE, REPO_PAGE, fetchReleases, formatBytes, formatDate, isApk,
  type Release,
} from "@/lib/releases";

type Screen = "library" | "player" | "lyrics" | "audio";

function MiniArtwork({ kind = "sunset" }: { kind?: string }) {
  return <div className={"mini-artwork art-" + kind}><span className="art-sun"/><span className="art-ring"/><span className="art-label">KAT / FM</span></div>;
}

function PhoneScreen({ screen }: { screen: Screen }) {
  return <div className="device-screen">
    <div className="device-island"/><div className="device-status"><span>9:41</span><span>◔ ◔ ▰</span></div>
    {screen === "library" && <>
      <div className="app-topbar"><span className="app-wordmark">kat<span>music</span></span><span className="app-avatar">V</span></div>
      <p className="app-overline">MONDAY, 26 OCTOBER</p><h3 className="app-greeting">Find your<br/>next favourite.</h3>
      <div className="app-search">⌕ <span>Search songs, artists, albums</span></div>
      <div className="app-row-title"><b>Made for your mood</b><span>SEE ALL</span></div>
      <div className="app-mix-grid"><div className="mix-tile mix-a"><span>✳</span><b>Late night<br/>drive</b></div><div className="mix-tile mix-b"><span>◉</span><b>Soft focus<br/>hours</b></div></div>
      <div className="app-row-title"><b>Recently played</b><span>•••</span></div>
      {["After Hours","Nights","Space Song"].map((name,i)=><div className="app-song" key={name}><span className={"song-cover cover-" + i}>{["✳","◒","✦"][i]}</span><span><b>{name}</b><small>{["The Weeknd","Frank Ocean","Beach House"][i]}</small></span><span className="song-more">•••</span></div>)}
      <div className="app-now-playing"><span className="now-cover">✳</span><span><b>After Hours</b><small>The Weeknd</small></span><Pause size={13}/></div>
      <div className="app-bottom-nav"><span>⌂</span><span>⌕</span><span>◉</span><span>☷</span></div>
    </>}
    {screen === "player" && <>
      <div className="app-player-top"><span>⌄</span><b>NOW PLAYING</b><span>•••</span></div>
      <MiniArtwork/>
      <div className="app-track-heading"><div><h3>Midnight City</h3><p>M83 · Hurry Up, We&apos;re Dreaming</p></div><Heart size={18}/></div>
      <div className="app-scrubber"><i/><div><span>2:18</span><span>4:03</span></div></div>
      <div className="app-controls"><Repeat2 size={16}/><ChevronLeft size={24}/><span><Pause size={18} fill="currentColor"/></span><ChevronRight size={24}/><ListMusic size={17}/></div>
      <div className="app-audio-chip"><AudioLines size={16}/><span>Audio engine</span><b>ACTIVE</b></div>
      <div className="app-up-next"><span>UP NEXT</span><b>Wait · M83</b><small>Outro · M83</small></div>
    </>}
    {screen === "lyrics" && <>
      <div className="app-player-top"><span>⌄</span><b>LYRICS</b><span>•••</span></div>
      <div className="lyrics-art"><div className="lyrics-disc">✳</div></div>
      <div className="lyrics-track"><b>Space Song</b><span>Beach House</span></div>
      <div className="lyrics-copy"><p>Fall back into place</p><p>It was late at night</p><h3>You held on tight</h3><p>From an empty seat</p><p>A flash of light</p></div>
      <div className="lyrics-actions"><span>文&nbsp; Translate</span><span>◉&nbsp; Timing</span></div>
      <div className="lyrics-mini-progress"><i/></div>
    </>}
    {screen === "audio" && <>
      <div className="app-player-top"><span>⌄</span><b>AUDIO LAB</b><span>•••</span></div>
      <div className="audio-title"><span className="audio-icon"><SlidersHorizontal/></span><p>MAKE IT<br/><b>SOUND YOURS.</b></p></div>
      <div className="audio-preset"><span>PRESET</span><b>Warm & wide <ChevronDown size={12}/></b></div>
      <div className="eq-graph"><div/><div/><div/><div/><div/><div/><div/></div>
      <div className="eq-labels"><span>60</span><span>150</span><span>400</span><span>1k</span><span>2.4k</span><span>6k</span><span>15k</span></div>
      <div className="audio-toggle"><span><b>Parametric EQ</b><small>Fine tune every band</small></span><i>ON</i></div>
      <div className="audio-toggle"><span><b>AutoEQ profile</b><small>Headphone correction</small></span><i>ON</i></div>
      <div className="audio-footer"><Waves size={17}/><span>POWERED BY NATIVE AUDIO</span></div>
    </>}
  </div>;
}

const SCREENS: { id: Screen; name: string; caption: string }[] = [
  { id:"library", name:"Your library", caption:"A home for every listening mood" },
  { id:"player", name:"The player", caption:"A player that stays out of the way" },
  { id:"lyrics", name:"Live lyrics", caption:"Follow every line as it happens" },
  { id:"audio", name:"Audio lab", caption:"Shape the sound to your taste" },
];

const FEATURES = [
  { icon:Radio, number:"01", eyebrow:"DISCOVER & PLAY", title:"The music universe.", copy:"Explore songs, albums, artists, playlists, mixes and podcasts from the YouTube Music catalogue. Keep your library and listening queue close." },
  { icon:Mic2, number:"02", eyebrow:"LYRICS THAT MOVE", title:"In time with every word.", copy:"Line-synced and word-by-word karaoke lyrics, with translation, romanisation, text sizing and timing adjustment." },
  { icon:AudioLines, number:"03", eyebrow:"NATIVE AUDIO", title:"Go deeper on sound.", copy:"A C++ audio engine built around Oboe, with parametric and graphic EQ, presets, AutoEQ headphone profiles and advanced output paths." },
  { icon:Download, number:"04", eyebrow:"TAKE IT OFFLINE", title:"Your library, anywhere.", copy:"A visible download queue with progress, cancel and retry controls, plus Wi-Fi-only downloads that can resume when you're back online." },
  { icon:Waves, number:"05", eyebrow:"AUTOMIX", title:"Let tracks flow.", copy:"Crossfade planning, BPM and phase analysis, and WSOLA time stretching help tracks blend into one continuous session." },
  { icon:Sparkles, number:"06", eyebrow:"MAKE IT YOURS", title:"Six ways to listen.", copy:"Choose Classic, Poster, Timeline, Lyrics, Artwork or Canvas. Pair your player style with themes and artwork-derived colour." },
];

function Phone({ screen, className = "" }: { screen: Screen; className?: string }) {
  return <div className={"device-frame " + className}><PhoneScreen screen={screen}/></div>;
}

export default function HomePage() {
  const [releases, setReleases] = useState<Release[] | null>(null);
  const [releaseError, setReleaseError] = useState(false);
  const [activeScreen, setActiveScreen] = useState<Screen>("player");
  const [selectedAsset, setSelectedAsset] = useState("universal");
  const [menuOpen, setMenuOpen] = useState(false);
  const [scrolled, setScrolled] = useState(false);

  useEffect(() => {
    let alive = true;
    fetchReleases(8).then((data) => { if (alive) setReleases(data); }).catch(() => { if (alive) setReleaseError(true); });
    const onScroll = () => setScrolled(window.scrollY > 20);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive:true });
    return () => { alive = false; window.removeEventListener("scroll", onScroll); };
  }, []);

  const latest = releases?.find((item) => !item.prerelease) ?? releases?.[0] ?? null;
  const apks = useMemo(() => latest?.assets.filter((asset) => isApk(asset.name)) ?? [], [latest]);
  const selected = apks.find((asset) => asset.name.toLowerCase().includes(selectedAsset)) ?? apks.find((asset) => asset.name.toLowerCase().includes("universal") && !asset.name.toLowerCase().includes("uncompressed")) ?? apks.find((asset) => !asset.name.toLowerCase().includes("android7")) ?? apks[0];
  const downloadHref = selected?.downloadUrl ?? RELEASES_PAGE;

  return <div className="kat-site">
    <div className="site-grain"/>
    <div className="ambient ambient-top"/><div className="ambient ambient-low"/>
    <header className={"site-header " + (scrolled ? "is-scrolled" : "")}>
      <div className="nav-wrap">
        <a className="brand" href="#top"><Image src="/katmusic_logo.svg" alt="" width={32} height={32} priority/><span>katmusic<span className="brand-period">.</span></span></a>
        <nav className={menuOpen ? "nav-links nav-links-open" : "nav-links"}>
          <a href="#experience" onClick={() => setMenuOpen(false)}>Experience</a>
          <a href="#features" onClick={() => setMenuOpen(false)}>Features</a>
          <a href="#player-styles" onClick={() => setMenuOpen(false)}>Player styles</a>
          <a href="#download" onClick={() => setMenuOpen(false)}>Download</a>
          <a href={REPO_PAGE} target="_blank" rel="noreferrer">GitHub <ArrowUpRight size={13}/></a>
        </nav>
        <a className="nav-download" href="#download">Get KatMusic <ArrowUpRight size={14}/></a>
        <button className="menu-toggle" aria-label="Toggle menu" onClick={() => setMenuOpen((v) => !v)}>{menuOpen ? <X/> : <span>☰</span>}</button>
      </div>
    </header>

    <main id="top">
      <section className="hero">
        <div className="hero-copy">
          <div className="hero-label"><span className="live-dot"/> A DIFFERENT KIND OF MUSIC APP</div>
          <h1>Your music.<br/><em>Your universe.</em></h1>
          <p className="hero-lede">A native Android experience for YouTube Music, built for people who care about how music sounds, feels and fits into their day.</p>
          <div className="hero-ctas">
            <a href="#download" className="cta-main"><Download size={17}/> Get KatMusic <ArrowUpRight size={16}/></a>
            <a href="#experience" className="cta-secondary">Explore the experience <ArrowDownRight size={16}/></a>
          </div>
          <div className="hero-trust"><span><ShieldCheck size={15}/> Open source · GPL-3.0</span><i/><span><Music2 size={15}/> Android 7+ support</span></div>
          <div className="hero-release"><span className="release-pulse"/> LATEST STABLE <b>{latest?.tag ?? "2.1.0"}</b><span className="hero-release-divider"/> BUILT WITH KOTLIN + C++</div>
        </div>
        <div className="hero-stage">
          <div className="hero-brand-orbit" aria-hidden="true"><span className="orbit orbit-one"/><span className="orbit orbit-two"/><span className="orbit orbit-three"/><div className="hero-brand-mark"><Image src="/katmusic_logo.svg" alt="" width={220} height={220} priority/></div><span className="brand-spark spark-one"/><span className="brand-spark spark-two"/><span className="brand-spark spark-three"/></div>
          <div className="stage-aura"/><div className="stage-ring stage-ring-a"/><div className="stage-ring stage-ring-b"/>
          <div className="stage-label stage-label-top"><span>01 / 04</span> THE APP, IN MOTION</div>
          <Phone screen="library" className="device-left"/>
          <Phone screen="lyrics" className="device-right"/>
          <Phone screen={activeScreen} className="device-main"/>
          <div className="stage-float float-a"><span><AudioLines size={16}/></span><div><b>Native audio</b><small>Built around Oboe</small></div></div>
          <div className="stage-float float-b"><span><Mic2 size={16}/></span><div><b>Word-sync lyrics</b><small>Every beat, every word</small></div></div>
          <div className="stage-bottom"><span>PREVIEW THE INTERFACE</span><div className="screen-tabs">{SCREENS.map((item) => <button key={item.id} className={activeScreen === item.id ? "screen-tab active" : "screen-tab"} onClick={() => setActiveScreen(item.id)}>{item.name}</button>)}</div></div>
        </div>
        <div className="hero-scroll"><span>SCROLL TO EXPLORE</span><i/></div>
      </section>

      <section className="ticker" aria-label="KatMusic highlights"><div className="ticker-track"><span>YOUTUBE MUSIC</span><i>✳</i><span>WORD-SYNC LYRICS</span><i>✳</i><span>NATIVE AUDIO ENGINE</span><i>✳</i><span>OFFLINE DOWNLOADS</span><i>✳</i><span>AUTOMIX</span><i>✳</i><span>YOUR PLAYER, YOUR WAY</span><i>✳</i></div></section>

      <section className="intro-section section-shell" id="experience">
        <div className="section-index"><span>01</span><i/> THE EXPERIENCE</div>
        <div className="intro-grid">
          <h2>Built around<br/>the <em>feeling.</em></h2>
          <div className="intro-copy"><p>KatMusic brings your streaming library, local music and serious playback tools into one carefully considered Android app.</p><p>Start with a song. Stay for the details. Whether you&apos;re digging into your sound profile or getting lost in a lyric, the experience stays yours.</p><a href="#features" className="underlined-link">See what makes it different <ArrowUpRight size={15}/></a></div>
        </div>
        <div className="experience-strip">
          <div className="experience-tile tile-art"><div className="tile-artwork"><span>✳</span><b>LISTEN<br/>CLOSER.</b></div><small>01 — THE LITTLE DETAILS</small></div>
          <div className="experience-tile tile-quote"><span className="quote-mark">“</span><p>Music is personal.<br/><em>Your player should be too.</em></p><small>MADE TO BE MADE YOURS</small></div>
          <div className="experience-tile tile-stat"><span className="stat-symbol"><SlidersHorizontal size={24}/></span><strong>6</strong><b>distinct player styles</b><small>ONE APP. YOUR KIND OF FLOW.</small></div>
        </div>
      </section>

      <section className="features-section" id="features"><div className="section-shell">
        <div className="section-index"><span>02</span><i/> MADE FOR THE WAY YOU LISTEN</div>
        <div className="features-heading"><h2>Every layer<br/>has a <em>purpose.</em></h2><p>Not a list of buzzwords. Real tools for real listening — from discovery to the final fade-out.</p></div>
        <div className="feature-grid">{FEATURES.map((feature) => <article className="feature-card" key={feature.number}><div className="feature-card-top"><span>{feature.number}</span><feature.icon size={21}/></div><p className="feature-eyebrow">{feature.eyebrow}</p><h3>{feature.title}</h3><p className="feature-copy">{feature.copy}</p><div className="feature-line"/></article>)}</div>
      </div></section>

      <section className="player-styles section-shell" id="player-styles">
        <div className="styles-copy"><div className="section-index"><span>03</span><i/> YOUR INTERFACE, YOUR RULES</div><h2>Six styles.<br/><em>Zero sameness.</em></h2><p>Choose the expanded player that matches your mood. Switch from full controls to artwork-led minimalism, or put the lyrics right in the spotlight.</p><div className="style-list"><div><span>01</span><b>Classic</b><small>Everything at hand</small></div><div><span>02</span><b>Poster</b><small>Artwork takes over</small></div><div><span>03</span><b>Timeline</b><small>Progress in focus</small></div><div><span>04</span><b>Lyrics</b><small>Words take the lead</small></div><div><span>05</span><b>Artwork</b><small>Clean and immersive</small></div><div><span>06</span><b>Canvas</b><small>Motion meets music</small></div></div></div>
        <div className="styles-visual"><div className="styles-glow"/><div className="styles-orbit"/><Phone screen="player" className="styles-phone"/><div className="styles-caption"><Sparkles size={14}/> PICK A LOOK. MAKE IT YOURS.</div></div>
      </section>

      <section className="download-section section-shell" id="download">
        <div className="download-panel"><div className="download-copy"><div className="section-index"><span>04</span><i/> GET STARTED</div><h2>Make your<br/>phone <em>play.</em></h2><p>Get the latest stable build, choose the package for your device and settle into your next favourite listening setup.</p><div className="download-note"><ShieldCheck size={16}/><span>Open source under GPL-3.0. Source code and release history are public.</span></div><a href={REPO_PAGE} target="_blank" rel="noreferrer" className="source-link"><Github size={16}/> Browse the source code <ArrowUpRight size={14}/></a></div>
          <div className="download-card"><div className="download-card-head"><div><span className="download-overline">LATEST STABLE RELEASE</span><h3>{latest?.tag ?? "2.1.0"}</h3><p>{latest ? formatDate(latest.publishedAt) : "Latest release"}</p></div><span className="stable-badge"><i/> STABLE</span></div>
            <div className="download-select-wrap"><label htmlFor="apk-choice">CHOOSE YOUR DEVICE</label><select id="apk-choice" value={selectedAsset} onChange={(event) => setSelectedAsset(event.target.value)}><option value="universal">Universal APK · Recommended</option><option value="arm64-v8a">ARM64 APK · Android 10+</option><option value="android7">Legacy APK · Android 7–9</option></select><ChevronDown className="select-chevron" size={15}/></div>
            <div className="download-file"><div className="file-icon"><Download size={17}/></div><div><b>{selected?.name ?? "KatMusic APK"}</b><small>{selected ? formatBytes(selected.size) : "Release assets on GitHub"}</small></div><span className="file-check"><Check size={14}/></span></div>
            <a className="download-button" href={downloadHref}><Download size={17}/> Download APK <ArrowUpRight size={16}/></a>
            <a className="all-releases" href={RELEASES_PAGE}>View all releases <ArrowRight size={14}/></a>
            {releaseError && <p className="download-error">Live release details couldn&apos;t load. You can still browse every build on GitHub.</p>}
            <p className="install-footnote">Package availability and compatibility depend on your Android device.</p>
          </div>
        </div>
      </section>

      <section className="closing-section"><div className="closing-orb"/><div className="section-index"><span>05</span><i/> BUILT IN THE OPEN</div><h2>Keep the music.<br/><em>Lose the limits.</em></h2><p>KatMusic is built in the open. Explore the code, follow development or help shape what comes next.</p><div className="closing-actions"><a href={REPO_PAGE} target="_blank" rel="noreferrer"><Github size={17}/> Explore on GitHub <ArrowUpRight size={15}/></a><a href={REPO_PAGE + "/issues"} target="_blank" rel="noreferrer">Report an issue <ArrowUpRight size={15}/></a></div></section>
    </main>
    <footer className="site-footer"><div className="footer-brand"><a className="brand" href="#top"><Image src="/katmusic_logo.svg" alt="" width={26} height={26}/><span>katmusic<span className="brand-period">.</span></span></a><p>A little more feeling in every play.</p></div><div className="footer-links"><a href="#features">Features</a><a href={RELEASES_PAGE}>Releases</a><a href={REPO_PAGE + "/blob/main/readme.md"}>Documentation</a><a href={REPO_PAGE + "/blob/main/LICENSE"}>GPL-3.0</a></div><span className="footer-copy">© {new Date().getFullYear()} ItzFallenMe</span></footer>
  </div>;
}
