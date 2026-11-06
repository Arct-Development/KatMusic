import 'dart:async';
import 'dart:math' as math;

import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../app/track_actions.dart';
import '../../features/blend/group_blend.dart';
import '../../features/feed/feed_repository.dart';
import '../../features/lastfm/auth_repository.dart';
import '../../features/library/playlists.dart';
import '../../features/search/shared_providers.dart';
import '../components/buttons.dart';
import '../components/desktop_table.dart';
import '../theme/tokens.dart';
import '../theme/kat_icons.dart';

/// Desktop Group Blend.
///
/// Android's blend is server-backed; this is the portable local version:
/// each listener shares a compact taste code, the app compares artist and
/// track overlap, and the match is presented with the same gauge idea.
class KatGroupBlendPage extends ConsumerStatefulWidget {
  const KatGroupBlendPage({super.key});

  @override
  ConsumerState<KatGroupBlendPage> createState() => _KatGroupBlendPageState();
}

class _KatGroupBlendPageState extends ConsumerState<KatGroupBlendPage> {
  BlendProfile? _me;
  final List<BlendProfile> _mates = [];
  final _code = TextEditingController();
  final _name = TextEditingController();
  String? _error;
  bool _copied = false;
  Timer? _copiedTimer;

  @override
  void initState() {
    super.initState();
    _buildProfile();
  }

  @override
  void dispose() {
    _copiedTimer?.cancel();
    _code.dispose();
    _name.dispose();
    super.dispose();
  }

  void _buildProfile() {
    final liked = <StoredTrack>[];
    for (final playlist in ref.read(playlistRepositoryProvider)) {
      if (playlist.isLikedSongs) liked.addAll(playlist.tracks);
    }
    final recent = ref.read(databaseProvider).loadRecentPlays(limit: 300);
    final username = ref.read(authRepositoryProvider).username;
    setState(() {
      _me = buildLocalBlendProfile(
        name: username.isEmpty ? 'You' : username,
        liked: liked,
        recent: recent,
      );
    });
  }

  Future<void> _copyCode() async {
    final profile = _me;
    if (profile == null) return;
    await Clipboard.setData(ClipboardData(text: profile.code));
    if (!mounted) return;
    setState(() => _copied = true);
    _copiedTimer?.cancel();
    _copiedTimer = Timer(const Duration(seconds: 2), () {
      if (mounted) setState(() => _copied = false);
    });
  }

  void _addMate() {
    final profile = BlendProfile.decode(_code.text);
    if (profile == null) {
      setState(
        () => _error =
            'That code could not be read. Paste the full KatMusic blend code.',
      );
      return;
    }
    final rename = _name.text.trim();
    final mate = rename.isEmpty
        ? profile
        : BlendProfile(
            name: rename,
            artists: profile.artists,
            tracks: profile.tracks,
          );
    if (_mates.any((m) => m.code == mate.code)) {
      setState(() => _error = '${mate.name} is already in this blend.');
      return;
    }
    setState(() {
      _mates.add(mate);
      _code.clear();
      _name.clear();
      _error = null;
    });
  }

  void _removeMate(int index) {
    if (index < 0 || index >= _mates.length) return;
    setState(() => _mates.removeAt(index));
  }

  void _playRecommendation(int index, List<BlendTrack> tracks) {
    if (index < 0 || index >= tracks.length) return;
    final all = [for (final t in tracks) _generated(t)];
    unawaited(
      playGenerated(
        ref,
        context,
        all[index],
        sourceLabel: 'Group Blend',
        queueAll: all,
        startIndex: index,
      ),
    );
  }

  GeneratedTrack _generated(BlendTrack track) => GeneratedTrack(
    name: track.title,
    artist: track.artist,
    artworkUrl: track.artworkUrl,
    videoId: track.videoId,
    durationSeconds: track.durationSeconds,
  );

