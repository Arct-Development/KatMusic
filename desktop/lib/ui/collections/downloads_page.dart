import 'package:file_picker/file_picker.dart';
import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:super_sliver_list/super_sliver_list.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../core/audio/stream_models.dart';
import '../../core/storage/prefs.dart';
import '../../features/downloads/download_manager.dart';
import '../../features/library/playlists.dart';
import '../../features/player/playback_service.dart';
import '../components/artwork.dart';
import '../components/buttons.dart';
import '../components/hero.dart';
import '../components/menus.dart';
import '../components/states.dart';
import '../navigation/kat_nav.dart';
import '../theme/motion.dart';
import '../theme/tokens.dart';
import '../theme/kat_icons.dart';

/// Downloads: Offline hero + 40px-artwork rows
/// (art · title/artist · progress or quality chip · play · delete).
class KatDownloadsPage extends ConsumerStatefulWidget {
  const KatDownloadsPage({super.key});

  @override
  ConsumerState<KatDownloadsPage> createState() => _KatDownloadsPageState();
}

enum _DownloadSort { recent, title, artist }

class _KatDownloadsPageState extends ConsumerState<KatDownloadsPage> {
  String _query = '';
  _DownloadSort _sort = _DownloadSort.recent;

  String _artworkFor(WidgetRef ref, DownloadEntry e) {
    if (e.artworkUrl.isNotEmpty) return e.artworkUrl;
    final playlists = ref.watch(playlistRepositoryProvider);
    for (final p in playlists) {
      for (final t in p.tracks) {
        if (t.name.toLowerCase() == e.title.toLowerCase() &&
            t.artist.toLowerCase() == e.artist.toLowerCase() &&
            t.artworkUrl.isNotEmpty) {
          return t.artworkUrl;
        }
      }
    }
    return '';
  }

