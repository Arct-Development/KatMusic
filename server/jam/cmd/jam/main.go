package main

import (
	"context"
	"flag"
	"log/slog"
	"os"
	"os/signal"
	"syscall"
	"time"

	"katmusic/jam/internal/api"
	"katmusic/jam/internal/rooms"
	"katmusic/jam/internal/store"
)

func main() {
	portFlag := flag.String("port", "", "Server HTTP/WebSocket port")
	flag.Parse()

	port := *portFlag
	if port == "" {
		port = os.Getenv("PORT")
	}
	if port == "" {
		port = "8080"
	}

	logger := slog.New(slog.NewTextHandler(os.Stdout, &slog.HandlerOptions{
		Level: slog.LevelInfo,
	}))
	slog.SetDefault(logger)

	slog.Info("initializing KatMusic Jam server", "port", port)

	hub := rooms.NewHub()
	hub.EnsurePermanentRooms()

	// Jam uses its own Supabase project, separate from Group Blend, so each
	// project stays inside its own free-tier quota. The service-role key never
	// leaves this process.
	serviceKey := os.Getenv("SUPABASE_SERVICE_ROLE_KEY")
	if serviceKey == "" {
		serviceKey = os.Getenv("SUPABASE_SECRET_KEY")
	}
	db := store.New(os.Getenv("SUPABASE_URL"), serviceKey)
	hub.EnablePersistence(db)

	if db.Enabled() {
		restoreCtx, cancelRestore := context.WithTimeout(context.Background(), 20*time.Second)
		if err := hub.Restore(restoreCtx); err != nil {
			slog.Warn("could not restore jam rooms; starting fresh", "err", err)
		}
		cancelRestore()

		persistCtx, stopPersist := context.WithCancel(context.Background())
		defer stopPersist()
		go hub.RunPersistenceLoop(persistCtx, 15*time.Second)
		slog.Info("jam persistence enabled")
	} else {
		slog.Info("jam persistence disabled; set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY")
	}

	// 24/7 radio in the always-on rooms, fed by the contribution site.
	radioCtx, stopRadio := context.WithCancel(context.Background())
	defer stopRadio()
	radioSource := os.Getenv("RADIO_SOURCE_URL")
	if radioSource == "" {
		radioSource = "http://127.0.0.1:6969"
	}
	if radioSource != "off" {
		hub.StartRadio(radioCtx, radioSource)
		slog.Info("radio started", "source", radioSource)
	}
	srv := api.NewServer(api.Config{
		Port:            port,
		CleanupInterval: 2 * time.Minute,
	}, hub)

	stopChan := make(chan os.Signal, 1)
	signal.Notify(stopChan, os.Interrupt, syscall.SIGTERM)

	go func() {
		if err := srv.Start(); err != nil {
			slog.Error("server error", "err", err)
			os.Exit(1)
		}
	}()

	<-stopChan
	slog.Info("shutting down KatMusic Jam server...")

	// Snapshot before closing sockets: leaving can hand the host role over and
	// clear the shared queue, which is not the state we want to wake up with.
	flushCtx, cancelFlush := context.WithTimeout(context.Background(), 10*time.Second)
	hub.PersistOnce(flushCtx)
	cancelFlush()

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := srv.Shutdown(shutdownCtx); err != nil {
		slog.Error("graceful shutdown failed", "err", err)
	} else {
		slog.Info("server exited cleanly")
	}
}
