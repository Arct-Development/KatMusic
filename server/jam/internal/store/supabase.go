// Package store persists Jam room snapshots through Supabase's PostgREST API.
// It deliberately depends only on net/http so the server keeps a small binary.
package store

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"

	"katmusic/jam/internal/wire"
)

// RoomSnapshot is the durable part of a room. Live connections, hosts and
// co-hosts are intentionally not stored: after a restart the first listener to
// rejoin becomes host, exactly as if the room had just been created.
type RoomSnapshot struct {
	Code      string             `json:"code"`
	Mode      string             `json:"mode"`
	Name      string             `json:"name"`
	Permanent bool               `json:"permanent"`
	Radio     bool               `json:"radio"`
	RadioKey  string             `json:"radio_key"`
	Playback  wire.PlaybackState `json:"playback"`
	Chat      []wire.ChatMessage `json:"chat"`
}

type row struct {
	Code      string             `json:"code"`
	Mode      string             `json:"mode"`
	Name      string             `json:"name"`
	Permanent bool               `json:"permanent"`
	Radio     bool               `json:"radio"`
	RadioKey  string             `json:"radio_key"`
	Playback  wire.PlaybackState `json:"playback"`
	Chat      []wire.ChatMessage `json:"chat"`
}

// Supabase persists snapshots to one table. A disabled store is a no-op, so
// local development and tests need no credentials.
type Supabase struct {
	baseURL string
	key     string
	client  *http.Client
	table   string
}

// New creates a store. Empty URL or key disables persistence.
func New(baseURL, key string) *Supabase {
	return &Supabase{
		baseURL: strings.TrimRight(strings.TrimSpace(baseURL), "/"),
		key:     strings.TrimSpace(key),
		client:  &http.Client{Timeout: 10 * time.Second},
		table:   "jam_rooms",
	}
}

// Enabled reports whether the server has enough configuration to persist.
func (s *Supabase) Enabled() bool {
	return s != nil && s.baseURL != "" && s.key != ""
}

// SaveRoom upserts a room. PostgREST's merge-duplicates keeps the code stable.
func (s *Supabase) SaveRoom(ctx context.Context, snap RoomSnapshot) error {
	if !s.Enabled() {
		return nil
	}
	if snap.Chat == nil {
		snap.Chat = []wire.ChatMessage{}
	}
	if snap.Playback.Queue == nil {
		snap.Playback.Queue = []wire.TrackInfo{}
	}
	now := time.Now().UTC()
	body, err := json.Marshal([]any{map[string]any{
		"code":       snap.Code,
		"mode":       snap.Mode,
		"name":       snap.Name,
		"permanent":  snap.Permanent,
		"radio":      snap.Radio,
		"radio_key":  snap.RadioKey,
		"playback":   snap.Playback,
		"chat":       snap.Chat,
		"updated_at": now.Format(time.RFC3339Nano),
		"expires_at": now.Add(6 * time.Hour).Format(time.RFC3339Nano),
	}})
	if err != nil {
		return err
	}
	endpoint := s.baseURL + "/rest/v1/" + s.table + "?on_conflict=code"
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, endpoint, bytes.NewReader(body))
	if err != nil {
		return err
	}
	s.headers(req)
	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Prefer", "resolution=merge-duplicates,return=minimal")
	return s.do(req, http.StatusCreated, http.StatusNoContent, http.StatusOK)
}

// ListRooms returns unexpired, non-permanent rooms.
func (s *Supabase) ListRooms(ctx context.Context) ([]RoomSnapshot, error) {
	if !s.Enabled() {
		return nil, nil
	}
	q := url.Values{}
	q.Set("select", "code,mode,name,permanent,radio,radio_key,playback,chat")
	q.Set("permanent", "eq.false")
	q.Set("expires_at", "gt."+time.Now().UTC().Format(time.RFC3339Nano))
	q.Set("order", "updated_at.desc")
	q.Set("limit", "500")
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, s.baseURL+"/rest/v1/"+s.table+"?"+q.Encode(), nil)
	if err != nil {
		return nil, err
	}
	s.headers(req)
	resp, err := s.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		msg, _ := io.ReadAll(io.LimitReader(resp.Body, 2048))
		return nil, fmt.Errorf("jam store list: %s: %s", resp.Status, strings.TrimSpace(string(msg)))
	}
	var rows []row
	if err := json.NewDecoder(resp.Body).Decode(&rows); err != nil {
		return nil, err
	}
	out := make([]RoomSnapshot, 0, len(rows))
	for _, r := range rows {
		if r.Code == "" {
			continue
		}
		out = append(out, RoomSnapshot{
			Code: r.Code, Mode: r.Mode, Name: r.Name,
			Permanent: r.Permanent, Radio: r.Radio, RadioKey: r.RadioKey,
			Playback: r.Playback, Chat: r.Chat,
		})
	}
	return out, nil
}

// DeleteRoom removes a room that has ended, so it is not restored later.
func (s *Supabase) DeleteRoom(ctx context.Context, code string) error {
	if !s.Enabled() || code == "" {
		return nil
	}
	q := url.Values{}
	q.Set("code", "eq."+strings.ToUpper(code))
	req, err := http.NewRequestWithContext(ctx, http.MethodDelete, s.baseURL+"/rest/v1/"+s.table+"?"+q.Encode(), nil)
	if err != nil {
		return err
	}
	s.headers(req)
	req.Header.Set("Prefer", "return=minimal")
	return s.do(req, http.StatusNoContent, http.StatusOK)
}

func (s *Supabase) headers(req *http.Request) {
	req.Header.Set("apikey", s.key)
	req.Header.Set("Authorization", "Bearer "+s.key)
	req.Header.Set("Accept", "application/json")
}

func (s *Supabase) do(req *http.Request, want ...int) error {
	resp, err := s.client.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	for _, code := range want {
		if resp.StatusCode == code {
			return nil
		}
	}
	msg, _ := io.ReadAll(io.LimitReader(resp.Body, 2048))
	if len(msg) == 0 {
		return fmt.Errorf("jam store: %s", resp.Status)
	}
	return fmt.Errorf("jam store: %s: %s", resp.Status, strings.TrimSpace(string(msg)))
}
