package api

import (
	"embed"
	"encoding/json"
	"log/slog"
	"net/http"
	"strings"
	"time"

	"github.com/gorilla/websocket"
	"katmusic/jam/internal/rooms"
)

//go:embed web/*
var embeddedWebFS embed.FS

var upgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		// Allow all origins (native mobile apps and desktop clients)
		return true
	},
}

type Handler struct {
	hub       *rooms.Hub
	startTime time.Time
	admin     *adminAuth
}

func NewHandler(hub *rooms.Hub) *Handler {
	return &Handler{
		hub:       hub,
		startTime: time.Now(),
		admin:     newAdminAuth(),
	}
}

func (h *Handler) RegisterRoutes(mux *http.ServeMux) {
	mux.HandleFunc("GET /health", h.handleHealth)
	mux.HandleFunc("GET /healthz", h.handleHealth)
	mux.HandleFunc("GET /stats", h.handleStats)
	mux.HandleFunc("GET /ws", h.handleWebSocket)
	mux.HandleFunc("GET /og/{file}", h.handleOG)
	mux.HandleFunc("GET /code/{file}", h.handleCodeImage)
	mux.HandleFunc("GET /assets/logo.png", h.handleLogo)
	h.registerAdminRoutes(mux)
	h.registerEmbedRoutes(mux)

	// Serve self-contained invite landing page
	indexHTML, err := embeddedWebFS.ReadFile("web/index.html")
	if err == nil {
		page := string(indexHTML)
		mux.HandleFunc("GET /", func(w http.ResponseWriter, r *http.Request) {
			code := normalizeCode(r.URL.Query().Get("code"))
			if code == "" {
				code = normalizeCode(r.URL.Query().Get("room"))
			}
			body := strings.Replace(page, metaMarker, renderMeta(code), 1)
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
			w.Header().Set("Cache-Control", "no-cache")
			w.WriteHeader(http.StatusOK)
			_, _ = w.Write([]byte(body))
		})
	}
}

func (h *Handler) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"service": "katmusic-jam",
		"status":  "healthy",
		"version": "1.0.0",
		"uptime":  time.Since(h.startTime).String(),
	})
}

func (h *Handler) handleStats(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	_ = json.NewEncoder(w).Encode(map[string]any{
		"active_rooms":       h.hub.RoomCount(),
		"total_participants": h.hub.TotalParticipants(),
	})
}

func (h *Handler) handleWebSocket(w http.ResponseWriter, r *http.Request) {
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		slog.Warn("websocket upgrade failed", "remote_addr", r.RemoteAddr, "err", err)
		return
	}

	client := rooms.NewClient(conn, h.hub)
	go client.WritePump()
	client.ReadPump()
}
