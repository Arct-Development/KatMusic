import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;

import 'package:cached_network_image/cached_network_image.dart';
import 'package:file_picker/file_picker.dart';
import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter/rendering.dart';
import 'package:palette_generator/palette_generator.dart';

import '../../core/audio/stream_models.dart';

/// Logical card size. [pixelRatio] 2 writes a 1080×1350 PNG.
const double _kCardWidth = 540;
const double _kCardHeight = 675;
const double _kCardPixelRatio = 2;

/// Background, ink and one quiet accent. No decorative gradients: the
/// artwork and the words are the only colour the card needs.
class _CardStyle {
  final String label;
  final Color background;
  final Color text;
  final Color subtext;
  final Color rule;
  final Color accent;

  const _CardStyle(
    this.label,
    this.background, {
    this.text = const Color(0xFFF5F2EA),
    this.subtext = const Color(0xFFB8B6AE),
    this.rule = const Color(0x33FFFFFF),
    this.accent = const Color(0xFFB7C9BE),
  });
}

const _presetStyles = <_CardStyle>[
  _CardStyle('Ink', Color(0xFF15171B)),
  _CardStyle(
    'Paper',
    Color(0xFFF2EDE2),
    text: Color(0xFF17191C),
    subtext: Color(0xFF62615C),
    rule: Color(0xFFD3CABB),
    accent: Color(0xFF1F4B3B),
  ),
  _CardStyle('Forest', Color(0xFF1D332B), accent: Color(0xFFB9D2C2)),
  _CardStyle('Wine', Color(0xFF321E24), accent: Color(0xFFD9B8BC)),
  _CardStyle('Slate', Color(0xFF232B34), accent: Color(0xFFB7C9D8)),
  _CardStyle(
    'Sand',
    Color(0xFFD8C9B2),
    text: Color(0xFF1B1A17),
    subtext: Color(0xFF5E594F),
    rule: Color(0xFFB9AB95),
    accent: Color(0xFF3E4A3B),
  ),
];

/// Renders artwork, title, artist, and [lines] to a 1080×1350 PNG. A dialog
/// shows the card first so the palette can be picked, then the PNG goes
/// through the same [FilePicker.saveFile] call playlists use.
Future<void> exportLyricsShareCard(
  BuildContext context, {
  required PlayableTrack track,
  required List<String> lines,
}) async {
  final art = await _loadArtwork(context, track.artworkUrl);
  if (!context.mounted) return;
  final artStyle = await _artworkStyle(art);
  if (!context.mounted) return;
  final styles = [?artStyle, ..._presetStyles];

  final style = await showDialog<_CardStyle>(
    context: context,
    builder: (dialogContext) {
      var selected = styles.first;
      return StatefulBuilder(
        builder: (context, setState) => ContentDialog(
          constraints: const BoxConstraints(maxWidth: 520),
          title: const Text('Lyrics card'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ClipRRect(
                borderRadius: BorderRadius.circular(4),
                child: FittedBox(
                  child: _LyricsShareCard(
                    track: track,
                    lines: lines,
                    artwork: art,
                    style: selected,
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final s in styles)
                    Tooltip(
                      message: s.label,
                      child: GestureDetector(
                        onTap: () => setState(() => selected = s),
                        child: Container(
                          width: 28,
                          height: 28,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            color: s.background,
                            border: Border.all(
                              color: identical(s, selected)
                                  ? FluentTheme.of(context).accentColor
                                  : const Color(0x33808080),
                              width: identical(s, selected) ? 2.5 : 1,
                            ),
                          ),
                        ),
                      ),
                    ),
                ],
              ),
            ],
          ),
          actions: [
            Button(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const Text('Cancel'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(selected),
              child: const Text('Save…'),
            ),
          ],
        ),
      );
    },
  );
  if (!context.mounted || style == null) return;

  final bytes = await _renderCard(
    context,
    track: track,
    lines: lines,
    art: art,
    style: style,
  );
  if (!context.mounted) return;
  if (bytes == null) {
    _note(context, 'Couldn’t render the lyrics card', error: true);
    return;
  }
  final safe = track.title.replaceAll(RegExp(r'[\\/:*?"<>|]'), '_').trim();
  final base = safe.isEmpty ? 'lyrics' : safe;
  final saved = await FilePicker.saveFile(
    dialogTitle: 'Save lyrics card',
    fileName: '$base.png',
    bytes: bytes,
    mimeType: 'image/png',
    type: FileType.custom,
    allowedExtensions: const ['png'],
  );
  if (!context.mounted || saved == null) return;
  _note(context, 'Lyrics card saved');
}

void _note(BuildContext context, String message, {bool error = false}) {
  displayInfoBar(
    context,
    builder: (context, close) => InfoBar(
      title: Text(message),
      severity: error ? InfoBarSeverity.error : InfoBarSeverity.success,
      onClose: close,
    ),
  );
}

Future<ImageProvider<Object>?> _loadArtwork(
  BuildContext context,
  String url,
) async {
  ImageProvider<Object>? art;
  if (url.startsWith('http')) {
    art = CachedNetworkImageProvider(url);
  } else if (url.startsWith('file:')) {
    final file = File(Uri.parse(url).toFilePath());
    if (file.existsSync()) art = FileImage(file);
  } else if (url.isNotEmpty && File(url).existsSync()) {
    art = FileImage(File(url));
  }
  if (art == null) return null;
  try {
    await precacheImage(art, context);
    return art;
  } catch (_) {
    return null;
  }
}

