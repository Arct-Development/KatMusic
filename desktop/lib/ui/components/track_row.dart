import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/audio/stream_models.dart';
import '../../core/storage/prefs.dart';
import '../../features/library/playlists.dart';
import '../../features/player/playback_service.dart';
import '../theme/tokens.dart';
import '../theme/kat_icons.dart';
import 'artist_line.dart';
import 'artwork.dart';
import 'buttons.dart' show KatTooltip, KatA11yButton;
import 'desktop_table.dart' show KatEqDots;
import 'menus.dart';

export 'artist_line.dart';
export 'desktop_table.dart' show KatDesktopTable, KatSortDir, KatEqDots;

/// Dense desktop song row.
///
/// New code should prefer [KatDesktopTable] (desktop_table.dart) — one
/// virtualized, sortable, keyboard-navigable table shared by Search, Home,
/// Discover and Collections. This single-row widget stays for existing
/// consumers and renders one [KatDesktopTable]-identical row.

/// Dense desktop song row.
///
/// `01  [cover]  Title / Artist   Album   ♡  …  5:14`
/// Hover: play replaces number, secondary actions appear.
/// Playing: small accent indicator. No borders around rows.
class KatTrackRow extends ConsumerStatefulWidget {
  final String title;
  final String artist;
  final String album;
  final String artworkUrl;
  final String videoId;
  final PlayableTrack? playable;
  final int? index;
  final bool playing;
  final bool isCurrent;
  final String? meta;
  final String? duration;
  final String? removeLabel;
  final VoidCallback? onRemove;
  final VoidCallback? onTap;
  final bool showLike;
  const KatTrackRow({
    super.key,
    required this.title,
    required this.artist,
    this.album = '',
    this.artworkUrl = '',
    this.videoId = '',
    this.playable,
    this.index,
    this.playing = false,
    this.isCurrent = false,
    this.meta,
    this.duration,
    this.removeLabel,
    this.onRemove,
    this.onTap,
    this.showLike = true,
  });

  @override
  ConsumerState<KatTrackRow> createState() => _KatTrackRowState();
}

class _KatTrackRowState extends ConsumerState<KatTrackRow> {
  bool _hover = false;
  final FocusNode _focus = FocusNode();

  @override
  void dispose() {
    _focus.dispose();
    super.dispose();
  }

  void _activate() {
    if (widget.onTap != null) {
      widget.onTap!();
      return;
    }
    final track =
        widget.playable ??
        PlayableTrack(
          title: widget.title,
          artist: widget.artist,
          album: widget.album,
          artworkUrl: widget.artworkUrl,
          videoId: widget.videoId,
        );
    ref
        .read(playbackServiceProvider.notifier)
        .play(
          track,
          sourceLabel: 'Track list',
          startRadio: ref.read(prefsProvider).autoplaySimilar,
        );
  }

