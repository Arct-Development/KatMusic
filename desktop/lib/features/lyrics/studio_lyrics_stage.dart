import 'dart:math' as math;

import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../core/audio/stream_models.dart';
import '../../core/storage/prefs.dart';
import '../../ui/components/ambient.dart';
import '../../ui/theme/wave_icons.dart';
import '../settings/accent.dart' show accentFromPalette;
import 'lyrics_models.dart';
import 'lyrics_studio_styles.dart';

/// Full-screen, one-line-at-a-time lyric stage for the ported Studio styles.
///
/// This is the LyricalStudio / theaestheticslyrics presentation: unlike the
/// scrolling lyrics rail, the stage gives one line the whole viewport, uses
/// the current cover's palette, and keeps a slow ambient drift underneath.
/// The Graph style gets a camera that follows the song down a continuous
/// vine instead of re-laying out only the active line.
/// What the stage should render right now. The lyrics rail keeps this
/// updated while the stage is open, so a track change swaps the song
/// instead of leaving the previous song's lines on screen.
class StudioStageFeed {
  final PlayableTrack track;
  final LyricsResult result;

  const StudioStageFeed(this.track, this.result);
}

class StudioLyricsStage extends ConsumerStatefulWidget {
  final PlayableTrack track;
  final LyricsResult result;
  final ValueListenable<StudioStageFeed?>? feed;
  final ValueListenable<int> positionListenable;
  final ValueListenable<int> activeLineListenable;
  final String initialStyle;
  final bool showBackdrop;

  const StudioLyricsStage({
    super.key,
    required this.track,
    required this.result,
    this.feed,
    required this.positionListenable,
    required this.activeLineListenable,
    required this.initialStyle,
    this.showBackdrop = true,
  });

  @override
  ConsumerState<StudioLyricsStage> createState() => _StudioLyricsStageState();
}

