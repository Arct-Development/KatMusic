package api

import (
	"bytes"
	"image/png"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"katmusic/jam/internal/rooms"
)

func newMux() *http.ServeMux {
	mux := http.NewServeMux()
	NewHandler(rooms.NewHub()).RegisterRoutes(mux)
	return mux
}

func TestBanner_IsRealPNGAndCachedPerCode(t *testing.T) {
	for _, code := range []string{"WAVE01", "", "ABCDEFGHIJKL"} {
		data := renderBanner(code)
		img, err := png.Decode(bytes.NewReader(data))
		if err != nil {
			t.Fatalf("%q: not a png: %v", code, err)
		}
		if b := img.Bounds(); b.Dx() != 1200 || b.Dy() != 630 {
			t.Fatalf("%q: bad size %v", code, b)
		}
		if dir := os.Getenv("OG_OUT"); dir != "" {
			name := code
			if name == "" {
				name = "default"
			}
			_ = os.WriteFile(filepath.Join(dir, name+".png"), data, 0o644)
		}
	}
}

func TestOGRoute(t *testing.T) {
	mux := newMux()
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest("GET", "/og/WAVE01.png", nil))
	if rec.Code != 200 || rec.Header().Get("Content-Type") != "image/png" {
		t.Fatalf("bad response %d %s", rec.Code, rec.Header().Get("Content-Type"))
	}
	for _, bad := range []string{"/og/<script>.png", "/og/a.png", "/og/WAVE01"} {
		rec = httptest.NewRecorder()
		mux.ServeHTTP(rec, httptest.NewRequest("GET", bad, nil))
		if rec.Code != 404 && !(bad == "/og/WAVE01" && rec.Code == 200) {
			t.Fatalf("%s should be rejected, got %d", bad, rec.Code)
		}
	}
	rec = httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest("GET", "/og/default.png", nil))
	if rec.Code != 200 {
		t.Fatal("default banner should render")
	}
}

func TestIndexInjectsPreviewMeta(t *testing.T) {
	mux := newMux()
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest("GET", "/?code=wave01", nil))
	body := rec.Body.String()
	for _, want := range []string{
		`og:image" content="https://katmusic.onrender.com/og/WAVE01.png"`,
		`og:title" content="Join room WAVE01 on KatMusic Jam"`,
		`twitter:card" content="summary_large_image"`,
		`og:url" content="https://katmusic.onrender.com/?code=WAVE01"`,
	} {
		if !strings.Contains(body, want) {
			t.Fatalf("missing %s", want)
		}
	}
	if strings.Contains(body, metaMarker) {
		t.Fatal("marker should be replaced")
	}

	// Hostile input never reaches the markup.
	rec = httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest("GET", `/?code="><script>alert(1)</script>`, nil))
	if strings.Contains(rec.Body.String(), "<script>alert") {
		t.Fatal("code must be validated")
	}
	if !strings.Contains(rec.Body.String(), "/og/default.png") {
		t.Fatal("invalid code should fall back to the default banner")
	}

	rec = httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest("GET", "/?code=WAVE01", nil))
	if !strings.Contains(rec.Body.String(), "Join room WAVE01 on KatMusic Jam") {
		t.Fatal("room title expected")
	}
}


func TestLogoRoute(t *testing.T) {
	mux := newMux()
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest("GET", "/assets/logo.png", nil))
	if rec.Code != http.StatusOK || rec.Header().Get("Content-Type") != "image/png" {
		t.Fatalf("bad logo response %d %s", rec.Code, rec.Header().Get("Content-Type"))
	}
	if !bytes.HasPrefix(rec.Body.Bytes(), []byte{0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}) {
		t.Fatal("logo response is not a PNG")
	}
}

func TestEmbedSongUsesPublicBaseURL(t *testing.T) {
	t.Setenv("PUBLIC_BASE_URL", "https://katmusic.onrender.com/")
	mux := newMux()
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest("GET", "/embed/song/dQw4w9WgXcQ?title=Test", nil))
	body := rec.Body.String()
	if rec.Code != http.StatusOK {
		t.Fatalf("embed returned %d", rec.Code)
	}
	if !strings.Contains(body, "https://katmusic.onrender.com/share.html?v=dQw4w9WgXcQ") {
		t.Fatal("song embed should link to the canonical KatMusic URL")
	}
	if strings.Contains(body, "katmusic.pages.dev") || strings.Contains(body, "LastWave") {
		t.Fatal("embed contains legacy branding or host")
	}
}
