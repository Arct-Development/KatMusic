import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../app/track_actions.dart';
import '../../features/feed/feed_repository.dart';
import '../../features/player/playback_service.dart';
import '../../features/sharing/share_links.dart';
import '../components/ambient.dart';
import '../components/artwork.dart';
import '../components/buttons.dart';
import '../sharing/share_track_dialog.dart';
import '../theme/haze.dart';
import '../theme/kat_icons.dart';
import '../theme/motion.dart';
import '../theme/tokens.dart';

/// Landing page for `katmusic://track?v=…` and the web share page.
///
/// It starts playback once and keeps a visible fallback card, so a link
/// opened on a new device is useful even while its stream is resolving.
class KatSharedTrackPage extends ConsumerStatefulWidget {
  final String videoId;
  final String title;
  final String artist;
  final String album;
  final String artworkUrl;

  const KatSharedTrackPage({
    super.key,
    required this.videoId,
    required this.title,
    required this.artist,
    required this.album,
    required this.artworkUrl,
  });

  @override
  ConsumerState<KatSharedTrackPage> createState() => _KatSharedTrackPageState();
}

class _KatSharedTrackPageState extends ConsumerState<KatSharedTrackPage> {
  bool _started = false;
  bool _starting = false;

  String get _title =>
      widget.title.trim().isEmpty ? 'Shared track' : widget.title.trim();