class _StudioLyricsStageState extends ConsumerState<StudioLyricsStage>
    with SingleTickerProviderStateMixin {
  late final AnimationController _drift;

  @override
  void initState() {
    super.initState();
    _drift = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 31),
    )..repeat();
  }

  @override
  void dispose() {
    _drift.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final feed = widget.feed;
    if (feed == null) return _buildStage(context, widget.track, widget.result);
    return ValueListenableBuilder<StudioStageFeed?>(
      valueListenable: feed,
      builder: (context, live, _) => _buildStage(
        context,
        live?.track ?? widget.track,
        live?.result ?? widget.result,
      ),
    );
  }

  Widget _buildStage(
    BuildContext context,
    PlayableTrack liveTrack,
    LyricsResult result,
  ) {
    final watched = ref.watch(prefsProvider.select((p) => p.lyricsAnimation));
    final style = isStudioLyricsAnimation(watched)
        ? watched
        : widget.initialStyle;

    final palette =
        ref.watch(artworkPaletteProvider(liveTrack.artworkUrl)).valueOrNull ??
        ArtworkPalette.fallback;
    final accent = accentFromPalette(palette) ?? palette.vibrant;
    final primary = Color.lerp(const Color(0xFFF6F4EF), palette.cream, 0.22)!;
    final secondary = primary.withValues(alpha: 0.56);
    final backdrop = Color.lerp(palette.shadow, const Color(0xFF080B12), 0.58)!;
    final reduceMotion =
        MediaQuery.maybeOf(context)?.disableAnimations ?? false;
    final glowMode = ref.watch(prefsProvider.select((p) => p.studioGlowMode));
    final glowThickness = ref.watch(
      prefsProvider.select((p) => p.studioGlowThickness),
    );
    final glowIntensity = ref.watch(
      prefsProvider.select((p) => p.studioGlowIntensity),
    );

    final paletteColors = <Color>[
      accent,
      palette.lightVibrant,
      palette.vibrant,
      accent,
    ];

    return CallbackShortcuts(
      bindings: <ShortcutActivator, VoidCallback>{
        const SingleActivator(LogicalKeyboardKey.escape): () =>
            Navigator.of(context).maybePop(),
      },
      child: Focus(
        autofocus: true,
        child: ColoredBox(
          color: backdrop,
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (widget.showBackdrop)
                Positioned.fill(
                  child: RepaintBoundary(
                    child: WaveAmbientMesh(
                      artworkUrl: liveTrack.artworkUrl,
                      isFullBleed: true,
                      cinematic: true,
                      apple: true,
                    ),
                  ),
                ),
              Positioned.fill(
                child: DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      colors: [
                        Colors.black.withValues(alpha: 0.16),
                        Colors.transparent,
                        backdrop.withValues(alpha: 0.62),
                      ],
                      stops: const [0, 0.42, 1],
                    ),
                  ),
                ),
              ),
              if (glowMode != 'off')
                Positioned.fill(
                  child: IgnorePointer(
                    child: _StageEdgeGlow(
                      top: accent,
                      bottom: palette.lightVibrant,
                      mode: glowMode,
                      thickness: glowThickness,
                      intensity: glowIntensity,
                      positionListenable: widget.positionListenable,
                      lines: result.lines,
                    ),
                  ),
                ),
              SafeArea(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(28, 20, 28, 22),
                  child: Column(
                    children: [
                      _StageTopBar(
                        track: liveTrack,
                        styleLabel:
                            Prefs.lyricsAnimationOptions[style] ?? 'Studio',
                        onClose: () => Navigator.of(context).maybePop(),
                      ),
                      const SizedBox(height: 8),
                      Expanded(
                        child: ValueListenableBuilder<int>(
                          valueListenable: widget.activeLineListenable,
                          builder: (context, activeIndex, _) {
                            return _StageLyrics(
                              result: result,
                              loading: false,
                              activeIndex: activeIndex,
                              style: style,
                              positionListenable: widget.positionListenable,
                              accent: paletteColors,
                              primary: primary,
                              secondary: secondary,
                              reduceMotion: reduceMotion,
                              drift: _drift,
                            );
                          },
                        ),
                      ),
                      const SizedBox(height: 12),
                      _StageFooter(
                        result: result,
                        activeLineListenable: widget.activeLineListenable,
                        primary: primary,
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// Lumn-style edge light: the album's two strongest colours wrap the screen
/// and the glow swells with the sung words, drifts like a breath, or stays
/// steady. Painted as gradient bands, so there is no per-frame blur.
class _StageEdgeGlow extends StatelessWidget {
  final Color top;
  final Color bottom;
  final String mode;
  final double thickness;
  final double intensity;
  final ValueListenable<int> positionListenable;
  final List<LyricLine> lines;

  const _StageEdgeGlow({
    required this.top,
    required this.bottom,
    required this.mode,
    required this.thickness,
    required this.intensity,
    required this.positionListenable,
    required this.lines,
  });

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<int>(
      valueListenable: positionListenable,
      builder: (context, positionMs, _) {
        final seconds = positionMs / 1000;
        final vocal = _vocalPulse(lines, positionMs);
        final pulse = switch (mode) {
          'breath' => 0.55 + 0.45 * math.sin(seconds * 0.9),
          'steady' => 1.0,
          _ => 0.35 + 0.65 * vocal,
        };
        return CustomPaint(
          painter: _EdgeGlowPainter(
            top: top,
            bottom: bottom,
            thickness: thickness,
            intensity: intensity,
            pulse: pulse.clamp(0.0, 1.0),
            drift: seconds,
          ),
        );
      },
    );
  }
}

/// Glow strength for the word being sung right now: it rises into the word
/// and falls away before the next one, so the light rides the vocal.
double _vocalPulse(List<LyricLine> lines, int positionMs) {
  if (lines.isEmpty) return 0.3 + 0.15 * math.sin(positionMs / 900);
  var active = -1;
  for (var i = 0; i < lines.length; i++) {
    if (lines[i].timeMs <= positionMs) {
      active = i;
    } else {
      break;
    }
  }
  if (active < 0) return 0.3;
  final line = lines[active];
  final syllables = line.syllables;
  if (syllables.isEmpty) {
    final duration = math.max(600, line.durationMs);
    final progress = ((positionMs - line.timeMs) / duration).clamp(0.0, 1.0);
    return math.sin(math.pi * progress) * 0.8;
  }
  for (final syllable in syllables) {
    final duration = math.max(120, syllable.durationMs);
    if (positionMs >= syllable.timeMs &&
        positionMs <= syllable.timeMs + duration) {
      final progress = ((positionMs - syllable.timeMs) / duration).clamp(
        0.0,
        1.0,
      );
      return math.sin(math.pi * progress);
    }
  }
  return 0.35;
}

class _EdgeGlowPainter extends CustomPainter {
  final Color top;
  final Color bottom;
  final double thickness;
  final double intensity;
  final double pulse;
  final double drift;

  const _EdgeGlowPainter({
    required this.top,
    required this.bottom,
    required this.thickness,
    required this.intensity,
    required this.pulse,
    required this.drift,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final band =
        math.min(size.width, size.height) * thickness * (0.9 + 0.35 * pulse);
    final alpha = ((0.18 + 0.6 * intensity) * (0.55 + 0.45 * pulse)).clamp(
      0.0,
      0.85,
    );
    final warm = top.withValues(alpha: alpha);
    final cool = bottom.withValues(alpha: alpha * 0.85);
    final sway = math.sin(drift * 0.35) * 0.12;

    void paintBand(Rect rect, Alignment begin, Alignment end, Color color) {
      canvas.drawRect(
        rect,
        Paint()
          ..shader = LinearGradient(
            begin: begin,
            end: end,
            colors: [color, color.withValues(alpha: 0)],
          ).createShader(rect),
      );
    }

    paintBand(
      Rect.fromLTWH(0, 0, size.width, band),
      Alignment(-0.6 + sway, -1),
      Alignment(0.6 + sway, 1),
      warm,
    );
    paintBand(
      Rect.fromLTWH(0, size.height - band, size.width, band),
      Alignment(-0.6 - sway, 1),
      Alignment(0.6 - sway, -1),
      cool,
    );
    paintBand(
      Rect.fromLTWH(0, 0, band, size.height),
      Alignment(-1, -0.4 + sway),
      Alignment(1, 0.4 + sway),
      cool,
    );
    paintBand(
      Rect.fromLTWH(size.width - band, 0, band, size.height),
      Alignment(1, -0.4 - sway),
      Alignment(-1, 0.4 - sway),
      warm,
    );

    final cornerRadius = band * 1.5;
    for (final (corner, color) in [
      (Offset.zero, warm),
      (Offset(size.width, 0), warm),
      (Offset(0, size.height), cool),
      (Offset(size.width, size.height), cool),
    ]) {
      final rect = Rect.fromCircle(center: corner, radius: cornerRadius);
      canvas.drawRect(
        rect,
        Paint()
          ..shader = RadialGradient(
            colors: [
              color.withValues(alpha: alpha * 0.55),
              Colors.transparent,
            ],
            stops: const [0, 1],
          ).createShader(rect),
      );
    }
  }

  @override
  bool shouldRepaint(covariant _EdgeGlowPainter oldDelegate) =>
      oldDelegate.pulse != pulse ||
      oldDelegate.drift != drift ||
      oldDelegate.thickness != thickness ||
      oldDelegate.intensity != intensity ||
      oldDelegate.top != top ||
      oldDelegate.bottom != bottom;
}

class _StageTopBar extends StatelessWidget {
  final PlayableTrack track;
  final String styleLabel;
  final VoidCallback onClose;

  const _StageTopBar({
    required this.track,
    required this.styleLabel,
    required this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'NOW PLAYING',
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 2.2,
                  color: Colors.white.withValues(alpha: 0.46),
                ),
              ),
              const SizedBox(height: 3),
              Text(
                track.title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(
                  fontSize: 15,
                  fontWeight: FontWeight.w700,
                  color: Colors.white,
                ),
              ),
            ],
          ),
        ),
        Container(
          margin: const EdgeInsets.only(right: 12),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 7),
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(999),
            border: Border.all(color: Colors.white.withValues(alpha: 0.12)),
          ),
          child: Text(
            styleLabel.toUpperCase(),
            style: TextStyle(
              fontSize: 10,
              fontWeight: FontWeight.w800,
              letterSpacing: 1.2,
              color: Colors.white.withValues(alpha: 0.72),
            ),
          ),
        ),
        _StageButton(icon: WaveIcons.close, onTap: onClose),
      ],
    );
  }
}

