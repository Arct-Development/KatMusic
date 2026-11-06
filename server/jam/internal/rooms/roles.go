package rooms

import (
	"strings"
	"time"

	"katmusic/jam/internal/wire"
)

// CooldownWindow is how long a user must wait between "disruptive" playback
// commands in an always-active room.
const CooldownWindow = time.Minute

// isCooldownAction reports whether the action counts against the cooldown.
// Play is deliberately exempt so a paused room can always be resumed.
func isCooldownAction(action string) bool {
	switch action {
	case wire.ActionPause,
		wire.ActionSeek,
		wire.ActionChangeTrack,
		wire.ActionSkipNext,
		wire.ActionSkipPrev,
		wire.ActionQueueClear:
		return true
	}
	return false
}

func (r *Room) cooldownRemainingLocked(userID string) time.Duration {
	last, ok := r.cooldowns[userID]
	if !ok {
		return 0
	}
	if remaining := CooldownWindow - r.now().Sub(last); remaining > 0 {
		return remaining
	}
	return 0
}

// rejectRateLimitedLocked tells the sender when they may retry and re-sends
// the authoritative playback state so their player snaps back in sync.
func (r *Room) rejectRateLimitedLocked(c *Client, wait time.Duration) {
	nowMs := r.now().UnixMilli()
	_ = c.Send(wire.MsgTypeError, wire.ErrorPayload{
		Code:         "rate_limited",
		Message:      "Only one pause or change per minute. Try again shortly.",
		RetryAfterMs: wait.Milliseconds(),
		RetryAtMs:    nowMs + wait.Milliseconds(),
	})
	if r.state.CurrentTrack == nil {
		return
	}
	_ = c.Send(wire.MsgTypeSyncPlayback, wire.PlaybackActionPayload{
		Action:       wire.ActionChangeTrack,
		Track:        r.state.CurrentTrack,
		PositionMs:   CalculateCurrentPosition(&r.state, nowMs),
		ServerTimeMs: nowMs,
		IsPlaying:    r.state.IsPlaying,
		IsAutoDJ:     r.state.IsAutoDJ,
		Queue:        append([]wire.TrackInfo(nil), r.state.Queue...),
		Revision:     r.state.Revision,
	})
}

// GrantCoHost lets the main host promote one other participant to co-host.
// A room never has more than two hosts.
func (r *Room) GrantCoHost(c *Client, targetID string) {
	r.mu.Lock()
	if r.Radio {
		r.mu.Unlock()
		c.SendError("not_available", "Live stations have no co-hosts. Use Make admin instead.")
		return
	}
	if r.host == nil || r.host.ID != c.ID {
		r.mu.Unlock()
		c.SendError("unauthorized", "Only the main host can give host")
		return
	}
	target, ok := r.participants[targetID]
	if !ok || targetID == c.ID {
		r.mu.Unlock()
		c.SendError("invalid_target", "Pick someone else in the room")
		return
	}
	if r.coHost != nil {
		r.mu.Unlock()
		c.SendError("cohost_exists", "A room can have at most two hosts. Remove the current co-host first")
		return
	}
	r.coHost = target
	hostID, coHostID := r.hostIDsLocked()
	r.mu.Unlock()

	r.Broadcast(wire.MsgTypeCoHostChanged, map[string]any{
		"host_id":     hostID,
		"cohost_id":   coHostID,
		"cohost_name": target.Name(),
	})
}

// RevokeCoHost lets the main host strip the co-host role.
func (r *Room) RevokeCoHost(c *Client, targetID string) {
	r.mu.Lock()
	if r.host == nil || r.host.ID != c.ID {
		r.mu.Unlock()
		c.SendError("unauthorized", "Only the main host can remove a host")
		return
	}
	if r.coHost == nil || r.coHost.ID != targetID {
		r.mu.Unlock()
		c.SendError("invalid_target", "That person is not a co-host")
		return
	}
	r.coHost = nil
	hostID, coHostID := r.hostIDsLocked()
	r.mu.Unlock()

	r.Broadcast(wire.MsgTypeCoHostChanged, map[string]any{
		"host_id":   hostID,
		"cohost_id": coHostID,
	})
}

// IsDefaultName reports whether a display name is one of the placeholder
// names the apps fall back to. Public rooms refuse these.
func IsDefaultName(name string) bool {
	switch strings.ToLower(strings.TrimSpace(name)) {
	case "listener", "host", "guest", "user", "anonymous":
		return true
	}
	return false
}

// HandleQueueUpdate stores the main host's upcoming queue and shows it,
// read-only, to everyone else. Only the main host (admin) may publish it.
func (r *Room) HandleQueueUpdate(c *Client, tracks []wire.TrackInfo) {
	r.mu.Lock()
	if r.host == nil || r.host.ID != c.ID {
		r.mu.Unlock()
		return // silently ignore: co-hosts and guests don't own the shared queue
	}
	queue := make([]wire.TrackInfo, 0, len(tracks))
	for i := range tracks {
		if len(queue) >= wire.MaxSharedQueue {
			break
		}
		t := tracks[i]
		if wire.SanitizeTrack(&t) {
			queue = append(queue, t)
		}
	}
	r.state.Queue = queue
	r.state.Revision++
	revision := r.state.Revision
	hostID := r.host.ID
	r.mu.Unlock()

	r.Broadcast(wire.MsgTypeQueueUpdated, map[string]any{
		"queue":    queue,
		"host_id":  hostID,
		"revision": revision,
	}, c.ID)
}