  @override
  Widget build(BuildContext context) {
    final me = _me;
    final result = me == null
        ? const BlendResult(percent: 0, sharedArtists: [], recommendations: [])
        : computeBlend(me, _mates);
    final hasTaste =
        me != null && (me.artists.isNotEmpty || me.tracks.isNotEmpty);

    return LayoutBuilder(
      builder: (context, constraints) => ListView(
        padding: const EdgeInsets.fromLTRB(24, 20, 24, 24),
        children: [
          Center(
            child: SizedBox(
              width: math.min(constraints.maxWidth - 48, KatDensity.contentMax),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'BLEND LAB',
                    style: KatType.overline.copyWith(color: katAccent(context)),
                  ),
                  const Text('Group Blend', style: KatType.pageTitle),
                  const SizedBox(height: 4),
                  Text(
                    'Share a taste code and see how your listening overlaps. '
                    'Local and private — no account or server needed.',
                    style: KatType.body.copyWith(
                      color: katTextSecondary(context),
                    ),
                  ),
                  const SizedBox(height: 18),
                  _panel(
                    context,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            const Icon(KatIcons.friends, size: 18),
                            const SizedBox(width: 8),
                            const Text(
                              'Your taste code',
                              style: KatType.sectionTitle,
                            ),
                            const Spacer(),
                            KatGhostButton(
                              label: _copied ? 'Copied' : 'Copy code',
                              icon: FluentIcons.copy,
                              onPressed: hasTaste ? _copyCode : null,
                            ),
                          ],
                        ),
                        const SizedBox(height: 8),
                        Text(
                          hasTaste
                              ? 'Send this to a friend. It contains artist and '
                                    'track names only.'
                              : 'Play or like a few songs first to build your '
                                    'taste profile.',
                          style: KatType.meta.copyWith(
                            color: katTextSecondary(context),
                          ),
                        ),
                        const SizedBox(height: 10),
                        Container(
                          width: double.infinity,
                          padding: const EdgeInsets.all(10),
                          decoration: BoxDecoration(
                            color: katSurface(context),
                            borderRadius: BorderRadius.circular(8),
                            border: Border.all(color: katDivider(context)),
                          ),
                          child: Text(
                            me?.code ?? '',
                            maxLines: 4,
                            overflow: TextOverflow.ellipsis,
                            style: KatType.meta.copyWith(
                              fontFamily: 'monospace',
                              fontSize: 10.5,
                              color: katTextSecondary(context),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 12),
                  _panel(
                    context,
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        const Text('Add a friend', style: KatType.sectionTitle),
                        const SizedBox(height: 8),
                        TextBox(
                          controller: _code,
                          placeholder: 'Paste a KMB1… taste code',
                        ),
                        const SizedBox(height: 8),
                        Row(
                          children: [
                            Expanded(
                              child: TextBox(
                                controller: _name,
                                placeholder: 'Name (optional)',
                              ),
                            ),
                            const SizedBox(width: 8),
                            KatPrimaryButton(
                              label: 'Add to blend',
                              icon: FluentIcons.add,
                              onPressed: _addMate,
                            ),
                          ],
                        ),
                        if (_error != null) ...[
                          const SizedBox(height: 8),
                          Text(
                            _error!,
                            style: KatType.meta.copyWith(
                              color: katAccent(context),
                            ),
                          ),
                        ],
                      ],
                    ),
                  ),
                  if (_mates.isNotEmpty) ...[
                    const SizedBox(height: 12),
                    Wrap(
                      spacing: 8,
                      runSpacing: 8,
                      children: [
                        for (var i = 0; i < _mates.length; i++)
                          _MemberChip(
                            name: _mates[i].name,
                            onRemove: () => _removeMate(i),
                          ),
                      ],
                    ),
                  ],
                  const SizedBox(height: 16),
                  _MatchPanel(
                    result: result,
                    memberCount: _mates.length,
                    hasTaste: hasTaste,
                  ),
                  if (result.sharedArtists.isNotEmpty) ...[
                    const SizedBox(height: 16),
                    Text('Shared artists', style: KatType.sectionTitle),
                    const SizedBox(height: 8),
                    Wrap(
                      spacing: 6,
                      runSpacing: 6,
                      children: [
                        for (final artist in result.sharedArtists)
                          KatChip(label: artist, highlight: true),
                      ],
                    ),
                  ],
                  const SizedBox(height: 20),
                  Row(
                    children: [
                      const Text(
                        'Blend recommendations',
                        style: KatType.sectionTitle,
                      ),
                      const Spacer(),
                      Text(
                        '${result.recommendations.length} tracks',
                        style: KatType.meta.copyWith(
                          color: katTextSecondary(context),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  if (result.recommendations.isEmpty)
                    _panel(
                      context,
                      child: Text(
                        _mates.isEmpty
                            ? 'Add a friend to unlock a shared queue.'
                            : 'No new shared tracks yet — your libraries '
                                  'already overlap heavily.',
                        style: KatType.body.copyWith(
                          color: katTextSecondary(context),
                        ),
                      ),
                    )
                  else
                    KatDesktopTable<BlendTrack>(
                      items: result.recommendations,
                      keyOf: (t) => t.key,
                      titleOf: (t) => t.title,
                      subtitleOf: (t) => t.artist,
                      albumOf: (t) => '',
                      artworkOf: (t) => t.artworkUrl,
                      videoIdOf: (t) => t.videoId,
                      durationOf: (t) =>
                          formatDuration(Duration(seconds: t.durationSeconds)),
                      playableOf: (t) => playableFromGenerated(_generated(t)),
                      onPlay: (i) =>
                          _playRecommendation(i, result.recommendations),
                      playOnSingleTap: true,
                      showLike: false,
                      shrinkWrap: true,
                    ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _panel(BuildContext context, {required Widget child}) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: katSurfaceRaised(context),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: katDivider(context)),
      ),
      child: child,
    );
  }
}

class _MemberChip extends StatelessWidget {
  final String name;
  final VoidCallback onRemove;
  const _MemberChip({required this.name, required this.onRemove});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.only(left: 10, right: 4, top: 4, bottom: 4),
      decoration: BoxDecoration(
        color: katSurfaceRaised(context),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: katDivider(context)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(name, style: KatType.label),
          const SizedBox(width: 4),
          IconButton(
            icon: const Icon(FluentIcons.cancel, size: 12),
            onPressed: onRemove,
          ),
        ],
      ),
    );
  }
}

class _MatchPanel extends StatelessWidget {
  final BlendResult result;
  final int memberCount;
  final bool hasTaste;

  const _MatchPanel({
    required this.result,
    required this.memberCount,
    required this.hasTaste,
  });

  @override
  Widget build(BuildContext context) {
    final accent = katAccent(context);
    final percent = result.percent;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: katSurfaceRaised(context),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: katDivider(context)),
      ),
      child: Row(
        children: [
          SizedBox(
            width: 112,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '$percent%',
                  style: KatType.numeral.copyWith(fontSize: 42, color: accent),
                ),
                Text(
                  'match',
                  style: KatType.meta.copyWith(
                    color: katTextSecondary(context),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 18),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  memberCount == 0
                      ? 'Waiting for a friend'
                      : (hasTaste ? result.label : 'Build your taste first'),
                  style: KatType.sectionTitle,
                ),
                const SizedBox(height: 3),
                Text(
                  memberCount == 0
                      ? 'Your code is ready. Add one or more friends to see '
                            'the shared gauge.'
                      : '$memberCount ${memberCount == 1 ? 'friend' : 'friends'} '
                            'in this blend · ${result.sharedArtists.length} '
                            'shared artists',
                  style: KatType.meta.copyWith(
                    color: katTextSecondary(context),
                  ),
                ),
                const SizedBox(height: 10),
                LayoutBuilder(
                  builder: (context, constraints) => Container(
                    height: 8,
                    decoration: BoxDecoration(
                      color: katDivider(context),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Align(
                      alignment: Alignment.centerLeft,
                      child: Container(
                        width: constraints.maxWidth * (percent / 100),
                        decoration: BoxDecoration(
                          color: accent,
                          borderRadius: BorderRadius.circular(4),
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