class _StageLyrics extends StatelessWidget {
  final LyricsResult result;
  final bool loading;
  final int activeIndex;
  final String style;
  final ValueListenable<int> positionListenable;
  final List<Color> accent;
  final Color primary;
  final Color secondary;
  final bool reduceMotion;
  final AnimationController drift;

  const _StageLyrics({
    required this.result,
    required this.loading,
    required this.activeIndex,
    required this.style,
    required this.positionListenable,
    required this.accent,
    required this.primary,
    required this.secondary,
    required this.reduceMotion,
    required this.drift,
  });

  @override
  Widget build(BuildContext context) {
    final lines = result.lines;
    if (lines.isEmpty) {
      return Center(
        child: Text(
          loading ? 'Loading lyrics…' : 'No timed lyrics for this track',
          style: TextStyle(
            fontSize: 22,
            fontWeight: FontWeight.w600,
            color: primary.withValues(alpha: 0.55),
          ),
        ),
      );
    }
    final active = activeIndex.clamp(0, lines.length - 1);
    if (style == 'lyrical_studio_graph') {
      return _StudioGraphCamera(
        result: result,
        activeIndex: active,
        positionListenable: positionListenable,
        accent: accent[active % accent.length],
        primary: primary,
        secondary: secondary,
        reduceMotion: reduceMotion,
      );
    }

    if (style == 'design_native_thread') {
      return _NativeThreadView(
        result: result,
        activeIndex: active,
        positionListenable: positionListenable,
        accent: accent[active % accent.length],
        primary: primary,
        secondary: secondary,
        reduceMotion: reduceMotion,
      );
    }

    final line = lines[active];
    final previous = active > 0 ? lines[active - 1] : null;
    final next = active + 1 < lines.length ? lines[active + 1] : null;
    final lineAccent = accent[active % accent.length];
    final words = _wordsForLine(line);

    return LayoutBuilder(
      builder: (context, constraints) {
        final fontSize = math.min(
          96.0,
          math.max(
            36.0,
            math.min(
              constraints.maxWidth * 0.072,
              constraints.maxHeight * 0.16,
            ),
          ),
        );
        final lineWidget = buildStudioLyricsLine(
          style: style,
          lineText: line.text,
          words: words,
          positionMs: positionListenable.value,
          positionListenable: positionListenable,
          accent: lineAccent,
          primary: primary,
          secondary: secondary,
          fontSize: fontSize,
          isRtl: line.isRtl,
          reduceMotion: reduceMotion,
        );
        return AnimatedSwitcher(
          duration: reduceMotion
              ? Duration.zero
              : const Duration(milliseconds: 460),
          switchInCurve: Curves.easeOutCubic,
          switchOutCurve: Curves.easeInCubic,
          transitionBuilder: (child, animation) => FadeTransition(
            opacity: animation,
            child: ScaleTransition(
              scale: Tween<double>(begin: 0.985, end: 1).animate(animation),
              child: child,
            ),
          ),
          child: Column(
            key: ValueKey('stage-$active-${line.text}'),
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              if (previous != null)
                _ContextLine(
                  text: previous.text,
                  fontSize: fontSize * 0.32,
                  color: primary,
                ),
              Expanded(
                child: Center(
                  child: _StageDrift(
                    controller: drift,
                    enabled: style.startsWith('lyrical_studio'),
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxWidth: 1240),
                      child: lineWidget,
                    ),
                  ),
                ),
              ),
              if (next != null)
                _ContextLine(
                  text: next.text,
                  fontSize: fontSize * 0.32,
                  color: primary,
                ),
            ],
          ),
        );
      },
    );
  }
}

