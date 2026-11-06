import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../features/sharing/share_links.dart';
import '../components/artwork.dart';
import '../components/buttons.dart';
import '../theme/haze.dart';
import '../theme/kat_icons.dart';
import '../theme/motion.dart';
import '../theme/tokens.dart';

/// Opens the full share surface for a track with a stable video id.
///
/// There are two intentional link shapes:
/// - the web link is safe to paste anywhere and provides the fallback page;
/// - the KatMusic link jumps straight into the installed app.
///
/// Keeping both visible makes sharing predictable on desktop instead of
/// hiding the app-specific handoff behind a context-menu-only action.
Future<void> showKatTrackShareDialog(
  BuildContext context, {
  required String videoId,
  required String title,
  required String artist,
  String album = '',
  String artworkUrl = '',
}) {
  final webLink = katTrackShareUrl(
    videoId: videoId,
    title: title,
    artist: artist,
    album: album,
    artworkUrl: artworkUrl,
  );
  final appLink = katTrackDeepLink(
    videoId: videoId,
    title: title,
    artist: artist,
    album: album,
    artworkUrl: artworkUrl,
  );
  if (webLink == null || appLink == null) return Future<void>.value();

  return showDialog<void>(
    context: context,
    builder: (_) => _KatTrackShareDialog(
      title: title,
      artist: artist,
      album: album,
      artworkUrl: artworkUrl,
      videoId: videoId,
      webLink: webLink,
      appLink: appLink,
    ),
  );
}

class _KatTrackShareDialog extends StatefulWidget {
  final String title;
  final String artist;
  final String album;
  final String artworkUrl;
  final String videoId;
  final String webLink;
  final String appLink;

  const _KatTrackShareDialog({
    required this.title,
    required this.artist,
    required this.album,
    required this.artworkUrl,
    required this.videoId,
    required this.webLink,
    required this.appLink,
  });

  @override
  State<_KatTrackShareDialog> createState() => _KatTrackShareDialogState();
}

class _KatTrackShareDialogState extends State<_KatTrackShareDialog> {
  String? _status;

  Future<void> _copy(String value, String label) async {
    await Clipboard.setData(ClipboardData(text: value));
    if (!mounted) return;
    setState(() => _status = '$label copied');
  }

  Future<void> _openWeb() async {
    final uri = Uri.tryParse(widget.webLink);
    if (uri == null) return;
    try {
      await launchUrl(uri, mode: LaunchMode.externalApplication);
    } catch (_) {}
  }

  @override
  Widget build(BuildContext context) {
    final dark = katIsDark(context);
    final accent = katAccent(context);
    final title = widget.title.trim().isEmpty ? 'Shared track' : widget.title;
    final artist = widget.artist.trim().isEmpty ? 'KatMusic' : widget.artist;

    return ContentDialog(
      title: Row(
        children: [
          Icon(KatIcons.share, size: 17, color: accent),
          const SizedBox(width: 8),
          const Text('Share track'),
        ],
      ),
      constraints: const BoxConstraints(maxWidth: 650),
      content: KatEntrance(
        local: true,
        rise: 8,
        child: KatLiquidGlass(
          borderRadius: KatRadius.menuRadius,
          base: katSurfaceRaised(context).withValues(alpha: dark ? 0.70 : 0.92),
          border: Border.all(color: katDivider(context)),
          child: Padding(
            padding: const EdgeInsets.all(14),
            child: LayoutBuilder(
              builder: (context, constraints) {
                final stacked = constraints.maxWidth < 500;
                final identity = _TrackIdentity(
                  title: title,
                  artist: artist,
                  album: widget.album,
                  artworkUrl: widget.artworkUrl,
                  videoId: widget.videoId,
                );
                final links = Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    _ShareLinkRow(
                      label: 'Web link',
                      caption: 'Best for messages and social apps',
                      icon: KatIcons.externalLink,
                      link: widget.webLink,
                      onCopy: () => _copy(widget.webLink, 'Web link'),
                    ),
                    const SizedBox(height: 8),
                    _ShareLinkRow(
                      label: 'Open directly in KatMusic',
                      caption: 'Use when the app is installed',
                      icon: KatIcons.link,
                      link: widget.appLink,
                      onCopy: () => _copy(widget.appLink, 'App link'),
                    ),
                  ],
                );

                if (stacked) {
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [identity, const SizedBox(height: 14), links],
                  );
                }
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    SizedBox(width: 220, child: identity),
                    const SizedBox(width: 14),
                    Expanded(child: links),
                  ],
                );
              },
            ),
          ),
        ),
      ),
      actions: [
        if (_status != null)
          Padding(
            padding: const EdgeInsets.only(right: 4),
            child: Text(
              _status!,
              style: KatType.meta.copyWith(color: accent),
            ),
          ),
        KatGhostButton(
          label: 'Copy web link',
          icon: KatIcons.copy,
          onPressed: () => _copy(widget.webLink, 'Web link'),
        ),
        KatPrimaryButton(
          label: 'Open share page',
          icon: KatIcons.externalLink,
          onPressed: _openWeb,
        ),
      ],
    );
  }
}

class _TrackIdentity extends StatelessWidget {
  final String title;
  final String artist;
  final String album;
  final String artworkUrl;
  final String videoId;

  const _TrackIdentity({
    required this.title,
    required this.artist,
    required this.album,
    required this.artworkUrl,
    required this.videoId,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        KatArtwork(
          url: artworkUrl,
          videoId: videoId,
          size: 74,
          radius: KatRadius.artwork,
          title: title,
          artist: artist,
          label: title,
        ),
        const SizedBox(width: 10),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'SHARED TRACK',
                style: KatType.overline.copyWith(color: katAccent(context)),
              ),
              const SizedBox(height: 4),
              Text(
                title,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: KatType.trackTitle.copyWith(fontSize: 14),
              ),
              const SizedBox(height: 3),
              Text(
                artist,
                maxLines: 2,
                overflow: TextOverflow.ellipsis,
                style: KatType.meta.copyWith(color: katTextSecondary(context)),
              ),
              if (album.trim().isNotEmpty)
                Text(
                  album,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: KatType.meta.copyWith(color: katTextTertiary(context)),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _ShareLinkRow extends StatelessWidget {
  final String label;
  final String caption;
  final IconData icon;
  final String link;
  final VoidCallback onCopy;

  const _ShareLinkRow({
    required this.label,
    required this.caption,
    required this.icon,
    required this.link,
    required this.onCopy,
  });

  @override
  Widget build(BuildContext context) {
    final dark = katIsDark(context);
    return AnimatedContainer(
      duration: katMotion(context, KatMotion.fast),
      padding: const EdgeInsets.fromLTRB(10, 9, 6, 8),
      decoration: BoxDecoration(
        color: (dark ? Colors.white : Colors.black).withValues(alpha: 0.045),
        borderRadius: KatRadius.controlsRadius,
        border: Border.all(color: katDivider(context)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, size: 14, color: katAccent(context)),
              const SizedBox(width: 7),
              Expanded(
                child: Text(label, style: KatType.label),
              ),
              KatGhostButton(label: 'Copy', onPressed: onCopy),
            ],
          ),
          const SizedBox(height: 2),
          Padding(
            padding: const EdgeInsets.only(left: 21),
            child: Text(
              caption,
              style: KatType.meta.copyWith(color: katTextTertiary(context)),
            ),
          ),
          const SizedBox(height: 5),
          Padding(
            padding: const EdgeInsets.only(left: 21),
            child: SelectableText(
              link,
              maxLines: 2,
              style: KatType.meta.copyWith(
                color: katTextSecondary(context),
                fontSize: 11,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
