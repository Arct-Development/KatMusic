/// Lyric animation ports for KatMusic Desktop.
///
/// Kinetic typography is adapted from **LyricalStudio** by Arijit Naskar
/// (MIT, https://github.com/arijit-debug/LyricalStudio): Stack, Slide, Zoom,
/// Letters, Scatter, Flow, Graph and the automatic per-line style picker.
///
/// Ship, Fisheye, Fisheye Visual and Visual are adapted from
/// **theaestheticslyrics** by darshanseenivasakumar (MIT,
/// https://github.com/darshanseenivasakumar/theaestheticslyrics).
///
/// Both are MIT licensed; see desktop/THIRD_PARTY_NOTICES.md. The originals
/// render on `<canvas>`/WebGL/WAAPI. This port uses Flutter widgets and
/// transforms, and keeps only the active lyric line's words, so it fits the
/// desktop lyrics rail while preserving each style's motion and hierarchy.
library;

import 'dart:async';
import 'dart:math' as math;
import 'dart:ui' show PathMetric;

import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/physics.dart';

/// One timed word handed to the studio renderers.
class StudioLyricWord {
  final String text;
  final int startMs;
  final int endMs;
  final bool isBackground;

  const StudioLyricWord({
    required this.text,
    required this.startMs,
    required this.endMs,
    this.isBackground = false,
  });
}

const studioLyricsStyleIds = <String>{
  'lyrical_studio',
  'lyrical_studio_stack',
  'lyrical_studio_slide',
  'lyrical_studio_zoom',
  'lyrical_studio_letters',
  'lyrical_studio_scatter',
  'lyrical_studio_flow',
  'lyrical_studio_graph',
  'aesthetic_ship',
  'aesthetic_fisheye',
  'aesthetic_fisheye_visual',
  'aesthetic_visual',
  'design_branches',
  'design_constellation',
};

bool isStudioLyricsAnimation(String id) => studioLyricsStyleIds.contains(id);

/// Builds one active lyric line in the requested ported style.
Widget buildStudioLyricsLine({
  required String style,
  required String lineText,
  required List<StudioLyricWord> words,
  required int positionMs,
  required ValueListenable<int>? positionListenable,
  required Color accent,
  required Color primary,
  required Color secondary,
  required double fontSize,
  required bool isRtl,
  required bool reduceMotion,
}) {
  if (words.isEmpty) return const SizedBox.shrink();
  switch (style) {
    case 'design_branches':
      return _BranchesLine(
        lineText: lineText,
        words: words,
        positionMs: positionMs,
        positionListenable: positionListenable,
        accent: accent,
        primary: primary,
        secondary: secondary,
        fontSize: fontSize,
        isRtl: isRtl,
        reduceMotion: reduceMotion,
      );
    case 'design_constellation':
      return _ConstellationLine(
        lineText: lineText,
        words: words,
        positionMs: positionMs,
        positionListenable: positionListenable,
        accent: accent,
        primary: primary,
        secondary: secondary,
        fontSize: fontSize,
        isRtl: isRtl,
      );
    case 'aesthetic_ship':
      return _AestheticShip(
        lineText: lineText,
        words: words,
        positionMs: positionMs,
        positionListenable: positionListenable,
        accent: accent,
        primary: primary,
        secondary: secondary,
        fontSize: fontSize,
        reduceMotion: reduceMotion,
      );
    case 'aesthetic_fisheye':
    case 'aesthetic_fisheye_visual':
      return _AestheticFisheye(
        lineText: lineText,
        words: words,
        positionMs: positionMs,
        positionListenable: positionListenable,
        accent: accent,
        primary: primary,
        secondary: secondary,
        fontSize: fontSize,
        withIcons: style == 'aesthetic_fisheye_visual',
      );
    case 'aesthetic_visual':
      return _AestheticVisual(
        lineText: lineText,
        words: words,
        positionMs: positionMs,
        positionListenable: positionListenable,
        accent: accent,
        primary: primary,
        secondary: secondary,
        fontSize: fontSize,
      );
  }
  final forced = switch (style) {
    'lyrical_studio_stack' => _StudioTemplate.stack,
    'lyrical_studio_slide' => _StudioTemplate.slide,
    'lyrical_studio_zoom' => _StudioTemplate.zoom,
    'lyrical_studio_letters' => _StudioTemplate.letters,
    'lyrical_studio_scatter' => _StudioTemplate.scatter,
    'lyrical_studio_flow' => _StudioTemplate.flow,
    'lyrical_studio_graph' => _StudioTemplate.graph,
    _ => null,
  };
  return _LyricalStudioLine(
    lineText: lineText,
    words: words,
    positionMs: positionMs,
    positionListenable: positionListenable,
    accent: accent,
    primary: primary,
    secondary: secondary,
    fontSize: fontSize,
    isRtl: isRtl,
    reduceMotion: reduceMotion,
    forced: forced,
  );
}

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

Widget _positionAware({
  required ValueListenable<int>? listenable,
  required int initial,
  required Widget Function(int positionMs) builder,
}) {
  if (listenable == null) return builder(initial);
  return ValueListenableBuilder<int>(
    valueListenable: listenable,
    builder: (context, positionMs, _) => builder(positionMs),
  );
}

int _hashText(String text) {
  var h = 0x811c9dc5;
  for (final unit in text.codeUnits) {
    h ^= unit;
    h = (h * 0x01000193) & 0x7fffffff;
  }
  return h;
}

double _visualLength(String text) {
  var n = 0.0;
  for (final rune in text.toUpperCase().runes) {
    final ch = String.fromCharCode(rune);
    if (RegExp(r'[\u3000-\u30ff\u4e00-\u9fff\uac00-\ud7af\uff00-\uffef]')
        .hasMatch(ch)) {
      n += 1;
    } else if (RegExp(r'[\u0900-\u097f]').hasMatch(ch)) {
      n += 0.62;
    } else if ('MW@'.contains(ch)) {
      n += 0.98;
    } else if ("I.,'!|:;".contains(ch)) {
      n += 0.3;
    } else if (ch == 'J') {
      n += 0.56;
    } else if (ch == ' ') {
      n += 0.26;
    } else if (RegExp(r'[0-9]').hasMatch(ch)) {
      n += 0.66;
    } else {
      n += 0.72;
    }
  }
  return math.max(n, 0.5);
}

List<List<int>> _groupRows(List<StudioLyricWord> words, int maxRows) {
  if (words.length <= maxRows) {
    return [
      for (var i = 0; i < words.length; i++) [i],
    ];
  }
  final total = words.fold<double>(
    0,
    (a, w) => a + _visualLength(w.text) + 0.3,
  );
  final target = total / maxRows;
  final rows = <List<int>>[[]];
  var width = 0.0;
  for (var i = 0; i < words.length; i++) {
    final len = _visualLength(words[i].text) + 0.3;
    if (width > 0 && width + len / 2 > target && rows.length < maxRows) {
      rows.add([]);
      width = 0;
    }
    rows.last.add(i);
    width += len;
  }
  return rows;
}

int _revealedCount(List<StudioLyricWord> words, int positionMs) {
  var count = 0;
  for (final word in words) {
    if (word.startMs <= positionMs) {
      count++;
    } else {
      break;
    }
  }
  if (count == 0 && positionMs >= words.first.startMs - 250) count = 1;
  return count;
}

int _activeIndex(List<StudioLyricWord> words, int positionMs) {
  final revealed = _revealedCount(words, positionMs);
  return revealed == 0 ? 0 : revealed - 1;
}

bool _isPreview(List<StudioLyricWord> words, int positionMs) =>
    positionMs < words.first.startMs;

const _tinyWords = {
  'a', 'an', 'the', 'to', 'of', 'in', 'on', 'at', 'by', 'for', 'and', //
  'or', 'but', 'so', 'is', 'am', 'are', 'be', 'i', "i'm", 'me', 'my',
  'we', 'it', 'oh', 'ooh', 'ah', 'yeah',
};

Set<int> _highlightSet(List<StudioLyricWord> words, int seed) {
  final random = math.Random(seed ^ 0x51ed27);
  var pool = <int>[
    for (var i = 0; i < words.length; i++)
      if (!words[i].isBackground &&
          !_tinyWords.contains(
            words[i].text.toLowerCase().replaceAll(RegExp(r"[^a-z']"), ''),
          ))
        i,
  ];
  if (pool.isEmpty) {
    pool = [
      for (var i = 0; i < words.length; i++)
        if (!words[i].isBackground) i,
    ];
  }
  if (pool.isEmpty) return const {};
  pool.shuffle(random);
  final count = math.max(
    1,
    math.min(
      4,
      math.min(
        pool.length,
        (words.length * (0.18 + random.nextDouble() * 0.17)).round(),
      ),
    ),
  );
  return pool.take(count).toSet();
}

Color _highlightColor(Color accent) => accent;

TextStyle _wordStyle({
  required Color color,
  required double fontSize,
  bool glow = false,
  bool italic = false,
}) {
  return TextStyle(
    fontSize: fontSize,
    height: 1.02,
    fontWeight: FontWeight.w900,
    letterSpacing: -0.5,
    fontStyle: italic ? FontStyle.italic : FontStyle.normal,
    color: color,
    shadows: [
      Shadow(
        color: Colors.black.withValues(alpha: 0.5),
        blurRadius: fontSize * 0.22,
        offset: Offset(0, fontSize * 0.035),
      ),
      if (glow)
        Shadow(
          color: color.withValues(alpha: 0.5),
          blurRadius: fontSize * 0.55,
        ),
    ],
  );
}

// ---------------------------------------------------------------------------
// LyricalStudio kinetic layouts
// ---------------------------------------------------------------------------

enum _StudioTemplate { stack, slide, zoom, letters, scatter, flow, graph }

class _StudioScene {
  final _StudioTemplate template;
  final bool alignRight;
  final bool reverse;
  final bool fromTop;
  final double indent;

  const _StudioScene(
    this.template, {
    this.alignRight = false,
    this.reverse = false,
    this.fromTop = true,
    this.indent = 4,
  });
}

_StudioScene _chooseScene(
  List<StudioLyricWord> words,
  int seed, [
  _StudioTemplate? forced,
]) {
  final random = math.Random(seed);
  final n = words.length;
  final duration = math.max(
    1.0,
    (words.last.endMs - words.first.startMs) / 1000,
  );
  final wordsPerSecond = n / duration;
  _StudioTemplate template;
  if (forced != null) {
    template = forced;
  } else if (n <= 2) {
    template = _StudioTemplate.zoom;
  } else if (n > 11 || wordsPerSecond > 3.8) {
    template = random.nextBool() ? _StudioTemplate.flow : _StudioTemplate.slide;
  } else {
    final picks = <(_StudioTemplate, int)>[
      (_StudioTemplate.stack, 2),
      (_StudioTemplate.slide, 2),
      (_StudioTemplate.zoom, 2),
      (_StudioTemplate.letters, 2),
      (_StudioTemplate.scatter, 2),
      (_StudioTemplate.flow, 1),
      (_StudioTemplate.graph, 1),
    ];
    var total = picks.fold<int>(0, (a, p) => a + p.$2);
    var x = random.nextDouble() * total;
    template = picks.first.$1;
    for (final pick in picks) {
      x -= pick.$2;
      if (x <= 0) {
        template = pick.$1;
        break;
      }
    }
  }
  return _StudioScene(
    template,
    alignRight: random.nextBool(),
    reverse: random.nextBool(),
    fromTop: random.nextBool(),
    indent: 3 + random.nextDouble() * 4,
  );
}

class _LyricalStudioLine extends StatelessWidget {
  final String lineText;
  final List<StudioLyricWord> words;
  final int positionMs;
  final ValueListenable<int>? positionListenable;
  final Color accent;
  final Color primary;
  final Color secondary;
  final double fontSize;
  final bool isRtl;
  final bool reduceMotion;
  final _StudioTemplate? forced;

  const _LyricalStudioLine({
    required this.lineText,
    required this.words,
    required this.positionMs,
    required this.positionListenable,
    required this.accent,
    required this.primary,
    required this.secondary,
    required this.fontSize,
    required this.isRtl,
    required this.reduceMotion,
    required this.forced,
  });