/// Message-thread stage: past lines stay as sent bubbles, the current line is
/// being typed word by word, and the next line waits as a typing indicator.
class _NativeThreadView extends StatelessWidget {
  final LyricsResult result;
  final int activeIndex;
  final ValueListenable<int> positionListenable;
  final Color accent;
  final Color primary;
  final Color secondary;
  final bool reduceMotion;

  const _NativeThreadView({
    required this.result,
    required this.activeIndex,
    required this.positionListenable,
    required this.accent,
    required this.primary,
    required this.secondary,
    required this.reduceMotion,
  });

  @override
  Widget build(BuildContext context) {
    final lines = result.lines;
    if (lines.isEmpty) return const SizedBox.shrink();
    final current = activeIndex.clamp(0, lines.length - 1);
    return LayoutBuilder(
      builder: (context, constraints) {
        final fontSize = math.min(
          34.0,
          math.max(18.0, constraints.maxWidth * 0.028),
        );
        // Every line is a message: the newest sits at the bottom, older lines
        // stack above it, and the next line shows a typing bubble underneath.
        final bubbles = <Widget>[
          if (current + 1 < lines.length)
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 760),
              child: buildNativeThreadBubble(
                words: const [],
                positionMs: positionListenable.value,
                positionListenable: positionListenable,
                accent: accent,
                primary: primary,
                secondary: secondary,
                fontSize: fontSize * 0.8,
                reduceMotion: reduceMotion,
                typing: true,
              ),
            ),
          for (var i = current; i >= 0 && i > current - 80; i--)
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 760),
              child: _threadBubble(
                line: lines[i],
                isCurrent: i == current,
                fontSize: i == current ? fontSize : fontSize * 0.86,
              ),
            ),
        ];
        return Align(
          alignment: Alignment.bottomLeft,
          child: ListView(
            reverse: true,
            padding: const EdgeInsets.symmetric(vertical: 18, horizontal: 4),
            children: bubbles,
          ),
        );
      },
    );
  }

  Widget _threadBubble({
    required LyricLine line,
    required bool isCurrent,
    required double fontSize,
  }) {
    return buildNativeThreadBubble(
      words: _wordsForLine(line),
      positionMs: isCurrent
          ? positionListenable.value
          : line.timeMs + math.max(line.durationMs, 900),
      positionListenable: isCurrent ? positionListenable : null,
      accent: accent,
      primary: primary,
      secondary: secondary,
      fontSize: fontSize,
      reduceMotion: reduceMotion,
      sung: !isCurrent,
    );
  }
}

