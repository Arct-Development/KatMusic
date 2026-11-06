import 'dart:async';

import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../app/track_actions.dart';
import '../../features/feed/feed_repository.dart';
import '../components/artwork.dart';
import '../components/buttons.dart';
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

  Future<void> _play() async {
    if (_started || widget.videoId.isEmpty) return;
    _started = true;
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

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(24, 32, 24, 32),
      children: [
        Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 620),
            child: Column(
              children: [
                Text(
                  'SHARED WITH YOU',
                  style: KatType.overline.copyWith(color: katAccent(context)),
                ),
                const SizedBox(height: 8),
                Text(
                  'Someone sent you a track.',
                  style: KatType.pageTitle,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 24),
                KatArtwork(
                  url: widget.artworkUrl,
                  videoId: widget.videoId,
                  size: 260,
                  radius: 12,
                  title: _title,
                  artist: _artist,
                  label: _title,
                ),
                const SizedBox(height: 22),
                Text(
                  _title,
                  style: KatType.pageTitle,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 4),
                Text(
                  _artist,
                  style: KatType.body.copyWith(
                    color: katTextSecondary(context),
                  ),
                  textAlign: TextAlign.center,
                ),
                if (widget.album.trim().isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(
                    widget.album,
                    style: KatType.meta.copyWith(
                      color: katTextTertiary(context),
                    ),
                    textAlign: TextAlign.center,
                  ),
                ],
                const SizedBox(height: 22),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  alignment: WrapAlignment.center,
                  children: [
                    KatPrimaryButton(
                      label: 'Play in KatMusic',
                      icon: FluentIcons.play,
                      onPressed: _play,
                    ),
                    KatGhostButton(
                      label: 'YouTube Music',
                      icon: FluentIcons.open_in_new_window,
                      onPressed: _openYouTube,
                    ),
                  ],
                ),
                const SizedBox(height: 18),
                Text(
                  'The same link opens in KatMusic on your phone, laptop '
                  'or desktop. Playback follows the device you open it on.',
                  style: KatType.meta.copyWith(color: katTextTertiary(context)),
                  textAlign: TextAlign.center,
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}
