package rooms

import (
	"errors"
	"sort"
	"time"

	"katmusic/jam/internal/wire"
)

// Room administration used by the web admin panel and host hand-off.

var ErrUserNotInRoom = errors.New("user not in room")

const systemUserName = "KatMusic"

// postSystemChat adds a KatMusic system line to the room chat.
func (r *Room) postSystemChat(text string) {
	msg := wire.ChatMessage{
		ID:        GenerateID("msg_"),
		UserID:    "system",
		UserName:  systemUserName,
		Text:      text,
		Timestamp: time.Now().UnixMilli(),
	}
	r.chat.Add(msg)
	r.Broadcast(wire.MsgTypeChatMessage, msg)
}

// announceHost tells the room who the host is now (admin == nil: nobody).
func (r *Room) announceHost(admin *Client, coHostID string) {
	hostID, name := "", ""
	if admin != nil {
		hostID, name = admin.ID, admin.Name()
	}
	r.Broadcast(wire.MsgTypeHostChanged, map[string]any{
		"new_host_id":   hostID,
		"new_host_name": name,
		"user_id":       hostID,
		"name":          name,
		"is_host":       admin != nil,
		"host_id":       hostID,
		"cohost_id":     coHostID,
	})
}

// TransferHost lets the main host hand the room to another participant.
func (r *Room) TransferHost(c *Client, targetID string) {
	r.mu.Lock()
	if r.host == nil || r.host.ID != c.ID {
		r.mu.Unlock()
		c.SendError("unauthorized", "Only the main host can transfer the room")
		return
	}
	target, ok := r.participants[targetID]
	if !ok || targetID == c.ID {
		r.mu.Unlock()
		c.SendError("invalid_target", "Pick someone else in the room")
		return
	}
	if r.coHost != nil && r.coHost.ID == targetID {
		r.coHost = nil
	}
	r.host = target
	hostID, coHostID := r.hostIDsLocked()
	r.mu.Unlock()
	r.announceHost(target, coHostID)
	r.postSystemChat(c.Name() + " handed the room to " + target.Name() + ".")
	_ = hostID
}

// AdminGiveHost makes a participant the host from the admin panel.
func (r *Room) AdminGiveHost(userID string) error {
	r.mu.Lock()
	target, ok := r.participants[userID]
	if !ok {
		r.mu.Unlock()
		return ErrUserNotInRoom
	}
	if r.coHost != nil && r.coHost.ID == userID {
		r.coHost = nil
	}
	r.host = target
	_, coHostID := r.hostIDsLocked()
	r.mu.Unlock()
	r.announceHost(target, coHostID)
	r.postSystemChat(target.Name() + " is now the host.")
	return nil
}

// AdminTakeHost removes the host and co-host.
func (r *Room) AdminTakeHost() error {
	r.mu.Lock()
	if r.host == nil && r.coHost == nil {
		r.mu.Unlock()
		return nil
	}
	r.host = nil
	r.coHost = nil
	r.mu.Unlock()
	r.announceHost(nil, "")
	r.postSystemChat("The host was removed.")
	return nil
}

// ParticipantView is a listener as shown in the admin panel.
type ParticipantView struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	IsHost   bool   `json:"isHost"`
	IsCoHost bool   `json:"isCoHost"`
}

// RoomView is a room as shown in the admin panel.
type RoomView struct {
	Code         string            `json:"code"`
	Mode         string            `json:"mode"`
	CreatedAt    int64             `json:"createdAt"`
	HostID       string            `json:"hostId"`
	HostName     string            `json:"hostName"`
	CoHostID     string            `json:"coHostId"`
	Participants []ParticipantView `json:"participants"`
	NowPlaying   *wire.TrackInfo   `json:"nowPlaying"`
	IsPlaying    bool              `json:"isPlaying"`
	PositionMs   int64             `json:"positionMs"`
	ChatCount    int               `json:"chatCount"`
	Seen         []string          `json:"seen"`
}

// View returns a consistent snapshot of the room for the admin panel.
func (r *Room) View() RoomView {
	r.mu.RLock()
	defer r.mu.RUnlock()
	v := RoomView{
		Code:       r.Code,
		Mode:       r.Mode,
		CreatedAt:  r.CreatedAt.UnixMilli(),
		IsPlaying:  r.state.IsPlaying,
		PositionMs: CalculateCurrentPosition(&r.state, time.Now().UnixMilli()),
		ChatCount:  len(r.chat.All()),
		Seen:       append([]string{}, r.seen...),
	}
	if r.state.CurrentTrack != nil {
		t := *r.state.CurrentTrack
		v.NowPlaying = &t
	}
	if r.host != nil {
		v.HostID, v.HostName = r.host.ID, r.host.Name()
	}
	if r.coHost != nil {
		v.CoHostID = r.coHost.ID
	}
	for _, id := range r.joinedOrder {
		p, ok := r.participants[id]
		if !ok {
			continue
		}
		v.Participants = append(v.Participants, ParticipantView{
			ID:       p.ID,
			Name:     p.Name(),
			IsHost:   v.HostID == p.ID,
			IsCoHost: v.CoHostID == p.ID,
		})
	}
	if v.Participants == nil {
		v.Participants = []ParticipantView{}
	}
	return v
}

// RoomViews lists every active room, busiest first.
func (h *Hub) RoomViews() []RoomView {
	h.mu.RLock()
	rooms := make([]*Room, 0, len(h.rooms))
	for _, r := range h.rooms {
		rooms = append(rooms, r)
	}
	h.mu.RUnlock()

	out := make([]RoomView, 0, len(rooms))
	for _, r := range rooms {
		out = append(out, r.View())
	}
	sort.SliceStable(out, func(i, j int) bool {
		if len(out[i].Participants) != len(out[j].Participants) {
			return len(out[i].Participants) > len(out[j].Participants)
		}
		return out[i].CreatedAt < out[j].CreatedAt
	})
	return out
}