class _ContextLine extends StatelessWidget {
  final String text;
  final double fontSize;
  final Color color;

  const _ContextLine({
    required this.text,
    required this.fontSize,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Text(
        text,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
        textAlign: TextAlign.center,
        style: TextStyle(
          fontSize: fontSize,
          fontWeight: FontWeight.w700,
          color: color.withValues(alpha: 0.18),
        ),
      ),
    );
  }
}

class _StageDrift extends StatelessWidget {
  final AnimationController controller;
  final bool enabled;
  final Widget child;

  const _StageDrift({
    required this.controller,
    required this.enabled,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    if (!enabled) return child;
    return AnimatedBuilder(
      animation: controller,
      child: child,
      builder: (context, child) {
        final t = controller.value * math.pi * 2;
        return Transform.translate(
          offset: Offset(math.sin(t / 7.3) * 5, math.cos(t / 9.1) * 4),
          child: Transform.rotate(
            angle: math.sin(t / 11.7) * 0.004,
            child: child,
          ),
        );
      },
    );
  }
}

class _StageFooter extends StatelessWidget {
  final LyricsResult result;
  final ValueListenable<int> activeLineListenable;
  final Color primary;

  const _StageFooter({
    required this.result,
    required this.activeLineListenable,
    required this.primary,
  });

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<int>(
      valueListenable: activeLineListenable,
      builder: (context, active, _) {
        final total = result.lines.length;
        return Row(
          children: [
            Text(
              total == 0 ? '' : 'LINE ${(active + 1).clamp(1, total)} / $total',
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w700,
                letterSpacing: 1.1,
                color: primary.withValues(alpha: 0.34),
              ),
            ),
            const Spacer(),
            Text(
              'ESC TO CLOSE',
              style: TextStyle(
                fontSize: 10,
                fontWeight: FontWeight.w700,
                letterSpacing: 1.1,
                color: primary.withValues(alpha: 0.28),
              ),
            ),
          ],
        );
      },
    );
  }
}

