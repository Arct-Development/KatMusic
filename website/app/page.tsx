"use client";

import { useEffect, useMemo, useState } from "react";
import Image from "next/image";
import {
  ArrowDown, ArrowRight, ArrowUpRight, AudioLines, Check, ChevronDown,
  Download, Github, Headphones, Heart, Menu, Mic2, Music2, Radio,
  ShieldCheck, SlidersHorizontal, Sparkles, Waves, X,
} from "lucide-react";
import {
  RELEASES_PAGE, REPO_PAGE, fetchReleases, formatBytes, formatDate, isApk,
  type Release,
} from "@/lib/releases";

type Screen = "library" | "player" | "lyrics" | "audio";
const SCREENS: { id: Screen; name: string; note: string }[] = [
  { id: "library", name: "Library", note: "Your music, collected in one place." },
  { id: "player", name: "Player", note: "Playback controls without the clutter." },
  { id: "lyrics", name: "Lyrics", note: "Timed lyrics with translation and adjustment." },
  { id: "audio", name: "Audio", note: "Equalisation and output controls." },
];

function Artwork({ variant = "blue" }: { variant?: string }) {
  return <div className={"preview-artwork " + variant}><span className="artwork-shape"/><span className="artwork-caption">KAT / MUSIC</span></div>;
}

function PhoneScreen({ screen }: { screen: Screen }) {
  return <div className="phone-screen">
    <div className="phone-status"><span>9:41</span><span>◔ ◔ ▰</span></div>
    {screen === "library" && <>
      <div className="phone-appbar"><Image src="/katmusic_logo.svg" alt="" width={25} height={25}/><b>Library</b><span>•••</span></div>
      <p className="phone-kicker">YOUR COLLECTION</p><h3 className="phone-heading">Made for<br/>your listening.</h3>
      <div className="phone-search">⌕ <span>Search your music</span></div>
      <div className="phone-section-title">Your library <small>VIEW ALL</small></div>
      <div className="phone-library-grid"><div><Artwork/><b>Liked songs</b><small>Playlist</small></div><div><Artwork variant="cyan"/><b>On repeat</b><small>Mix</small></div></div>
      <div className="phone-row"><span className="phone-cover">♫</span><span><b>Recently played</b><small>Continue listening</small></span></div>
      <div className="phone-row"><span className="phone-cover cyan">◖</span><span><b>Downloads</b><small>Available offline</small></span></div>
      <div className="phone-bottom-nav"><span>⌂</span><span>⌕</span><span>◉</span><span>☷</span></div>
    </>}
    {screen === "player" && <>
      <div className="phone-playerbar"><span>⌄</span><b>NOW PLAYING</b><span>•••</span></div>
      <Artwork/>
      <div className="phone-track"><div><h3>Now playing</h3><p>Your current track</p></div><Heart size={17}/></div>
      <div className="phone-progress"><i/></div><div className="phone-time"><span>1:24</span><span>4:02</span></div>
      <div className="phone-controls"><span>↻</span><span>◀</span><b>Ⅱ</b><span>▶</span><span>☷</span></div>
      <div className="phone-audio-status"><AudioLines size={14}/> AUDIO ENGINE <b>READY</b></div>
      <div className="phone-upnext"><small>UP NEXT</small><b>Queue and playback</b></div>
    </>}
    {screen === "lyrics" && <>
      <div className="phone-playerbar"><span>⌄</span><b>LYRICS</b><span>•••</span></div>
      <Artwork variant="violet"/>
      <div className="phone-track"><div><h3>Synced lyrics</h3><p>Line and word timing</p></div></div>
      <div className="phone-lyric-lines"><span>Lyrics follow the music</span><b>Current line highlight</b><span>Translation and romanisation</span><span>Timing adjustment</span></div>
      <div className="phone-lyric-tools"><span>文 Translate</span><span>◷ Timing</span></div>
    </>}
    {screen === "audio" && <>
      <div className="phone-playerbar"><span>⌄</span><b>AUDIO SETTINGS</b><span>•••</span></div>
      <div className="phone-audio-heading"><SlidersHorizontal/><p>Shape your<br/><b>sound.</b></p></div>
      <div className="phone-preset"><small>PRESET</small><b>Equaliser settings <ChevronDown size={12}/></b></div>
      <div className="phone-eq">{[25,52,72,43,62,35,56].map((n,i)=><i key={i} style={{height:n+"%"}}/>)}</div>
      <div className="phone-eq-labels"><span>60</span><span>150</span><span>400</span><span>1k</span><span>2.4k</span><span>6k</span><span>15k</span></div>
      <div className="phone-setting"><b>Parametric EQ</b><span>ON</span></div><div className="phone-setting"><b>AutoEQ profiles</b><span>ON</span></div>
    </>}
  </div>;
}