  @override
  Widget build(BuildContext context) {
    final all = ref.watch(downloadManagerProvider);
    final entries = <DownloadEntry>[...all];
    final q = _query.trim().toLowerCase();
    if (q.isNotEmpty) {
      entries.removeWhere(
        (e) =>
            !e.title.toLowerCase().contains(q) &&
            !e.artist.toLowerCase().contains(q),
      );
    }
    switch (_sort) {
      case _DownloadSort.recent:
        break; // manager keeps newest first
      case _DownloadSort.title:
        entries.sort(
          (a, b) => a.title.toLowerCase().compareTo(b.title.toLowerCase()),
        );
      case _DownloadSort.artist:
        entries.sort((a, b) {
          final c = a.artist.toLowerCase().compareTo(b.artist.toLowerCase());
          return c != 0
              ? c
              : a.title.toLowerCase().compareTo(b.title.toLowerCase());
        });
    }
    final done = all.where((e) => e.status == DownloadStatus.done);
    final failed = all.where((e) => e.status == DownloadStatus.error).toList();
    // Hero cover: the most recently downloaded track that has art.
    var heroArt = '';
    for (final e in all) {
      if (e.status != DownloadStatus.done) continue;
      final a = _artworkFor(ref, e);
      if (a.isNotEmpty) {
        heroArt = a;
        break;
      }
    }
    return KatEntranceGroup(
      child: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(24, 20, 24, 0),
              child: ConstrainedBox(
                constraints: const BoxConstraints(
                  maxWidth: KatDensity.contentMax,
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    KatEntrance(
                      rise: 10,
                      child: KatCollectionHero(
                        overline: 'Offline',
                        title: 'Downloads',
                        meta:
                            '${done.length} ${done.length == 1 ? 'track' : 'tracks'} available offline · ${all.length} total',
                        artworkUrl: heroArt,
                        fallbackIcon: FluentIcons.download,
                        artworkSize: 120,
                      ),
                    ),
                    const SizedBox(height: 12),
                    const _LocationRow(),
                    const SizedBox(height: 12),
                    Row(
                      children: [
                        Expanded(
                          child: TextBox(
                            placeholder: 'Search downloads',
                            prefix: const Padding(
                              padding: EdgeInsets.only(left: 10),
                              child: Icon(KatIcons.search, size: 14),
                            ),
                            onChanged: (v) => setState(() => _query = v),
                          ),
                        ),
                        const SizedBox(width: 10),
                        ComboBox<_DownloadSort>(
                          value: _sort,
                          items: const [
                            ComboBoxItem(
                              value: _DownloadSort.recent,
                              child: Text('Recently downloaded'),
                            ),
                            ComboBoxItem(
                              value: _DownloadSort.title,
                              child: Text('Title A–Z'),
                            ),
                            ComboBoxItem(
                              value: _DownloadSort.artist,
                              child: Text('Artist A–Z'),
                            ),
                          ],
                          onChanged: (v) {
                            if (v != null) setState(() => _sort = v);
                          },
                        ),
                      ],
                    ),
                    const SizedBox(height: 12),
                    // Local InfoBar for failures — never breaks the list.
                    if (failed.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.only(bottom: 12),
                        child: InfoBar(
                          severity: InfoBarSeverity.warning,
                          title: Text(
                            '${failed.length} download${failed.length == 1 ? '' : 's'} failed',
                          ),
                          content: Text(
                            failed.first.error ?? 'Check connection and retry.',
                          ),
                          action: KatButton(
                            onPressed: () {
                              final m = ref.read(
                                downloadManagerProvider.notifier,
                              );
                              for (final f in failed) {
                                m.downloadTrack(
                                  title: f.title,
                                  artist: f.artist,
                                );
                              }
                            },
                            child: const Text('Retry all'),
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
          if (all.isEmpty)
            const SliverToBoxAdapter(
              child: KatEmpty(
                icon: FluentIcons.download,
                title: 'No downloads yet',
                subtitle:
                    'Download any track to keep it offline in full quality.',
              ),
            )
          else if (entries.isEmpty)
            const SliverToBoxAdapter(
              child: KatEmpty(
                icon: KatIcons.search,
                title: 'No matches',
                subtitle: 'No downloads match your search.',
              ),
            )
          else
            SuperSliverList.builder(
              itemCount: entries.length,
              itemBuilder: (context, i) {
                final e = entries[i];
                final art = _artworkFor(ref, e);
                return KatEntrance(
                  index: i,
                  rise: 10,
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: _DownloadRow(entry: e, artwork: art),
                  ),
                );
              },
            ),
          const SliverToBoxAdapter(child: SizedBox(height: 24)),
        ],
      ),
    );
  }
}

/// Download folder row: current location + change / reset.
/// Stateful so the path refreshes right after picking (prefs is not
/// a listenable provider).
class _LocationRow extends ConsumerStatefulWidget {
  const _LocationRow();
  @override
  ConsumerState<_LocationRow> createState() => _LocationRowState();
}

class _LocationRowState extends ConsumerState<_LocationRow> {
  Future<void> _pick() async {
    try {
      final path = await FilePicker.getDirectoryPath(
        dialogTitle: 'Choose download folder',
      );
      if (path == null || path.trim().isEmpty) return;
      await ref.read(prefsProvider).setDownloadDir(path);
      if (mounted) setState(() {});
    } catch (_) {}
  }

  Future<void> _reset() async {
    try {
      await ref.read(prefsProvider).resetDownloadDir();
      if (mounted) setState(() {});
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final custom = ref.read(prefsProvider).downloadDir;
    return Row(
      children: [
        const Icon(FluentIcons.open_folder_horizontal, size: 15),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            custom.isEmpty ? 'Saving to default location' : custom,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: KatType.meta.copyWith(color: katTextSecondary(context)),
          ),
        ),
        KatButton(onPressed: _pick, child: const Text('Change folder')),
        if (custom.isNotEmpty) ...[
          const SizedBox(width: 8),
          KatButton(onPressed: _reset, child: const Text('Reset')),
        ],
      ],
    );
  }
}

/// Interactive download row: hover wash, double-click to play,
/// right-click menu (play / go to artist / delete).
class _DownloadRow extends ConsumerStatefulWidget {
  final DownloadEntry entry;
  final String artwork;
  const _DownloadRow({required this.entry, required this.artwork});

  @override
  ConsumerState<_DownloadRow> createState() => _DownloadRowState();
}

class _DownloadRowState extends ConsumerState<_DownloadRow> {
  bool _hover = false;

  void _play() {
    final e = widget.entry;
    if (e.status != DownloadStatus.done) return;
    ref
        .read(playbackServiceProvider.notifier)
        .play(
          PlayableTrack(
            title: e.title,
            artist: e.artist,
            artworkUrl: widget.artwork,
            playbackUrl: e.filePath ?? '',
          ),
          sourceLabel: 'Downloads',
          startRadio: ref.read(prefsProvider).autoplaySimilar,
        );
  }

