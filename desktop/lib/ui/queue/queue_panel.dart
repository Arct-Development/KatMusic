import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../theme/kat_icons.dart';

import '../../app/track_actions.dart';
import '../../core/audio/stream_models.dart';
import '../../features/downloads/download_manager.dart';
import '../../features/player/playback_service.dart';
import '../components/artwork.dart';
import '../components/buttons.dart'
    show KatTooltip, KatButton, KatFilledButton, KatLink;
import '../components/menus.dart';
import '../navigation/kat_nav.dart';
import '../theme/tokens.dart';

/// Session-only. Closing the app clears it; it is not written to SQLite.
final queueLockedProvider = StateProvider<bool>((_) => false);

/// Contextual queue — slides from right, 360px, disappears fully.
///
/// Header: Queue 18px + UP NEXT count + Clear. NOW PLAYING 44px accent row.
/// Reorderable 48px rows: drag handle visible on hover but always
/// accessible (low-opacity idle), hover X remove, right-click menu
/// (Play next, Go to album/artist, Download, Add to playlist, Remove).
class KatQueuePanel extends ConsumerWidget {
  final VoidCallback? onClose;
  final bool embedded;
  const KatQueuePanel({
    super.key,
    required this.onClose,
    this.embedded = false,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dark = katIsDark(context);
    final queue = ref.watch(playbackServiceProvider.select((s) => s.queue));
    final index = ref.watch(
      playbackServiceProvider.select((s) => s.currentIndex),
    );
    final notifier = ref.read(playbackServiceProvider.notifier);
    final locked = ref.watch(queueLockedProvider);
    if (queue.isEmpty) {
      return _Shell(
        onClose: onClose,
        embedded: embedded,
        child: KatQueueEmpty(onBrowse: () => context.go('/home')),
      );
    }
    final current = index >= 0 && index < queue.length ? queue[index] : null;
    final upcoming = index >= 0
        ? queue.sublist((index + 1).clamp(0, queue.length))
        : queue;

    return _Shell(
      onClose: onClose,
      embedded: embedded,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 2),
            child: Text(
              'Queue',
              style: KatType.pageTitle.copyWith(fontSize: 18),
            ),
          ),
          if (current != null) ...[
            const SizedBox(height: 10),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text(
                'NOW PLAYING',
                style: KatType.overline.copyWith(
                  fontSize: 9,
                  color: dark
                      ? KatColors.textTertiary
                      : KatColors.lightTextTertiary,
                ),
              ),
            ),
            const SizedBox(height: 6),
            _NowRow(track: current, queueIndex: index),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
              child: Container(height: 1, color: katDivider(context)),
            ),
          ],
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(
              children: [
                Text(
                  'UP NEXT · ${upcoming.length}',
                  style: KatType.overline.copyWith(
                    fontSize: 9,
                    color: dark
                        ? KatColors.textTertiary
                        : KatColors.lightTextTertiary,
                  ),
                ),
                const Spacer(),
                KatTooltip(
                  message: locked
                      ? 'Unlock reorder and remove'
                      : 'Lock reorder and remove',
                  child: KatLink(
                    onPressed: () =>
                        ref.read(queueLockedProvider.notifier).state = !locked,
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          locked ? FluentIcons.lock : FluentIcons.unlock,
                          size: 12,
                        ),
                        const SizedBox(width: 4),
                        Text(
                          locked ? 'Unlock' : 'Lock',
                          style: KatType.label.copyWith(fontSize: 11),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                KatTooltip(
                  message: locked
                      ? 'Queue is locked'
                      : upcoming.isEmpty
                      ? 'Nothing to clear'
                      : 'Clear up next',
                  child: KatLink(
                    onPressed: locked || upcoming.isEmpty
                        ? null
                        : notifier.clearUpcoming,
                    child: Text(
                      'Clear',
                      style: KatType.label.copyWith(fontSize: 11),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 4),
          Expanded(
            child: upcoming.isEmpty
                ? Center(
                    child: Text(
                      'Queue ends here.',
                      style: KatType.meta.copyWith(
                        color: dark
                            ? KatColors.textTertiary
                            : KatColors.lightTextTertiary,
                      ),
                    ),
                  )
                : locked
                ? ListView.builder(
                    itemCount: upcoming.length,
                    itemBuilder: (context, i) {
                      final queueIndex = index + 1 + i;
                      final t = upcoming[i];
                      return _QueueRow(
                        key: ValueKey('q:${t.queueKey}:$queueIndex'),
                        track: t,
                        queueIndex: queueIndex,
                        dragIndex: i,
                        locked: true,
                      );
                    },
                  )
                : ReorderableListView.builder(
                    buildDefaultDragHandles: false,
                    itemCount: upcoming.length,
                    // onReorderItem hands over post-removal coordinates;
                    // moveInQueue expects classic onReorder coordinates,
                    // so shift back up when moving down.
                    onReorderItem: (oldI, newI) {
                      final r = newI > oldI ? newI + 1 : newI;
                      final oldQueue = index + 1 + oldI;
                      final newQueue = index + 1 + r;
                      notifier.moveInQueue(oldQueue, newQueue);
                    },
                    itemBuilder: (context, i) {
                      final queueIndex = index + 1 + i;
                      final t = upcoming[i];
                      return _QueueRow(
                        key: ValueKey('q:${t.queueKey}:$queueIndex'),
                        track: t,
                        queueIndex: queueIndex,
                        dragIndex: i,
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}

class KatQueueEmpty extends StatelessWidget {
  final VoidCallback onBrowse;
  const KatQueueEmpty({super.key, required this.onBrowse});
  @override
  Widget build(BuildContext context) {
    final dark = katIsDark(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              width: 52,
              height: 52,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: katSurfaceOverlay(context),
                border: Border.all(
                  color: dark ? katOutlineSoft(context) : katOutline(context),
                ),
              ),
              child: Icon(
                KatIcons.queue,
                size: 22,
                color: dark
                    ? KatColors.textTertiary
                    : KatColors.lightTextTertiary,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              'Queue is empty',
              style: KatType.sectionTitle.copyWith(fontSize: 15),
            ),
            const SizedBox(height: 4),
            Text(
              'Play something to get started',
              style: KatType.meta.copyWith(
                color: dark
                    ? KatColors.textSecondary
                    : KatColors.lightTextSecondary,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            KatFilledButton(
              onPressed: onBrowse,
              child: const Text('Browse music'),
            ),
          ],
        ),
      ),
    );
  }
}

class _Shell extends StatelessWidget {
  final VoidCallback? onClose;
  final Widget child;
  final bool embedded;
  const _Shell({
    required this.onClose,
    required this.child,
    this.embedded = false,
  });
  @override
  Widget build(BuildContext context) {
    // Haze Level 2 — contextual panel translucency; rows stay crisp
    // (no per-row blur, bounded 360px region only).
    final body = Column(
      children: [
        if (!embedded)
          Align(
            alignment: Alignment.topRight,
            child: Padding(
              padding: const EdgeInsets.only(top: 8, right: 8),
              child: _CloseGlyph(onTap: onClose ?? () {}),
            ),
          ),
        Expanded(child: child),
      ],
    );
    if (embedded) return body;
    // Haze Level 2 tonality WITHOUT the live backdrop readback. The
    // panel is a full-height overlay: a live BackdropFilter on it
    // re-samples the whole 360px×window-height region every time the
    // page beneath repaints (scroll, entrance motion, artwork fades).
    // Matches the TitleBar / SideRail / PlayerDock decision — static
    // tonal acrylic, same look, no per-frame GPU readback.
    // Fully opaque: nothing from the page may show through the panel.
    return DecoratedBox(
      decoration: BoxDecoration(
        color: katBackground(context),
        border: Border(left: BorderSide(color: katDivider(context))),
      ),
      child: SizedBox(width: KatDensity.contextPanel, child: body),
    );
  }
}

class _CloseGlyph extends StatefulWidget {
  final VoidCallback onTap;
  const _CloseGlyph({required this.onTap});
  @override
  State<_CloseGlyph> createState() => _CloseGlyphState();
}

class _CloseGlyphState extends State<_CloseGlyph> {
  bool _hover = false;
  @override
  Widget build(BuildContext context) {
    return KatTooltip(
      message: 'Close (Esc)',
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _hover = true),
        onExit: (_) => setState(() => _hover = false),
        child: GestureDetector(
          onTap: widget.onTap,
          child: Container(
            width: 32,
            height: 32,
            decoration: BoxDecoration(
              color: _hover ? katHoverWash(context) : Colors.transparent,
              borderRadius: BorderRadius.circular(KatRadius.controls),
            ),
            child: Icon(
              FluentIcons.chrome_close,
              size: 12,
              color: _hover
                  ? katTextPrimary(context)
                  : katTextTertiary(context),
            ),
          ),
        ),
      ),
    );
  }
}

class _NowRow extends ConsumerWidget {
  final PlayableTrack track;
  final int queueIndex;
  const _NowRow({required this.track, required this.queueIndex});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return Container(
      constraints: const BoxConstraints(minHeight: 44),
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Row(
        children: [
          KatArtwork(
            url: track.artworkUrl,
            size: 44,
            radius: KatRadius.artwork,
            label: track.title,
            title: track.title,
            artist: track.artist,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  track.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: KatType.trackTitle.copyWith(fontSize: 13),
                ),
                Text(
                  track.artist,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: KatType.meta,
                ),
              ],
            ),
          ),
          Icon(KatIcons.queue, size: 15, color: katTextTertiary(context)),
        ],
      ),
    );
  }
}

class _QueueRow extends ConsumerStatefulWidget {
  final PlayableTrack track;
  final int queueIndex;
  final int dragIndex;
  final bool locked;
  const _QueueRow({
    super.key,
    required this.track,
    required this.queueIndex,
    required this.dragIndex,
    this.locked = false,
  });
  @override
  ConsumerState<_QueueRow> createState() => _QueueRowState();
}

class _QueueRowState extends ConsumerState<_QueueRow> {
  bool _hover = false;

  List<KatMenuEntry> _menuItems(WidgetRef ref) {
    final notifier = ref.read(playbackServiceProvider.notifier);
    final t = widget.track;
    return [
      KatMenuAction(
        leading: const Icon(KatIcons.queue, size: 15),
        label: 'Play next',
        onPressed: () => notifier.playNext(t),
      ),
      KatMenuAction(
        leading: const Icon(KatIcons.albums, size: 15),
        label: 'Go to album',
        onPressed: () => goToAlbumOfTrack(
          ref,
          title: t.title,
          artist: t.artist,
          album: t.album,
        ),
      ),
      KatMenuAction(
        leading: const Icon(KatIcons.artists, size: 15),
        label: 'Go to artist',
        onPressed: () =>
            katOpen(context, '/artist/${Uri.encodeComponent(t.artist)}'),
      ),
      const KatMenuSeparator(),
      KatMenuAction(
        leading: const Icon(KatIcons.downloadAction, size: 15),
        label: 'Download',
        onPressed: () => ref
            .read(downloadManagerProvider.notifier)
            .downloadTrack(
              title: t.title,
              artist: t.artist,
              album: t.album,
              artworkUrl: t.artworkUrl,
            ),
      ),
      KatMenuAction(
        leading: const Icon(KatIcons.addTo, size: 15),
        label: 'Add to playlist',
        onPressed: () => showWaveAddToPlaylist(
          context,
          ref,
          title: t.title,
          artist: t.artist,
          artworkUrl: t.artworkUrl,
          videoId: t.videoId,
          album: t.album,
          durationSeconds: t.duration.inSeconds,
        ),
      ),
      const KatMenuSeparator(),
      KatMenuAction(
        leading: const Icon(KatIcons.delete, size: 15),
        label: 'Remove',
        onPressed: widget.locked
            ? null
            : () => notifier.removeAt(widget.queueIndex),
      ),
    ];
  }

  @override
  Widget build(BuildContext context) {
    final dark = katIsDark(context);
    final notifier = ref.read(playbackServiceProvider.notifier);
    final row = MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hover = true),
      onExit: (_) => setState(() => _hover = false),
      child: GestureDetector(
        onTap: () => notifier.seekToQueueItem(widget.queueIndex),
        onDoubleTap: () => notifier.seekToQueueItem(widget.queueIndex),
        child: Container(
          constraints: const BoxConstraints(minHeight: 48),
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
          decoration: BoxDecoration(
            color: _hover ? katHoverWash(context) : Colors.transparent,
            borderRadius: KatRadius.artworkRadius,
          ),
          child: Row(
            children: [
              // Drag handle: low-opacity idle (still visible/keyboard
              // reachable) → full opacity on hover. Hidden while locked
              // so the row cannot start a reorder.
              if (widget.locked)
                KatTooltip(
                  message: 'Queue is locked',
                  child: Container(
                    width: 28,
                    height: 36,
                    color: Colors.transparent,
                    alignment: Alignment.center,
                    child: Icon(
                      FluentIcons.lock,
                      size: 13,
                      color:
                          (dark
                                  ? KatColors.textTertiary
                                  : KatColors.lightTextTertiary)
                              .withValues(alpha: 0.7),
                    ),
                  ),
                )
              else
                KatTooltip(
                  message: 'Drag to reorder',
                  child: ReorderableDragStartListener(
                    index: widget.dragIndex,
                    child: MouseRegion(
                      cursor: SystemMouseCursors.move,
                      child: Container(
                        width: 28,
                        height: 36,
                        color: Colors.transparent,
                        child: Icon(
                          FluentIcons.gripper_bar_vertical,
                          size: 15,
                          color: _hover
                              ? (dark
                                    ? KatColors.textSecondary
                                    : KatColors.lightTextSecondary)
                              : (dark
                                        ? KatColors.textTertiary
                                        : KatColors.lightTextTertiary)
                                    .withValues(alpha: 0.45),
                        ),
                      ),
                    ),
                  ),
                ),
              const SizedBox(width: 4),
              KatArtwork(
                url: widget.track.artworkUrl,
                size: 36,
                radius: KatRadius.artwork,
                label: widget.track.title,
                title: widget.track.title,
                artist: widget.track.artist,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      widget.track.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: KatType.trackTitle.copyWith(fontSize: 12.5),
                    ),
                    Text(
                      widget.track.artist,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: KatType.meta.copyWith(fontSize: 11.5),
                    ),
                  ],
                ),
              ),
              if (_hover && !widget.locked)
                KatTooltip(
                  message: 'Remove',
                  child: GestureDetector(
                    onTap: () => notifier.removeAt(widget.queueIndex),
                    child: Container(
                      width: 32,
                      height: 36,
                      color: Colors.transparent,
                      child: const Icon(FluentIcons.chrome_close, size: 12),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
    return KatContextMenu(items: () => _menuItems(ref), child: row);
  }
}

/// Back-compat: narrow-window queue sheet + public body.
Future<void> showWaveQueueSheet(BuildContext context) {
  return showDialog(
    context: context,
    barrierDismissible: true,
    builder: (context) {
      final contentW = MediaQuery.sizeOf(context).width;
      // Same clamp as the overlay (280–360, never >90% of content).
      final w = (360.0).clamp(280.0, (contentW * 0.9).clamp(180.0, 360.0));
      return ContentDialog(
        title: const Text('Queue'),
        constraints: const BoxConstraints(maxWidth: 440),
        content: SizedBox(
          width: w.toDouble(),
          height: 520,
          child: KatQueuePanel(onClose: () => Navigator.of(context).pop()),
        ),
        actions: [
          KatButton(
            onPressed: () => Navigator.of(context).pop(),
            child: const Text('Close'),
          ),
        ],
      );
    },
  );
}

class QueueBodyPublic extends ConsumerWidget {
  final bool embedded;
  const QueueBodyPublic({super.key, this.embedded = false});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return KatQueuePanel(onClose: null, embedded: embedded);
  }
}
