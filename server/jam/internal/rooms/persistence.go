package rooms

import (
	"context"
	"log/slog"
	"strings"
	"time"

	"katmusic/jam/internal/store"
	"katmusic/jam/internal/wire"
)

// EnablePersistence attaches a Supabase store. Passing nil keeps the hub
// memory-only, which is what tests and local development use.
func (h *Hub) EnablePersistence(db *store.Supabase) {
	h.persist = db
}

// Restore reloads unexpired rooms. A restored room has no connected host:
// the first listener to rejoin becomes host, as with a freshly created room.
// Playback is paused at the last saved position so a wake-up does not skip
// through tracks while nobody was listening.
func (h *Hub) Restore(ctx context.Context) error {
	if h.persist == nil || !h.persist.Enabled() {
		return nil
	}
	snaps, err := h.persist.ListRooms(ctx)
	if err != nil {
		return err
	}
	now := time.Now().UnixMilli()
	restored := 0
	for _, snap := range snaps {
		code := strings.ToUpper(strings.TrimSpace(snap.Code))
		if code == "" || snap.Permanent || snap.Radio {
			continue
		}
		h.mu.RLock()
		_, exists := h.rooms[code]
		h.mu.RUnlock()
		if exists {
			continue
		}
		room := NewRoom(code, nil, snap.Mode)
		room.Name = snap.Name
		room.CreatedAt = time.Now()
		playback := snap.Playback
		if playback.Queue == nil {
			playback.Queue = []wire.TrackInfo{}
		}
		playback.IsPlaying = false
		playback.ServerTimeMs = now
		room.state = playback
		for _, msg := range snap.Chat {
			room.chat.Add(msg)
		}
		h.mu.Lock()
		if _, exists := h.rooms[code]; !exists {
			h.rooms[code] = room
			restored++
		}
		h.mu.Unlock()
	}
	if restored > 0 {
		slog.Info("restored jam rooms", "count", restored)
	}
	return nil
}

// Snapshot copies the durable state under the room lock.
func (r *Room) Snapshot() store.RoomSnapshot {
	r.mu.RLock()
	defer r.mu.RUnlock()
	state := r.state
	if state.Queue == nil {
		state.Queue = []wire.TrackInfo{}
	} else {
		state.Queue = append([]wire.TrackInfo(nil), state.Queue...)
	}
	if state.CurrentTrack != nil {
		track := *state.CurrentTrack
		state.CurrentTrack = &track
	}
	// Recent history is enough to restore a conversation; the full 24-hour
	// buffer stays in memory and in the admin archive.
	chat := r.chat.History()
	if chat == nil {
		chat = []wire.ChatMessage{}
	}
	return store.RoomSnapshot{
		Code:      r.Code,
		Mode:      r.Mode,
		Name:      r.Name,
		Permanent: r.Permanent,
		Radio:     r.Radio,
		RadioKey:  r.RadioKey,
		Playback:  state,
		Chat:      chat,
	}
}

// RunPersistenceLoop snapshots active rooms every interval. The default write
// rate is intentionally low: a room costs four tiny upserts a minute.
func (h *Hub) RunPersistenceLoop(ctx context.Context, interval time.Duration) {
	if h.persist == nil || !h.persist.Enabled() {
		return
	}
	if interval <= 0 {
		interval = 15 * time.Second
	}
	ticker := time.NewTicker(interval)
	defer ticker.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case <-ticker.C:
			h.PersistOnce(ctx)
		}
	}
}

// PersistOnce saves every non-permanent room. Called by the loop and during
// graceful shutdown.
func (h *Hub) PersistOnce(ctx context.Context) {
	if h.persist == nil || !h.persist.Enabled() {
		return
	}
	h.mu.RLock()
	rooms := make([]*Room, 0, len(h.rooms))
	for _, room := range h.rooms {
		if room != nil && !room.Permanent && !room.Radio {
			rooms = append(rooms, room)
		}
	}
	h.mu.RUnlock()

	for _, room := range rooms {
		snap := room.Snapshot()
		saveCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
		err := h.persist.SaveRoom(saveCtx, snap)
		cancel()
		if err != nil {
			slog.Warn("jam room snapshot failed", "room_code", snap.Code, "err", err)
		}
	}
}

// persistAsync saves a newly created room without delaying its creator.
func (h *Hub) persistAsync(room *Room) {
	if h.persist == nil || !h.persist.Enabled() || room == nil {
		return
	}
	snap := room.Snapshot()
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := h.persist.SaveRoom(ctx, snap); err != nil {
			slog.Warn("jam room snapshot failed", "room_code", snap.Code, "err", err)
		}
	}()
}

// deletePersisted removes an ended room so it cannot be restored later.
func (h *Hub) deletePersisted(code string) {
	if h.persist == nil || !h.persist.Enabled() || code == "" {
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := h.persist.DeleteRoom(ctx, code); err != nil {
			slog.Warn("jam room delete failed", "room_code", code, "err", err)
		}
	}()
}