  @override
  Widget build(BuildContext context) {
    final e = widget.entry;
    final art = widget.artwork;
    final manager = ref.read(downloadManagerProvider.notifier);
    final downloading = e.status == DownloadStatus.downloading;
    final queued = e.status == DownloadStatus.queued;
    return KatContextMenu(
      items: () => [
        if (e.status == DownloadStatus.done)
          KatMenuAction(
            leading: const Icon(FluentIcons.play, size: 13),
            label: 'Play offline file',
            onPressed: _play,
          ),
        KatMenuAction(
          leading: const Icon(FluentIcons.contact, size: 13),
          label: 'Go to artist',
          onPressed: () =>
              katOpen(context, '/artist/${Uri.encodeComponent(e.artist)}'),
        ),
        const KatMenuSeparator(),
        KatMenuAction(
          leading: const Icon(FluentIcons.delete, size: 13),
          label: 'Delete download',
          onPressed: () => manager.delete(e.key),
        ),
      ],
      child: MouseRegion(
        cursor: e.status == DownloadStatus.done
            ? SystemMouseCursors.click
            : SystemMouseCursors.basic,
        onEnter: (_) => setState(() => _hover = true),
        onExit: (_) => setState(() => _hover = false),
        child: GestureDetector(
          onDoubleTap: _play,
          child: AnimatedContainer(
            duration: katMotion(context, KatMotion.fast),
            curve: Curves.easeOutCubic,
            height: 54,
            // Inset the content, not the fill: the 6px hover plate keeps its
            // full width while the artwork clears its rounded edge (same
            // 8px row inset as the track rows).
            padding: const EdgeInsetsDirectional.only(start: 8),
            decoration: BoxDecoration(
              color: _hover ? katHoverWash(context) : Colors.transparent,
              borderRadius: BorderRadius.circular(KatRadius.tiny),
            ),
            child: Row(
              children: [
                Stack(
                  children: [
                    KatArtwork(
                      url: art,
                      size: 40,
                      radius: KatRadius.artwork,
                      label: e.title,
                      title: e.title,
                      artist: e.artist,
                    ),
                    if (downloading || queued)
                      Positioned.fill(
                        child: Container(
                          decoration: BoxDecoration(
                            color: Colors.black.withValues(alpha: 0.45),
                            borderRadius: KatRadius.artworkRadius,
                          ),
                          child: const Center(
                            child: SizedBox(
                              width: 16,
                              height: 16,
                              child: ProgressRing(strokeWidth: 2),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text(
                        e.title,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: KatType.trackTitle,
                      ),
                      if (downloading)
                        Padding(
                          padding: const EdgeInsets.only(top: 5),
                          child: ProgressBar(
                            value: e.progress > 0
                                ? (e.progress * 100).clamp(0, 100)
                                : null,
                          ),
                        )
                      else
                        Text(
                          e.status == DownloadStatus.error
                              ? (e.error ??
                                    'Download failed — retry from the track menu')
                              : queued
                              ? 'Waiting…'
                              : e.artist,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: KatType.meta.copyWith(
                            color: katTextSecondary(context),
                          ),
                        ),
                    ],
                  ),
                ),
                if (!downloading && e.badge.isNotEmpty) ...[
                  KatChip(label: e.badge),
                  const SizedBox(width: 6),
                ],
                // Untagged fallback files stay visible (never silent).
                if (!downloading &&
                    e.status == DownloadStatus.done &&
                    e.tagNote.startsWith('raw')) ...[
                  const KatChip(label: 'NO TAGS', highlight: true),
                  const SizedBox(width: 6),
                ],
                // Status text: progress % / size / status — tabular.
                if (downloading)
                  Text(
                    e.progress > 0
                        ? '${(e.progress * 100).round()}%'
                        : 'Starting…',
                    style: KatType.meta.copyWith(
                      fontFeatures: const [FontFeature.tabularFigures()],
                      color: katTextTertiary(context),
                    ),
                  )
                else if (e.status == DownloadStatus.error)
                  const Icon(
                    FluentIcons.error,
                    size: 14,
                    color: Color(0xFFE0506A),
                  ),
                if (e.status == DownloadStatus.done)
                  KatIconButton(
                    tooltip: 'Play offline file',
                    icon: const Icon(FluentIcons.play, size: 14),
                    onPressed: _play,
                  ),
                if (e.status == DownloadStatus.error)
                  KatIconButton(
                    tooltip: 'Retry download',
                    icon: const Icon(FluentIcons.refresh, size: 14),
                    onPressed: () =>
                        manager.downloadTrack(title: e.title, artist: e.artist),
                  ),
                if (e.status == DownloadStatus.done && e.filePath != null)
                  KatIconButton(
                    tooltip: 'Open file location',
                    icon: const Icon(
                      FluentIcons.open_folder_horizontal,
                      size: 14,
                    ),
                    onPressed: () => _openLocation(context, e.filePath!),
                  ),
                KatIconButton(
                  tooltip: 'Delete download',
                  icon: const Icon(FluentIcons.delete, size: 14),
                  onPressed: () => manager.delete(e.key),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _openLocation(BuildContext context, String filePath) async {
    // Best-effort: reveal the file in Explorer/Finder/Files.
    try {
      final uri = Uri.file(filePath);
      // url_launcher handles file:// on desktop; failure is silent.
      final ok = await canLaunchUrl(uri);
      if (ok) await launchUrl(uri);
    } catch (_) {}
  }
}