  @override
  Widget build(BuildContext context) {
    final dark = katIsDark(context);
    final accent = katAccent(context);
    // Watch the playlist STATE (not the notifier — its identity never
    // changes, so watching it never rebuilds and the heart sticks).
    final liked = ref
        .watch(playlistRepositoryProvider)
        .where((p) => p.isLikedSongs)
        .expand((p) => p.tracks)
        .map((t) => '${t.name.toLowerCase()}|${t.artist.toLowerCase()}')
        .contains(
          (widget.playable?.queueKey) ??
              '${widget.title.toLowerCase()}|${widget.artist.toLowerCase()}',
        );

    final bg = widget.isCurrent
        ? accent.withValues(alpha: 0.0)
        : _hover
        ? katHoverWash(context)
        : Colors.transparent;

    final row = Focus(
      focusNode: _focus,
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent) {
          if (event.logicalKey == LogicalKeyboardKey.enter ||
              event.logicalKey == LogicalKeyboardKey.numpadEnter) {
            _activate();
            return KeyEventResult.handled;
          }
          if (event.logicalKey == LogicalKeyboardKey.delete &&
              widget.onRemove != null) {
            widget.onRemove!();
            return KeyEventResult.handled;
          }
        }
        return KeyEventResult.ignored;
      },
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _hover = true),
        onExit: (_) => setState(() => _hover = false),
        child: GestureDetector(
          onTap: () {
            _focus.requestFocus();
            _activate();
          },
          onDoubleTap: _activate,
          child: AnimatedContainer(
            duration: katMotion(context, KatMotion.fast),
            height: KatDensity.trackRow,
            padding: const EdgeInsets.symmetric(horizontal: 8),
            decoration: BoxDecoration(
              color: bg,
              borderRadius: KatRadius.artworkRadius,
            ),
            child: Row(
              children: [
                // Gutter: number → play on hover, equalizer when playing.
                SizedBox(
                  width: 30,
                  child: Center(
                    child: widget.playing
                        ? const KatEqDots()
                        : _hover
                        ? Icon(
                            KatIcons.play,
                            size: 14,
                            color: dark
                                ? KatColors.textPrimary
                                : KatColors.lightTextPrimary,
                          )
                        : Text(
                            widget.index != null
                                ? widget.index!.toString().padLeft(2, '0')
                                : '',
                            style: KatType.meta.copyWith(
                              fontSize: 12,
                              fontFeatures: const [
                                FontFeature.tabularFigures(),
                              ],
                              color: widget.isCurrent
                                  ? accent
                                  : (dark
                                        ? KatColors.textTertiary
                                        : KatColors.lightTextTertiary),
                            ),
                            textAlign: TextAlign.center,
                          ),
                  ),
                ),
                KatArtwork(
                  url: widget.artworkUrl,
                  videoId: widget.videoId,
                  size: KatDensity.trackArt,
                  radius: KatRadius.artwork,
                  title: widget.title,
                  artist: widget.artist,
                  label: widget.title,
                ),
                const SizedBox(width: 12),
                Expanded(
                  flex: 5,
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        widget.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: KatType.trackTitle.copyWith(
                          color: widget.isCurrent
                              ? accent
                              : (dark
                                    ? KatColors.textPrimary
                                    : KatColors.lightTextPrimary),
                        ),
                      ),
                      KatArtistLine(artist: widget.artist),
                    ],
                  ),
                ),
                Expanded(
                  flex: 3,
                  child: widget.album.isEmpty
                      ? const SizedBox.shrink()
                      : Text(
                          widget.album,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: KatType.meta.copyWith(
                            color: dark
                                ? KatColors.textTertiary
                                : KatColors.lightTextTertiary,
                          ),
                        ),
                ),
                // Secondary actions appear on hover / current.
                AnimatedOpacity(
                  duration: katMotion(context, KatMotion.fast),
                  opacity: (_hover || widget.isCurrent || liked) ? 1 : 0,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      if (widget.showLike)
                        _RowGlyph(
                          tooltip: liked ? 'Unlike' : 'Like',
                          icon: liked ? KatIcons.likedFill : KatIcons.liked,
                          active: liked,
                          onTap: () {
                            ref
                                .read(playlistRepositoryProvider.notifier)
                                .toggleLiked(
                                  StoredTrack(
                                    name: widget.title,
                                    artist: widget.artist,
                                    artworkUrl: widget.artworkUrl,
                                    videoId: widget.videoId,
                                    album: widget.album,
                                  ),
                                );
                          },
                        ),
                      _RowGlyph(
                        tooltip: 'More',
                        icon: KatIcons.more,
                        menuItems: katTrackMenuItems(
                          context: context,
                          ref: ref,
                          title: widget.title,
                          artist: widget.artist,
                          artworkUrl: widget.artworkUrl,
                          videoId: widget.videoId,
                          album: widget.album,
                          playable: widget.playable,
                          removeLabel: widget.removeLabel,
                          onRemove: widget.onRemove,
                        ),
                      ),
                    ],
                  ),
                ),
                SizedBox(
                  width: 44,
                  child: Text(
                    widget.duration ?? widget.meta ?? '',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.right,
                    style: KatType.meta.copyWith(
                      fontFeatures: const [FontFeature.tabularFigures()],
                      color: dark
                          ? KatColors.textTertiary
                          : KatColors.lightTextTertiary,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );

    return KatContextMenu(
      items: () => katTrackMenuItems(
        context: context,
        ref: ref,
        title: widget.title,
        artist: widget.artist,
        artworkUrl: widget.artworkUrl,
        videoId: widget.videoId,
        playable: widget.playable,
        removeLabel: widget.removeLabel,
        onRemove: widget.onRemove,
      ),
      child: Semantics(
        button: true,
        selected: widget.isCurrent,
        // Name comes from the title / artist / duration Text children,
        // which merge into this node; labelling it too would read twice.
        onTap: _activate,
        child: row,
      ),
    );
  }
}

