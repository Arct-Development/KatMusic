package rooms

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"katmusic/jam/internal/store"
	"katmusic/jam/internal/wire"
)

func TestPersistenceRestoresPausedRoomAndChat(t *testing.T) {
	var mu sync.Mutex
	var saved []byte
	deletedCh := make(chan string, 1)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.Method {
		case http.MethodGet:
			_ = json.NewEncoder(w).Encode([]map[string]any{{
				"code":      "ABC123",
				"mode":      wire.RoomModeCollaborative,
				"playback":  map[string]any{"position_ms": 42000, "is_playing": true, "queue": []any{}},
				"chat":      []map[string]any{{"id": "m1", "user_id": "u1", "user_name": "Ada", "text": "hello", "timestamp": time.Now().UnixMilli()}},
				"permanent": false,
				"radio":     false,
			}})
		case http.MethodPost:
			mu.Lock()
			saved, _ = io.ReadAll(r.Body)
			mu.Unlock()
			w.WriteHeader(http.StatusCreated)
		case http.MethodDelete:
			select {
			case deletedCh <- r.URL.RawQuery:
			default:
			}
			w.WriteHeader(http.StatusNoContent)
		}
	}))
	defer srv.Close()

	hub := NewHub()
	hub.EnablePersistence(store.New(srv.URL, "service-role-test"))
	if err := hub.Restore(context.Background()); err != nil {
		t.Fatalf("restore: %v", err)
	}
	room, ok := hub.GetRoom("ABC123")
	if !ok {
		t.Fatal("expected restored room")
	}
	snapshot := room.Snapshot()
	if snapshot.Playback.IsPlaying {
		t.Fatal("restored playback must be paused")
	}
	if snapshot.Playback.PositionMs != 42000 {
		t.Fatalf("expected saved position, got %d", snapshot.Playback.PositionMs)
	}
	if len(snapshot.Chat) != 1 || snapshot.Chat[0].Text != "hello" {
		t.Fatalf("expected restored chat, got %+v", snapshot.Chat)
	}
	if room.HostID() != "" {
		t.Fatal("restored room should wait for the first listener to become host")
	}

	hub.PersistOnce(context.Background())
	mu.Lock()
	body := string(saved)
	mu.Unlock()
	if !strings.Contains(body, `"code":"ABC123"`) {
		t.Fatalf("expected saved snapshot, got %s", body)
	}

	hub.RemoveRoom("ABC123")
	select {
	case query := <-deletedCh:
		if !strings.Contains(query, "code=eq.ABC123") {
			t.Fatalf("expected delete request, got %q", query)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("timed out waiting for delete request")
	}
}