  @override
  Widget build(BuildContext context) {
    final seed = _hashText(lineText);
    return _positionAware(
      listenable: positionListenable,
      initial: positionMs,
      builder: (position) {
        final revealed = _revealedCount(words, position);
        final scene = _chooseScene(words, seed, forced);
        return LayoutBuilder(
          builder: (context, constraints) {
            final width = constraints.maxWidth.isFinite
                ? constraints.maxWidth
                : MediaQuery.sizeOf(context).width;
            final size = fontSize * (width < 380 ? 0.92 : 1.0);
            if (scene.template == _StudioTemplate.graph) {
              return _StudioGraphLine(
                words: words,
                revealed: revealed,
                seed: seed,
                accent: accent,
                primary: primary,
                secondary: secondary,
                fontSize: size,
                width: width,
              );
            }
            return _kineticLayout(
              context: context,
              scene: scene,
              words: words,
              revealed: revealed,
              seed: seed,
              accent: accent,
              primary: primary,
              secondary: secondary,
              size: size,
              reduceMotion: reduceMotion,
            );
          },
        );
      },
    );
  }

  Widget _kineticLayout({
    required BuildContext context,
    required _StudioScene scene,
    required List<StudioLyricWord> words,
    required int revealed,
    required int seed,
    required Color accent,
    required Color primary,
    required Color secondary,
    required double size,
    required bool reduceMotion,
  }) {
    final highlights = _highlightSet(words, seed);
    final random = math.Random(seed ^ 0x9e3779b9);
    final rows = _groupRows(words, switch (scene.template) {
      _StudioTemplate.stack => math.min(
        5,
        math.max(2, (words.length / 2).ceil()),
      ),
      _StudioTemplate.slide => math.min(
        3,
        math.max(1, (words.length / 2).ceil()),
      ),
      _StudioTemplate.zoom || _StudioTemplate.letters => math.min(
        4,
        math.max(1, (words.length / 2).ceil()),
      ),
      _ => 1,
    });

    Widget wordAt(int i, {_Pose? pose, Duration? duration, int delayMs = 0}) {
      final shown = i < revealed;
      final color = highlights.contains(i)
          ? _highlightColor(accent)
          : (shown ? primary : secondary);
      if (scene.template == _StudioTemplate.flow) {
        return _FlowWord(
          text: words[i].text,
          shown: shown,
          active: i == revealed - 1,
          color: highlights.contains(i) ? accent : primary,
          idleColor: secondary,
          fontSize: size,
          reduceMotion: reduceMotion,
        );
      }
      if (scene.template == _StudioTemplate.letters) {
        return _LettersWord(
          word: words[i].text,
          shown: shown,
          color: color,
          accent: highlights.contains(i) ? accent : null,
          fontSize: size,
          fromTop: scene.fromTop,
          reduceMotion: reduceMotion,
        );
      }
      final from =
          pose ??
          switch (scene.template) {
            _StudioTemplate.stack => _Pose(
              dy: size * 0.7,
              scaleX: 0.9,
              scaleY: 1.3,
              opacity: 0,
            ),
            _StudioTemplate.slide => _Pose(
              dx: scene.reverse ? 80 : -80,
              scaleX: 1.25,
              scaleY: 0.88,
              skewX: scene.reverse ? 0.22 : -0.18,
              opacity: 0,
            ),
            _StudioTemplate.zoom => const _Pose(
              scale: 2.6,
              scaleY: 0.75,
              skewX: -0.17,
              opacity: 0,
            ),
            _StudioTemplate.scatter => _Pose(
              dx: (random.nextDouble() - 0.5) * 90,
              dy: (random.nextDouble() - 0.5) * 60,
              scale: 0.7,
              skewX: (random.nextDouble() - 0.5) * 0.3,
              rotate: (random.nextDouble() - 0.5) * 0.35,
              opacity: 0,
            ),
            _ => const _Pose(opacity: 0),
          };
      return _KineticWord(
        key: ValueKey('studio-$seed-$i'),
        shown: shown,
        from: from,
        duration:
            duration ??
            (scene.template == _StudioTemplate.zoom
                ? const Duration(milliseconds: 620)
                : const Duration(milliseconds: 480)),
        curve: scene.template == _StudioTemplate.zoom
            ? Curves.easeOutCubic
            : Curves.easeOutBack,
        reduceMotion: reduceMotion,
        child: Text(
          words[i].text.toUpperCase(),
          style: _wordStyle(
            color: color,
            fontSize: scene.template == _StudioTemplate.scatter
                ? size * (0.78 + random.nextDouble() * 0.35)
                : size,
            glow: highlights.contains(i),
          ),
          textAlign: TextAlign.center,
        ),
      );
    }

    switch (scene.template) {
      case _StudioTemplate.stack:
        final align = scene.alignRight
            ? CrossAxisAlignment.end
            : CrossAxisAlignment.start;
        return Align(
          alignment: scene.alignRight
              ? Alignment.centerRight
              : Alignment.centerLeft,
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: align,
            children: [
              for (final row in rows)
                Wrap(
                  spacing: size * 0.25,
                  runSpacing: 0,
                  alignment: scene.alignRight
                      ? WrapAlignment.end
                      : WrapAlignment.start,
                  children: [for (final i in row) wordAt(i)],
                ),
            ],
          ),
        );
      case _StudioTemplate.slide:
        return ClipRect(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (var r = 0; r < rows.length; r++)
                Padding(
                  padding: EdgeInsets.only(
                    left:
                        (scene.reverse ? rows.length - 1 - r : r) *
                        scene.indent *
                        0.01 *
                        (MediaQuery.sizeOf(context).width),
                  ),
                  child: Wrap(
                    spacing: size * 0.24,
                    runSpacing: 0,
                    children: [for (final i in rows[r]) wordAt(i)],
                  ),
                ),
            ],
          ),
        );
      case _StudioTemplate.zoom:
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            for (final row in rows)
              Wrap(
                alignment: WrapAlignment.center,
                spacing: size * 0.25,
                children: [for (final i in row) wordAt(i)],
              ),
          ],
        );
      case _StudioTemplate.scatter:
        return Wrap(
          alignment: WrapAlignment.center,
          crossAxisAlignment: WrapCrossAlignment.center,
          spacing: size * 0.18,
          runSpacing: size * 0.04,
          children: [
            for (var i = 0; i < words.length; i++)
              Transform.rotate(
                angle: (random.nextDouble() - 0.5) * 0.35,
                child: wordAt(i),
              ),
          ],
        );
      case _StudioTemplate.letters:
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.center,
          children: [
            for (final row in rows)
              Wrap(
                alignment: WrapAlignment.center,
                spacing: size * 0.25,
                runSpacing: 0,
                children: [for (final i in row) wordAt(i)],
              ),
          ],
        );
      case _StudioTemplate.flow:
        return Wrap(
          alignment: WrapAlignment.center,
          spacing: size * 0.26,
          runSpacing: size * 0.05,
          children: [for (var i = 0; i < words.length; i++) wordAt(i)],
        );
      case _StudioTemplate.graph:
        throw StateError('graph handled above');
    }
  }
}

class _Pose {
  final double dx;
  final double dy;
  final double scale;
  final double scaleX;
  final double scaleY;
  final double skewX;
  final double rotate;
  final double rotateX;
  final double opacity;

  const _Pose({
    this.dx = 0,
    this.dy = 0,
    this.scale = 1,
    this.scaleX = 1,
    this.scaleY = 1,
    this.skewX = 0,
    this.rotate = 0,
    this.rotateX = 0,
    this.opacity = 1,
  });
}

class _KineticWord extends StatefulWidget {
  final bool shown;
  final _Pose from;
  final Duration duration;
  final Curve curve;
  final int delayMs;
  final bool reduceMotion;
  final bool trails;
  final Widget child;

  const _KineticWord({
    super.key,
    required this.shown,
    required this.from,
    required this.duration,
    required this.curve,
    required this.reduceMotion,
    required this.child,
    this.delayMs = 0,
    this.trails = true,
  });

  @override
  State<_KineticWord> createState() => _KineticWordState();
}

class _KineticWordState extends State<_KineticWord>
    with SingleTickerProviderStateMixin {
  late final AnimationController _controller;
  Timer? _delay;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController.unbounded(vsync: this, value: 0);
    if (widget.shown) _schedule();
  }

  @override
  void didUpdateWidget(covariant _KineticWord oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!oldWidget.shown && widget.shown) {
      _schedule();
    } else if (oldWidget.shown && !widget.shown) {
      _delay?.cancel();
      _controller.value = 0;
    }
  }

  void _schedule() {
    _delay?.cancel();
    if (widget.reduceMotion) {
      _controller.value = 1;
      return;
    }
    _delay = Timer(Duration(milliseconds: widget.delayMs), () {
      if (!mounted) return;
      // A lightly damped spring gives the same overshoot the original
      // WAAPI scenes get from their physics easing. The controller is
      // unbounded so the overshoot above 1.0 survives.
      _controller.animateWith(
        SpringSimulation(
          SpringDescription.withDampingRatio(
            mass: 1,
            stiffness: 320,
            ratio: 0.72,
          ),
          _controller.value,
          1,
          0,
        ),
      );
    });
  }

  @override
  void dispose() {
    _delay?.cancel();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _controller,
      child: widget.child,
      builder: (context, child) {
        final raw = _controller.value.clamp(-0.25, 1.25);
        final t = raw.clamp(0.0, 1.0);
        final trail =
            widget.trails &&
            !widget.reduceMotion &&
            (widget.from.dx.abs() > 0.5 ||
                widget.from.dy.abs() > 0.5 ||
                widget.from.scale != 1 ||
                widget.from.rotate != 0);
        Widget posed(double progress, double opacityScale) {
          final inv = 1 - progress;
          final scale = 1 + (widget.from.scale - 1) * inv;
          final matrix = Matrix4.identity();
          if (widget.from.rotateX != 0) {
            matrix.setEntry(3, 2, 0.0016);
            matrix.rotateX(widget.from.rotateX * inv);
          }
          if (widget.from.rotate != 0) {
            matrix.rotateZ(widget.from.rotate * inv);
          }
          matrix.scale(
            scale * (1 + (widget.from.scaleX - 1) * inv),
            scale * (1 + (widget.from.scaleY - 1) * inv),
          );
          if (widget.from.skewX != 0) {
            matrix.setEntry(0, 1, widget.from.skewX * inv);
          }
          if (widget.from.dx != 0 || widget.from.dy != 0) {
            matrix.translate(widget.from.dx * inv, widget.from.dy * inv);
          }
          return Opacity(
            opacity:
                ((widget.from.opacity +
                            (1 - widget.from.opacity) * progress.clamp(0, 1)) *
                        opacityScale)
                    .clamp(0.0, 1.0),
            child: Transform(
              alignment: Alignment.center,
              transform: matrix,
              child: child,
            ),
          );
        }

        final main = posed(raw, 1);
        if (!trail) return main;
        // Two delayed copies of the same word read as the original's
        // motion-blur trail without a per-frame ImageFilter.blur.
        return Stack(
          alignment: Alignment.center,
          clipBehavior: Clip.none,
          children: [
            posed((t - 0.20).clamp(0.0, 1.0), 0.10),
            posed((t - 0.10).clamp(0.0, 1.0), 0.18),
            main,
          ],
        );
      },
    );
  }
}

class _FlowWord extends StatelessWidget {
  final String text;
  final bool shown;
  final bool active;
  final Color color;
  final Color idleColor;
  final double fontSize;
  final bool reduceMotion;

  const _FlowWord({
    required this.text,
    required this.shown,
    required this.active,
    required this.color,
    required this.idleColor,
    required this.fontSize,
    required this.reduceMotion,
  });

  @override
  Widget build(BuildContext context) {
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 1, end: active ? 1.04 : 1),
      duration: reduceMotion
          ? Duration.zero
          : const Duration(milliseconds: 280),
      curve: Curves.easeOutCubic,
      builder: (context, scale, child) =>
          Transform.scale(scale: scale, child: child),
      child: AnimatedDefaultTextStyle(
        duration: reduceMotion
            ? Duration.zero
            : const Duration(milliseconds: 280),
        curve: Curves.easeOutCubic,
        style: _wordStyle(
          color: shown ? color : idleColor.withValues(alpha: 0.32),
          fontSize: fontSize,
          glow: shown,
        ).copyWith(height: 1.12),
        child: Text(text.toUpperCase(), textAlign: TextAlign.center),
      ),
    );
  }
}

