package rooms_test

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"katmusic/jam/internal/api"
	"katmusic/jam/internal/rooms"
	"katmusic/jam/internal/wire"
)

// wsHelper connects to the test server and manages read/write for E2E tests.
type wsHelper struct {
	conn *websocket.Conn
	t    *testing.T
}

func dialWS(t *testing.T, url string) *wsHelper {
	t.Helper()
	wsURL := "ws" + strings.TrimPrefix(url, "http") + "/ws"
	conn, resp, err := websocket.DefaultDialer.Dial(wsURL, nil)
	if err != nil {
		t.Fatalf("failed to dial websocket %s: %v", wsURL, err)
	}
	if resp != nil {
		_ = resp.Body.Close()
	}
	return &wsHelper{conn: conn, t: t}
}

func (h *wsHelper) send(msgType string, payload any) {
	h.t.Helper()
	raw, err := json.Marshal(payload)
	if err != nil {
		h.t.Fatalf("failed to marshal payload: %v", err)
	}
	env := wire.Envelope{
		Type:    msgType,
		Payload: raw,
	}
	data, err := json.Marshal(env)
	if err != nil {
		h.t.Fatalf("failed to marshal envelope: %v", err)
	}
	if err := h.conn.WriteMessage(websocket.TextMessage, data); err != nil {
		h.t.Fatalf("failed to write websocket message: %v", err)
	}
}

func (h *wsHelper) readEnvelope(timeout time.Duration) *wire.Envelope {
	h.t.Helper()
	_ = h.conn.SetReadDeadline(time.Now().Add(timeout))
	_, msg, err := h.conn.ReadMessage()
	if err != nil {
		h.t.Fatalf("failed to read websocket message: %v", err)
	}
	var env wire.Envelope
	if err := json.Unmarshal(msg, &env); err != nil {
		h.t.Fatalf("failed to unmarshal envelope: %v", err)
	}
	return &env
}

func (h *wsHelper) close() {
	_ = h.conn.Close()
}

