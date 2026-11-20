(() => {
  "use strict";
  /* Donation feature: payment dialog (UPI QR / UPI ID / Crypto) + donor board fed by donations.json */
  const $ = (s, r = document) => r.querySelector(s), $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const UPI_ID = "atishayprasad@fam", UPI_NAME = "Atishay Prasad Sahu";
  const UPI_LINK = `upi://pay?pa=${UPI_ID}&pn=${encodeURIComponent(UPI_NAME)}&cu=INR`;
  const COINS = [
    { id: "btc", name: "Bitcoin", net: "BTC", addr: "bc1qny8ptghxra0fk7dms0r6z476kg272shg4kql3n", img: "assets/img/qr-btc.png" },
    { id: "eth", name: "Ethereum", net: "ETH · ERC-20", addr: "0x9633090e63A842F38797049C763Cf3F0732f7d12", img: "assets/img/qr-eth.png" },
    { id: "sol", name: "Solana", net: "SOL", addr: "EidVTHdBgYoWPecYrQCMY7TAzJ1xyahafduN56jWXo8b", img: "assets/img/qr-sol.png" },
    { id: "ltc", name: "Litecoin", net: "LTC", addr: "ltc1qnmd38ud8ly3ae2s3n6rx2q6jjn3n3hz3y83n2s", img: "assets/img/qr-ltc.png" }
  ];

  /* ---------- copy helper ---------- */
  async function copy(text, btn) {
    let ok = false;
    try { await navigator.clipboard.writeText(text); ok = true; } catch (_) {
      const t = document.createElement("textarea"); t.value = text; t.style.cssText = "position:fixed;opacity:0"; document.body.append(t); t.select();
      try { ok = document.execCommand("copy"); } catch (_) {} t.remove();
    }
    if (!btn) return;
    const old = btn.dataset.label || (btn.dataset.label = btn.textContent);
    btn.textContent = ok ? "Copied ✓" : "Copy failed"; clearTimeout(btn._t);
    btn._t = setTimeout(() => (btn.textContent = old), 1600);
  }
  document.addEventListener("click", (e) => { const b = e.target.closest("[data-copy]"); if (b) copy(b.dataset.copy, b); });

  /* ---------- payment dialog ---------- */
  const dlg = $("#donateDlg");
  if (dlg) {
    const tabs = $$("[data-dtab]", dlg), panes = $$("[data-dpane]", dlg);
    const showTab = (id) => {
      tabs.forEach((t) => t.setAttribute("aria-selected", t.dataset.dtab === id));
      panes.forEach((p) => (p.hidden = p.dataset.dpane !== id));
    };
    tabs.forEach((t) => t.addEventListener("click", () => showTab(t.dataset.dtab)));

    /* crypto coin picker */
    const coinBar = $("#coinBar"), coinImg = $("#coinImg"), coinName = $("#coinName"), coinNet = $("#coinNet"), coinAddr = $("#coinAddr"), coinCopy = $("#coinCopy");
    const pickCoin = (id) => {
      const c = COINS.find((x) => x.id === id) || COINS[0];
      $$("button", coinBar).forEach((b) => b.setAttribute("aria-selected", b.dataset.coin === c.id));
      coinImg.src = c.img; coinImg.alt = `${c.name} wallet QR code`;
      coinName.textContent = c.name; coinNet.textContent = c.net; coinAddr.textContent = c.addr;
      coinCopy.dataset.copy = c.addr;
    };
    COINS.forEach((c) => { const b = document.createElement("button"); b.type = "button"; b.className = "tab"; b.dataset.coin = c.id; b.textContent = c.id.toUpperCase(); b.addEventListener("click", () => pickCoin(c.id)); coinBar.append(b); });
    pickCoin("btc");

    /* UPI app button only makes sense on phones */
    const appBtn = $("#upiApp"); appBtn.href = UPI_LINK;
    if (!/Android|iPhone|iPad|iPod/i.test(navigator.userAgent)) appBtn.hidden = true;

    const open = (tab) => { showTab(tab || "qr"); if (!dlg.open) dlg.showModal(); document.documentElement.classList.add("modal-open"); };
    const close = () => dlg.open && dlg.close();
    dlg.addEventListener("close", () => document.documentElement.classList.remove("modal-open"));
    dlg.addEventListener("click", (e) => { if (e.target === dlg) close(); });          // click on backdrop
    $$("[data-donate]").forEach((b) => b.addEventListener("click", (e) => { e.preventDefault(); open(b.dataset.donate || "qr"); }));
    $("#dlgClose").addEventListener("click", close);
    showTab("qr");
  }

  /* ---------- donor board ---------- */
  const list = $("#boardList");
  if (!list) return;
  const fmt = (n, cur) => `${cur}${Number(n).toLocaleString("en-IN", { maximumFractionDigits: 2 })}`;
  const el = (tag, cls, txt) => { const n = document.createElement(tag); if (cls) n.className = cls; if (txt != null) n.textContent = txt; return n; };
  const initials = (s) => (s.trim().split(/\s+/).slice(0, 2).map((w) => w[0]).join("") || "♥").toUpperCase();

  function render(data) {
    const cur = typeof data.currency === "string" ? data.currency : "₹";
    const topN = Math.max(3, parseInt(data.showTop, 10) || 10);
    let rows = (Array.isArray(data.donors) ? data.donors : [])
      .map((d, i) => ({ name: String(d && d.name != null ? d.name : "").trim() || "Anonymous", amount: Number(d && d.amount), message: d && d.message ? String(d.message).slice(0, 140) : "", date: d && d.date ? String(d.date) : "", i }))
      .filter((d) => isFinite(d.amount) && d.amount > 0);
    const total = rows.reduce((s, d) => s + d.amount, 0);
    $("#boardTotal").textContent = fmt(total, cur);
    $("#boardCount").textContent = rows.length.toLocaleString();
    rows.sort(data.sort === "latest" ? (a, b) => b.i - a.i : (a, b) => b.amount - a.amount || a.i - b.i);

    list.replaceChildren();
    const more = $("#boardMore");
    if (!rows.length) { const e = el("li", "bd-empty", "No donations listed yet. Be the first to support KatMusic ♥"); list.append(e); more.hidden = true; return; }

    const draw = (all) => {
      list.replaceChildren();
      rows.slice(0, all ? rows.length : topN).forEach((d, n) => {
        const li = el("li", "bd-row" + (data.sort !== "latest" && n < 3 ? ` top t${n + 1}` : ""));
        li.append(el("span", "bd-rank", data.sort !== "latest" ? String(n + 1) : "♥"), el("span", "bd-av", initials(d.name)));
        const who = el("span", "bd-who"); who.append(el("b", null, d.name));
        const sub = [d.message, d.date].filter(Boolean).join(" · "); if (sub) who.append(el("small", null, sub));
        li.append(who, el("span", "bd-amt", fmt(d.amount, cur)));
        list.append(li);
      });
    };
    draw(false);
    more.hidden = rows.length <= topN;
    let all = false; more.onclick = () => { all = !all; draw(all); more.textContent = all ? "Show less" : `Show all ${rows.length}`; };
    more.textContent = `Show all ${rows.length}`;
  }

  fetch("donations.json", { cache: "no-cache" })
    .then((r) => { if (!r.ok) throw new Error(r.status); return r.json(); })
    .then(render)
    .catch(() => { list.replaceChildren(el("li", "bd-empty", "The donation board could not be loaded right now.")); });
})();