/// A muted print tone pulled from the cover. Text stays white, and the
/// accent is used only for the small masthead rule.
Future<_CardStyle?> _artworkStyle(ImageProvider<Object>? art) async {
  if (art == null) return null;
  try {
    final palette = await PaletteGenerator.fromImageProvider(
      art,
      size: const Size(96, 96),
      maximumColorCount: 12,
    );
    final base =
        (palette.darkMutedColor ?? palette.mutedColor ?? palette.dominantColor)
            ?.color;
    if (base == null) return null;
    final hsl = HSLColor.fromColor(base);
    final background = hsl
        .withLightness(hsl.lightness.clamp(0.12, 0.22))
        .withSaturation(hsl.saturation.clamp(0.12, 0.42))
        .toColor();
    final accent = hsl
        .withLightness(0.68)
        .withSaturation(hsl.saturation.clamp(0.25, 0.55))
        .toColor();
    return _CardStyle(
      'Cover',
      background,
      accent: accent,
      rule: accent.withValues(alpha: 0.22),
    );
  } catch (_) {
    return null;
  }
}

Future<Uint8List?> _renderCard(
  BuildContext context, {
  required PlayableTrack track,
  required List<String> lines,
  required ImageProvider<Object>? art,
  required _CardStyle style,
}) async {
  final key = GlobalKey();
  final overlay = Overlay.of(context);
  final entry = OverlayEntry(
    builder: (context) => IgnorePointer(
      child: Opacity(
        opacity: 0.01,
        child: Align(
          alignment: Alignment.topLeft,
          child: RepaintBoundary(
            key: key,
            child: _LyricsShareCard(
              track: track,
              lines: lines,
              artwork: art,
              style: style,
            ),
          ),
        ),
      ),
    ),
  );
  overlay.insert(entry);
  ui.Image? image;
  try {
    for (var i = 0; i < 8; i++) {
      await WidgetsBinding.instance.endOfFrame;
      final boundary =
          key.currentContext?.findRenderObject() as RenderRepaintBoundary?;
      if (boundary == null) continue;
      try {
        image = await boundary.toImage(pixelRatio: _kCardPixelRatio);
        break;
      } catch (_) {}
    }
    if (image == null) return null;
    final data = await image.toByteData(format: ui.ImageByteFormat.png);
    return data?.buffer.asUint8List();
  } finally {
    image?.dispose();
    entry.remove();
  }
}

class _LyricsShareCard extends StatelessWidget {
  final PlayableTrack track;
  final List<String> lines;
  final ImageProvider<Object>? artwork;
  final _CardStyle style;

  const _LyricsShareCard({
    required this.track,
    required this.lines,
    required this.artwork,
    required this.style,
  });

  @override
  Widget build(BuildContext context) {
    final shown = lines.where((l) => l.trim().isNotEmpty).take(7).toList();
    return SizedBox(
      width: _kCardWidth,
      height: _kCardHeight,
      child: ColoredBox(
        color: style.background,
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(28, 26, 24, 22),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Text(
                          'KATMUSIC',
                          style: TextStyle(
                            color: style.accent,
                            fontSize: 9.5,
                            fontWeight: FontWeight.w700,
                            letterSpacing: 2.1,
                          ),
                        ),
                        const Spacer(),
                        Text(
                          'LYRICS',
                          style: TextStyle(
                            color: style.subtext,
                            fontSize: 9,
                            fontWeight: FontWeight.w600,
                            letterSpacing: 1.5,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 22),
                    Text(
                      track.title,
                      maxLines: 3,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: style.text,
                        fontSize: 27,
                        fontWeight: FontWeight.w700,
                        height: 1.08,
                        letterSpacing: -0.5,
                      ),
                    ),
                    const SizedBox(height: 7),
                    Text(
                      track.artist.toUpperCase(),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: style.subtext,
                        fontSize: 11.5,
                        fontWeight: FontWeight.w600,
                        height: 1.3,
                        letterSpacing: 0.9,
                      ),
                    ),
                    const SizedBox(height: 16),
                    Container(height: 1, color: style.rule),
                    const SizedBox(height: 16),
                    Expanded(
                      child: ClipRect(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            for (final line in shown)
                              Padding(
                                padding: const EdgeInsets.only(bottom: 8),
                                child: Text(
                                  line,
                                  maxLines: 2,
                                  overflow: TextOverflow.ellipsis,
                                  style: TextStyle(
                                    color: style.text,
                                    fontSize: 16.5,
                                    height: 1.28,
                                    fontWeight: FontWeight.w500,
                                    letterSpacing: -0.1,
                                  ),
                                ),
                              ),
                          ],
                        ),
                      ),
                    ),
                    Container(height: 1, color: style.rule),
                    const SizedBox(height: 9),
                    Row(
                      children: [
                        Text(
                          'KATMUSIC.PAGES.DEV',
                          style: TextStyle(
                            color: style.subtext,
                            fontSize: 8.5,
                            fontWeight: FontWeight.w600,
                            letterSpacing: 1.3,
                          ),
                        ),
                        const Spacer(),
                        Text(
                          'SHARED CARD',
                          style: TextStyle(
                            color: style.accent,
                            fontSize: 8.5,
                            fontWeight: FontWeight.w700,
                            letterSpacing: 1.3,
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),
            SizedBox(
              width: 186,
              child: DecoratedBox(
                decoration: BoxDecoration(
                  border: Border(left: BorderSide(color: style.rule)),
                  color: style.rule,
                ),
                child: artwork == null
                    ? Center(
                        child: Icon(
                          FluentIcons.music_note,
                          color: style.subtext,
                          size: 34,
                        ),
                      )
                    : Image(
                        image: artwork!,
                        fit: BoxFit.cover,
                        errorBuilder: (_, _, _) => Center(
                          child: Icon(
                            FluentIcons.music_note,
                            color: style.subtext,
                            size: 34,
                          ),
                        ),
                      ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