function Phone({ screen, className = "" }: { screen: Screen; className?: string }) {
  return <div className={"phone-frame " + className}><PhoneScreen screen={screen}/></div>;
}

const FEATURES = [
  { icon: Radio, label: "Music & discovery", title: "A catalogue and a library.", copy: "Browse songs, albums, artists, playlists, mixes and podcasts. Keep liked music, listening history, local files and downloads close." },
  { icon: Mic2, label: "Lyrics", title: "Follow every line.", copy: "Line- and word-synced karaoke lyrics, multiple providers, translation, romanisation, text sizing and timing offset." },
  { icon: AudioLines, label: "Audio engine", title: "More control over sound.", copy: "A native C++ engine built around Oboe, with parametric and graphic EQ, presets, AutoEQ profiles and supported direct-output paths." },
  { icon: Download, label: "Downloads", title: "A queue you can manage.", copy: "See transfer progress, cancel or retry items, and use Wi-Fi-only downloads that pause on mobile data and resume later." },
  { icon: Waves, label: "Automix", title: "Transitions with intention.", copy: "Crossfade planning, BPM and phase analysis, and WSOLA time stretching help tracks move into one another." },
  { icon: Sparkles, label: "Personalisation", title: "Make the interface yours.", copy: "Six expanded player styles, light, dark and pure-black themes, artwork-derived palettes, widgets and layout controls." },
];

const FAQ = [
  ["Is KatMusic an official YouTube Music app?", "No. KatMusic is an independent, third-party Android project. It is not affiliated with, endorsed by or officially connected to Google or YouTube."],
  ["Which Android versions are supported?", "The current release offers Android 10+ packages and a legacy package for Android 7–9. Some features and media controls use alternatives on older Android versions."],
  ["Which APK should I install?", "The universal APK is the recommended option for most devices. The ARM64 package is for 64-bit ARM devices. Choose the legacy package only for Android 7–9."],
  ["Does every audio feature work on every phone?", "No. Output modes, supported codecs and bit-perfect behaviour depend on the Android device, DAC, audio route and operating-system limits."],
  ["Does KatMusic collect analytics?", "The project README states that KatMusic does not bundle an analytics or advertising-tracking SDK. Network requests are made to services required by the features you use."],
  ["Can I inspect or contribute to the source?", "Yes. KatMusic is GPL-3.0 licensed. The source, issue tracker, contribution guide and release history are public on GitHub."],
];