  String get _artist =>
      widget.artist.trim().isEmpty ? 'KatMusic' : widget.artist.trim();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _play();
    });
  }

  Future<void> _play({bool restart = false}) async {
    if (_starting || (!restart && _started) || widget.videoId.isEmpty) return;
    _starting = true;
    _started = true;
    try {
      await playGenerated(
        ref,
        context,
        GeneratedTrack(
          name: _title,
          artist: _artist,
          album: widget.album,
          artworkUrl: widget.artworkUrl,
          videoId: widget.videoId,
        ),
        sourceLabel: 'Shared link',
      );
    } finally {
      _starting = false;
    }
  }

  Future<void> _openYouTube() async {
    final uri = Uri.parse(
      'https://music.youtube.com/watch?v=${Uri.encodeComponent(widget.videoId)}',
    );
    try {
      if (await canLaunchUrl(uri)) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      }
    } catch (_) {}
  }

  Future<void> _copyWebLink() async {
    final link = katTrackShareUrl(
      videoId: widget.videoId,
      title: _title,
      artist: _artist,
      album: widget.album,
      artworkUrl: widget.artworkUrl,
    );
    if (link == null) return;
    await Clipboard.setData(ClipboardData(text: link));
    if (!mounted) return;
    displayInfoBar(
      context,
      builder: (context, close) => InfoBar(
        title: const Text('Share link copied'),
        content: const Text('Anyone can open it in KatMusic or YouTube Music.'),
        severity: InfoBarSeverity.success,
        onClose: close,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final palette = ref.watch(artworkPaletteProvider(widget.artworkUrl)).valueOrNull;
    final playback = ref.watch(
      playbackServiceProvider.select(
        (state) => (current: state.current, playing: state.isPlaying),
      ),
    );
    final isCurrent = playback.current?.videoId == widget.videoId;
    final glow = palette?.vibrant ?? katAccent(context);
    final card = katSurfaceRaised(context);

    return KatEntranceGroup(
      child: ListView(
        padding: const EdgeInsets.fromLTRB(24, 30, 24, 34),
        children: [
          Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 820),
              child: KatEntrance(
                local: true,
                rise: 10,
                child: TweenAnimationBuilder<Color?>(
                  tween: ColorTween(
                    begin: katAccent(context).withValues(alpha: 0.08),
                    end: glow.withValues(alpha: 0.18),
                  ),
                  duration: katMotion(context, KatMotion.slow),
                  curve: Curves.easeOutCubic,
                  builder: (context, tint, child) => DecoratedBox(
                    decoration: BoxDecoration(
                      borderRadius: KatRadius.heroRadius,
                      gradient: RadialGradient(
                        center: const Alignment(-0.65, -0.72),
                        radius: 1.25,
                        colors: [
                          tint ?? glow.withValues(alpha: 0.12),
                          card,
                        ],
                        stops: const [0, 0.78],
                      ),
                    ),
                    child: KatLiquidGlass(
                      borderRadius: KatRadius.heroRadius,
                      base: card.withValues(alpha: 0.72),
                      border: Border.all(color: katDivider(context)),
                      child: child!,
                    ),
                  ),
                  child: Padding(
                    padding: const EdgeInsets.all(28),
                    child: LayoutBuilder(
                      builder: (context, constraints) {
                        final stacked = constraints.maxWidth < 610;
                        final artwork = KatArtwork(
                          url: widget.artworkUrl,
                          videoId: widget.videoId,
                          size: stacked ? 220 : 248,
                          radius: KatRadius.artwork,
                          title: _title,
                          artist: _artist,
                          label: _title,
                        );
                        final details = _details(
                          context,
                          isCurrent: isCurrent,
                          isPlaying: playback.playing,
                        );
                        if (stacked) {
                          return Column(
                            crossAxisAlignment: CrossAxisAlignment.center,
                            children: [artwork, const SizedBox(height: 24), details],
                          );
                        }
                        return Row(
                          crossAxisAlignment: CrossAxisAlignment.center,
                          children: [
                            artwork,
                            const SizedBox(width: 30),
                            Expanded(child: details),
                          ],
                        );
                      },
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _details(
    BuildContext context, {
    required bool isCurrent,
    required bool isPlaying,
  }) {
    final accent = katAccent(context);
    final centered = MediaQuery.sizeOf(context).width < 720;
    final textAlign = centered ? TextAlign.center : TextAlign.left;
    return Column(
      crossAxisAlignment: centered
          ? CrossAxisAlignment.center
          : CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          'SHARED WITH YOU',
          style: KatType.overline.copyWith(color: accent),
          textAlign: textAlign,
        ),
        const SizedBox(height: 8),
        Text(
          'Someone sent you a track.',
          style: KatType.pageTitle.copyWith(fontSize: 28),
          textAlign: textAlign,
        ),
        const SizedBox(height: 14),
        Text(
          _title,
          maxLines: 2,
          overflow: TextOverflow.ellipsis,
          style: KatType.pageTitle.copyWith(fontSize: 22),
          textAlign: textAlign,
        ),
        const SizedBox(height: 4),
        Text(
          _artist,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: KatType.body.copyWith(color: katTextSecondary(context)),
          textAlign: textAlign,
        ),
        if (widget.album.trim().isNotEmpty)
          Text(
            widget.album,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: KatType.meta.copyWith(color: katTextTertiary(context)),
            textAlign: textAlign,
          ),
        const SizedBox(height: 20),
        AnimatedSwitcher(
          duration: katMotion(context, KatMotion.fast),
          child: isCurrent
              ? Row(
                  key: const ValueKey('playing'),
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      isPlaying ? KatIcons.visualizer : KatIcons.pause,
                      size: 14,
                      color: accent,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      isPlaying ? 'Playing from shared link' : 'Ready to play',
                      style: KatType.meta.copyWith(color: accent),
                    ),
                  ],
                )
              : Text(
                  'Ready to play on this device',
                  key: const ValueKey('ready'),
                  style: KatType.meta.copyWith(color: katTextTertiary(context)),
                ),
        ),
        const SizedBox(height: 18),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          alignment: centered ? WrapAlignment.center : WrapAlignment.start,
          children: [
            KatPrimaryButton(
              label: isCurrent && isPlaying ? 'Play again' : 'Play in KatMusic',
              icon: KatIcons.play,
              onPressed: () => _play(restart: true),
            ),
            KatGhostButton(
              label: 'Share options',
              icon: KatIcons.share,
              onPressed: () => showKatTrackShareDialog(
                context,
                videoId: widget.videoId,
                title: _title,
                artist: _artist,
                album: widget.album,
                artworkUrl: widget.artworkUrl,
              ),
            ),
            KatGhostButton(
              label: 'Copy link',
              icon: KatIcons.copy,
              onPressed: _copyWebLink,
            ),
            KatGhostButton(
              label: 'YouTube Music',
              icon: KatIcons.externalLink,
              onPressed: _openYouTube,
            ),
          ],
        ),
        const SizedBox(height: 18),
        Text(
          'The same link opens in KatMusic on your phone, laptop or desktop. '
          'If the app is not installed, the web page keeps a YouTube Music fallback.',
          style: KatType.meta.copyWith(color: katTextTertiary(context)),
          textAlign: textAlign,
        ),
      ],
    );
  }
}