/// Camera-following Graph stage: the active line stays centred while the
/// surrounding lines glide past, instead of rebuilding one isolated tree.
class _StudioGraphCamera extends StatefulWidget {
  final LyricsResult result;
  final int activeIndex;
  final ValueListenable<int> positionListenable;
  final Color accent;
  final Color primary;
  final Color secondary;
  final bool reduceMotion;

  const _StudioGraphCamera({
    required this.result,
    required this.activeIndex,
    required this.positionListenable,
    required this.accent,
    required this.primary,
    required this.secondary,
    required this.reduceMotion,
  });

  @override
  State<_StudioGraphCamera> createState() => _StudioGraphCameraState();
}

class _StudioGraphCameraState extends State<_StudioGraphCamera> {
  late final ScrollController _scroll;

  @override
  void initState() {
    super.initState();
    _scroll = ScrollController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _followCamera(immediate: true);
    });
  }

  @override
  void didUpdateWidget(covariant _StudioGraphCamera oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.activeIndex != widget.activeIndex) {
      _followCamera();
    }
  }

  void _followCamera({bool immediate = false}) {
    if (!_scroll.hasClients) return;
    final rowHeight = _rowHeight(context);
    final target = (widget.activeIndex * rowHeight).clamp(
      0.0,
      _scroll.position.maxScrollExtent,
    );
    if (immediate || widget.reduceMotion) {
      _scroll.jumpTo(target);
      return;
    }
    _scroll.animateTo(
      target,
      duration: const Duration(milliseconds: 680),
      curve: Curves.easeInOutCubic,
    );
  }

  double _rowHeight(BuildContext context) {
    final h = MediaQuery.sizeOf(context).height;
    return math.max(180.0, math.min(320.0, h * 0.34));
  }

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final lines = widget.result.lines;
    final rowHeight = _rowHeight(context);
    return ClipRect(
      child: LayoutBuilder(
        builder: (context, constraints) {
          final height = constraints.maxHeight;
          return ListView.builder(
            controller: _scroll,
            physics: const NeverScrollableScrollPhysics(),
            itemExtent: rowHeight,
            padding: EdgeInsets.symmetric(
              vertical: math.max(0, (height - rowHeight) / 2),
            ),
            itemCount: lines.length,
            itemBuilder: (context, i) {
              final line = lines[i];
              final isActive = i == widget.activeIndex;
              final past = i < widget.activeIndex;
              final int position = isActive
                  ? widget.positionListenable.value
                  : past
                  ? line.timeMs + math.max<int>(line.durationMs, 1200) + 500
                  : line.timeMs - 1000;
              final words = _wordsForLine(line);
              return Opacity(
                opacity: isActive ? 1 : 0.34,
                child: SizedBox(
                  height: rowHeight,
                  child: Center(
                    child: buildStudioLyricsLine(
                      style: 'lyrical_studio_graph',
                      lineText: line.text,
                      words: words,
                      positionMs: position,
                      positionListenable: isActive
                          ? widget.positionListenable
                          : null,
                      accent: widget.accent,
                      primary: widget.primary,
                      secondary: widget.secondary,
                      fontSize: math.max(42.0, rowHeight * 0.2),
                      isRtl: line.isRtl,
                      reduceMotion: widget.reduceMotion,
                    ),
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }
}

class _StageButton extends StatelessWidget {
  final IconData icon;
  final VoidCallback onTap;

  const _StageButton({required this.icon, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onTap,
      child: Container(
        width: 38,
        height: 38,
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: 0.09),
          shape: BoxShape.circle,
          border: Border.all(color: Colors.white.withValues(alpha: 0.14)),
        ),
        child: Icon(icon, size: 17, color: Colors.white.withValues(alpha: 0.9)),
      ),
    );
  }
}

/// Opens the full-screen Studio stage over the current lyrics view.
Future<void> showStudioLyricsStage(
  BuildContext context, {
  required PlayableTrack track,
  required LyricsResult result,
  ValueListenable<StudioStageFeed?>? feed,
  required ValueListenable<int> positionListenable,
  required ValueListenable<int> activeLineListenable,
  required String style,
}) {
  return Navigator.of(context, rootNavigator: true).push<void>(
    PageRouteBuilder<void>(
      opaque: true,
      transitionDuration: const Duration(milliseconds: 260),
      reverseTransitionDuration: const Duration(milliseconds: 220),
      pageBuilder: (context, animation, secondaryAnimation) =>
          StudioLyricsStage(
            track: track,
            result: result,
            feed: feed,
            positionListenable: positionListenable,
            activeLineListenable: activeLineListenable,
            initialStyle: style,
          ),
      transitionsBuilder: (context, animation, secondaryAnimation, child) =>
          FadeTransition(opacity: animation, child: child),
    ),
  );
}

/// Turns a timed lyric line into the word list the Studio renderers expect.
List<StudioLyricWord> _wordsForLine(LyricLine line) {
  final syllables = line.syllables;
  if (syllables.isNotEmpty) {
    final groups = <List<LyricSyllable>>[];
    var current = <LyricSyllable>[];
    for (var i = 0; i < syllables.length; i++) {
      final syllable = syllables[i];
      current.add(syllable);
      var end = syllable.text.endsWith(' ');
      if (!end && i == syllables.length - 1) {
        end = true;
      } else if (!end) {
        final next = syllables[i + 1];
        end =
            next.text.startsWith(' ') ||
            !line.text.contains('${syllable.text}${next.text}');
      }
      if (end) {
        groups.add(current);
        current = [];
      }
    }
    if (current.isNotEmpty) groups.add(current);
    return [
      for (final group in groups)
        StudioLyricWord(
          text: group.map((s) => s.text).join().trim(),
          startMs: group.first.timeMs,
          endMs: group.last.timeMs + math.max(80, group.last.durationMs),
          isBackground: group.every((s) => s.isBackground),
        ),
    ].where((word) => word.text.isNotEmpty).toList();
  }

  final text = line.text.trim();
  if (text.isEmpty) return const [];
  final words = text.split(RegExp(r'\s+'));
  final start = line.timeMs;
  final duration = line.durationMs > 0 ? line.durationMs : 2400;
  final step = duration / words.length;
  return [
    for (var i = 0; i < words.length; i++)
      StudioLyricWord(
        text: words[i],
        startMs: start + (i * step).round(),
        endMs: start + ((i + 1) * step).round(),
      ),
  ];
}
