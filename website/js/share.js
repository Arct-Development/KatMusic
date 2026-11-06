(() => {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const title = $("shTitle"), artist = $("shArtist"), album = $("shAlbum");
  const art = $("shArt"), fallback = $("shFallback");
  const form = $("shForm"), input = $("shInput");
  const actions = $("shActions"), open = $("shOpen"), yt = $("shYt"), copy = $("shCopy");
  const status = $("shStatus");

  const PACKAGE = "com.itzfallenme.katmusic";
  const validVideo = (v) => /^[A-Za-z0-9_-]{6,24}$/.test((v || "").trim());
  const params = new URLSearchParams(location.search);
  const onAndroid = /android/i.test(navigator.userAgent);
  const onIOS = /iphone|ipad|ipod/i.test(navigator.userAgent);
  const skipAuto = params.get("noapp") === "1";

  function parseTarget(raw) {
    const text = (raw || "").trim();
    if (!text) return null;
    try {
      const url = new URL(text.includes("://") ? text : `https://${text}`);
      const videoId = (url.searchParams.get("v") || "").trim();
      if (!validVideo(videoId)) return null;
      return {
        v: videoId,
        title: url.searchParams.get("title") || "",
        artist: url.searchParams.get("artist") || "",
        album: url.searchParams.get("album") || "",
        art: url.searchParams.get("art") || ""
      };
    } catch {
      return null;
    }
  }

  function shareQuery(data) {
    const q = new URLSearchParams({ v: data.v });
    for (const key of ["title", "artist", "album", "art"]) {
      if (data[key]) q.set(key, data[key]);
    }
    return q;
  }

  const deepLink = (q) => `katmusic://track?${q.toString()}`;
  const pageLink = (q) => `${location.origin}${location.pathname}?${q.toString()}`;

  /* Android Chrome opens the app directly from an intent:// URL and uses
     browser_fallback_url when it is not installed, which avoids the dead
     "page cannot be opened" error a bare custom scheme leaves behind. */
  function intentLink(q) {
    const fallback = `${pageLink(q)}&noapp=1`;
    return `intent://track?${q.toString()}#Intent;scheme=katmusic;package=${PACKAGE};S.browser_fallback_url=${encodeURIComponent(fallback)};end`;
  }

  function launch(data) {
    const q = shareQuery(data);
    location.href = onAndroid ? intentLink(q) : deepLink(q);
  }

  let autoAttempted = false;
  function tryAutoOpen(data) {
    if (autoAttempted) return;
    autoAttempted = true;
    if (skipAuto) {
      status.textContent = "KatMusic is not installed on this device yet. Install it below, then open this link again.";
      return;
    }
    if (onIOS) {
      status.textContent = "KatMusic runs on Android, Windows, macOS and Linux — there is no iPhone or iPad build yet.";
      return;
    }
    status.textContent = "Opening KatMusic…";
    launch(data);
    const started = Date.now();
    setTimeout(() => {
      // The tab going hidden means the app took over; only nag when the
      // page is still in front of the user after the attempt.
      if (document.hidden || document.visibilityState !== "visible") return;
      if (Date.now() - started < 1200) return;
      status.textContent = onAndroid
        ? "KatMusic did not open. It may not be installed — download it below, or listen on YouTube Music."
        : "If KatMusic did not open, it may not be installed. Get it below, or listen on YouTube Music.";
    }, 2200);
  }

  function render(data, replaceHistory, initial) {
    if (!data || !validVideo(data.v)) {
      actions.hidden = true;
      form.hidden = false;
      status.textContent = "Paste a KatMusic or YouTube Music link to open a shared track.";
      return;
    }
    const q = shareQuery(data);
    const page = pageLink(q);
    if (replaceHistory) history.replaceState(null, "", `?${q.toString()}`);

    const name = data.title || "Shared track";
    title.textContent = name;
    artist.textContent = data.artist || "KatMusic";
    album.textContent = data.album || "";
    album.hidden = !data.album;
    document.title = `${name} — KatMusic`;

    const image = data.art || `https://i.ytimg.com/vi/${encodeURIComponent(data.v)}/hqdefault.jpg`;
    art.src = image;
    art.alt = `${name} artwork`;
    art.hidden = false;
    fallback.hidden = true;
    art.onerror = () => { art.hidden = true; fallback.hidden = false; };

    open.href = onAndroid ? intentLink(q) : deepLink(q);
    yt.href = `https://music.youtube.com/watch?v=${encodeURIComponent(data.v)}`;
    actions.hidden = false;
    form.hidden = true;
    status.textContent = "Opening KatMusic…";
    copy.onclick = async () => {
      try {
        await navigator.clipboard.writeText(page);
        status.textContent = "Link copied. Send it to anyone — it opens in KatMusic on their device.";
      } catch {
        const area = document.createElement("textarea");
        area.value = page; document.body.append(area); area.select();
        document.execCommand("copy"); area.remove();
        status.textContent = "Link copied. Send it to anyone — it opens in KatMusic on their device.";
      }
    };
    open.onclick = () => {
      status.textContent = "Opening KatMusic… If nothing happens, install it below or use YouTube Music.";
    };
    if (initial) tryAutoOpen(data);
  }

  const initial = {
    v: params.get("v") || "",
    title: params.get("title") || "",
    artist: params.get("artist") || "",
    album: params.get("album") || "",
    art: params.get("art") || ""
  };
  render(initial, false, true);

  document.addEventListener("visibilitychange", () => {
    if (document.hidden && !actions.hidden) status.textContent = "Opening KatMusic…";
  });

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    const parsed = parseTarget(input.value);
    if (!parsed) {
      status.textContent = "That link does not contain a track id. Try a music.youtube.com/watch?v=… link.";
      return;
    }
    render(parsed, true, false);
  });
})();
