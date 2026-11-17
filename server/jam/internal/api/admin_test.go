package api

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"katmusic/jam/internal/rooms"
	"katmusic/jam/internal/wire"
)

type adminEnv struct {
	t      *testing.T
	hub    *rooms.Hub
	mux    *http.ServeMux
	cookie *http.Cookie
}

func newAdminEnv(t *testing.T) *adminEnv {
	t.Setenv("JAM_ADMIN_PASSWORD", "pw-123")
	hub := rooms.NewHub()
	mux := http.NewServeMux()
	NewHandler(hub).RegisterRoutes(mux)
	return &adminEnv{t: t, hub: hub, mux: mux}
}

func (e *adminEnv) do(method, path, body string, headers map[string]string) *httptest.ResponseRecorder {
	req := httptest.NewRequest(method, path, strings.NewReader(body))
	req.Host = "jam.example"
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	if e.cookie != nil {
		req.AddCookie(e.cookie)
	}
	rec := httptest.NewRecorder()
	e.mux.ServeHTTP(rec, req)
	return rec
}

var xhr = map[string]string{"X-Requested-With": "lwjam", "Content-Type": "application/json"}

func (e *adminEnv) login() {
	rec := e.do("POST", "/admin/login", `{"password":"pw-123"}`, xhr)
	if rec.Code != 200 {
		e.t.Fatalf("login failed: %d %s", rec.Code, rec.Body.String())
	}
	for _, c := range rec.Result().Cookies() {
		if c.Name == adminCookie {
			e.cookie = c
		}
	}
	if e.cookie == nil {
		e.t.Fatal("no session cookie")
	}
}

func TestAdmin_LoginAndAccessControl(t *testing.T) {
	e := newAdminEnv(t)
	if rec := e.do("GET", "/admin", "", nil); rec.Code != http.StatusFound || rec.Header().Get("Location") != "/admin/login" {
		t.Fatalf("dashboard without login should redirect, got %d", rec.Code)
	}
	if rec := e.do("GET", "/admin/api/rooms", "", nil); rec.Code != http.StatusUnauthorized {
		t.Fatalf("api without login should be 401, got %d", rec.Code)
	}
	if rec := e.do("GET", "/admin/admin.js", "", nil); rec.Code == 200 {
		t.Fatal("dashboard script must need a login")
	}
	if rec := e.do("GET", "/admin/login", "", nil); rec.Code != 200 || !strings.Contains(rec.Header().Get("X-Robots-Tag"), "noindex") {
		t.Fatal("login page should load and be noindex")
	}
	if rec := e.do("POST", "/admin/login", `{"password":"pw-123"}`, map[string]string{"Content-Type": "application/json"}); rec.Code != http.StatusForbidden {
		t.Fatal("login without the X-Requested-With header must be refused")
	}
	if rec := e.do("POST", "/admin/login", `{"password":"nope"}`, xhr); rec.Code != http.StatusUnauthorized {
		t.Fatal("wrong password must be refused")
	}

	e.login()
	c := e.cookie
	if !c.HttpOnly || c.SameSite != http.SameSiteStrictMode || c.Path != "/admin" {
		t.Fatalf("cookie flags wrong: %+v", c)
	}
	if rec := e.do("GET", "/admin", "", nil); rec.Code != 200 || !strings.Contains(rec.Header().Get("Content-Security-Policy"), "script-src 'self'") {
		t.Fatal("dashboard should load with a strict CSP")
	}
	if rec := e.do("POST", "/admin/api/rooms/ABCDEF/unhost", "{}", map[string]string{"Content-Type": "application/json"}); rec.Code != http.StatusForbidden {
		t.Fatal("POST without X-Requested-With must be refused")
	}
	if rec := e.do("POST", "/admin/api/rooms/ABCDEF/unhost", "{}", map[string]string{"X-Requested-With": "lwjam", "Origin": "https://evil.example"}); rec.Code != http.StatusForbidden {
		t.Fatal("POST from another origin must be refused")
	}

	e.do("POST", "/admin/logout", "{}", xhr)
	if rec := e.do("GET", "/admin/api/rooms", "", nil); rec.Code != http.StatusUnauthorized {
		t.Fatal("session should end at logout")
	}
}

func TestAdmin_LoginIsRateLimited(t *testing.T) {
	e := newAdminEnv(t)
	for i := 0; i < adminLoginMax; i++ {
		e.do("POST", "/admin/login", `{"password":"nope"}`, xhr)
	}
	if rec := e.do("POST", "/admin/login", `{"password":"pw-123"}`, xhr); rec.Code != http.StatusTooManyRequests {
		t.Fatalf("6th attempt should be blocked, got %d", rec.Code)
	}
}

func TestAdmin_DisabledWithoutPassword(t *testing.T) {
	t.Setenv("JAM_ADMIN_PASSWORD", "")
	hub := rooms.NewHub()
	mux := http.NewServeMux()
	NewHandler(hub).RegisterRoutes(mux)
	req := httptest.NewRequest("POST", "/admin/login", strings.NewReader(`{"password":""}`))
	req.Header.Set("X-Requested-With", "lwjam")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)
	if rec.Code != http.StatusServiceUnavailable {
		t.Fatalf("no password configured must disable login, got %d", rec.Code)
	}
}

func TestAdmin_ListGiveTakeAndChat(t *testing.T) {
	e := newAdminEnv(t)
	e.login()

	owner := rooms.NewClient(nil, e.hub)
	guest := rooms.NewClient(nil, e.hub)
	private, _ := e.hub.CreateRoom(owner, "Owner", wire.RoomModeHostOnly)
	_ = private.Join(guest, "Guest Person")
	private.HandleChat(guest, "hello room", "")

	rec := e.do("GET", "/admin/api/rooms", "", nil)
	var list struct {
		Rooms []rooms.RoomView `json:"rooms"`
	}
	_ = json.Unmarshal(rec.Body.Bytes(), &list)
	if len(list.Rooms) != 1 || list.Rooms[0].Code != private.Code {
		t.Fatalf("unexpected room list: %+v", list.Rooms)
	}

	rec = e.do("GET", "/admin/api/rooms/"+private.Code+"/chat", "", nil)
	if !strings.Contains(rec.Body.String(), "hello room") {
		t.Fatal("admin should see the room chat")
	}

	rec = e.do("POST", "/admin/api/rooms/"+private.Code+"/host", `{"userId":"`+guest.ID+`"}`, xhr)
	var v rooms.RoomView
	_ = json.Unmarshal(rec.Body.Bytes(), &v)
	if rec.Code != 200 || v.HostID != guest.ID {
		t.Fatalf("give host failed: %d %+v", rec.Code, v)
	}
	rec = e.do("POST", "/admin/api/rooms/"+private.Code+"/unhost", "{}", xhr)
	_ = json.Unmarshal(rec.Body.Bytes(), &v)
	if v.HostID != "" {
		t.Fatal("take host failed")
	}

	if rec := e.do("POST", "/admin/api/rooms/"+private.Code+"/host", `{"userId":"usr_nobody"}`, xhr); rec.Code != http.StatusNotFound {
		t.Fatal("unknown listener should be 404")
	}
	if rec := e.do("GET", "/admin/api/rooms/ZZZZZZ/chat", "", nil); rec.Code != http.StatusNotFound {
		t.Fatal("unknown room should be 404")
	}

	rec = e.do("GET", "/admin/api/rooms/"+private.Code+"/chat", "", nil)
	if !strings.Contains(rec.Body.String(), "is now the host") || !strings.Contains(rec.Body.String(), "host was removed") {
		t.Fatal("host changes should be announced in chat")
	}
}
