(() => {
  "use strict";
  /* Report-a-bug. Reports are emailed to the KatMusic team through FormSubmit
     (a free form-to-email relay), so the visitor needs no GitHub account.
     If the relay is unreachable, a pre-filled email draft is offered instead. */
  const TO = "arctdevelopments@gmail.com";
  const FORM_KEY = "f9f2109595b7c5311adb5f28cc899230"; // FormSubmit alias for the address above (from the activation email)
  const ENDPOINT = `https://formsubmit.co/ajax/${FORM_KEY}`;
  const $ = (s, r = document) => r.querySelector(s);
  const form = $("#rpForm");
  if (!form) return;

  const f = { title: $("#rpTitle"), area: $("#rpArea"), plat: $("#rpPlat"), ver: $("#rpVer"),
              steps: $("#rpSteps"), exp: $("#rpExp"), dev: $("#rpDev"), honey: $("#rpHoney") };
  const status = $("#rpStatus"), prev = $("#rpPrev"), go = $("#rpGo");
  const eT = $("#rpTitleErr"), eS = $("#rpStepsErr");
  const COOLDOWN_KEY = "kat-rp-last";

  (() => {                                   // preselect the visitor's platform
    const ua = navigator.userAgent; let p = "Android";
    if (/android/i.test(ua)) p = "Android";
    else if (/windows|win32|win64/i.test(ua)) p = "Windows";
    else if (/mac os|macintosh/i.test(ua) && !/iphone|ipad|ipod/i.test(ua)) p = "macOS";
    else if (/linux|x11/i.test(ua)) p = "Linux";
    f.plat.value = p;
  })();

  const clean = (s) => s.replace(/\r\n?/g, "\n").trim();
  const severity = () => (form.querySelector("input[name=rpSev]:checked") || {}).value || "Minor";
  const deviceInfo = () => [
    `- Browser: ${navigator.userAgent}`,
    `- Language: ${navigator.language || "unknown"}`,
    `- Screen: ${screen.width}×${screen.height} @${window.devicePixelRatio || 1}x`,
    `- Page: ${location.origin}${location.pathname}`
  ].join("\n");

  function build() {
    const title = clean(f.title.value).replace(/\s+/g, " ");
    const body = [
      "What happened:", clean(f.steps.value), "",
      "What I expected:", clean(f.exp.value) || "Not provided", "",
      "Details:",
      `- Area: ${f.area.value}`, `- Platform: ${f.plat.value}`,
      `- App version: ${clean(f.ver.value) || "Not provided"}`, `- Severity: ${severity()}`,
      ...(f.dev.checked ? ["", "Browser info:", deviceInfo()] : []),
      "", "Reported via the KatMusic website."
    ].join("\n");
    return { title: `[Bug] ${title}`, body };
  }

  const say = (html, cls = "") => { status.className = `rp-status ${cls}`.trim(); status.innerHTML = html; };
  const mark = (el, errEl, msg) => { el.classList.toggle("bad", !!msg); el.setAttribute("aria-invalid", msg ? "true" : "false"); errEl.textContent = msg; };
  function validate() {
    mark(f.title, eT, clean(f.title.value).length < 5 ? "Please give the bug a short title (at least 5 characters)." : "");
    mark(f.steps, eS, clean(f.steps.value).length < 15 ? "Please describe what happened (at least 15 characters)." : "");
    const first = [f.title, f.steps].find((el) => el.classList.contains("bad"));
    if (first) { first.focus(); return false; }
    return true;
  }
  [[f.title, eT], [f.steps, eS]].forEach(([el, er]) => el.addEventListener("input", () => { if (el.classList.contains("bad")) mark(el, er, ""); }));
  const counter = (el, out, max) => { const u = () => (out.textContent = `${el.value.length}/${max}`); el.addEventListener("input", u); u(); };
  counter(f.title, $("#rpTitleN"), 100); counter(f.steps, $("#rpStepsN"), 2000);

  async function copyText(text) {
    try { await navigator.clipboard.writeText(text); return true; } catch {}
    try {
      const ta = document.createElement("textarea");
      ta.value = text; ta.setAttribute("readonly", ""); ta.style.cssText = "position:fixed;top:-999px;opacity:0";
      document.body.append(ta); ta.select();
      const ok = document.execCommand("copy"); ta.remove(); return ok;
    } catch { return false; }
  }
  const mailto = (r) => `mailto:${TO}?subject=${encodeURIComponent(r.title)}&body=${encodeURIComponent(r.body).slice(0, 1800)}`;

  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    if (f.honey.value) return;                                   // bots fill the hidden field
    if (!validate()) return;
    const last = +(localStorage.getItem(COOLDOWN_KEY) || 0);
    if (Date.now() - last < 30000) { say("Please wait a few seconds before sending another report.", "warn"); return; }
    const r = build();
    go.disabled = true; say("Sending…");
    try {
      const res = await fetch(ENDPOINT, {
        method: "POST",
        headers: { "Content-Type": "application/json", Accept: "application/json" },
        body: JSON.stringify({
          _subject: r.title, _template: "box", _captcha: "false", _honey: "",
          Title: r.title, Area: f.area.value, Platform: f.plat.value,
          "App version": clean(f.ver.value) || "Not provided", Severity: severity(),
          "What happened": clean(f.steps.value), "What was expected": clean(f.exp.value) || "Not provided",
          "Browser info": f.dev.checked ? deviceInfo() : "Not attached"
        })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || data.success === "false" || data.success === false) throw new Error("relay");
      try { localStorage.setItem(COOLDOWN_KEY, String(Date.now())); } catch {}
      say("Thank you — your report was sent to the KatMusic team.", "ok");
      form.reset();
    } catch {
      say(`Couldn't send automatically. <a href="${mailto(r)}">Open an email draft</a> or use <b>Copy report</b> and send it to ${TO}.`, "warn");
    } finally { go.disabled = false; }
  });

  $("#rpCopy").addEventListener("click", async () => {
    if (!validate()) return;
    const r = build(), text = `${r.title}\n\n${r.body}`;
    const ok = await copyText(text);
    say(ok ? "Report copied to your clipboard." : "Couldn't copy automatically — please select and copy the text below.", ok ? "ok" : "warn");
    if (!ok) { prev.hidden = false; prev.textContent = text; prev.style.whiteSpace = "pre-wrap"; }
  });

  form.addEventListener("reset", () => setTimeout(() => {
    mark(f.title, eT, ""); mark(f.steps, eS, "");
    $("#rpTitleN").textContent = "0/100"; $("#rpStepsN").textContent = "0/2000";
    prev.hidden = true;
  }));
})();
