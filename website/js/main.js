(() => {
  "use strict";
  const REPO = "ItzFallenMe/KatMusic", API = `https://api.github.com/repos/${REPO}/releases?per_page=10`;
  const $ = (s, r = document) => r.querySelector(s), $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const reduce = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const STYLES = [["Classic", "Full player, tabs, queue and controls."], ["Poster", "Large circular artwork and expressive track text."], ["Timeline", "Progress-led layout with oversized timestamps."], ["Lyrics Stage", "Lyrics-focused view with synced highlighting and source controls."], ["iPod", "A compact, tactile player inspired by classic music devices."], ["Full Video", "Immersive playback with animated Canvas artwork when available."]];
  const TICKER = ["YouTube Music catalogue", "Word-synced lyrics", "Shareable lyric cards", "Native C++ audio", "Parametric EQ", "Automix", "Offline downloads", "Six player styles", "Open source"];

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

  /* ---------- live GitHub releases (always the newest stable) ---------- */
  const fmtSize = (b) => (b >= 1048576 ? `${(b / 1048576).toFixed(1)} MB` : `${Math.max(1, Math.round(b / 1024))} KB`);
  const fmtDate = (s) => { const d = new Date(s); return isNaN(d) ? "" : d.toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" }); };
  const label = (n) => { n = n.toLowerCase(); if (n.includes("uncompressed")) return null; if (n.includes("android7")) return "Legacy · Android 7–9"; if (n.includes("arm64")) return "ARM64 · Android 10+"; return "Universal · Recommended"; };
  let shownTag = null;

  function render(rel) {
    const apks = rel.assets.filter((a) => /\.apk$/i.test(a.name) && label(a.name))
      .sort((a, b) => (label(a.name).startsWith("Universal") ? -1 : 0) - (label(b.name).startsWith("Universal") ? -1 : 0));
    const sum = rel.assets.find((a) => /sha256/i.test(a.name)), sel = $("#apk"), prev = sel.value;
    $$("[data-tag]").forEach((e) => (e.textContent = rel.tag_name));
    $$("[data-date]").forEach((e) => (e.textContent = e.closest(".chip") ? ` · ${fmtDate(rel.published_at)}` : fmtDate(rel.published_at)));
    sel.innerHTML = ""; apks.forEach((a, i) => { const o = document.createElement("option"); o.value = i; o.textContent = label(a.name); sel.append(o); });
    if (prev && apks[prev]) sel.value = prev;
    const upd = () => { const a = apks[sel.value]; if (!a) return; $("#fname").textContent = a.name; $("#fsize").textContent = fmtSize(a.size); $("#dlbtn").href = a.browser_download_url; };
    sel.onchange = upd; upd();
    $("#notes").href = rel.html_url; const s = $("#sum"); s.hidden = !sum; if (sum) s.href = sum.browser_download_url;
    $("#err").hidden = true;
    if (shownTag && shownTag !== rel.tag_name) { const c = $(".dl-card"); c.classList.remove("flash"); void c.offsetWidth; c.classList.add("flash"); } // new release appeared while page was open
    shownTag = rel.tag_name;
  }

  async function loadRelease() {
    try {
      let data; const c = sessionStorage.getItem("kat-rel");
      if (c) { const p = JSON.parse(c); if (Date.now() - p.t < 120000) data = p.d; }   // 2-minute cache keeps GitHub rate limits happy
      if (!data) {
        const r = await fetch(API, { headers: { Accept: "application/vnd.github+json" } });
        if (!r.ok) throw new Error(r.status);
        data = await r.json(); sessionStorage.setItem("kat-rel", JSON.stringify({ t: Date.now(), d: data }));
      }
      const ok = data.filter((r) => !r.draft && r.assets.some((a) => /\.apk$/i.test(a.name)));
      const rel = ok.find((r) => !r.prerelease) || ok[0];
      if (!rel) throw new Error("none");
      render(rel);
    } catch { $("#err").hidden = false; $("#fsize").textContent = "Use the GitHub releases page"; }
  }
  loadRelease();
  setInterval(loadRelease, 180000);                                  // re-check every 3 minutes while the page is open
  document.addEventListener("visibilitychange", () => { if (!document.hidden) loadRelease(); });
})();
