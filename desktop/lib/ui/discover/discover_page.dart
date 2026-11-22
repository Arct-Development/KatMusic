import 'dart:async';
import 'dart:math' as math;

import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../app/track_actions.dart';
import '../../features/feed/feed_repository.dart';
import '../../features/library/playlists.dart';
import '../components/buttons.dart';
import '../components/desktop_table.dart';
import '../components/states.dart';
import '../theme/tokens.dart';
import '../theme/kat_icons.dart';

/// Android's Discover and New Releases screens, rebuilt with the desktop
/// table and the shared feed repository.
///
/// Discover is a taste-led mix; New Releases is the fresh-release queue.
/// Both write to the same generated-playlist path as Mix Lab.
class KatDiscoverPage extends StatelessWidget {
  const KatDiscoverPage({super.key});

  @override
  Widget build(BuildContext context) =>
      const _FreshTracksPage(mode: _FreshMode.discover);
}

class KatNewReleasesPage extends StatelessWidget {
  const KatNewReleasesPage({super.key});

  @override
  Widget build(BuildContext context) =>
      const _FreshTracksPage(mode: _FreshMode.newReleases);
}

enum _FreshMode { discover, newReleases }

class _FreshTracksPage extends ConsumerStatefulWidget {
  final _FreshMode mode;
  const _FreshTracksPage({required this.mode});

  @override
  ConsumerState<_FreshTracksPage> createState() => _FreshTracksPageState();
}

class _FreshTracksPageState extends ConsumerState<_FreshTracksPage> {
  List<GeneratedTrack> _tracks = const [];
  bool _busy = true;
  String? _error;

  bool get _isDiscover => widget.mode == _FreshMode.discover;

  String get _kicker => _isDiscover ? 'For you' : 'Catalogue';

  String get _title => _isDiscover ? 'Discover' : 'New Releases';

  String get _subtitle => _isDiscover
      ? 'A fresh mix built from your listening, liked songs and local taste.'
      : 'The newest records in the catalogue, ready to play or save.';

  IconData get _icon => _isDiscover ? KatIcons.discover : KatIcons.albums;

  @override
  void initState() {
    super.initState();
    unawaited(_load());
  }

  Future<List<GeneratedTrack>> _fetch(FeedRepository repo) => _isDiscover
      ? repo.fetchMix(total: 30)
      : repo.fetchNewMusicTracks(limit: 30);

  Future<void> _load() async {
    if (mounted) {
      setState(() {
        _busy = true;
        _error = null;
      });
    }
    try {
      final tracks = await _fetch(ref.read(feedRepositoryProvider));
      if (!mounted) return;
      setState(() {
        _tracks = tracks;
        _busy = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = '$e';
        _busy = false;
      });
    }
  }

  void _play(int index) {
    if (index < 0 || index >= _tracks.length) return;
    unawaited(
      playGenerated(
        ref,
        context,
        _tracks[index],
        sourceLabel: _title,
        queueAll: _tracks,
        startIndex: index,
      ),
    );
  }

  Future<void> _shuffle() async {
    if (_tracks.isEmpty) return;
    final shuffled = List<GeneratedTrack>.of(_tracks)..shuffle(math.Random());
    await playGenerated(
      ref,
      context,
      shuffled.first,
      sourceLabel: '$_title · Shuffle',
      queueAll: shuffled,
    );
  }

  Future<void> _save() async {
    if (_tracks.isEmpty) return;
    final name = TextEditingController(text: _title);
    final ok = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => ContentDialog(
        title: const Text('Save as playlist'),
        content: TextBox(controller: name, placeholder: 'Playlist name'),
        actions: [
          KatButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: const Text('Cancel'),
          ),
          KatFilledButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: const Text('Save'),
          ),
        ],
      ),
    );
    final title = name.text.trim();
    name.dispose();
    if (ok != true || !mounted) return;
    final saved = await ref
        .read(playlistRepositoryProvider.notifier)
        .saveGenerated(title.isEmpty ? _title : title, [
          for (final t in _tracks)
            StoredTrack(
              name: t.name,
              artist: t.artist,
              album: t.album,
              artworkUrl: t.artworkUrl,
              videoId: t.videoId,
              durationSeconds: t.durationSeconds,
              playbackUrl: t.playbackUrl,
            ),
        ]);
    if (mounted) context.go('/playlists/${saved.id}');
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(24, 20, 24, 8),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      _kicker.toUpperCase(),
                      style: KatType.overline.copyWith(
                        color: katAccent(context),
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(_title, style: KatType.pageTitle),
                    const SizedBox(height: 3),
                    Text(
                      _subtitle,
                      style: KatType.body.copyWith(
                        color: katTextSecondary(context),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 16),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  KatGhostButton(
                    label: 'Shuffle',
                    icon: KatIcons.shuffle,
                    onPressed: _busy || _tracks.isEmpty ? null : _shuffle,
                  ),
                  KatGhostButton(
                    label: 'Save',
                    icon: FluentIcons.save,
                    onPressed: _busy || _tracks.isEmpty ? null : _save,
                  ),
                  KatPrimaryButton(
                    label: 'Refresh',
                    icon: FluentIcons.refresh,
                    onPressed: _busy ? null : _load,
                  ),
                ],
              ),
            ],
          ),
        ),
        Expanded(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(24, 8, 24, 20),
            child: LayoutBuilder(
              builder: (context, constraints) => Center(
                child: SizedBox(
                  width: math.min(constraints.maxWidth, 1180),
                  height: constraints.maxHeight,
                  child: _body(),
                ),
              ),
            ),
          ),
        ),
      ],
    );
  }

  Widget _body() {
    if (_busy) {
      return KatLoading(
        label: _isDiscover ? 'Building your mix…' : 'Reading new releases…',
      );
    }
    if (_error != null) {
      return KatError(
        title: _isDiscover ? 'Discover failed' : 'New Releases failed',
        message: _error!,
        onRetry: _load,
      );
    }
    if (_tracks.isEmpty) {
      return KatEmpty(
        icon: _icon,
        title: _isDiscover
            ? 'Nothing to discover yet'
            : 'No new releases found',
        subtitle: _isDiscover
            ? 'Play or like a few songs, then refresh to build a mix.'
            : 'The catalogue did not return any new records. Try again later.',
        actionLabel: 'Refresh',
        onAction: _load,
      );
    }
    return KatDesktopTable<GeneratedTrack>(
      items: _tracks,
      keyOf: (t) => t.key,
      titleOf: (t) => t.name,
      subtitleOf: (t) => t.artist,
      albumOf: (t) => t.album,
      artworkOf: (t) => t.artworkUrl,
      videoIdOf: (t) => t.videoId,
      durationOf: (t) => formatDuration(Duration(seconds: t.durationSeconds)),
      playableOf: playableFromGenerated,
      onPlay: _play,
      playOnSingleTap: true,
      showLike: false,
    );
  }
}