class _RowGlyph extends StatefulWidget {
  final String tooltip;
  final IconData icon;
  final bool active;
  final VoidCallback? onTap;
  final List<KatMenuEntry>? menuItems;
  const _RowGlyph({
    required this.tooltip,
    required this.icon,
    this.active = false,
    this.onTap,
    this.menuItems,
  });
  @override
  State<_RowGlyph> createState() => _RowGlyphState();
}

class _RowGlyphState extends State<_RowGlyph> {
  bool _hover = false;
  @override
  Widget build(BuildContext context) {
    final dark = katIsDark(context);
    final color = widget.active
        ? katAccent(context)
        : _hover
        ? (dark ? KatColors.textPrimary : KatColors.lightTextPrimary)
        : (dark ? KatColors.textTertiary : KatColors.lightTextTertiary);
    if (widget.menuItems != null) {
      return KatTooltip(
        message: widget.tooltip,
        child: KatMenuButton(
          entries: widget.menuItems!,
          buttonBuilder: (context, onOpen) => KatA11yButton(
            label: widget.tooltip,
            onTap: onOpen,
            child: MouseRegion(
              cursor: SystemMouseCursors.click,
              onEnter: (_) => setState(() => _hover = true),
              onExit: (_) => setState(() => _hover = false),
              child: GestureDetector(
                onTap: onOpen,
                child: AnimatedScale(
                  scale: _hover ? 1.15 : 1.0,
                  duration: katMotion(context, KatMotion.fast),
                  curve: Curves.easeOutCubic,
                  child: Container(
                    width: 30,
                    height: 36,
                    color: Colors.transparent,
                    child: Icon(widget.icon, size: 14, color: color),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
    }
    return KatTooltip(
      message: widget.tooltip,
      child: KatA11yButton(
        label: widget.tooltip,
        selected: widget.active,
        onTap: widget.onTap,
        child: MouseRegion(
          cursor: SystemMouseCursors.click,
          onEnter: (_) => setState(() => _hover = true),
          onExit: (_) => setState(() => _hover = false),
          child: GestureDetector(
            onTap: widget.onTap,
            child: AnimatedScale(
              scale: _hover ? 1.15 : 1.0,
              duration: katMotion(context, KatMotion.fast),
              curve: Curves.easeOutCubic,
              child: Container(
                width: 30,
                height: 36,
                color: Colors.transparent,
                child: Icon(widget.icon, size: 14, color: color),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class KatTrackTableHeader extends StatelessWidget {
  final bool showAlbum;
  const KatTrackTableHeader({super.key, this.showAlbum = true});
  @override
  Widget build(BuildContext context) {
    final dark = katIsDark(context);
    final style = KatType.overline.copyWith(
      fontSize: 9.5,
      color: dark ? KatColors.textTertiary : KatColors.lightTextTertiary,
    );
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      child: Row(
        children: [
          const SizedBox(width: 30),
          const SizedBox(width: KatDensity.trackArt),
          const SizedBox(width: 12),
          Expanded(flex: 5, child: Text('TITLE', style: style)),
          if (showAlbum) Expanded(flex: 3, child: Text('ALBUM', style: style)),
          const SizedBox(width: 100),
        ],
      ),
    );
  }
}
