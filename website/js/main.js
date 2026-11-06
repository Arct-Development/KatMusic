(() => {
  "use strict";
  const REPO = "Arct-Development/KatMusic", API = `https://api.github.com/repos/${REPO}/releases?per_page=100`;
  const RELEASES = `https://github.com/${REPO}/releases`;
  const $ = (s, r = document) => r.querySelector(s), $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const STYLES = [["Classic", "Full player, tabs, queue and controls."], ["Poster", "Large circular artwork and expressive track text."], ["Timeline", "Progress-led layout with oversized timestamps."], ["Lyrics Stage", "Lyrics-focused view with synced highlighting and source controls."], ["iPod", "A compact, tactile player inspired by classic music devices."], ["Full Video", "Immersive playback with animated Canvas artwork when available."]];
  const TICKER = ["Listen Together", "Group Blend", "Liquid Glass UI", "YouTube Music catalogue", "Word-synced lyrics", "Shareable lyric cards", "Native C++ audio", "Parametric EQ", "Automix", "Offline downloads", "Six player styles", "Android · Windows · macOS · Linux", "Open source"];

  /* ---------- platforms ---------- */
  const ICONS = {
    android: '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="13.5" r="6.4"/><rect x="3.2" y="12.4" width="2.3" height="5.6" rx="1.15"/><rect x="18.5" y="12.4" width="2.3" height="5.6" rx="1.15"/><path d="M8.1 6.3 6.9 4.4M15.9 6.3l1.2-1.9" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/><circle cx="9.8" cy="12.2" r=".9" fill="#000"/><circle cx="14.2" cy="12.2" r=".9" fill="#000"/></svg>',
    windows: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M3 5.4 10.4 4v7.2H3zM11.6 3.8 21 2.4v8.8h-9.4zM3 12.8h7.4V20L3 18.6zM11.6 12.8H21v8.8l-9.4-1.4z"/></svg>',
    macos: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M16.5 12.7c0-2.1 1.7-3.1 1.8-3.2-1-1.5-2.5-1.7-3-1.7-1.3-.1-2.5.8-3.1.8-.7 0-1.7-.8-2.8-.7-1.4 0-2.8.8-3.5 2.1-1.5 2.6-.4 6.4 1.1 8.5.7 1 1.6 2.2 2.7 2.1 1.1 0 1.5-.7 2.8-.7s1.6.7 2.7.7c1.1 0 1.9-1.1 2.6-2.1.5-.8.7-1.1 1.1-2-2.9-1.1-2.4-4.3-2.4-4.4ZM14.4 5.4c.6-.7 1-1.7.9-2.7-.9.1-2 .6-2.6 1.3-.6.7-1.1 1.7-.9 2.7 1 .1 2-.6 2.6-1.3Z"/></svg>',
    linux: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2c-2.4 0-3.6 2.1-3.6 4.3 0 1.6-.4 2.6-1.5 4-1.2 1.6-1.9 3.1-1.9 4.7 0 2.9 3.1 5 7 5s7-2.1 7-5c0-1.6-.7-3.1-1.9-4.7-1.1-1.4-1.5-2.4-1.5-4C15.6 4.1 14.4 2 12 2Zm-1.6 4.1a1 1 0 1 1 0 2 1 1 0 0 1 0-2Zm3.2 0a1 1 0 1 1 0 2 1 1 0 0 1 0-2ZM12 8.5c1.2 0 2.2.7 2.2 1.5S13.2 11.5 12 11.5s-2.2-.7-2.2-1.5S10.8 8.5 12 8.5Z"/></svg>'
  };
  const PLATFORMS = [
    {
      id: "android", name: "Android", file: "APK package", button: "Download APK",
      desc: "The full app, in your pocket: Listen Together, Group Blend, synced lyrics, native audio and offline downloads.",
      specs: ["Android 10+", "ARM64 or universal", "Legacy for Android 7–9"],
      hint: "Signed APKs. Verify with the SHA-256 checksum from the release.",
      match: (n) => /\.apk$/i.test(n)
    },
    {
      id: "windows", name: "Windows", file: "Windows package", button: "Download for Windows",
      desc: "The same player, queue and lyrics on a desktop window, with Discord presence and system media keys.",
      specs: ["Windows 10 / 11", "64-bit", "Portable ZIP — no installer"],
      hint: "Extract the ZIP anywhere, then run KatMusic.exe. The app registers its own katmusic:// links.",
      match: (n) => /windows/i.test(n) && /\.zip$/i.test(n)
    },
    {
      id: "macos", name: "macOS", file: "macOS package", button: "Download for macOS",
      desc: "A native window for the same library, with keyboard shortcuts tuned for macOS and URL-scheme handoff.",
      specs: ["macOS 12+", "Apple silicon + Intel", "Unsigned .app in a ZIP"],
      hint: "Drag KatMusic.app into Applications. If Gatekeeper blocks the first launch, right-click → Open.",
      match: (n) => /macos|macosx|osx|darwin/i.test(n) && /\.(zip|dmg)$/i.test(n)
    },
    {
      id: "linux", name: "Linux", file: "Linux package", button: "Download for Linux",
      desc: "A 64-bit desktop build for the same account, playlists and downloads, packaged as a portable tarball.",
      specs: ["64-bit Linux", "Tarball bundle", "GTK 3 + media_kit runtime"],
      hint: "Extract and run the KatMusic binary inside the folder. Install your distribution's GTK 3 and libmpv packages if it refuses to start.",
      match: (n) => /linux/i.test(n) && /\.(tar\.gz|tgz|zip|appimage)$/i.test(n)
    }
  ];
  const STEPS = {
    android: [
      ["Choose a build", "Universal for most phones, ARM64 for 64-bit ARM devices, legacy for Android 7–9."],
      ["Install the APK", "Open the download and allow installs from your browser when Android asks."],
      ["Sign in or continue as guest", "Guest mode works offline; connect YouTube Music or Last.fm whenever you like."]
    ],
    windows: [
      ["Download the ZIP", "Grab the Windows package above — it already contains everything the app needs."],
      ["Extract and run", "Unzip the folder anywhere and launch KatMusic.exe. No installer, no admin rights."],
      ["Sign in and pin it", "Sign in or continue as guest, then pin KatMusic to the taskbar for quick access."]
    ],
    macos: [
      ["Download the ZIP", "The macOS package is a signed-out .app bundle inside a ZIP."],
      ["Move it to Applications", "Drag KatMusic.app into Applications. On first launch use right-click → Open if Gatekeeper asks."],
      ["Sign in", "Sign in or continue as guest, then keep KatMusic in the Dock."]
    ],
    linux: [
      ["Download the tarball", "The Linux package is a portable folder, not a distro-specific package."],
      ["Extract and run", "Unpack it and start the KatMusic binary. Install GTK 3 and libmpv if a library is missing."],
      ["Sign in", "Sign in or continue as guest. The app adds its own katmusic:// handler."]
    ]
  };

  /* ---------- static pieces ---------- */
  $("#yr").textContent = new Date().getFullYear();
  $("#ticker").innerHTML = [...TICKER, ...TICKER].map((t) => `<span>${t} ✦</span>`).join("");
  const h = (i) => (Math.sin(i * 1.7) + Math.cos(i * 0.6) + 2) / 4;
  $("#wave").innerHTML = Array.from({ length: 41 }, (_, i) => `<i style="--h:${h(i).toFixed(2)};--i:${i}"></i>`).join("");
  $("#eq").innerHTML = Array.from({ length: 18 }, (_, i) => `<i style="--h:${(0.25 + h(i) * 0.75).toFixed(2)};--i:${i}"></i>`).join("");

  /* player-style tabs */
  const tabs = $("#tabs"), panel = $(".panel");
  const pick = (i) => {
    $$(".tab", tabs).forEach((b, n) => b.setAttribute("aria-selected", n === i));
    $("#st").textContent = STYLES[i][0]; $("#sd").textContent = STYLES[i][1];
    panel.classList.remove("swap"); void panel.offsetWidth; panel.classList.add("swap");
  };
  STYLES.forEach(([n], i) => { const b = document.createElement("button"); b.className = "tab"; b.role = "tab"; b.textContent = n; b.onclick = () => pick(i); tabs.append(b); });
  pick(0);

  /* nav */
  const burger = $("#burger"), links = $("#links");
  burger.onclick = () => { const o = links.classList.toggle("open"); burger.setAttribute("aria-expanded", o); };
  links.onclick = (e) => { if (e.target.closest("a")) { links.classList.remove("open"); burger.setAttribute("aria-expanded", "false"); } };

  /* ---------- animations ---------- */
  if (!reduce) {
    document.documentElement.classList.add("js");
    $$("[data-split]").forEach((el) => {            // split headline into animated words
      let i = 0;
      const wrap = (node) => [...node.childNodes].forEach((n) => {
        if (n.nodeType === 3) {
          const f = document.createDocumentFragment();
          n.textContent.split(/(\s+)/).forEach((p) => { if (!p.trim()) return f.append(p || ""); const s = document.createElement("span"); s.className = "w"; s.style.setProperty("--i", i++); s.textContent = p; f.append(s, " "); });
          n.replaceWith(f);
        } else if (n.nodeType === 1) wrap(n);
      });
      wrap(el);
    });
    const io = new IntersectionObserver((es) => es.forEach((e) => { if (e.isIntersecting) { e.target.classList.add("in"); io.unobserve(e.target); } }), { threshold: 0.12 });
    $$(".rv").forEach((el, i) => { el.style.setProperty("--d", `${(i % 3) * 0.09}s`); io.observe(el); });

    $$("[data-tilt]").forEach((el) => {              // 3D tilt following the pointer
      el.addEventListener("pointermove", (e) => { const r = el.getBoundingClientRect(); el.style.setProperty("--ry", `${((e.clientX - r.left) / r.width - 0.5) * 22}deg`); el.style.setProperty("--rx", `${-((e.clientY - r.top) / r.height - 0.5) * 14}deg`); });
      el.addEventListener("pointerleave", () => { el.style.setProperty("--ry", "0deg"); el.style.setProperty("--rx", "0deg"); });
    });
    document.addEventListener("pointermove", (e) => { // card spotlight + magnetic buttons
      const c = e.target.closest(".card"); if (c) { const r = c.getBoundingClientRect(); c.style.setProperty("--mx", `${e.clientX - r.left}px`); c.style.setProperty("--my", `${e.clientY - r.top}px`); }
      $$(".mag:hover").forEach((b) => { const r = b.getBoundingClientRect(); b.style.translate = `${(e.clientX - r.left - r.width / 2) * 0.18}px ${(e.clientY - r.top - r.height / 2) * 0.25}px`; });
    });
    document.addEventListener("pointerout", (e) => { const b = e.target.closest?.(".mag"); if (b) b.style.translate = ""; });
  }
  let tick = false;                                  // scroll progress, nav state, parallax
  const onScroll = () => {
    const y = scrollY, max = document.documentElement.scrollHeight - innerHeight;
    document.documentElement.style.setProperty("--sy", y); document.documentElement.style.setProperty("--p", max > 0 ? y / max : 0);
    $("#nav").classList.toggle("stuck", y > 40); tick = false;
  };
  addEventListener("scroll", () => { if (!tick) { tick = true; requestAnimationFrame(onScroll); } }, { passive: true }); onScroll();

  /* ---------- total-downloads counters (header pill + download card): count up from 0 ---------- */
  const counters = $$("[data-count]").map((el) => ({ el, shown: 0, seen: !el.closest("#download"), raf: 0 }));
  let cTarget = 0;
  function cRun(c) {
    cancelAnimationFrame(c.raf);
    const from = c.shown, to = cTarget, t0 = performance.now(), dur = reduce ? 0 : Math.min(2800, 1000 + Math.log10(Math.abs(to - from) + 1) * 450);
    const step = (t) => { const k = dur ? Math.min((t - t0) / dur, 1) : 1; c.shown = from + (to - from) * (1 - Math.pow(1 - k, 4)); c.el.textContent = Math.round(c.shown).toLocaleString(); if (k < 1) c.raf = requestAnimationFrame(step); };
    c.raf = requestAnimationFrame(step);
  }
  const setCount = (n) => { cTarget = n; counters.forEach((c) => c.seen && cRun(c)); };
  counters.filter((c) => !c.seen).forEach((c) => new IntersectionObserver((es, o) => { if (es[0].isIntersecting) { c.seen = true; o.disconnect(); if (cTarget) cRun(c); } }, { threshold: 0.4 }).observe(c.el));

  /* ---------- platform download panel ---------- */
  const fmtSize = (b) => (b >= 1048576 ? `${(b / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`);
  const fmtDate = (s) => { const d = new Date(s); return isNaN(d) ? "" : d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" }); };
  const apkLabel = (n) => { n = n.toLowerCase(); if (n.includes("uncompressed")) return "Raw build · unminified"; if (n.includes("android7")) return "Legacy · Android 7–9"; if (n.includes("arm64")) return "ARM64 · Android 10+"; return "Universal · recommended"; };
  const apkRank = (n) => { n = n.toLowerCase(); if (n.includes("uncompressed")) return 3; if (n.includes("android7")) return 2; if (n.includes("arm64")) return 1; return 0; };
  const desktopLabel = (p) => {
    if (p.id === "windows") return "Windows 10/11 · 64-bit portable";
    if (p.id === "macos") return "macOS 12+ · .app bundle";
    return "Linux 64-bit · portable tarball";
  };
  const isDownloadable = (n) => PLATFORMS.some((p) => p.match(n));

  function detectPlatform() {
    const ua = navigator.userAgent;
    if (/android/i.test(ua)) return "android";
    if (/iphone|ipad|ipod/i.test(ua)) return "android";        // no iOS build yet; show the closest real option
    if (/windows|win32|win64/i.test(ua)) return "windows";
    if (/mac os|macintosh/i.test(ua)) return "macos";
    if (/linux|x11/i.test(ua)) return "linux";
    return "android";
  }

  let rel = null, current = detectPlatform(), shownTag = null;
  const chosen = {};
  const plats = $("#plats");

  PLATFORMS.forEach((p) => {
    const b = document.createElement("button");
    b.className = "plat";
    b.type = "button";
    b.role = "tab";
    b.dataset.id = p.id;
    b.setAttribute("aria-selected", "false");
    b.innerHTML = `${ICONS[p.id] || ""}<span>${p.name}</span>`;
    b.onclick = () => { current = p.id; paint(); };
    plats.append(b);
  });

  const assetsFor = (p) => {
    const list = ((rel && rel.assets) || []).filter((a) => p.match(a.name));
    if (p.id === "android") list.sort((a, b) => apkRank(a.name) - apkRank(b.name));
    return list;
  };

  function paint() {
    const p = PLATFORMS.find((x) => x.id === current) || PLATFORMS[0];
    $$(".plat", plats).forEach((b) => {
      const on = b.dataset.id === p.id;
      b.setAttribute("aria-selected", String(on));
      b.setAttribute("aria-controls", "asset");
    });
    $("#pChip").textContent = p.name;
    $("#pName").textContent = `KatMusic for ${p.name}`;
    $("#pDesc").textContent = p.desc;
    $("#pSpecs").innerHTML = p.specs.map((s) => `<li>${s}</li>`).join("");
    $("#assetLabel").textContent = p.file;
    $("#hint").textContent = p.hint;
    $("#heroDl").textContent = p.button;

    const steps = STEPS[p.id];
    steps.forEach(([t, d], i) => { $(`#step${i + 1}t`).textContent = t; $(`#step${i + 1}p`).textContent = d; });
    $("#installLead").textContent = `Steps for ${p.name}. Switch platforms above to change them.`;

    const sel = $("#asset"), list = assetsFor(p), btn = $("#dlbtn"), miss = $("#miss");
    if (!rel) {
      sel.innerHTML = "<option>Loading…</option>";
      sel.disabled = true;
      $("#fname").textContent = "Fetching release…";
      $("#fsize").textContent = "";
      return;
    }
    sel.disabled = false;
    sel.innerHTML = "";
    if (!list.length) {
      sel.innerHTML = `<option>Not in ${rel.tag_name}</option>`;
      sel.disabled = true;
      $("#fname").textContent = `No ${p.name} package in ${rel.tag_name}`;
      $("#fsize").textContent = "Desktop packages attach to a release after their builds finish.";
      btn.textContent = "See all releases";
      btn.href = RELEASES;
      miss.hidden = false;
      miss.textContent = `The ${p.name} build for ${rel.tag_name} is not published yet. The release page will list it as soon as the build finishes.`;
      return;
    }
    miss.hidden = true;
    list.forEach((a, i) => {
      const o = document.createElement("option");
      o.value = i;
      o.textContent = p.id === "android" ? apkLabel(a.name) : desktopLabel(p);
      sel.append(o);
    });
    const prev = chosen[p.id];
    if (typeof prev === "number" && list[prev]) sel.value = prev;
    const show = () => {
      const a = list[+sel.value];
      if (!a) return;
      chosen[p.id] = +sel.value;
      $("#fname").textContent = a.name;
      $("#fsize").textContent = `${fmtSize(a.size)}${p.id === "android" ? " · signed" : ""}`;
      btn.textContent = p.button;
      btn.href = a.browser_download_url;
    };
    sel.onchange = show;
    show();
  }

  function render(r) {
    rel = r;
    $$("[data-tag]").forEach((e) => (e.textContent = r.tag_name));
    $$("[data-date]").forEach((e) => (e.textContent = e.closest(".chip") ? ` · ${fmtDate(r.published_at)}` : fmtDate(r.published_at)));
    $("#notes").href = r.html_url;
    const sum = r.assets.find((a) => /sha256|checksums/i.test(a.name));
    const s = $("#sum");
    s.hidden = !sum;
    if (sum) s.href = sum.browser_download_url;
    $("#err").hidden = true;
    paint();
    if (shownTag && shownTag !== r.tag_name) { const c = $(".dl-card"); c.classList.remove("flash"); void c.offsetWidth; c.classList.add("flash"); } // new release appeared while page was open
    shownTag = r.tag_name;
  }

  async function loadRelease() {
    try {
      let data; const c = sessionStorage.getItem("kat-rel");
      if (c) { const p = JSON.parse(c); if (Date.now() - p.t < 30000) data = p.d; }   // short cache keeps the download total fresh
      if (!data) {
        const r = await fetch(API, { headers: { Accept: "application/vnd.github+json" } });
        if (!r.ok) throw new Error(r.status);
        data = (await r.json()).map((x) => ({ tag_name: x.tag_name, draft: x.draft, prerelease: x.prerelease, published_at: x.published_at, html_url: x.html_url, assets: x.assets.map((a) => ({ name: a.name, size: a.size, download_count: a.download_count, browser_download_url: a.browser_download_url })) }));
        try { sessionStorage.setItem("kat-rel", JSON.stringify({ t: Date.now(), d: data })); } catch {}
      }
      const ok = data.filter((r) => !r.draft && r.assets.some((a) => isDownloadable(a.name)));
      const release = ok.find((r) => !r.prerelease) || ok[0];
      if (!release) throw new Error("none");
      render(release);
      // Every platform's packages count, across all releases: the asset
      // download_count is served by GitHub and already includes clicks here.
      setCount(data.reduce((n, r) => n + (r.draft ? 0 : r.assets.reduce((m, a) => m + (isDownloadable(a.name) ? a.download_count || 0 : 0), 0)), 0));
    } catch { $("#err").hidden = false; $("#fsize").textContent = "Use the GitHub releases page"; }
  }
  paint();
  loadRelease();

  // GitHub's API lags a few seconds behind a click, so refresh just after
  // one (bypassing the cache) instead of waiting for the 3-minute poll.
  const refreshAfterDownload = (delay) => setTimeout(() => {
    sessionStorage.removeItem("kat-rel");
    loadRelease();
  }, delay);
  $("#dlbtn").addEventListener("click", () => {
    refreshAfterDownload(10_000);
    refreshAfterDownload(30_000);
  });
  setInterval(loadRelease, 180000);                                  // re-check every 3 minutes while the page is open
  document.addEventListener("visibilitychange", () => { if (!document.hidden) loadRelease(); });
})();