func TestE2E_FullListenTogetherFlow(t *testing.T) {
	hub := rooms.NewHub()
	handler := api.NewHandler(hub)
	mux := http.NewServeMux()
	handler.RegisterRoutes(mux)

	ts := httptest.NewServer(mux)
	defer ts.Close()

	// 1. Alice connects and creates a room
	alice := dialWS(t, ts.URL)
	defer alice.close()

	alice.send(wire.MsgTypeCreateRoom, wire.CreateRoomPayload{
		Name:     "Alice",
		RoomMode: wire.RoomModeHostOnly,
	})

	createdEnv := alice.readEnvelope(2 * time.Second)
	if createdEnv.Type != wire.MsgTypeRoomCreated {
		t.Fatalf("expected room_created, got %s", createdEnv.Type)
	}

	var createdData struct {
		RoomCode string `json:"room_code"`
		UserID   string `json:"user_id"`
		Name     string `json:"name"`
		IsHost   bool   `json:"is_host"`
	}
	if err := json.Unmarshal(createdEnv.Payload, &createdData); err != nil {
		t.Fatalf("failed to unmarshal room_created payload: %v", err)
	}

	roomCode := createdData.RoomCode
	if len(roomCode) != 6 {
		t.Fatalf("expected 6-char room code, got %q", roomCode)
	}
	if !createdData.IsHost {
		t.Fatal("expected Alice to be host")
	}

	// The host also receives its initial snapshot right after room_created.
	hostSyncEnv := alice.readEnvelope(2 * time.Second)
	if hostSyncEnv.Type != wire.MsgTypeSyncState {
		t.Fatalf("expected host sync_state, got %s", hostSyncEnv.Type)
	}

	// 2. Bob connects
	bob := dialWS(t, ts.URL)
	defer bob.close()

	// 3. Bob attempts to join without a name -> must be rejected
	bob.send(wire.MsgTypeJoinRoom, wire.JoinRoomPayload{
		RoomCode: roomCode,
		Name:     "",
	})

	errEnv := bob.readEnvelope(2 * time.Second)
	if errEnv.Type != wire.MsgTypeError {
		t.Fatalf("expected error for empty name, got %s", errEnv.Type)
	}

	var errPayload wire.ErrorPayload
	_ = json.Unmarshal(errEnv.Payload, &errPayload)
	if errPayload.Code != "invalid_name" {
		t.Fatalf("expected error code invalid_name, got %q", errPayload.Code)
	}

	// 4. Bob joins with a valid name "Bob"
	bob.send(wire.MsgTypeJoinRoom, wire.JoinRoomPayload{
		RoomCode: roomCode,
		Name:     "Bob",
	})

	// Bob receives sync_state
	syncStateEnv := bob.readEnvelope(2 * time.Second)
	if syncStateEnv.Type != wire.MsgTypeSyncState {
		t.Fatalf("expected sync_state, got %s", syncStateEnv.Type)
	}

	var syncState wire.SyncStatePayload
	if err := json.Unmarshal(syncStateEnv.Payload, &syncState); err != nil {
		t.Fatalf("failed to unmarshal sync_state: %v", err)
	}
	if syncState.RoomCode != roomCode {
		t.Errorf("expected room code %q, got %q", roomCode, syncState.RoomCode)
	}
	if len(syncState.Participants) != 2 {
		t.Errorf("expected 2 participants in sync_state, got %d", len(syncState.Participants))
	}

	// Alice receives user_joined notification
	joinedEnv := alice.readEnvelope(2 * time.Second)
	if joinedEnv.Type != wire.MsgTypeUserJoined {
		t.Fatalf("expected user_joined for Alice, got %s", joinedEnv.Type)
	}

	// 5. Alice sends a chat message
	alice.send(wire.MsgTypeSendChat, wire.SendChatPayload{
		Text: "Welcome to KatMusic party Bob!",
	})

	// Both Alice and Bob should receive the chat message
	aliceChat := alice.readEnvelope(2 * time.Second)
	bobChat := bob.readEnvelope(2 * time.Second)

	if aliceChat.Type != wire.MsgTypeChatMessage || bobChat.Type != wire.MsgTypeChatMessage {
		t.Fatalf("expected chat_message broadcast, got alice=%s, bob=%s", aliceChat.Type, bobChat.Type)
	}

	var chatMsg wire.ChatMessage
	_ = json.Unmarshal(bobChat.Payload, &chatMsg)
	if chatMsg.UserName != "Alice" || chatMsg.Text != "Welcome to KatMusic party Bob!" {
		t.Fatalf("unexpected chat message content: %+v", chatMsg)
	}

	// 6. Alice (host) changes track and plays
	track := &wire.TrackInfo{
		ID:         "yt_dQw4w9WgXcQ",
		Title:      "Starboy",
		Artist:     "The Weeknd",
		Album:      "Starboy",
		DurationMs: 230000,
	}

	alice.send(wire.MsgTypePlaybackAction, wire.PlaybackActionPayload{
		Action:       wire.ActionChangeTrack,
		Track:        track,
		PositionMs:   0,
		ClientTimeMs: time.Now().UnixMilli(),
	})

	_ = alice.readEnvelope(2 * time.Second) // Alice playback confirmation
	bobTrackEnv := bob.readEnvelope(2 * time.Second)
	if bobTrackEnv.Type != wire.MsgTypeSyncPlayback {
		t.Fatalf("expected sync_playback for Bob, got %s", bobTrackEnv.Type)
	}

	var bobPlayback wire.PlaybackActionPayload
	_ = json.Unmarshal(bobTrackEnv.Payload, &bobPlayback)
	if bobPlayback.Track == nil || bobPlayback.Track.Title != "Starboy" {
		t.Fatalf("expected track title Starboy, got %+v", bobPlayback.Track)
	}

	// 7. Ping / Pong latency measurement
	clientTime := time.Now().UnixMilli()
	bob.send(wire.MsgTypePing, clientTime)
	pongEnv := bob.readEnvelope(2 * time.Second)
	if pongEnv.Type != wire.MsgTypePong {
		t.Fatalf("expected pong, got %s", pongEnv.Type)
	}

	// 8. Alice disconnects -> Bob becomes host
	alice.close()

	// Bob receives user_left first
	leftEnv := bob.readEnvelope(2 * time.Second)
	if leftEnv.Type != wire.MsgTypeUserLeft {
		t.Fatalf("expected user_left, got %s", leftEnv.Type)
	}

	// Bob receives host_changed
	hostChangedEnv := bob.readEnvelope(2 * time.Second)
	if hostChangedEnv.Type != wire.MsgTypeHostChanged {
		t.Fatalf("expected host_changed, got %s", hostChangedEnv.Type)
	}

	var hostData struct {
		NewHostID   string `json:"new_host_id"`
		NewHostName string `json:"new_host_name"`
	}
	_ = json.Unmarshal(hostChangedEnv.Payload, &hostData)
	if hostData.NewHostName != "Bob" {
		t.Fatalf("expected Bob to be promoted to host, got %s", hostData.NewHostName)
	}
}