class _LettersWord extends StatelessWidget {
  final String word;
  final bool shown;
  final Color color;
  final Color? accent;
  final double fontSize;
  final bool fromTop;
  final bool reduceMotion;

  const _LettersWord({
    required this.word,
    required this.shown,
    required this.color,
    required this.accent,
    required this.fontSize,
    required this.fromTop,
    required this.reduceMotion,
  });

  @override
  Widget build(BuildContext context) {
    final letters = word.runes.map(String.fromCharCode).toList();
    return Semantics(
      label: word,
      child: ExcludeSemantics(
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            for (var i = 0; i < letters.length; i++)
              _KineticWord(
                key: ValueKey('letter-$word-$i'),
                shown: shown,
                from: _Pose(
                  dy: (fromTop ? -0.4 : 0.4) * fontSize,
                  rotateX: (fromTop ? 1 : -1) * math.pi / 2,
                  opacity: 0,
                ),
                duration: const Duration(milliseconds: 420),
                curve: Curves.easeOutBack,
                delayMs: i * 30,
                reduceMotion: reduceMotion,
                trails: false,
                child: Text(
                  letters[i],
                  style: _wordStyle(
                    color: color,
                    fontSize: fontSize,
                    glow: accent != null,
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

class _StudioGraphLine extends StatelessWidget {
  final List<StudioLyricWord> words;
  final int revealed;
  final int seed;
  final Color accent;
  final Color primary;
  final Color secondary;
  final double fontSize;
  final double width;

  const _StudioGraphLine({
    required this.words,
    required this.revealed,
    required this.seed,
    required this.accent,
    required this.primary,
    required this.secondary,
    required this.fontSize,
    required this.width,
  });

  @override
  Widget build(BuildContext context) {
    final highlights = _highlightSet(words, seed);
    final box = _layoutGraph(words, width, fontSize, seed);
    return SizedBox(
      width: width,
      height: box.height,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Positioned.fill(
            child: CustomPaint(
              painter: _GraphPainter(
                nodes: box.nodes,
                edges: box.edges,
                revealed: revealed,
                accent: accent,
              ),
            ),
          ),
          for (var i = 0; i < words.length; i++)
            Positioned(
              left: box.nodes[i].x,
              top: box.nodes[i].y - box.nodes[i].fontSize,
              child: _KineticWord(
                key: ValueKey('graph-$seed-$i'),
                shown: i < revealed,
                from: const _Pose(dy: 8, scale: 0.85, opacity: 0),
                duration: const Duration(milliseconds: 420),
                curve: Curves.easeOutCubic,
                reduceMotion: false,
                child: Text(
                  words[i].text.toUpperCase(),
                  style: _wordStyle(
                    color: highlights.contains(i) ? accent : primary,
                    fontSize: box.nodes[i].fontSize,
                    glow: highlights.contains(i),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _GraphNode {
  final double x;
  final double y;
  final double fontSize;
  final bool leaf;
  final int parent;

  const _GraphNode({
    required this.x,
    required this.y,
    required this.fontSize,
    required this.leaf,
    required this.parent,
  });
}

class _GraphEdge {
  final int from;
  final int to;
  final bool leaf;
  final bool wrap;

  const _GraphEdge({
    required this.from,
    required this.to,
    required this.leaf,
    required this.wrap,
  });
}

class _GraphBox {
  final List<_GraphNode> nodes;
  final List<_GraphEdge> edges;
  final double height;

  const _GraphBox(this.nodes, this.edges, this.height);
}

_GraphBox _layoutGraph(
  List<StudioLyricWord> words,
  double width,
  double fontSize,
  int seed,
) {
  final n = words.length;
  final leaves = <bool>[
    for (var i = 0; i < n; i++)
      i != n - 1 &&
          (_tinyWords.contains(
                words[i].text.toLowerCase().replaceAll(RegExp(r"[^a-z']"), ''),
              ) ||
              words[i].text.length <= 2),
  ];
  if (!leaves.contains(false)) {
    for (var i = 0; i < leaves.length; i++) {
      leaves[i] = false;
    }
  }
  final parents = <int>[
    for (var i = 0; i < n; i++)
      if (leaves[i])
        () {
          for (var j = i + 1; j < n; j++) {
            if (!leaves[j]) return j;
          }
          for (var j = i - 1; j >= 0; j--) {
            if (!leaves[j]) return j;
          }
          return i;
        }()
      else
        i,
  ];

  var size = math.min(width * 0.075, fontSize);
  var nodes = <_GraphNode>[];
  var edges = <_GraphEdge>[];
  var height = size * 2;

  for (var attempt = 0; attempt < 8; attempt++) {
    final phase = (seed % 628) / 100;
    final rowStep = size * 1.45;
    final maxWidth = width * 0.9;
    final slots = List<_GraphNode?>.filled(n, null);
    var x = 0.0;
    var y = 0.0;
    var row = 0;
    var lastTrunk = -1;
    var countInRow = 0;

    for (var i = 0; i < n; i++) {
      if (leaves[i]) continue;
      final textWidth = _visualLength(words[i].text) * size;
      if (lastTrunk < 0) {
        x = 0;
      } else {
        final gap = width * 0.05;
        if (countInRow > 0 && x + textWidth + gap > maxWidth) {
          row++;
          countInRow = 0;
          x = 0;
        } else {
          x += gap;
        }
      }
      y = fontSize + row * rowStep + math.sin(i * 1.25 + phase) * size * 0.55;
      slots[i] = _GraphNode(x: x, y: y, fontSize: size, leaf: false, parent: i);
      x += textWidth;
      lastTrunk = i;
      countInRow++;
    }

    final leafOrder = <int, int>{};
    for (var i = 0; i < n; i++) {
      if (!leaves[i]) continue;
      final parent = parents[i];
      final order = leafOrder.update(parent, (v) => v + 1, ifAbsent: () => 0);
      final parentNode = slots[parent];
      if (parentNode != null) {
        slots[i] = _GraphNode(
          x: math.max(0, parentNode.x - size * 0.62 * (order + 1)),
          y: parentNode.y + size * 0.78,
          fontSize: size * 0.42,
          leaf: true,
          parent: parent,
        );
      } else {
        slots[i] = _GraphNode(
          x: x,
          y: y + size * 0.6,
          fontSize: size * 0.42,
          leaf: true,
          parent: parent,
        );
      }
    }

    for (var i = 0; i < n; i++) {
      slots[i] ??= _GraphNode(
        x: 0,
        y: row * rowStep,
        fontSize: size,
        leaf: false,
        parent: i,
      );
    }
    nodes = [for (final node in slots) node!];

    var maxX = 0.0;
    for (var i = 0; i < n; i++) {
      maxX = math.max(
        maxX,
        nodes[i].x + _visualLength(words[i].text) * nodes[i].fontSize,
      );
    }
    final shiftX = math.max(0.0, (width - maxX) / 2);
    if (shiftX > 0) {
      nodes = [
        for (final node in nodes)
          _GraphNode(
            x: node.x + shiftX,
            y: node.y,
            fontSize: node.fontSize,
            leaf: node.leaf,
            parent: node.parent,
          ),
      ];
    }

    edges = [];
    var previousTrunk = -1;
    for (var i = 0; i < n; i++) {
      if (leaves[i]) {
        edges.add(_GraphEdge(from: i, to: parents[i], leaf: true, wrap: false));
      } else if (previousTrunk >= 0) {
        edges.add(
          _GraphEdge(
            from: previousTrunk,
            to: i,
            leaf: false,
            wrap:
                nodes[i].y > nodes[previousTrunk].y + size * 0.2 &&
                nodes[i].x < nodes[previousTrunk].x,
          ),
        );
      }
      if (!leaves[i]) previousTrunk = i;
    }

    height = fontSize + (row + 1) * rowStep + size * 1.4;
    if (maxX <= maxWidth || size <= 12) break;
    size *= 0.86;
  }
  return _GraphBox(nodes, edges, math.max(height, size * 2.2));
}

class _GraphPainter extends CustomPainter {
  final List<_GraphNode> nodes;
  final List<_GraphEdge> edges;
  final int revealed;
  final Color accent;

  const _GraphPainter({
    required this.nodes,
    required this.edges,
    required this.revealed,
    required this.accent,
  });

  @override
  void paint(Canvas canvas, Size size) {
    for (final edge in edges) {
      if ((edge.leaf ? edge.from : edge.to) >= revealed) continue;
      final a = nodes[edge.from];
      final b = nodes[edge.to];
      final paint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeCap = StrokeCap.round
        ..strokeWidth = edge.leaf ? 1.5 : 3
        ..color = edge.leaf
            ? Colors.white.withValues(alpha: 0.7)
            : accent.withValues(alpha: 0.85);
      final path = Path()..moveTo(a.x, a.y);
      if (edge.leaf) {
        path.lineTo(b.x, b.y);
      } else if (edge.wrap) {
        final midY = (a.y + b.y) / 2;
        path.cubicTo(a.x + 120, midY, b.x - 120, midY, b.x, b.y);
      } else {
        final dx = (b.x - a.x) / 2;
        path.cubicTo(a.x + dx, a.y, b.x - dx, b.y, b.x, b.y);
      }
      canvas.drawPath(path, paint);
      canvas.drawCircle(
        Offset(b.x, b.y),
        edge.leaf ? 4 : 6,
        Paint()..color = edge.leaf ? Colors.white : accent,
      );
    }
  }

  @override
  bool shouldRepaint(covariant _GraphPainter oldDelegate) =>
      oldDelegate.revealed != revealed ||
      oldDelegate.accent != accent ||
      oldDelegate.nodes != nodes;
}

// ---------------------------------------------------------------------------
// theaestheticslyrics: Ship / Fisheye / Visual
// ---------------------------------------------------------------------------

class _AestheticShip extends StatefulWidget {
  final String lineText;
  final List<StudioLyricWord> words;
  final int positionMs;
  final ValueListenable<int>? positionListenable;
  final Color accent;
  final Color primary;
  final Color secondary;
  final double fontSize;
  final bool reduceMotion;

  const _AestheticShip({
    required this.lineText,
    required this.words,
    required this.positionMs,
    required this.positionListenable,
    required this.accent,
    required this.primary,
    required this.secondary,
    required this.fontSize,
    required this.reduceMotion,
  });

  @override
  State<_AestheticShip> createState() => _AestheticShipState();
}

class _AestheticShipState extends State<_AestheticShip>
    with SingleTickerProviderStateMixin {
  late final AnimationController _drift;

  @override
  void initState() {
    super.initState();
    _drift = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 19),
    );
    if (!widget.reduceMotion) _drift.repeat();
  }

  @override
  void dispose() {
    _drift.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return _positionAware(
      listenable: widget.positionListenable,
      initial: widget.positionMs,
      builder: (position) {
        final active = _activeIndex(widget.words, position);
        final preview = _isPreview(widget.words, position);
        final start = math.max(0, active - 4);
        final end = math.min(widget.words.length - 1, active + 3);
        final rowHeight = widget.fontSize * 0.96;
        final height = math.max(
          widget.fontSize * 4.8,
          (end - start + 1) * rowHeight + widget.fontSize,
        );
        final random = math.Random(_hashText(widget.lineText) ^ 0x45d9f3b);
        final stack = SizedBox(
          height: height,
          child: Stack(
            clipBehavior: Clip.none,
            alignment: Alignment.center,
            children: [
              for (var i = start; i <= end; i++)
                Align(
                  alignment: Alignment.center,
                  child: Transform(
                    alignment: Alignment.center,
                    transform: Matrix4.identity()
                      ..setEntry(3, 2, 0.0015)
                      ..translate(
                        (random.nextDouble() - 0.5) * 26,
                        (i - active) * rowHeight,
                        -70.0 * (i - active).abs(),
                      ),
                    child: _shipWord(
                      word: widget.words[i],
                      isActive: !preview && i == active,
                      isPast: i < active,
                      isPreview: preview,
                      accent: widget.accent,
                      primary: widget.primary,
                      secondary: widget.secondary,
                      fontSize: widget.fontSize,
                    ),
                  ),
                ),
            ],
          ),
        );
        return AnimatedBuilder(
          animation: _drift,
          child: stack,
          builder: (context, child) {
            final t = _drift.value * math.pi * 2;
            final ry = 11 * math.sin(t * 19 / 17) + 4 * math.sin(t * 19 / 7.3);
            final rx = 6 * math.sin(t * 19 / 13 + 1);
            final matrix = Matrix4.identity()
              ..setEntry(3, 2, 0.0012)
              ..rotateX(rx * math.pi / 180)
              ..rotateY(ry * math.pi / 180);
            return Transform(
              alignment: Alignment.center,
              transform: matrix,
              child: child,
            );
          },
        );
      },
    );
  }

  Widget _shipWord({
    required StudioLyricWord word,
    required bool isActive,
    required bool isPast,
    required bool isPreview,
    required Color accent,
    required Color primary,
    required Color secondary,
    required double fontSize,
  }) {
    final color = isActive
        ? const Color(0xFF18181B)
        : isPreview
        ? secondary
        : isPast
        ? primary.withValues(alpha: 0.72)
        : secondary.withValues(alpha: 0.62);
    return AnimatedScale(
      scale: isActive ? 1 : (isPast ? 0.9 : 0.84),
      duration: const Duration(milliseconds: 260),
      curve: Curves.easeOutBack,
      child: AnimatedOpacity(
        opacity: isPast ? 0.55 : (isPreview ? 0.35 : 1),
        duration: const Duration(milliseconds: 220),
        child: Container(
          padding: EdgeInsets.symmetric(
            horizontal: fontSize * 0.12,
            vertical: fontSize * 0.04,
          ),
          decoration: BoxDecoration(
            color: isActive ? accent : Colors.transparent,
            borderRadius: BorderRadius.circular(fontSize * 0.07),
          ),
          child: Text(
            word.text.toUpperCase(),
            style: _wordStyle(color: color, fontSize: fontSize, glow: false),
          ),
        ),
      ),
    );
  }
}

class _AestheticFisheye extends StatelessWidget {
  final String lineText;
  final List<StudioLyricWord> words;
  final int positionMs;
  final ValueListenable<int>? positionListenable;
  final Color accent;
  final Color primary;
  final Color secondary;
  final double fontSize;
  final bool withIcons;

  const _AestheticFisheye({
    required this.lineText,
    required this.words,
    required this.positionMs,
    required this.positionListenable,
    required this.accent,
    required this.primary,
    required this.secondary,
    required this.fontSize,
    required this.withIcons,
  });

  @override
  Widget build(BuildContext context) {
    return _positionAware(
      listenable: positionListenable,
      initial: positionMs,
      builder: (position) {
        final active = _activeIndex(words, position);
        final preview = _isPreview(words, position);
        final start = math.max(0, active - 4);
        final end = math.min(words.length - 1, active + 4);
        final step = fontSize * 1.02;
        final height = math.max(
          fontSize * 6,
          (end - start + 1) * step + fontSize,
        );
        return SizedBox(
          height: height,
          child: Stack(
            clipBehavior: Clip.none,
            alignment: Alignment.center,
            children: [
              for (var i = start; i <= end; i++)
                Align(
                  alignment: Alignment.center,
                  child: Transform.translate(
                    offset: Offset(0, (i - active) * step),
                    child: Transform.scale(
                      scale: i == active
                          ? 1
                          : (1 - 0.1 * (i - active).abs()).clamp(0.55, 1),
                      child: _fisheyeWord(
                        word: words[i],
                        isActive: !preview && i == active,
                        isPreview: preview,
                        accent: accent,
                        primary: primary,
                        secondary: secondary,
                        fontSize: fontSize,
                      ),
                    ),
                  ),
                ),
              Positioned.fill(
                child: IgnorePointer(
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      gradient: RadialGradient(
                        radius: 0.72,
                        colors: [
                          Colors.transparent,
                          Colors.black.withValues(alpha: 0.22),
                        ],
                        stops: const [0.45, 1],
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _fisheyeWord({
    required StudioLyricWord word,
    required bool isActive,
    required bool isPreview,
    required Color accent,
    required Color primary,
    required Color secondary,
    required double fontSize,
  }) {
    final icon = withIcons && isActive ? _iconFor(word.text) : null;
    return AnimatedOpacity(
      opacity: isActive ? 1 : (isPreview ? 0.35 : 0.6),
      duration: const Duration(milliseconds: 240),
      child: Container(
        padding: EdgeInsets.symmetric(
          horizontal: fontSize * 0.14,
          vertical: fontSize * 0.1,
        ),
        decoration: BoxDecoration(
          color: isActive ? accent : Colors.transparent,
          borderRadius: BorderRadius.circular(fontSize * 0.05),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              word.text.toUpperCase(),
              style: _wordStyle(
                color: isActive
                    ? const Color(0xFF18181B)
                    : (isPreview ? secondary : primary.withValues(alpha: 0.8)),
                fontSize: fontSize,
                glow: false,
              ),
            ),
            if (icon != null) ...[
              SizedBox(width: fontSize * 0.3),
              TweenAnimationBuilder<double>(
                tween: Tween(begin: 0, end: 1),
                duration: const Duration(milliseconds: 340),
                curve: Curves.easeOutBack,
                builder: (context, v, child) => Transform.rotate(
                  angle: (1 - v) * -0.24,
                  child: Transform.scale(scale: v, child: child),
                ),
                child: Icon(
                  icon,
                  size: fontSize * 0.84,
                  color: const Color(0xFF18181B),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _AestheticVisual extends StatelessWidget {
  final String lineText;
  final List<StudioLyricWord> words;
  final int positionMs;
  final ValueListenable<int>? positionListenable;
  final Color accent;
  final Color primary;
  final Color secondary;
  final double fontSize;

  const _AestheticVisual({
    required this.lineText,
    required this.words,
    required this.positionMs,
    required this.positionListenable,
    required this.accent,
    required this.primary,
    required this.secondary,
    required this.fontSize,
  });

  @override
  Widget build(BuildContext context) {
    return _positionAware(
      listenable: positionListenable,
      initial: positionMs,
      builder: (position) {
        final active = _activeIndex(words, position);
        final word = words[active];
        final icon = _iconFor(word.text) ?? FluentIcons.favorite_star;
        final seed = _hashText(word.text);
        return SizedBox(
          height: fontSize * 2.8,
          child: Stack(
            clipBehavior: Clip.none,
            alignment: Alignment.center,
            children: [
              // A whole ring of supporting icons lights up around the word,
              // so the style is always dense with pictures, not just one.
              for (var i = 0; i < 10; i++)
                _satellite(seed: seed, index: i),
              AnimatedSwitcher(
                duration: const Duration(milliseconds: 300),
                switchInCurve: Curves.easeOutBack,
                switchOutCurve: Curves.easeInCubic,
                transitionBuilder: (child, animation) {
                  final scale = Tween<double>(begin: 0.6, end: 1).animate(
                    CurvedAnimation(
                      parent: animation,
                      curve: Curves.easeOutBack,
                    ),
                  );
                  return FadeTransition(
                    opacity: animation,
                    child: ScaleTransition(scale: scale, child: child),
                  );
                },
                child: FittedBox(
                  key: ValueKey('$active-${word.text}'),
                  fit: BoxFit.scaleDown,
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(
                        word.text.toUpperCase(),
                        style: _wordStyle(
                          color: primary,
                          fontSize: fontSize * 1.28,
                          glow: true,
                        ),
                      ),
                      SizedBox(width: fontSize * 0.3),
                      Icon(icon, size: fontSize * 0.9, color: accent),
                    ],
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  /// One faint icon in the ring around the word. The ring placement and icon
  /// choice come from the word hash, so it changes on every lyric line but
  /// stays still while that word is sung.
  Widget _satellite({required int seed, required int index}) {
    final angle = (seed % 360) / 180 * math.pi + index * math.pi / 5;
    final rx = fontSize * (2.0 + ((seed >> (index * 3)) & 7) * 0.25);
    final ry = fontSize * (0.75 + ((seed >> (index * 5)) & 7) * 0.08);
    final icon = _iconPalette[(seed + index * 11) % _iconPalette.length];
    return Transform.translate(
      offset: Offset(math.cos(angle) * rx, math.sin(angle) * ry),
      child: Opacity(
        opacity: 0.38 + (index % 4) * 0.12,
        child: Icon(
          icon,
          size: fontSize * (0.26 + (index % 3) * 0.08),
          color: accent,
        ),
      ),
    );
  }
}

/// The stand-by icon set. A word outside the table still gets a stable icon
/// picked from here, so Icon Spark never falls back to text alone.
const _iconPalette = <IconData>[
  FluentIcons.favorite_star,
  FluentIcons.heart,
  FluentIcons.music_note,
  FluentIcons.sunny,
  FluentIcons.clear_night,
  FluentIcons.cloud,
  FluentIcons.drop,
  FluentIcons.snowflake,
  FluentIcons.lightning_bolt,
  FluentIcons.flame_solid,
  FluentIcons.flower,
  FluentIcons.city_next,
  FluentIcons.rocket,
  FluentIcons.globe,
  FluentIcons.camera,
  FluentIcons.headset,
  FluentIcons.compass_n_w,
  FluentIcons.mountain_climbing,
  FluentIcons.ferry,
  FluentIcons.balloons,
  FluentIcons.diamond,
  FluentIcons.crown,
  FluentIcons.shield,
  FluentIcons.trophy,
  FluentIcons.code,
  FluentIcons.game,
  FluentIcons.color,
  FluentIcons.chat,
];

/// Icon vocabulary for Icon Spark and Fisheye Icons: 300+ everyday lyric
/// words mapped to a Fluent icon, plus a hashed fallback above.
IconData? _iconFor(String text) {
  final w = text.toLowerCase().replaceAll(RegExp(r"[^a-z']"), '');
  if (w.isEmpty) return null;
  const icons = <String, IconData>{
    // Love, people and affection.
    'love': FluentIcons.heart_fill,
    'loved': FluentIcons.heart_fill,
    'lover': FluentIcons.heart_fill,
    'lovers': FluentIcons.heart_fill,
    'heart': FluentIcons.heart,
    'hearts': FluentIcons.heart,
    'heartbeat': FluentIcons.heart,
    'kiss': FluentIcons.heart,
    'kisses': FluentIcons.heart,
    'hug': FluentIcons.contact_heart,
    'soul': FluentIcons.contact_heart,
    'baby': FluentIcons.contact_heart,
    'honey': FluentIcons.heart,
    'darling': FluentIcons.heart,
    'sweet': FluentIcons.cake,
    'sweetheart': FluentIcons.heart,
    'girl': FluentIcons.contact_heart,
    'boy': FluentIcons.contact_heart,
    'friend': FluentIcons.people,
    'friends': FluentIcons.people,
    'family': FluentIcons.family,
    'people': FluentIcons.people,
    'crowd': FluentIcons.people,
    'stranger': FluentIcons.contact,
    'mother': FluentIcons.contact_heart,
    'father': FluentIcons.contact_heart,
    'sister': FluentIcons.contact_heart,
    'brother': FluentIcons.contact_heart,

    // Home, night and sky.
    'home': FluentIcons.home,
    'house': FluentIcons.home,
    'room': FluentIcons.home,
    'roof': FluentIcons.home,
    'bed': FluentIcons.home,
    'sleep': FluentIcons.clear_night,
    'sleeping': FluentIcons.clear_night,
    'dream': FluentIcons.cloud,
    'dreams': FluentIcons.cloud,
    'dreaming': FluentIcons.cloud,
    'night': FluentIcons.clear_night,
    'nights': FluentIcons.clear_night,
    'midnight': FluentIcons.clear_night,
    'tonight': FluentIcons.clear_night,
    'moon': FluentIcons.clear_night,
    'moonlight': FluentIcons.clear_night,
    'star': FluentIcons.favorite_star,
    'stars': FluentIcons.favorite_star,
    'starlight': FluentIcons.favorite_star,
    'sky': FluentIcons.cloud,
    'skies': FluentIcons.cloud,
    'sun': FluentIcons.sunny,
    'sunshine': FluentIcons.sunny,
    'sunny': FluentIcons.sunny,
    'sunrise': FluentIcons.partly_sunny_day,
    'dawn': FluentIcons.partly_sunny_day,
    'morning': FluentIcons.sunny,
    'day': FluentIcons.sunny,
    'daylight': FluentIcons.sunny,
    'light': FluentIcons.lightbulb,
    'lights': FluentIcons.lightbulb,
    'shine': FluentIcons.lightbulb,
    'shining': FluentIcons.lightbulb,
    'glow': FluentIcons.lightbulb,
    'bright': FluentIcons.sunny,

    // Fire, weather and water.
    'fire': FluentIcons.flame_solid,
    'flame': FluentIcons.flame_solid,
    'flames': FluentIcons.flame_solid,
    'burn': FluentIcons.flame_solid,
    'burning': FluentIcons.flame_solid,
    'hot': FluentIcons.flame_solid,
    'warm': FluentIcons.flame_solid,
    'spark': FluentIcons.lightning_bolt,
    'sparks': FluentIcons.lightning_bolt,
    'lightning': FluentIcons.lightning_bolt,
    'thunder': FluentIcons.thunderstorms,
    'storm': FluentIcons.thunderstorms,
    'storms': FluentIcons.thunderstorms,
    'rain': FluentIcons.rain,
    'raining': FluentIcons.rain,
    'rainy': FluentIcons.rain,
    'tears': FluentIcons.drop,
    'tear': FluentIcons.drop,
    'snow': FluentIcons.snowflake,
    'snowing': FluentIcons.snowflake,
    'cold': FluentIcons.snowflake,
    'ice': FluentIcons.snowflake,
    'frozen': FluentIcons.snowflake,
    'winter': FluentIcons.snowflake,
    'cloud': FluentIcons.cloud,
    'clouds': FluentIcons.cloud,
    'cloudy': FluentIcons.cloud,
    'fog': FluentIcons.cloud,
    'smoke': FluentIcons.cloud,
    'wind': FluentIcons.cloud,
    'breeze': FluentIcons.cloud,
    'air': FluentIcons.cloud,
    'breathe': FluentIcons.cloud,
    'breathing': FluentIcons.cloud,
    'weather': FluentIcons.partly_sunny_day,
    'sea': FluentIcons.ferry,
    'ocean': FluentIcons.ferry,
    'waves': FluentIcons.ferry,
    'water': FluentIcons.drop,
    'river': FluentIcons.ferry,
    'lake': FluentIcons.ferry,
    'swim': FluentIcons.ferry,
    'swimming': FluentIcons.ferry,
    'drown': FluentIcons.drop,
    'drowning': FluentIcons.drop,
    'boat': FluentIcons.ferry,
    'ship': FluentIcons.ferry,
    'sail': FluentIcons.ferry,
    'sailing': FluentIcons.ferry,
    'beach': FluentIcons.ferry,
    'shore': FluentIcons.ferry,
    'tide': FluentIcons.ferry,
    'anchor': FluentIcons.anchor_lock,

    // Streets, travelling and places.
    'city': FluentIcons.city_next,
    'town': FluentIcons.city_next,
    'street': FluentIcons.city_next,
    'streets': FluentIcons.city_next,
    'road': FluentIcons.city_next,
    'roads': FluentIcons.city_next,
    'drive': FluentIcons.car,
    'driving': FluentIcons.car,
    'car': FluentIcons.car,
    'cars': FluentIcons.car,
    'ride': FluentIcons.car,
    'taxi': FluentIcons.car,
    'bus': FluentIcons.bus,
    'train': FluentIcons.train,
    'plane': FluentIcons.airplane,
    'flight': FluentIcons.airplane,
    'fly': FluentIcons.airplane,
    'flying': FluentIcons.airplane,
    'airport': FluentIcons.airplane,
    'travel': FluentIcons.map_pin,
    'journey': FluentIcons.map_pin,
    'adventure': FluentIcons.compass_n_w,
    'wander': FluentIcons.compass_n_w,
    'map': FluentIcons.map_pin,
    'roadtrip': FluentIcons.map_pin,

    // Money, shopping and objects.
    'money': FluentIcons.money,
    'cash': FluentIcons.money,
    'rich': FluentIcons.money,
    'pay': FluentIcons.payment_card,
    'paid': FluentIcons.payment_card,
    'payment': FluentIcons.payment_card,
    'bill': FluentIcons.bill,
    'bills': FluentIcons.bill,
    'bank': FluentIcons.bank,
    'dollar': FluentIcons.money,
    'dollars': FluentIcons.money,
    'coin': FluentIcons.bank,
    'coins': FluentIcons.bank,
    'credit': FluentIcons.payment_card,
    'card': FluentIcons.payment_card,
    'gold': FluentIcons.medal,
    'silver': FluentIcons.medal,
    'price': FluentIcons.tag,
    'tag': FluentIcons.tag,
    'ticket': FluentIcons.ticket,
    'tickets': FluentIcons.ticket,
    'shopping': FluentIcons.shopping_cart,
    'shop': FluentIcons.shopping_cart,
    'store': FluentIcons.shopping_cart,
    'buy': FluentIcons.shopping_cart,
    'bought': FluentIcons.shopping_cart,
    'sell': FluentIcons.money,
    'sold': FluentIcons.money,
    'gift': FluentIcons.giftbox,
    'gifts': FluentIcons.giftbox,
    'present': FluentIcons.giftbox,
    'door': FluentIcons.door,
    'doors': FluentIcons.door,
    'key': FluentIcons.lock,
    'keys': FluentIcons.lock,
    'lock': FluentIcons.lock,
    'locked': FluentIcons.lock,
    'unlock': FluentIcons.lock,
    'open': FluentIcons.door,
    'close': FluentIcons.door,
    'closed': FluentIcons.door,

    // Time and dates.
    'time': FluentIcons.clock,
    'times': FluentIcons.clock,
    'clock': FluentIcons.clock,
    'hour': FluentIcons.clock,
    'hours': FluentIcons.clock,
    'minute': FluentIcons.clock,
    'minutes': FluentIcons.clock,
    'second': FluentIcons.timer,
    'seconds': FluentIcons.timer,
    'forever': FluentIcons.timer,
    'always': FluentIcons.timer,
    'never': FluentIcons.timer,
    'yesterday': FluentIcons.history,
    'tomorrow': FluentIcons.calendar,
    'today': FluentIcons.calendar,
    'evening': FluentIcons.clear_night,
    'date': FluentIcons.calendar,
    'week': FluentIcons.calendar,
    'month': FluentIcons.calendar,
    'year': FluentIcons.calendar,
    'years': FluentIcons.calendar,
    'birthday': FluentIcons.birthday_cake,
    'anniversary': FluentIcons.cake,
    'party': FluentIcons.balloons,
    'holiday': FluentIcons.balloons,
    'christmas': FluentIcons.snowflake,

    // Music and performance.
    'music': FluentIcons.music_note,
    'song': FluentIcons.music_note,
    'songs': FluentIcons.playlist_music,
    'sing': FluentIcons.microphone,
    'singing': FluentIcons.microphone,
    'sung': FluentIcons.microphone,
    'melody': FluentIcons.guitar,
    'tune': FluentIcons.guitar,
    'beat': FluentIcons.speakers,
    'rhythm': FluentIcons.speakers,
    'guitar': FluentIcons.guitar,
    'piano': FluentIcons.music_note,
    'drum': FluentIcons.speakers,
    'drums': FluentIcons.speakers,
    'bass': FluentIcons.speakers,
    'chorus': FluentIcons.microphone,
    'radio': FluentIcons.headset,
    'stereo': FluentIcons.speakers,
    'speaker': FluentIcons.speakers,
    'headphones': FluentIcons.headset,
    'listen': FluentIcons.headset,
    'listening': FluentIcons.headset,
    'dance': FluentIcons.balloons,
    'dancing': FluentIcons.balloons,
    'danced': FluentIcons.balloons,
    'play': FluentIcons.game,
    'playing': FluentIcons.game,
    'played': FluentIcons.game,
    'game': FluentIcons.game,
    'games': FluentIcons.game,
    'playlist': FluentIcons.playlist_music,

    // Talking, messages and writing.
    'phone': FluentIcons.phone,
    'call': FluentIcons.phone,
    'calling': FluentIcons.phone,
    'ringing': FluentIcons.phone,
    'text': FluentIcons.chat,
    'message': FluentIcons.chat,
    'messages': FluentIcons.chat,
    'chat': FluentIcons.chat,
    'talk': FluentIcons.chat,
    'talking': FluentIcons.chat,
    'speak': FluentIcons.microphone,
    'say': FluentIcons.chat,
    'said': FluentIcons.chat,
    'tell': FluentIcons.chat,
    'told': FluentIcons.chat,
    'word': FluentIcons.chat,
    'words': FluentIcons.chat,
    'whisper': FluentIcons.chat,
    'shout': FluentIcons.microphone,
    'scream': FluentIcons.microphone,
    'mail': FluentIcons.mail,
    'letter': FluentIcons.mail,
    'letters': FluentIcons.mail,
    'write': FluentIcons.mail,
    'writing': FluentIcons.mail,
    'wrote': FluentIcons.mail,
    'book': FluentIcons.book_answers,
    'books': FluentIcons.library,
    'read': FluentIcons.book_answers,
    'reading': FluentIcons.book_answers,
    'story': FluentIcons.book_answers,
    'stories': FluentIcons.book_answers,
    'page': FluentIcons.book_answers,
    'pages': FluentIcons.book_answers,
    'paper': FluentIcons.book_answers,
    'poem': FluentIcons.book_answers,
    'poetry': FluentIcons.book_answers,

    // Eyes and feelings.
    'eye': FluentIcons.red_eye,
    'eyes': FluentIcons.red_eye,
    'see': FluentIcons.red_eye,
    'seen': FluentIcons.red_eye,
    'saw': FluentIcons.red_eye,
    'look': FluentIcons.red_eye,
    'looking': FluentIcons.red_eye,
    'watch': FluentIcons.red_eye,
    'watching': FluentIcons.red_eye,
    'face': FluentIcons.emoji,
    'smile': FluentIcons.emoji,
    'smiling': FluentIcons.emoji,
    'laugh': FluentIcons.emoji,
    'laughing': FluentIcons.emoji,
    'happy': FluentIcons.emoji,
    'joy': FluentIcons.balloons,
    'hope': FluentIcons.sunny,
    'hopeful': FluentIcons.sunny,
    'free': FluentIcons.airplane,
    'freedom': FluentIcons.airplane,
    'alive': FluentIcons.heart_fill,
    'life': FluentIcons.heart_fill,
    'live': FluentIcons.heart_fill,
    'living': FluentIcons.heart_fill,
    'sad': FluentIcons.sad,
    'sadness': FluentIcons.sad,
    'cry': FluentIcons.sad,
    'crying': FluentIcons.sad,
    'pain': FluentIcons.sad,
    'hurt': FluentIcons.heart_broken,
    'hurting': FluentIcons.heart_broken,
    'broken': FluentIcons.heart_broken,
    'broke': FluentIcons.heart_broken,
    'heartbreak': FluentIcons.heart_broken,
    'lonely': FluentIcons.emoji_disappointed,
    'alone': FluentIcons.emoji_disappointed,
    'empty': FluentIcons.emoji_neutral,
    'lost': FluentIcons.compass_n_w,
    'miss': FluentIcons.contact_heart,
    'missing': FluentIcons.contact_heart,

    // Movement, power and royalty.
    'run': FluentIcons.running,
    'running': FluentIcons.running,
    'ran': FluentIcons.running,
    'walk': FluentIcons.running,
    'walking': FluentIcons.running,
    'walked': FluentIcons.running,
    'move': FluentIcons.running,
    'moving': FluentIcons.running,
    'jump': FluentIcons.running,
    'jumping': FluentIcons.running,
    'fall': FluentIcons.drop,
    'falling': FluentIcons.drop,
    'fell': FluentIcons.drop,
    'rise': FluentIcons.sunny,
    'rising': FluentIcons.sunny,
    'climb': FluentIcons.mountain_climbing,
    'climbing': FluentIcons.mountain_climbing,
    'high': FluentIcons.mountain_climbing,
    'top': FluentIcons.mountain_climbing,
    'fight': FluentIcons.shield,
    'fighting': FluentIcons.shield,
    'fought': FluentIcons.shield,
    'war': FluentIcons.shield,
    'battle': FluentIcons.shield,
    'win': FluentIcons.trophy,
    'winning': FluentIcons.trophy,
    'won': FluentIcons.trophy,
    'lose': FluentIcons.sad,
    'losing': FluentIcons.sad,
    'strong': FluentIcons.shield,
    'stronger': FluentIcons.shield,
    'brave': FluentIcons.shield,
    'power': FluentIcons.lightning_bolt,
    'king': FluentIcons.crown,
    'queen': FluentIcons.crown,
    'crown': FluentIcons.crown,
    'throne': FluentIcons.crown,
    'royal': FluentIcons.crown,
    'diamond': FluentIcons.diamond,
    'diamonds': FluentIcons.diamond,
    'ring': FluentIcons.diamond,
    'rings': FluentIcons.diamond,
    'jewel': FluentIcons.diamond,
    'victory': FluentIcons.trophy,
    'champion': FluentIcons.medal,
    'medal': FluentIcons.medal,
    'goal': FluentIcons.bullseye,
    'aim': FluentIcons.bullseye,
    'focus': FluentIcons.bullseye,

    // School, work and technology.
    'school': FluentIcons.education,
    'class': FluentIcons.education,
    'teacher': FluentIcons.education,
    'learn': FluentIcons.education,
    'learning': FluentIcons.education,
    'study': FluentIcons.education,
    'studying': FluentIcons.education,
    'student': FluentIcons.education,
    'college': FluentIcons.education,
    'work': FluentIcons.laptop_selected,
    'working': FluentIcons.laptop_selected,
    'job': FluentIcons.laptop_selected,
    'office': FluentIcons.office_chat,
    'business': FluentIcons.office_chat,
    'boss': FluentIcons.contact,
    'computer': FluentIcons.tablet,
    'code': FluentIcons.code,
    'coding': FluentIcons.code,
    'program': FluentIcons.code,
    'hacker': FluentIcons.code,
    'internet': FluentIcons.wifi,
    'online': FluentIcons.wifi,
    'wifi': FluentIcons.wifi,
    'signal': FluentIcons.wifi,
    'robot': FluentIcons.robot,
    'machine': FluentIcons.robot,
    'tech': FluentIcons.robot,
    'future': FluentIcons.rocket,
    'digital': FluentIcons.code,
    'tablet': FluentIcons.tablet,
    'keyboard': FluentIcons.keyboard_classic,
    'plug': FluentIcons.plug,
    'battery': FluentIcons.plug,
    'calculator': FluentIcons.calculator,
    'chart': FluentIcons.chart,
    'history': FluentIcons.history,
    'factory': FluentIcons.factory,
    'library': FluentIcons.library,

    // Pictures, food and nights out.
    'camera': FluentIcons.camera,
    'photo': FluentIcons.photo,
    'photos': FluentIcons.photo,
    'picture': FluentIcons.photo,
    'pictures': FluentIcons.photo,
    'film': FluentIcons.video,
    'movie': FluentIcons.video,
    'movies': FluentIcons.video,
    'video': FluentIcons.video,
    'videos': FluentIcons.video,
    'screen': FluentIcons.video,
    'tv': FluentIcons.video,
    'television': FluentIcons.video,
    'show': FluentIcons.video,
    'food': FluentIcons.eat_drink,
    'eat': FluentIcons.eat_drink,
    'eating': FluentIcons.eat_drink,
    'ate': FluentIcons.eat_drink,
    'dinner': FluentIcons.eat_drink,
    'lunch': FluentIcons.eat_drink,
    'breakfast': FluentIcons.coffee,
    'hungry': FluentIcons.eat_drink,
    'cake': FluentIcons.cake,
    'sugar': FluentIcons.cake,
    'coffee': FluentIcons.coffee,
    'tea': FluentIcons.coffee,
    'cup': FluentIcons.coffee,
    'drink': FluentIcons.cocktails,
    'drinking': FluentIcons.cocktails,
    'drank': FluentIcons.cocktails,
    'wine': FluentIcons.cocktails,
    'beer': FluentIcons.beer_mug,
    'whiskey': FluentIcons.cocktails,
    'alcohol': FluentIcons.cocktails,
    'drunk': FluentIcons.cocktails,
    'cocktail': FluentIcons.cocktails,
    'cocktails': FluentIcons.cocktails,

    // Nature, space and animals.
    'flower': FluentIcons.flower,
    'flowers': FluentIcons.flower,
    'rose': FluentIcons.flower,
    'roses': FluentIcons.flower,
    'garden': FluentIcons.flower,
    'bloom': FluentIcons.flower,
    'blooming': FluentIcons.flower,
    'spring': FluentIcons.flower,
    'tree': FluentIcons.flower,
    'trees': FluentIcons.flower,
    'leaf': FluentIcons.flower,
    'leaves': FluentIcons.flower,
    'forest': FluentIcons.flower,
    'mountain': FluentIcons.mountain_climbing,
    'mountains': FluentIcons.mountain_climbing,
    'hill': FluentIcons.mountain_climbing,
    'hills': FluentIcons.mountain_climbing,
    'earth': FluentIcons.globe,
    'world': FluentIcons.globe,
    'globe': FluentIcons.globe,
    'planet': FluentIcons.globe,
    'space': FluentIcons.rocket,
    'universe': FluentIcons.globe,
    'galaxy': FluentIcons.favorite_star,
    'rocket': FluentIcons.rocket,
    'spaceship': FluentIcons.rocket,
    'astronaut': FluentIcons.rocket,
    'mars': FluentIcons.globe,
    'cat': FluentIcons.cat,
    'cats': FluentIcons.cat,
    'kitten': FluentIcons.cat,
    'dog': FluentIcons.cat,
    'dogs': FluentIcons.cat,
    'puppy': FluentIcons.cat,
    'pet': FluentIcons.cat,
    'bird': FluentIcons.airplane,
    'birds': FluentIcons.airplane,
    'fish': FluentIcons.ferry,
    'butterfly': FluentIcons.flower,
    'bee': FluentIcons.bug,
    'animal': FluentIcons.cat,
    'animals': FluentIcons.cat,

    // Faith, colour and the body.
    'god': FluentIcons.favorite_star,
    'heaven': FluentIcons.favorite_star,
    'angel': FluentIcons.favorite_star,
    'pray': FluentIcons.favorite_star,
    'prayer': FluentIcons.favorite_star,
    'faith': FluentIcons.favorite_star,
    'bless': FluentIcons.favorite_star,
    'blessed': FluentIcons.favorite_star,
    'holy': FluentIcons.favorite_star,
    'devil': FluentIcons.emoji_disappointed,
    'hell': FluentIcons.emoji_disappointed,
    'sin': FluentIcons.emoji_disappointed,
    'demon': FluentIcons.emoji_disappointed,
    'monster': FluentIcons.emoji_disappointed,
    'ghost': FluentIcons.emoji_neutral,
    'dark': FluentIcons.clear_night,
    'darkness': FluentIcons.clear_night,
    'shadow': FluentIcons.clear_night,
    'black': FluentIcons.color,
    'white': FluentIcons.color,
    'red': FluentIcons.color,
    'blue': FluentIcons.color,
    'green': FluentIcons.color,
    'yellow': FluentIcons.color,
    'purple': FluentIcons.color,
    'pink': FluentIcons.color,
    'colour': FluentIcons.color,
    'color': FluentIcons.color,
    'shirt': FluentIcons.shirt,
    'clothes': FluentIcons.shirt,
    'dress': FluentIcons.shirt,
    'jacket': FluentIcons.shirt,
    'shoes': FluentIcons.shirt,
    'hat': FluentIcons.shirt,
    'glasses': FluentIcons.glasses,
    'health': FluentIcons.health,
    'sick': FluentIcons.health,
    'ill': FluentIcons.medical,
    'doctor': FluentIcons.medical,
    'hospital': FluentIcons.hospital,
    'medicine': FluentIcons.pill,
    'pill': FluentIcons.pill,
    'pills': FluentIcons.pill,
    'flag': FluentIcons.flag,
    'nation': FluentIcons.flag,
    'country': FluentIcons.flag,
    'police': FluentIcons.shield,
    'cop': FluentIcons.shield,
    'crime': FluentIcons.shield,
    'brush': FluentIcons.brush,
    'paint': FluentIcons.brush,
    'art': FluentIcons.color,
    'draw': FluentIcons.brush,
    'drawing': FluentIcons.brush,
  };
  final exact = icons[w];
  if (exact != null) return exact;
  return _iconPalette[_hashText(w) % _iconPalette.length];
}

// Signature designs: Branches / Constellation
// ---------------------------------------------------------------------------

/// How much of [word] has been sung at [positionMs], 0..1.
double _wordProgress(StudioLyricWord word, int positionMs) {
  if (word.endMs <= word.startMs) {
    return positionMs >= word.startMs ? 1.0 : 0.0;
  }
  return ((positionMs - word.startMs) / (word.endMs - word.startMs)).clamp(
    0.0,
    1.0,
  );
}

/// One word anchor on a spine (Branches) or a sky star (Constellation).
class _SpineNode {
  final Offset point;
  final double textLeft;
  final double textTop;

  const _SpineNode(this.point, this.textLeft, this.textTop);
}

class _SpineLayout {
  final List<_SpineNode> nodes;
  final double size;

  const _SpineLayout(this.nodes, this.size);
}

/// Places words along a gently waving horizontal spine, shrinking the type
/// until the whole line fits [width].
_SpineLayout _layoutSpine({
  required List<StudioLyricWord> words,
  required double width,
  required double height,
  required double fontSize,
  required String seedText,
  required bool isRtl,
  required bool labelsBelow,
  required double amplitude,
  required double baseline,
}) {
  final n = words.length;
  var size = fontSize * (labelsBelow ? 0.46 : 0.62);
  final gapFactor = labelsBelow ? 0.62 : 0.46;
  var widths = List<double>.filled(n, 0);
  for (var attempt = 0; attempt < 12; attempt++) {
    widths = [for (final word in words) _visualLength(word.text) * size];
    final total =
        widths.fold<double>(0, (a, b) => a + b) + size * gapFactor * (n - 1);
    if (total <= width * 0.94 || size <= 11) break;
    size *= 0.88;
  }
  final total =
      widths.fold<double>(0, (a, b) => a + b) + size * gapFactor * (n - 1);
  final phase = (_hashText(seedText) % 628) / 100;
  final rowY = height * baseline;
  var x = math.max(0.0, (width - total) / 2);
  final nodes = <_SpineNode>[];
  for (var i = 0; i < n; i++) {
    final index = isRtl ? n - 1 - i : i;
    final centerX = x + widths[index] / 2;
    final y = rowY + math.sin(i * 1.12 + phase) * amplitude;
    nodes.add(
      _SpineNode(
        Offset(centerX, y),
        centerX - widths[index] / 2,
        labelsBelow ? y + size * 0.52 : y - size * 1.08,
      ),
    );
    x += widths[index] + size * gapFactor;
  }
  return _SpineLayout(nodes, size);
}

/// Branches: a vine draws itself under the line and each word lights a node
/// on it as the singer reaches that word.
class _BranchesLine extends StatefulWidget {
  final String lineText;
  final List<StudioLyricWord> words;
  final int positionMs;
  final ValueListenable<int>? positionListenable;
  final Color accent;
  final Color primary;
  final Color secondary;
  final double fontSize;
  final bool isRtl;
  final bool reduceMotion;

  const _BranchesLine({
    required this.lineText,
    required this.words,
    required this.positionMs,
    required this.positionListenable,
    required this.accent,
    required this.primary,
    required this.secondary,
    required this.fontSize,
    required this.isRtl,
    required this.reduceMotion,
  });

  @override
  State<_BranchesLine> createState() => _BranchesLineState();
}

class _BranchesLineState extends State<_BranchesLine> {
  // The spine, vine path and its metrics depend only on the line and the
  // canvas size. Caching them keeps the 60 fps position tick from measuring
  // every word and walking the path again on each frame.
  _SpineLayout? _layout;
  Path? _vine;
  PathMetric? _metric;
  String _cacheKey = '';

  void _ensureGeometry(double width, double height) {
    final key =
        '${widget.lineText}|${widget.words.length}|'
        '${width.toStringAsFixed(1)}|${height.toStringAsFixed(1)}|'
        '${widget.fontSize.toStringAsFixed(1)}|${widget.isRtl}';
    if (key == _cacheKey) return;
    _cacheKey = key;
    final layout = _layoutSpine(
      words: widget.words,
      width: width,
      height: height,
      fontSize: widget.fontSize,
      seedText: widget.lineText,
      isRtl: widget.isRtl,
      labelsBelow: false,
      amplitude: widget.fontSize * 0.26,
      baseline: 0.66,
    );
    final vine = _vinePath([for (final node in layout.nodes) node.point]);
    // PathMetrics is single-pass, so take the first metric from its iterator
    // instead of asking isEmpty and then first.
    final metrics = vine.computeMetrics().iterator;
    _layout = layout;
    _vine = vine;
    _metric = metrics.moveNext() ? metrics.current : null;
  }

  @override
  Widget build(BuildContext context) {
    return _positionAware(
      listenable: widget.positionListenable,
      initial: widget.positionMs,
      builder: (position) {
        return LayoutBuilder(
          builder: (context, constraints) {
            final width = constraints.maxWidth.isFinite
                ? constraints.maxWidth
                : MediaQuery.sizeOf(context).width;
            final height = math.max(widget.fontSize * 2.6, 96.0);
            _ensureGeometry(width, height);
            final layout = _layout!;
            final revealed = _revealedCount(widget.words, position);
            final preview = _isPreview(widget.words, position);
            final active = preview ? -1 : revealed - 1;
            final activeProgress = active >= 0
                ? _wordProgress(widget.words[active], position)
                : 0.0;
            var fraction = 0.0;
            if (active >= 0) {
              fraction = (active + activeProgress) / widget.words.length;
            }
            return SizedBox(
              width: width,
              height: height,
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  Positioned.fill(
                    child: CustomPaint(
                      painter: _BranchesPainter(
                        points: [for (final node in layout.nodes) node.point],
                        vine: _vine,
                        metric: _metric,
                        size: layout.size,
                        fraction: fraction.clamp(0.0, 1.0),
                        revealed: revealed,
                        active: active,
                        activeProgress: activeProgress,
                        accent: widget.accent,
                      ),
                    ),
                  ),
                  for (var i = 0; i < widget.words.length; i++)
                    Positioned(
                      left: layout.nodes[i].textLeft,
                      top: layout.nodes[i].textTop,
                      child: AnimatedScale(
                        scale: i == active
                            ? 1 + 0.06 * math.sin(activeProgress * math.pi)
                            : 1,
                        alignment: Alignment.bottomCenter,
                        duration: widget.reduceMotion
                            ? Duration.zero
                            : const Duration(milliseconds: 200),
                        curve: Curves.easeOutCubic,
                        child: AnimatedDefaultTextStyle(
                          duration: widget.reduceMotion
                              ? Duration.zero
                              : const Duration(milliseconds: 220),
                          curve: Curves.easeOutCubic,
                          style: _wordStyle(
                            color: i == active
                                ? widget.accent
                                : i < revealed
                                ? widget.primary
                                : widget.secondary.withValues(alpha: 0.42),
                            fontSize: layout.size,
                            glow: i == active,
                          ),
                          child: Text(widget.words[i].text.toUpperCase()),
                        ),
                      ),
                    ),
                ],
              ),
            );
          },
        );
      },
    );
  }
}

/// The vine under a Branches line, one quadratic curve between star nodes.
Path _vinePath(List<Offset> points) {
  final path = Path();
  if (points.isEmpty) return path;
  path.moveTo(points.first.dx, points.first.dy);
  if (points.length == 1) {
    path.lineTo(points.first.dx + 0.1, points.first.dy);
    return path;
  }
  for (var i = 1; i < points.length; i++) {
    final a = points[i - 1];
    final b = points[i];
    final mid = Offset((a.dx + b.dx) / 2, (a.dy + b.dy) / 2);
    path.quadraticBezierTo(a.dx, a.dy, mid.dx, mid.dy);
    if (i == points.length - 1) {
      path.quadraticBezierTo(b.dx, b.dy, b.dx, b.dy);
    }
  }
  return path;
}

class _BranchesPainter extends CustomPainter {
  final List<Offset> points;
  final Path? vine;
  final PathMetric? metric;
  final double size;
  final double fraction;
  final int revealed;
  final int active;
  final double activeProgress;
  final Color accent;

  const _BranchesPainter({
    required this.points,
    required this.vine,
    required this.metric,
    required this.size,
    required this.fraction,
    required this.revealed,
    required this.active,
    required this.activeProgress,
    required this.accent,
  });

  @override
  void paint(Canvas canvas, Size canvasSize) {
    final metric = this.metric;
    final vine = this.vine;
    if (metric == null || vine == null) return;

    canvas.drawPath(
      vine,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.6
        ..strokeCap = StrokeCap.round
        ..color = Colors.white.withValues(alpha: 0.12),
    );

    final grown = metric.length * fraction;

    // Leaf pairs sprout along the part of the vine that has grown, alternating
    // sides, so the stem reads as a plant rather than a line.
    final leafSpacing = math.max(18.0, size * 0.9);
    var flip = false;
    for (var d = leafSpacing * 0.55; d < grown; d += leafSpacing) {
      final tangent = metric.getTangentForOffset(d);
      if (tangent == null) continue;
      final normal = Offset(-tangent.vector.dy, tangent.vector.dx);
      _leaf(
        canvas,
        tangent.position + normal * (size * 0.05),
        tangent.vector,
        normal,
        size * 0.34,
        flip ? -1 : 1,
        Paint()..color = accent.withValues(alpha: 0.16),
      );
      flip = !flip;
    }

    if (grown > 0) {
      final lit = metric.extractPath(0, grown);
      // A wide translucent stroke instead of a blurred one: the glow used to
      // run a mask blur over the whole grown path on every frame.
      canvas.drawPath(
        lit,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 10
          ..strokeCap = StrokeCap.round
          ..color = accent.withValues(alpha: 0.11),
      );
      canvas.drawPath(
        lit,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 3.2
          ..strokeCap = StrokeCap.round
          ..color = accent.withValues(alpha: 0.9),
      );
      final tip = metric.getTangentForOffset(grown)?.position;
      if (tip != null) {
        canvas.drawCircle(
          tip,
          size * 0.24,
          Paint()..color = accent.withValues(alpha: 0.12),
        );
        canvas.drawCircle(
          tip,
          size * 0.09,
          Paint()..color = Colors.white.withValues(alpha: 0.9),
        );
      }
    }

    for (var i = 0; i < points.length; i++) {
      final p = points[i];
      if (i >= revealed) {
        canvas.drawCircle(
          p,
          size * 0.11,
          Paint()..color = Colors.white.withValues(alpha: 0.06),
        );
        canvas.drawCircle(
          p,
          size * 0.045,
          Paint()..color = Colors.white.withValues(alpha: 0.3),
        );
        continue;
      }
      final open = i == active ? activeProgress.clamp(0.0, 1.0) : 1.0;
      canvas.drawCircle(
        p,
        size * (0.24 + 0.10 * open),
        Paint()..color = accent.withValues(alpha: 0.10 + 0.10 * open),
      );
      // Five petals unfurl as the word is sung, the active bloom still opening.
      for (var k = 0; k < 5; k++) {
        final angle = -math.pi / 2 + k * 2 * math.pi / 5 + (1 - open) * 0.9;
        final petal =
            p +
            Offset(math.cos(angle), math.sin(angle)) *
                size *
                (0.13 + 0.12 * open);
        canvas.save();
        canvas.translate(petal.dx, petal.dy);
        canvas.rotate(angle);
        canvas.drawOval(
          Rect.fromCenter(
            center: Offset.zero,
            width: size * (0.16 + 0.10 * open),
            height: size * 0.11,
          ),
          Paint()..color = accent.withValues(alpha: 0.24 + 0.16 * open),
        );
        canvas.restore();
      }
      canvas.drawCircle(
        p,
        size * 0.075,
        Paint()..color = Colors.white.withValues(alpha: 0.9),
      );
    }
  }

  void _leaf(
    Canvas canvas,
    Offset base,
    Offset dir,
    Offset normal,
    double length,
    double side,
    Paint paint,
  ) {
    final n = normal * side;
    final tip = base + n * (length * 0.95) + dir * (length * 0.34);
    final c1 = base + n * (length * 0.6) + dir * (length * 0.06);
    final c2 = base + n * (length * 0.48) + dir * (length * 0.4);
    final path = Path()
      ..moveTo(base.dx, base.dy)
      ..quadraticBezierTo(c1.dx, c1.dy, tip.dx, tip.dy)
      ..quadraticBezierTo(c2.dx, c2.dy, base.dx, base.dy)
      ..close();
    canvas.drawPath(path, paint);
  }

  @override
  bool shouldRepaint(covariant _BranchesPainter oldDelegate) =>
      oldDelegate.metric != metric ||
      oldDelegate.vine != vine ||
      oldDelegate.revealed != revealed ||
      oldDelegate.fraction != fraction ||
      oldDelegate.active != active ||
      oldDelegate.activeProgress != activeProgress ||
      oldDelegate.accent != accent ||
      !listEquals(oldDelegate.points, points);
}

/// A real night sky: the line's words are stars at scattered, irregular
/// positions, joined into a constellation figure, with each word labelling
/// its own star from below.
class _ConstellationLayout {
  final List<_SpineNode> nodes;
  final double size;
  final List<Offset> sky;
  final List<double> segments;
  final double length;

  const _ConstellationLayout({
    required this.nodes,
    required this.size,
    required this.sky,
    required this.segments,
    required this.length,
  });
}

_ConstellationLayout _layoutConstellation({
  required List<StudioLyricWord> words,
  required double width,
  required double height,
  required double fontSize,
  required String seedText,
  required bool isRtl,
}) {
  final n = words.length;
  final seed = _hashText(seedText);
  final random = math.Random(seed ^ 0x2c1b3c6d);

  // Labels are measured once; the random star scatter below never changes
  // their width, so a long line is shrunk until every label fits.
  var size = fontSize * 0.46;
  var widths = List<double>.filled(n, 0);
  for (var attempt = 0; attempt < 12; attempt++) {
    widths = [for (final word in words) _visualLength(word.text) * size];
    final total =
        widths.fold<double>(0, (a, b) => a + b) + size * 0.7 * (n - 1);
    if (total <= width * 0.9 || size <= 10) break;
    size *= 0.88;
  }

  final labelTotal = widths.fold<double>(0, (a, b) => a + b);
  final free = math.max(0.0, width * 0.92 - labelTotal);
  final gaps = [
    for (var i = 0; i <= n; i++) free * (0.35 + random.nextDouble()),
  ];
  final gapSum = gaps.fold<double>(0, (a, b) => a + b);
  final gapScale = free <= 0 || gapSum == 0 ? 0.0 : free / gapSum;
  final top = height * 0.10;
  final bottom = height * 0.50;
  var x = width * 0.04 + gaps[0] * gapScale;
  var lastY = height * 0.33;
  final nodes = <_SpineNode>[];
  for (var i = 0; i < n; i++) {
    final index = isRtl ? n - 1 - i : i;
    var y = top + random.nextDouble() * (bottom - top);
    // Neighbouring stars should not line up like a ruler.
    if ((y - lastY).abs() < size * 0.8) {
      y = y < lastY
          ? lastY - size * (0.9 + random.nextDouble())
          : lastY + size * (0.9 + random.nextDouble());
      y = y.clamp(top, bottom);
    }
    lastY = y;
    final centerX = x + widths[index] / 2;
    nodes.add(
      _SpineNode(
        Offset(centerX, y),
        centerX - widths[index] / 2,
        y + size * (0.55 + random.nextDouble() * 0.35),
      ),
    );
    x += widths[index] + gaps[i + 1] * gapScale;
  }

  final skyRandom = math.Random(seed ^ 0x1f123bb5);
  final sky = [
    for (var i = 0; i < 64; i++)
      Offset(skyRandom.nextDouble() * width, skyRandom.nextDouble() * height),
  ];

  final segments = <double>[];
  var length = 0.0;
  for (var i = 1; i < nodes.length; i++) {
    final distance = (nodes[i].point - nodes[i - 1].point).distance;
    segments.add(distance);
    length += distance;
  }

  return _ConstellationLayout(
    nodes: nodes,
    size: size,
    sky: sky,
    segments: segments,
    length: length,
  );
}

/// Constellation: every word is a star at its own scattered spot in the sky,
/// connected into a figure that lights and draws itself as the line is sung.
class _ConstellationLine extends StatefulWidget {
  final String lineText;
  final List<StudioLyricWord> words;
  final int positionMs;
  final ValueListenable<int>? positionListenable;
  final Color accent;
  final Color primary;
  final Color secondary;
  final double fontSize;
  final bool isRtl;

  const _ConstellationLine({
    required this.lineText,
    required this.words,
    required this.positionMs,
    required this.positionListenable,
    required this.accent,
    required this.primary,
    required this.secondary,
    required this.fontSize,
    required this.isRtl,
  });

  @override
  State<_ConstellationLine> createState() => _ConstellationLineState();
}

class _ConstellationLineState extends State<_ConstellationLine> {
  _ConstellationLayout? _layout;
  String _cacheKey = '';

  _ConstellationLayout _ensureLayout(double width, double height) {
    final key =
        '${widget.lineText}|${widget.words.length}|'
        '${width.toStringAsFixed(1)}|${height.toStringAsFixed(1)}|'
        '${widget.fontSize.toStringAsFixed(1)}|${widget.isRtl}';
    if (key != _cacheKey || _layout == null) {
      _cacheKey = key;
      _layout = _layoutConstellation(
        words: widget.words,
        width: width,
        height: height,
        fontSize: widget.fontSize,
        seedText: widget.lineText,
        isRtl: widget.isRtl,
      );
    }
    return _layout!;
  }

  @override
  Widget build(BuildContext context) {
    return _positionAware(
      listenable: widget.positionListenable,
      initial: widget.positionMs,
      builder: (position) {
        return LayoutBuilder(
          builder: (context, constraints) {
            final width = constraints.maxWidth.isFinite
                ? constraints.maxWidth
                : MediaQuery.sizeOf(context).width;
            final height = math.max(widget.fontSize * 4.0, 170.0);
            final layout = _ensureLayout(width, height);
            final revealed = _revealedCount(widget.words, position);
            final preview = _isPreview(widget.words, position);
            final active = preview ? -1 : revealed - 1;
            final activeProgress = active >= 0
                ? _wordProgress(widget.words[active], position)
                : 0.0;
            var traced = 0.0;
            if (active >= 0) {
              traced = (active + activeProgress) / widget.words.length;
            }
            return SizedBox(
              width: width,
              height: height,
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  Positioned.fill(
                    child: CustomPaint(
                      painter: _ConstellationPainter(
                        points: [for (final node in layout.nodes) node.point],
                        size: layout.size,
                        sky: layout.sky,
                        segments: layout.segments,
                        length: layout.length,
                        traced: traced.clamp(0.0, 1.0),
                        revealed: revealed,
                        active: active,
                        activeProgress: activeProgress,
                        phase: position / 640,
                        accent: widget.accent,
                        ink: widget.primary,
                      ),
                    ),
                  ),
                  for (var i = 0; i < widget.words.length; i++)
                    Positioned(
                      left: layout.nodes[i].textLeft,
                      top: layout.nodes[i].textTop,
                      child: AnimatedScale(
                        scale: i == active ? 1.08 : 1,
                        alignment: Alignment.topCenter,
                        duration: const Duration(milliseconds: 220),
                        curve: Curves.easeOutCubic,
                        child: AnimatedDefaultTextStyle(
                          duration: const Duration(milliseconds: 220),
                          curve: Curves.easeOutCubic,
                          style: _wordStyle(
                            color: i == active
                                ? widget.accent
                                : i < revealed
                                ? widget.primary
                                : widget.secondary.withValues(alpha: 0.40),
                            fontSize: layout.size,
                            glow: i == active,
                          ),
                          child: Text(widget.words[i].text.toUpperCase()),
                        ),
                      ),
                    ),
                ],
              ),
            );
          },
        );
      },
    );
  }
}

class _ConstellationPainter extends CustomPainter {
  final List<Offset> points;
  final double size;
  final List<Offset> sky;
  final List<double> segments;
  final double length;
  final double traced;
  final int revealed;
  final int active;
  final double activeProgress;
  final double phase;
  final Color accent;
  final Color ink;

  const _ConstellationPainter({
    required this.points,
    required this.size,
    required this.sky,
    required this.segments,
    required this.length,
    required this.traced,
    required this.revealed,
    required this.active,
    required this.activeProgress,
    required this.phase,
    required this.accent,
    required this.ink,
  });

  @override
  void paint(Canvas canvas, Size canvasSize) {
    _paintSky(canvas);

    final lit = traced * length;
    var run = 0.0;
    for (var i = 1; i < points.length; i++) {
      final segment = segments[i - 1];
      final coverage = segment <= 0
          ? (lit >= run ? 1.0 : 0.0)
          : ((lit - run) / segment).clamp(0.0, 1.0);
      run += segment;
      if (coverage <= 0) {
        _dashedLine(
          canvas,
          points[i - 1],
          points[i],
          Paint()
            ..style = PaintingStyle.stroke
            ..strokeWidth = 1
            ..color = Colors.white.withValues(alpha: 0.09),
        );
        continue;
      }
      final end = Offset.lerp(points[i - 1], points[i], coverage)!;
      // Wide translucent stroke in place of a mask blur: cheaper per frame.
      canvas.drawLine(
        points[i - 1],
        end,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 7
          ..strokeCap = StrokeCap.round
          ..color = accent.withValues(alpha: 0.07 + 0.05 * coverage),
      );
      canvas.drawLine(
        points[i - 1],
        end,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1.6
          ..strokeCap = StrokeCap.round
          ..color = Color.lerp(
            ink,
            accent,
            coverage,
          )!.withValues(alpha: 0.35 + 0.45 * coverage),
      );
    }

    // A comet head runs along the traced path, trailing the singer's word.
    final head = _pointAt(traced);
    if (head != null && traced > 0 && traced < 1) {
      for (var k = 5; k >= 1; k--) {
        final back = _pointAt((traced - k * 0.014).clamp(0.0, 1.0));
        if (back == null) continue;
        canvas.drawCircle(
          back,
          size * (0.055 - k * 0.007),
          Paint()..color = accent.withValues(alpha: 0.30 - k * 0.045),
        );
      }
      canvas.drawCircle(
        head,
        size * 0.34,
        Paint()..color = accent.withValues(alpha: 0.16),
      );
      canvas.drawCircle(
        head,
        size * 0.12,
        Paint()..color = Colors.white.withValues(alpha: 0.95),
      );
    }

    for (var i = 0; i < points.length; i++) {
      final p = points[i];
      if (i >= revealed) {
        canvas.drawCircle(
          p,
          size * 0.07,
          Paint()..color = Colors.white.withValues(alpha: 0.32),
        );
        canvas.drawCircle(
          p,
          size * 0.18,
          Paint()
            ..style = PaintingStyle.stroke
            ..strokeWidth = 0.8
            ..color = Colors.white.withValues(alpha: 0.10),
        );
        continue;
      }
      final isActive = i == active;
      final open = isActive ? activeProgress.clamp(0.0, 1.0) : 1.0;
      final flare = size * (0.55 + 0.32 * open);
      canvas.drawCircle(
        p,
        flare * 0.62,
        Paint()
          ..color = accent.withValues(
            alpha: (isActive ? 0.26 : 0.12) * (0.6 + 0.4 * open),
          ),
      );
      if (isActive) {
        final lens = Paint()
          ..strokeWidth = 1
          ..strokeCap = StrokeCap.round
          ..color = accent.withValues(alpha: 0.35 + 0.3 * open);
        canvas.drawLine(
          Offset(p.dx - flare * 1.5, p.dy),
          Offset(p.dx + flare * 1.5, p.dy),
          lens,
        );
        canvas.drawLine(
          Offset(p.dx, p.dy - flare * 0.7),
          Offset(p.dx, p.dy + flare * 0.7),
          lens,
        );
        canvas.drawCircle(
          p,
          flare * 0.9,
          Paint()
            ..style = PaintingStyle.stroke
            ..strokeWidth = 0.9
            ..color = accent.withValues(alpha: 0.35 * (1 - open) + 0.15),
        );
      }
      _sparkle(
        canvas,
        p,
        size * (isActive ? 0.36 : 0.24),
        Paint()..color = isActive ? Colors.white : accent,
      );
    }
  }

  /// The faint background sky. Star positions are pre-scattered by the
  /// layout, so only the twinkle changes from frame to frame.
  void _paintSky(Canvas canvas) {
    final paint = Paint();
    for (var i = 0; i < sky.length; i++) {
      final twinkle = 0.5 + 0.5 * math.sin(phase + i * 1.73);
      final big = i % 9 == 0;
      final base = 0.03 + ((i * 37) % 9) / 100;
      paint.color = Colors.white.withValues(alpha: base + twinkle * 0.10);
      canvas.drawCircle(
        sky[i],
        (big ? 1.2 : 0.55) + ((i * 13) % 5) * 0.18,
        paint,
      );
      if (big) {
        final arm = 3.2 + twinkle * 2.2;
        final cross = Paint()
          ..strokeWidth = 0.7
          ..color = Colors.white.withValues(alpha: 0.10 + twinkle * 0.12);
        canvas.drawLine(
          Offset(sky[i].dx - arm, sky[i].dy),
          Offset(sky[i].dx + arm, sky[i].dy),
          cross,
        );
        canvas.drawLine(
          Offset(sky[i].dx, sky[i].dy - arm),
          Offset(sky[i].dx, sky[i].dy + arm),
          cross,
        );
      }
    }
  }

  /// Point at [t] (0..1) along the constellation figure.
  Offset? _pointAt(double t) {
    if (points.isEmpty) return null;
    if (points.length == 1 || length <= 0) return points.first;
    var target = t.clamp(0.0, 1.0) * length;
    for (var i = 0; i < segments.length; i++) {
      if (target <= segments[i]) {
        final f = segments[i] == 0 ? 0.0 : target / segments[i];
        return Offset.lerp(points[i], points[i + 1], f);
      }
      target -= segments[i];
    }
    return points.last;
  }

  void _sparkle(Canvas canvas, Offset p, double r, Paint paint) {
    final path = Path()
      ..moveTo(p.dx, p.dy - r)
      ..quadraticBezierTo(p.dx + r * 0.16, p.dy - r * 0.16, p.dx + r, p.dy)
      ..quadraticBezierTo(p.dx + r * 0.16, p.dy + r * 0.16, p.dx, p.dy + r)
      ..quadraticBezierTo(p.dx - r * 0.16, p.dy + r * 0.16, p.dx - r, p.dy)
      ..quadraticBezierTo(p.dx - r * 0.16, p.dy - r * 0.16, p.dx, p.dy - r)
      ..close();
    canvas.drawPath(path, paint);
  }

  void _dashedLine(Canvas canvas, Offset a, Offset b, Paint paint) {
    const dash = 5.0;
    const gap = 4.0;
    final delta = b - a;
    final distance = delta.distance;
    if (distance == 0) return;
    final step = delta / distance;
    var start = 0.0;
    while (start < distance) {
      final end = math.min(start + dash, distance);
      canvas.drawLine(a + step * start, a + step * end, paint);
      start = end + gap;
    }
  }

  @override
  bool shouldRepaint(covariant _ConstellationPainter oldDelegate) =>
      oldDelegate.revealed != revealed ||
      oldDelegate.traced != traced ||
      oldDelegate.active != active ||
      oldDelegate.activeProgress != activeProgress ||
      oldDelegate.phase != phase ||
      oldDelegate.accent != accent ||
      !listEquals(oldDelegate.points, points);
}
