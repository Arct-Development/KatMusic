package rooms

import (
	"katmusic/jam/internal/wire"
)

// GrantCoHost lets the main host promote one other participant to co-host.
// A room never has more than two hosts.
func (r *Room) GrantCoHost(c *Client, targetID string) {
	r.mu.Lock()
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

// HandleQueueUpdate stores the main host's upcoming queue and shows it,
// read-only, to everyone else. Only the main host may publish it.
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