export default function HomePage() {
  const [releases, setReleases] = useState<Release[] | null>(null);
  const [releaseError, setReleaseError] = useState(false);
  const [activeScreen, setActiveScreen] = useState<Screen>("player");
  const [selectedAsset, setSelectedAsset] = useState("universal");
  const [menuOpen, setMenuOpen] = useState(false);

  useEffect(() => {
    let alive = true;
    fetchReleases(8).then((data) => { if (alive) setReleases(data); })
      .catch(() => { if (alive) setReleaseError(true); });
    return () => { alive = false; };
  }, []);

  const latest = releases?.find((item) => !item.prerelease) ?? releases?.[0] ?? null;
  const apks = useMemo(() => latest?.assets.filter((asset) => isApk(asset.name)) ?? [], [latest]);
  const selected = apks.find((asset) => asset.name.toLowerCase().includes(selectedAsset)) ??
    apks.find((asset) => asset.name.toLowerCase().includes("universal") && !asset.name.toLowerCase().includes("uncompressed")) ??
    apks.find((asset) => !asset.name.toLowerCase().includes("android7")) ?? apks[0];
  const checksum = latest?.assets.find((asset) => asset.name.toLowerCase().includes("sha256"));
  const active = SCREENS.find((item) => item.id === activeScreen) ?? SCREENS[1];

  return <div className="kat-site">
    <header className="site-header">
      <div className="nav-wrap">
        <a className="brand" href="#top" aria-label="KatMusic home"><Image src="/katmusic_logo.svg" alt="" width={34} height={34} priority/><span>KatMusic</span></a>
        <nav className={menuOpen ? "nav-links nav-links-open" : "nav-links"}>
          <a href="#features" onClick={() => setMenuOpen(false)}>Features</a>
          <a href="#experience" onClick={() => setMenuOpen(false)}>The app</a>
          <a href="#install" onClick={() => setMenuOpen(false)}>Install</a>
          <a href="#faq" onClick={() => setMenuOpen(false)}>FAQ</a>
          <a href={REPO_PAGE} target="_blank" rel="noreferrer">Source <ArrowUpRight size={14}/></a>
        </nav>
        <a className="nav-cta" href="#download">Download <ArrowDown size={14}/></a>
        <button className="menu-toggle" aria-label={menuOpen ? "Close menu" : "Open menu"} onClick={() => setMenuOpen((value) => !value)}>{menuOpen ? <X/> : <Menu/>}</button>
      </div>
    </header>

    <main id="top">
      <section className="hero">
        <div className="hero-copy">
          <p className="eyebrow"><span/> AN ANDROID MUSIC PLAYER</p>
          <h1>Music, with room<br/>to make it <em>yours.</em></h1>
          <p className="hero-description">Streaming, your personal library and serious playback tools — brought together in one Android app.</p>
          <div className="hero-actions">
            <a className="button-primary" href="#download"><Download size={17}/> Download KatMusic <ArrowUpRight size={15}/></a>
            <a className="text-link" href="#experience">Explore the app <ArrowDown size={15}/></a>
          </div>
          <div className="hero-meta"><span><ShieldCheck size={15}/> GPL-3.0 open source</span><span>Android 7+</span><span>Kotlin · Compose · C++</span></div>
          <div className="release-inline"><span className="release-dot"/><span>Latest stable</span><b>{latest?.tag ?? "2.1.0"}</b>{latest?.publishedAt && <time>{formatDate(latest.publishedAt)}</time>}</div>
        </div>
        <div className="hero-visual">
          <div className="hero-index">01 <span>/</span> PRODUCT PREVIEW</div>
          <div className="phone-stage">
            <Phone screen="library" className="phone-back phone-back-left"/>
            <Phone screen="lyrics" className="phone-back phone-back-right"/>
            <Phone screen={activeScreen} className="phone-front"/>
            <span className="phone-orbit orbit-a"/><span className="phone-orbit orbit-b"/>
          </div>
          <div className="preview-caption"><span>INTERFACE STUDY</span><p>{active.note}</p><span className="preview-disclaimer">Illustrative UI preview · not app screenshots</span></div>
          <div className="preview-tabs" aria-label="Choose interface preview">{SCREENS.map((screen) => <button key={screen.id} className={activeScreen === screen.id ? "preview-tab active" : "preview-tab"} onClick={() => setActiveScreen(screen.id)}>{screen.name}</button>)}</div>
        </div>
      </section>

      <div className="fact-strip"><span>YOUTUBE MUSIC CATALOGUE</span><i/><span>SYNCED LYRICS</span><i/><span>NATIVE AUDIO</span><i/><span>OFFLINE DOWNLOADS</span><i/><span>OPEN SOURCE</span></div>

      <section className="intro section-wrap" id="experience">
        <div className="section-label"><span>01</span> THE IDEA</div>
        <div className="intro-content"><h2>One place for<br/>the way you <em>listen.</em></h2><div><p>KatMusic combines a streaming catalogue with your own library and playback controls that go beyond the basics.</p><p>It is built with Kotlin and Jetpack Compose, with a native C++ audio engine for the parts that benefit from it.</p><a className="inline-link" href={REPO_PAGE + "/blob/main/readme.md"} target="_blank" rel="noreferrer">Read the project overview <ArrowUpRight size={14}/></a></div></div>
        <div className="principles">
          <article><span>01 / DISCOVER</span><Music2/><h3>Find something<br/>worth keeping.</h3><p>Songs, artists, albums, mixes, podcasts and local music.</p></article>
          <article><span>02 / PERSONALISE</span><SlidersHorizontal/><h3>Set it up<br/>your way.</h3><p>Player styles, themes, artwork colours and lyric controls.</p></article>
          <article><span>03 / LISTEN</span><Headphones/><h3>Stay with<br/>the music.</h3><p>Background playback, queue tools, downloads and audio controls.</p></article>
        </div>
      </section>

      <section className="features section-wrap" id="features">
        <div className="section-heading"><div><div className="section-label"><span>02</span> WHAT'S INSIDE</div><h2>Thoughtful tools.<br/><em>Real control.</em></h2></div><p>Specific features, not empty superlatives. Here is what the app actually includes.</p></div>
        <div className="feature-list">{FEATURES.map((feature, index) => <article className="feature-row" key={feature.label}><span className="feature-number">0{index + 1}</span><feature.icon className="feature-icon" size={22}/><div className="feature-title"><small>{feature.label}</small><h3>{feature.title}</h3></div><p>{feature.copy}</p></article>)}</div>
      </section>

      <section className="player-section">
        <div className="section-wrap player-layout">
          <div className="player-copy"><div className="section-label"><span>03</span> PLAYER STYLES</div><h2>Six ways to<br/><em>see the music.</em></h2><p>Pick the expanded player layout that fits your listening. Change it in Settings → Playback &amp; UI → Player Style.</p><a className="inline-link" href={REPO_PAGE + "/blob/main/readme.md#player-styles"} target="_blank" rel="noreferrer">Compare all six styles <ArrowUpRight size={14}/></a></div>
          <div className="style-table">
            {[
              ["01","Classic","Full player, tabs, queue and controls."],
              ["02","Poster","Large circular artwork and expressive track text."],
              ["03","Timeline","Progress-led layout with oversized timestamps."],
              ["04","Lyrics","Lyrics-focused view with timed syllable highlighting."],
              ["05","Artwork","Large album art and focused controls."],
              ["06","Canvas","Immersive background using available Canvas artwork."],
            ].map((item) => <div className="style-row" key={item[0]}><span>{item[0]}</span><b>{item[1]}</b><p>{item[2]}</p><ArrowUpRight size={14}/></div>)}
          </div>
        </div>
      </section>

      <section className="install-section section-wrap" id="install">
        <div className="section-label"><span>04</span> GETTING STARTED</div>
        <div className="section-heading"><div><h2>Up and running<br/>in <em>three steps.</em></h2></div><p>Choose the package for your Android version, install it, then configure the services you want to use.</p></div>
        <div className="install-steps">
          <article><span>01</span><h3>Choose a build</h3><p>Use Universal for most devices. ARM64 is for 64-bit ARM phones. Legacy is for Android 7–9.</p></article>
          <article><span>02</span><h3>Install the APK</h3><p>Open the downloaded package and follow Android's installation prompt.</p></article>
          <article><span>03</span><h3>Set up KatMusic</h3><p>Launch the app and configure account access or optional integrations in Settings.</p></article>
        </div>
        <p className="install-caveat">Compatibility varies by device. Some output modes and advanced audio behaviours depend on your hardware and Android version.</p>
      </section>

      <section className="download-section section-wrap" id="download">
        <div className="download-main">
          <div className="download-copy"><div className="section-label"><span>05</span> DOWNLOAD</div><h2>Your next<br/>listening setup.</h2><p>Get the latest stable release directly from the project. No third-party APK mirrors.</p><div className="download-proof"><ShieldCheck size={17}/><span>Public GitHub release · GPL-3.0 source</span></div></div>
          <div className="release-card">
            <div className="release-card-top"><div><span className="micro-label">LATEST STABLE RELEASE</span><h3>{latest?.tag ?? "2.1.0"}</h3><p>{latest ? formatDate(latest.publishedAt) : "Release details load from GitHub"}</p></div><Image src="/katmusic_logo.svg" alt="" width={52} height={52}/></div>
            <label htmlFor="apk-choice">INSTALL PACKAGE</label>
            <div className="select-wrap"><select id="apk-choice" value={selectedAsset} onChange={(event) => setSelectedAsset(event.target.value)}><option value="universal">Universal · Recommended</option><option value="arm64-v8a">ARM64 · Android 10+</option><option value="android7">Legacy · Android 7–9</option></select><ChevronDown size={16}/></div>
            <div className="asset-info"><div><b>{selected?.name ?? "APK package"}</b><small>{selected ? formatBytes(selected.size) : "Open GitHub releases to choose a build"}</small></div><Check size={17}/></div>
            <a className="button-primary download-button" href={selected?.downloadUrl ?? RELEASES_PAGE}><Download size={17}/> Download APK <ArrowUpRight size={15}/></a>
            <div className="release-links"><a href={latest?.htmlUrl ?? RELEASES_PAGE} target="_blank" rel="noreferrer">Release notes <ArrowUpRight size={13}/></a>{checksum && <a href={checksum.downloadUrl}>SHA-256 checksum <ArrowUpRight size={13}/></a>}<a href={RELEASES_PAGE} target="_blank" rel="noreferrer">All versions <ArrowUpRight size={13}/></a></div>
            {releaseError && <p className="release-error">GitHub release details could not load. The releases page is still available.</p>}
          </div>
        </div>
      </section>

      <section className="technical section-wrap">
        <div className="section-label"><span>06</span> UNDER THE HOOD</div>
        <div className="section-heading"><div><h2>Built with<br/><em>purpose.</em></h2></div><p>A native Android app with a clear technology stack. Explore the source to see how each part fits together.</p></div>
        <div className="tech-grid">
          <div><small>APP</small><b>Kotlin</b><span>Jetpack Compose · Material 3</span></div>
          <div><small>PLAYBACK</small><b>Media3</b><span>Media sessions · background playback</span></div>
          <div><small>AUDIO</small><b>C++ / Oboe</b><span>Native processing · DSP</span></div>
          <div><small>DATA</small><b>Room / DataStore</b><span>Local persistence · app state</span></div>
        </div>
        <div className="technical-note"><ShieldCheck size={17}/><p>KatMusic does not bundle an analytics or advertising-tracking SDK, according to its project documentation. Optional integrations are controlled by your settings.</p></div>
      </section>

      <section className="faq-section section-wrap" id="faq">
        <div className="section-heading"><div><div className="section-label"><span>07</span> GOOD TO KNOW</div><h2>Questions, <em>answered.</em></h2></div><a className="inline-link" href={REPO_PAGE + "/issues"} target="_blank" rel="noreferrer">Ask on GitHub <ArrowUpRight size={14}/></a></div>
        <div className="faq-list">{FAQ.map((item, index) => <details key={item[0]} className="faq-item"><summary><span>{String(index + 1).padStart(2,"0")}</span><b>{item[0]}</b><i>+</i></summary><p>{item[1]}</p></details>)}</div>
      </section>

      <section className="open-source">
        <div className="open-source-inner"><Image src="/katmusic_logo.svg" alt="" width={66} height={66}/><div><span className="micro-label">BUILT IN THE OPEN</span><h2>Good music.<br/><em>Open source.</em></h2><p>Read the code, follow development, report a bug or contribute a feature.</p></div><div className="open-source-actions"><a className="button-primary" href={REPO_PAGE} target="_blank" rel="noreferrer"><Github size={17}/> View source <ArrowUpRight size={15}/></a><a className="text-link" href={REPO_PAGE + "/issues"} target="_blank" rel="noreferrer">Issues and feedback <ArrowUpRight size={14}/></a></div></div>
      </section>
    </main>

    <footer className="site-footer"><a className="brand" href="#top"><Image src="/katmusic_logo.svg" alt="" width={28} height={28}/><span>KatMusic</span></a><p>Independent Android music player · GPL-3.0</p><div><a href={REPO_PAGE + "/blob/main/readme.md"}>Documentation</a><a href={REPO_PAGE + "/blob/main/CHANGELOG.md"}>Changelog</a><a href={REPO_PAGE + "/blob/main/LICENSE"}>License</a></div><span className="copyright">© {new Date().getFullYear()} ItzFallenMe</span></footer>
  </div>;
}
