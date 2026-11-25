package api

import (
	"html"
	"net/http"
	"net/url"
	"regexp"
	"strings"
)

var videoIDRe = regexp.MustCompile(`^[A-Za-z0-9_-]{11}$`)

// registerEmbedRoutes serves lightweight, iframe-friendly detail cards. They
// contain no scripts, so a chat app or website can embed a song or Jam safely.
func (h *Handler) registerEmbedRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /embed/jam/{code}", h.handleEmbedJam)
	mux.HandleFunc("GET /embed/song/{videoId}", h.handleEmbedSong)
}

func (h *Handler) handleEmbedJam(w http.ResponseWriter, r *http.Request) {
	code := normalizeCode(r.PathValue("code"))
	if code == "" {
		http.NotFound(w, r)
		return
	}
	base := publicBaseURL()
	body := embedShell(
		"Join Jam "+code,
		"Listen together on KatMusic",
		"Listen together in sync. Enter code "+code+" in KatMusic.",
		code,
		base+"/?code="+code,
	)
	writeEmbed(w, body)
}

func (h *Handler) handleEmbedSong(w http.ResponseWriter, r *http.Request) {
	videoID := r.PathValue("videoId")
	if !videoIDRe.MatchString(videoID) {
		http.NotFound(w, r)
		return
	}
	title := strings.TrimSpace(r.URL.Query().Get("title"))
	artist := strings.TrimSpace(r.URL.Query().Get("artist"))
	if title == "" {
		title = "Shared song"
	}
	if artist == "" {
		artist = "KatMusic"
	}
	link := "https://katmusic.pages.dev/share.html?v=" + url.QueryEscape(videoID) +
		"&title=" + url.QueryEscape(title) + "&artist=" + url.QueryEscape(artist)
	body := embedShell(title, artist, "Open this song in KatMusic.", "", link)
	writeEmbed(w, body)
}

func writeEmbed(w http.ResponseWriter, body string) {
	w.Header().Set("Content-Type", "text/html; charset=utf-8")
	w.Header().Set("Cache-Control", "public, max-age=300")
	w.Header().Set("Content-Security-Policy", "default-src 'none'; style-src 'unsafe-inline'; img-src 'self' https: data:")
	w.WriteHeader(http.StatusOK)
	_, _ = w.Write([]byte(body))
}

func embedShell(title, subtitle, hint, code, link string) string {
	e := html.EscapeString
	codeBlock := ""
	if code != "" {
		codeBlock = `<div class="code">` + e(code) + `</div>`
	}
	return `<!doctype html>
<html lang="en"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>` + e(title) + ` · KatMusic</title>
<style>
:root{color-scheme:dark;--accent:#66b5ff;--bg:#080d18;--panel:#111b2d;--line:#263a56;--text:#f2f7ff;--muted:#9cacc2}*{box-sizing:border-box}
body{margin:0;padding:16px;background:radial-gradient(ellipse at 50% 0%,#172d4b 0,var(--bg) 68%);color:var(--text);font:15px/1.5 system-ui,sans-serif;display:grid;place-items:center;min-height:100vh}
.card{width:min(440px,100%);background:var(--panel);border:1px solid var(--line);border-radius:20px;padding:24px;text-align:center;box-shadow:0 18px 55px #0007}
.brand{display:flex;align-items:center;justify-content:center;gap:10px;font-size:22px;font-weight:800;letter-spacing:-.03em}.brand img{width:32px;height:32px;object-fit:contain;border-radius:8px}.brand span{color:var(--accent)}
h1{font-size:20px;margin:14px 0 4px}.sub{color:var(--muted);margin:0 0 14px}
.code{font:700 30px/1.2 ui-monospace,monospace;letter-spacing:.12em;color:var(--accent);padding:16px;border:1px dashed var(--line);border-radius:14px;margin:14px 0}
.hint{color:var(--muted);font-size:13px}
a{display:inline-block;margin-top:14px;padding:11px 18px;border-radius:999px;background:#2583e8;color:#fff;text-decoration:none;font-weight:700}
a:hover{background:#4097f4}
</style></head><body><div class="card">
<div class="brand"><img src="/assets/logo.png" alt="">KatMusic</div>
<h1>` + e(title) + `</h1><p class="sub">` + e(subtitle) + `</p>` + codeBlock + `
<p class="hint">` + e(hint) + `</p><a href="` + e(link) + `" target="_blank" rel="noopener">Open in KatMusic</a>
</div></body></html>`
}
