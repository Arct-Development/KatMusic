package api

import (
	"bytes"
	"golang.org/x/image/font"
	"image"
	"image/draw"
	"image/png"
	"net/http"
	"regexp"
	"strings"

	xdraw "golang.org/x/image/draw"
)

var songCodeRe = regexp.MustCompile(`^[A-Za-z0-9-]{16,40}$`)

// handleCodeImage serves a shareable KatMusic song-code card:
// /code/KM-XXXXX-XXXXX-XXXXX-XXXXX.png
func (h *Handler) handleCodeImage(w http.ResponseWriter, r *http.Request) {
	code := strings.TrimSuffix(r.PathValue("file"), ".png")
	if !songCodeRe.MatchString(code) {
		http.NotFound(w, r)
		return
	}
	w.Header().Set("Content-Type", "image/png")
	w.Header().Set("Cache-Control", "public, max-age=86400")
	w.Header().Set("X-Content-Type-Options", "nosniff")
	_, _ = w.Write(renderCodeImage(strings.ToUpper(code)))
}

func renderCodeImage(code string) []byte {
	img := image.NewRGBA(image.Rect(0, 0, ogWidth, ogHeight))
	draw.Draw(img, img.Bounds(), image.NewUniform(colBlack), image.Point{}, draw.Src)

	if logoImg != nil {
		const logoSize = 96
		dst := image.Rect(80, 64, 80+logoSize, 64+logoSize)
		xdraw.CatmullRom.Scale(img, dst, logoImg, logoImg.Bounds(), xdraw.Over, nil)
	}
	if f := newFace(boldFont, 52); f != nil {
		drawText(img, f, "KatMusic", 196, 130, 0, colWhite)
		w := textWidth(f, "KatMusic", 0)
		if f2 := newFace(regularFont, 52); f2 != nil {
			drawText(img, f2, "Code", 196+w+16, 130, 0, colBlue)
		}
	}
	if f := newFace(boldFont, 30); f != nil {
		drawText(img, f, "SONG SHARE CODE", 80, 268, 6, colGray)
	}

	var codeFace font.Face
	for px := 82.0; px >= 40; px -= 4 {
		f := newFace(boldFont, px)
		if f != nil && textWidth(f, code, 4) <= ogWidth-160 {
			codeFace = f
			break
		}
	}
	if codeFace != nil {
		drawText(img, codeFace, code, 80, 430, 4, colBlue)
	}
	if f := newFace(regularFont, 36); f != nil {
		drawText(img, f, "Open KatMusic and enter this code to play the song", 80, 524, 0, colWhite)
	}
	draw.Draw(img, image.Rect(80, 560, ogWidth-80, 562), image.NewUniform(colLine), image.Point{}, draw.Src)
	if f := newFace(regularFont, 26); f != nil {
		drawText(img, f, "One code. Any device. Your music.", 80, 604, 0, colGray)
	}

	var buf bytes.Buffer
	_ = png.Encode(&buf, img)
	return buf.Bytes()
}
