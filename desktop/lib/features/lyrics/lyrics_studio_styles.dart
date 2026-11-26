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
  'design_web',
  'design_constellation',
  'design_native_thread',
  'design_orbit',
  'design_rain',
  'design_typewriter',
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
    case 'design_web':
      return _WebLine(
        lineText: lineText,
        words: words,
        positionMs: positionMs,
        positionListenable: positionListenable,
        accent: accent,
        primary: primary,
        secondary: secondary,
        fontSize: fontSize,
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
    case 'design_orbit':
      return _OrbitLine(
        lineText: lineText,
        words: words,
        positionMs: positionMs,
        positionListenable: positionListenable,
        accent: accent,
        primary: primary,
        secondary: secondary,
        fontSize: fontSize,
      );
    case 'design_rain':
      return _RainLine(
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
    case 'design_typewriter':
      return _TypewriterLine(
        words: words,
        positionMs: positionMs,
        positionListenable: positionListenable,
        accent: accent,
        primary: primary,
        fontSize: fontSize,
        isRtl: isRtl,
      );
    case 'design_native_thread':
      return _MessageBubbleLine(
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
        final icon = _iconFor(word.text);
        return SizedBox(
          height: fontSize * 1.9,
          child: Center(
            child: AnimatedSwitcher(
              duration: const Duration(milliseconds: 300),
              switchInCurve: Curves.easeOutBack,
              switchOutCurve: Curves.easeInCubic,
              transitionBuilder: (child, animation) {
                final scale = Tween<double>(begin: 0.6, end: 1).animate(
                  CurvedAnimation(parent: animation, curve: Curves.easeOutBack),
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
                    if (icon != null) ...[
                      SizedBox(width: fontSize * 0.3),
                      Icon(icon, size: fontSize * 0.9, color: accent),
                    ],
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }
}

IconData? _iconFor(String text) {
  final w = text.toLowerCase().replaceAll(RegExp(r"[^a-z']"), '');
  const icons = <String, IconData>{
    'love': FluentIcons.heart,
    'heart': FluentIcons.heart,
    'home': FluentIcons.home,
    'night': FluentIcons.clear_night,
    'moon': FluentIcons.clear_night,
    'star': FluentIcons.favorite_star,
    'stars': FluentIcons.favorite_star,
    'time': FluentIcons.clock,
    'clock': FluentIcons.clock,
    'music': FluentIcons.music_note,
    'song': FluentIcons.music_note,
    'sing': FluentIcons.microphone,
    'voice': FluentIcons.microphone,
    'fire': FluentIcons.equalizer,
    'dance': FluentIcons.equalizer,
    'dancing': FluentIcons.equalizer,
    'light': FluentIcons.lightbulb,
    'shine': FluentIcons.lightbulb,
    'dreams': FluentIcons.cloud,
    'dream': FluentIcons.cloud,
    'cloud': FluentIcons.cloud,
  };
  return icons[w];
}

// ---------------------------------------------------------------------------
// Signature designs: Branches / Web / Constellation / Message Thread
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

List<double> _wordProgressList(List<StudioLyricWord> words, int positionMs) => [
  for (final word in words) _wordProgress(word, positionMs),
];

/// Rainfall fall offset, 1 = still in the cloud, 0 = landed. The word drops
/// fast and lifts a touch as it settles, so nothing snaps to the baseline.
double _rainFall(double progress) {
  final p = progress.clamp(0.0, 1.0);
  if (p <= 0) return 1;
  if (p >= 1) return 0;
  final fall = Curves.easeInCubic.transform((p / 0.78).clamp(0.0, 1.0));
  final bounce = p < 0.78 ? 0.0 : math.sin((p - 0.78) / 0.22 * math.pi) * 0.16;
  return (1 - fall + bounce).clamp(0.0, 1.15);
}

/// One word anchor on a spine (Branches) or sky path (Constellation).
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
class _BranchesLine extends StatelessWidget {
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
  Widget build(BuildContext context) {
    return _positionAware(
      listenable: positionListenable,
      initial: positionMs,
      builder: (position) {
        return LayoutBuilder(
          builder: (context, constraints) {
            final width = constraints.maxWidth.isFinite
                ? constraints.maxWidth
                : MediaQuery.sizeOf(context).width;
            final height = math.max(fontSize * 2.6, 96.0);
            final layout = _layoutSpine(
              words: words,
              width: width,
              height: height,
              fontSize: fontSize,
              seedText: lineText,
              isRtl: isRtl,
              labelsBelow: false,
              amplitude: fontSize * 0.26,
              baseline: 0.66,
            );
            final revealed = _revealedCount(words, position);
            final preview = _isPreview(words, position);
            final active = preview ? -1 : revealed - 1;
            final activeProgress = active >= 0
                ? _wordProgress(words[active], position)
                : 0.0;
            var fraction = 0.0;
            if (active >= 0) {
              fraction = (active + activeProgress) / words.length;
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
                        size: layout.size,
                        fraction: fraction.clamp(0.0, 1.0),
                        revealed: revealed,
                        active: active,
                        activeProgress: activeProgress,
                        accent: accent,
                      ),
                    ),
                  ),
                  for (var i = 0; i < words.length; i++)
                    Positioned(
                      left: layout.nodes[i].textLeft,
                      top: layout.nodes[i].textTop,
                      child: AnimatedScale(
                        scale: i == active
                            ? 1 + 0.06 * math.sin(activeProgress * math.pi)
                            : 1,
                        alignment: Alignment.bottomCenter,
                        duration: reduceMotion
                            ? Duration.zero
                            : const Duration(milliseconds: 200),
                        curve: Curves.easeOutCubic,
                        child: AnimatedDefaultTextStyle(
                          duration: reduceMotion
                              ? Duration.zero
                              : const Duration(milliseconds: 220),
                          curve: Curves.easeOutCubic,
                          style: _wordStyle(
                            color: i == active
                                ? accent
                                : i < revealed
                                ? primary
                                : secondary.withValues(alpha: 0.42),
                            fontSize: layout.size,
                            glow: i == active,
                          ),
                          child: Text(words[i].text.toUpperCase()),
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

class _BranchesPainter extends CustomPainter {
  final List<Offset> points;
  final double size;
  final double fraction;
  final int revealed;
  final int active;
  final double activeProgress;
  final Color accent;

  const _BranchesPainter({
    required this.points,
    required this.size,
    required this.fraction,
    required this.revealed,
    required this.active,
    required this.activeProgress,
    required this.accent,
  });

  Path _vine() {
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

  @override
  void paint(Canvas canvas, Size canvasSize) {
    final path = _vine();
    final metrics = path.computeMetrics().toList();
    if (metrics.isEmpty) return;
    final metric = metrics.first;
    final grown = metric.length * fraction;

    canvas.drawPath(
      path,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.6
        ..strokeCap = StrokeCap.round
        ..color = Colors.white.withValues(alpha: 0.12),
    );

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
      canvas.drawPath(
        lit,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 9
          ..strokeCap = StrokeCap.round
          ..color = accent.withValues(alpha: 0.10)
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 7),
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
      oldDelegate.revealed != revealed ||
      oldDelegate.fraction != fraction ||
      oldDelegate.active != active ||
      oldDelegate.activeProgress != activeProgress ||
      oldDelegate.accent != accent ||
      !listEquals(oldDelegate.points, points);
}

/// Web: each line is spun from a centre hub, one strand per word, with the
/// strands and the concentric web lighting up as the words are sung.
class _WebLine extends StatelessWidget {
  final String lineText;
  final List<StudioLyricWord> words;
  final int positionMs;
  final ValueListenable<int>? positionListenable;
  final Color accent;
  final Color primary;
  final Color secondary;
  final double fontSize;

  const _WebLine({
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
        return LayoutBuilder(
          builder: (context, constraints) {
            final width = constraints.maxWidth.isFinite
                ? constraints.maxWidth
                : MediaQuery.sizeOf(context).width;
            final height = math.max(fontSize * 3.4, 140.0);
            final n = words.length;
            final center = Offset(width / 2, height / 2);
            final radius = math.min(width * 0.34, height * 0.34);
            final labelSize = math.max(
              10.0,
              math.min(fontSize * 0.36, radius * 0.42),
            );
            final progress = _wordProgressList(words, position);
            final preview = _isPreview(words, position);
            final revealed = _revealedCount(words, position);
            final active = preview ? -1 : revealed - 1;
            final activeProgress = active >= 0
                ? _wordProgress(words[active], position)
                : 0.0;
            final trace = active >= 0 ? (active + activeProgress) / n : 0.0;
            final nodes = <Offset>[];
            final labels = <_WebLabel>[];
            final phase = (_hashText(lineText) % 628) / 100;
            for (var i = 0; i < n; i++) {
              final angle = -math.pi / 2 + (2 * math.pi * i) / n + phase * 0.02;
              final r = radius * (i.isEven ? 0.8 : 1.0);
              final point = Offset(
                center.dx + math.cos(angle) * r,
                center.dy + math.sin(angle) * r,
              );
              nodes.add(point);
              final outward = labelSize * 1.5;
              final dx = point.dx + math.cos(angle) * outward;
              final dy = point.dy + math.sin(angle) * outward;
              final w = _visualLength(words[i].text) * labelSize * 0.66;
              labels.add(
                _WebLabel(
                  left: (dx - w / 2).clamp(0.0, math.max(0.0, width - w)),
                  top: (dy - labelSize * 0.55).clamp(
                    0.0,
                    math.max(0.0, height - labelSize * 1.1),
                  ),
                  width: w,
                ),
              );
            }
            return SizedBox(
              width: width,
              height: height,
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  Positioned.fill(
                    child: CustomPaint(
                      painter: _WebPainter(
                        hub: center,
                        nodes: nodes,
                        progress: progress,
                        radius: radius,
                        revealed: revealed,
                        active: active,
                        activeProgress: activeProgress,
                        trace: trace.clamp(0.0, 1.0),
                        accent: accent,
                      ),
                    ),
                  ),
                  for (var i = 0; i < n; i++)
                    Positioned(
                      left: labels[i].left,
                      top: labels[i].top,
                      width: labels[i].width,
                      child: AnimatedScale(
                        scale: i == active ? 1.16 : 1,
                        duration: const Duration(milliseconds: 220),
                        curve: Curves.easeOutCubic,
                        child: AnimatedDefaultTextStyle(
                          duration: const Duration(milliseconds: 220),
                          curve: Curves.easeOutCubic,
                          style: _wordStyle(
                            color: i == active
                                ? accent
                                : i < revealed
                                ? primary
                                : secondary.withValues(alpha: 0.42),
                            fontSize: labelSize,
                            glow: i == active,
                          ).copyWith(letterSpacing: 0),
                          child: Text(
                            words[i].text.toUpperCase(),
                            textAlign: TextAlign.center,
                            maxLines: 1,
                          ),
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

class _WebLabel {
  final double left;
  final double top;
  final double width;

  const _WebLabel({required this.left, required this.top, required this.width});
}

class _WebPainter extends CustomPainter {
  final Offset hub;
  final List<Offset> nodes;
  final List<double> progress;
  final double radius;
  final int revealed;
  final int active;
  final double activeProgress;
  final double trace;
  final Color accent;

  const _WebPainter({
    required this.hub,
    required this.nodes,
    required this.progress,
    required this.radius,
    required this.revealed,
    required this.active,
    required this.activeProgress,
    required this.trace,
    required this.accent,
  });

  @override
  void paint(Canvas canvas, Size canvasSize) {
    final ring = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1
      ..color = Colors.white.withValues(alpha: 0.08);
    const ringFactors = [0.34, 0.62, 0.9];
    for (final factor in ringFactors) {
      canvas.drawCircle(hub, radius * factor, ring);
    }
    // The rings fill in clockwise as the singer moves through the line.
    if (trace > 0) {
      for (var r = 0; r < ringFactors.length; r++) {
        final rect = Rect.fromCircle(
          center: hub,
          radius: radius * ringFactors[r],
        );
        canvas.drawArc(
          rect,
          -math.pi / 2 + r * 0.42,
          2 * math.pi * trace,
          false,
          Paint()
            ..style = PaintingStyle.stroke
            ..strokeWidth = 1.2
            ..strokeCap = StrokeCap.round
            ..color = accent.withValues(alpha: 0.10 + r * 0.07),
        );
      }
    }

    final faintStrand = Paint()
      ..strokeWidth = 1
      ..color = Colors.white.withValues(alpha: 0.08);
    for (final node in nodes) {
      canvas.drawLine(hub, node, faintStrand);
    }

    if (nodes.length > 1) {
      final thread = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1
        ..color = Colors.white.withValues(alpha: 0.06);
      for (var i = 0; i < nodes.length; i++) {
        final a = nodes[i];
        final b = nodes[(i + 1) % nodes.length];
        final ctrl = Offset.lerp(
          hub,
          Offset((a.dx + b.dx) / 2, (a.dy + b.dy) / 2),
          0.72,
        )!;
        canvas.drawPath(
          Path()
            ..moveTo(a.dx, a.dy)
            ..quadraticBezierTo(ctrl.dx, ctrl.dy, b.dx, b.dy),
          thread,
        );
      }
    }

    for (var i = 0; i < nodes.length; i++) {
      final p = progress[i];
      if (p <= 0) continue;
      final tip = Offset.lerp(hub, nodes[i], p)!;
      canvas.drawLine(
        hub,
        tip,
        Paint()
          ..strokeWidth = 1.4
          ..strokeCap = StrokeCap.round
          ..color = accent.withValues(alpha: 0.25 + 0.5 * p),
      );
      if (p >= 1) {
        final done = i == active;
        canvas.drawCircle(
          nodes[i],
          radius * (done ? 0.075 : 0.055),
          Paint()..color = accent.withValues(alpha: done ? 0.18 : 0.10),
        );
        canvas.drawCircle(
          nodes[i],
          radius * 0.035,
          Paint()..color = accent.withValues(alpha: 0.65),
        );
        canvas.drawCircle(
          nodes[i],
          radius * 0.012,
          Paint()..color = Colors.white.withValues(alpha: 0.8),
        );
      }
    }

    if (nodes.length > 1) {
      for (var i = 0; i < nodes.length; i++) {
        final j = (i + 1) % nodes.length;
        final both = math.min(progress[i], progress[j]);
        if (both <= 0) continue;
        final a = nodes[i];
        final b = nodes[j];
        final ctrl = Offset.lerp(
          hub,
          Offset((a.dx + b.dx) / 2, (a.dy + b.dy) / 2),
          0.72,
        )!;
        final metric =
            (Path()
                  ..moveTo(a.dx, a.dy)
                  ..quadraticBezierTo(ctrl.dx, ctrl.dy, b.dx, b.dy))
                .computeMetrics()
                .first;
        canvas.drawPath(
          metric.extractPath(0, metric.length * both),
          Paint()
            ..style = PaintingStyle.stroke
            ..strokeWidth = 1.2
            ..color = both >= 1
                ? accent.withValues(alpha: 0.35)
                : Colors.white.withValues(alpha: 0.16),
        );
        if (both >= 1) {
          // A dew drop hangs where two finished strands meet.
          final mid = a * 0.25 + ctrl * 0.5 + b * 0.25;
          canvas.drawCircle(
            mid,
            4.2,
            Paint()..color = accent.withValues(alpha: 0.12),
          );
          canvas.drawCircle(
            mid,
            1.6,
            Paint()..color = Colors.white.withValues(alpha: 0.65),
          );
        }
      }
    }

    final lit = revealed > 0;
    final pulse = active >= 0
        ? 0.5 + 0.5 * math.sin(activeProgress.clamp(0.0, 1.0) * math.pi)
        : 0.0;
    canvas.drawCircle(
      hub,
      radius * (0.10 + 0.04 * pulse + (lit ? 0.02 : 0)),
      Paint()
        ..color = accent.withValues(alpha: (lit ? 0.22 : 0.10) + 0.10 * pulse),
    );
    canvas.drawCircle(
      hub,
      radius * 0.045,
      Paint()..color = accent.withValues(alpha: lit ? 0.95 : 0.45),
    );
    canvas.drawCircle(
      hub,
      radius * 0.018,
      Paint()..color = Colors.white.withValues(alpha: 0.85),
    );
  }

  @override
  bool shouldRepaint(covariant _WebPainter oldDelegate) =>
      oldDelegate.revealed != revealed ||
      oldDelegate.active != active ||
      oldDelegate.activeProgress != activeProgress ||
      oldDelegate.trace != trace ||
      oldDelegate.accent != accent ||
      !listEquals(oldDelegate.progress, progress) ||
      !listEquals(oldDelegate.nodes, nodes);
}

/// Constellation: the line is a sky path; every word is a star that lights
/// as it is sung and the traced line follows the singer across the stars.
class _ConstellationLine extends StatelessWidget {
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
  Widget build(BuildContext context) {
    return _positionAware(
      listenable: positionListenable,
      initial: positionMs,
      builder: (position) {
        return LayoutBuilder(
          builder: (context, constraints) {
            final width = constraints.maxWidth.isFinite
                ? constraints.maxWidth
                : MediaQuery.sizeOf(context).width;
            final height = math.max(fontSize * 2.8, 110.0);
            final layout = _layoutSpine(
              words: words,
              width: width,
              height: height,
              fontSize: fontSize,
              seedText: lineText,
              isRtl: isRtl,
              labelsBelow: true,
              amplitude: fontSize * 0.42,
              baseline: 0.40,
            );
            final revealed = _revealedCount(words, position);
            final preview = _isPreview(words, position);
            final active = preview ? -1 : revealed - 1;
            final activeProgress = active >= 0
                ? _wordProgress(words[active], position)
                : 0.0;
            var traced = 0.0;
            if (active >= 0) {
              traced = (active + activeProgress) / words.length;
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
                        traced: traced.clamp(0.0, 1.0),
                        revealed: revealed,
                        active: active,
                        activeProgress: activeProgress,
                        phase: position / 640,
                        accent: accent,
                        ink: primary,
                        seed: _hashText(lineText),
                      ),
                    ),
                  ),
                  for (var i = 0; i < words.length; i++)
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
                                ? accent
                                : i < revealed
                                ? primary
                                : secondary.withValues(alpha: 0.40),
                            fontSize: layout.size,
                            glow: i == active,
                          ),
                          child: Text(words[i].text.toUpperCase()),
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
  final double traced;
  final int revealed;
  final int active;
  final double activeProgress;
  final double phase;
  final Color accent;
  final Color ink;
  final int seed;

  const _ConstellationPainter({
    required this.points,
    required this.size,
    required this.traced,
    required this.revealed,
    required this.active,
    required this.activeProgress,
    required this.phase,
    required this.accent,
    required this.ink,
    required this.seed,
  });

  @override
  void paint(Canvas canvas, Size canvasSize) {
    final random = math.Random(seed ^ 0x1f123bb5);
    final sky = Paint();
    for (var i = 0; i < 52; i++) {
      final twinkle = 0.5 + 0.5 * math.sin(phase + i * 1.73);
      final base = 0.03 + random.nextDouble() * 0.08;
      final big = i % 9 == 0;
      final star = Offset(
        random.nextDouble() * canvasSize.width,
        random.nextDouble() * canvasSize.height,
      );
      sky.color = Colors.white.withValues(alpha: base + twinkle * 0.10);
      canvas.drawCircle(
        star,
        (big ? 1.2 : 0.55) + random.nextDouble() * 0.9,
        sky,
      );
      if (big) {
        final arm = 3.2 + twinkle * 2.2;
        final cross = Paint()
          ..strokeWidth = 0.7
          ..color = Colors.white.withValues(alpha: 0.10 + twinkle * 0.12);
        canvas.drawLine(
          Offset(star.dx - arm, star.dy),
          Offset(star.dx + arm, star.dy),
          cross,
        );
        canvas.drawLine(
          Offset(star.dx, star.dy - arm),
          Offset(star.dx, star.dy + arm),
          cross,
        );
      }
    }

    final litLength = traced * points.length;
    for (var i = 1; i < points.length; i++) {
      final coverage = (litLength - (i - 1)).clamp(0.0, 1.0);
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
      canvas.drawLine(
        points[i - 1],
        end,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 7
          ..strokeCap = StrokeCap.round
          ..color = accent.withValues(alpha: 0.07 + 0.05 * coverage)
          ..maskFilter = const MaskFilter.blur(BlurStyle.normal, 6),
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

  /// Point at [t] (0..1) along the constellation polyline.
  Offset? _pointAt(double t) {
    if (points.isEmpty) return null;
    if (points.length == 1) return points.first;
    final lengths = <double>[];
    var total = 0.0;
    for (var i = 1; i < points.length; i++) {
      final length = (points[i] - points[i - 1]).distance;
      lengths.add(length);
      total += length;
    }
    if (total == 0) return points.first;
    var target = t.clamp(0.0, 1.0) * total;
    for (var i = 0; i < lengths.length; i++) {
      if (target <= lengths[i]) {
        final f = lengths[i] == 0 ? 0.0 : target / lengths[i];
        return Offset.lerp(points[i], points[i + 1], f);
      }
      target -= lengths[i];
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

/// One lyric bubble for the Native message-thread design. Public because the
/// full-screen stage stacks past lines and the next line with it.
Widget buildNativeThreadBubble({
  required List<StudioLyricWord> words,
  required int positionMs,
  required ValueListenable<int>? positionListenable,
  required Color accent,
  required Color primary,
  required Color secondary,
  required double fontSize,
  required bool reduceMotion,
  bool sung = false,
  bool typing = false,
}) {
  return _positionAware(
    listenable: positionListenable,
    initial: positionMs,
    builder: (position) {
      final preview = typing || words.isEmpty || _isPreview(words, position);
      final revealed = words.isEmpty ? 0 : _revealedCount(words, position);
      final active = preview ? -1 : revealed - 1;
      // iMessage reading: a finished line is a grey incoming message, the line
      // being sung is a blue outgoing one. Typing stays on the left.
      final outgoing = !sung && !typing;
      final radius = fontSize * 0.86;
      final bubbleColor = outgoing
          ? accent.withValues(alpha: 0.92)
          : Colors.white.withValues(alpha: 0.10);
      final textColor = outgoing ? Colors.white : primary;
      final pending = outgoing
          ? Colors.white.withValues(alpha: 0.52)
          : secondary.withValues(alpha: 0.5);
      final clock = _clockLabel(words.isEmpty ? position : words.first.startMs);

      final bubble = Container(
        padding: EdgeInsets.fromLTRB(
          fontSize * 0.66,
          fontSize * 0.40,
          fontSize * 0.66,
          fontSize * 0.32,
        ),
        decoration: BoxDecoration(
          color: bubbleColor,
          borderRadius: BorderRadius.only(
            topLeft: Radius.circular(radius),
            topRight: Radius.circular(radius),
            bottomLeft: Radius.circular(outgoing ? radius : radius * 0.16),
            bottomRight: Radius.circular(outgoing ? radius * 0.16 : radius),
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withValues(alpha: 0.18),
              blurRadius: fontSize * 0.7,
              offset: const Offset(0, 3),
            ),
            if (outgoing)
              BoxShadow(
                color: accent.withValues(alpha: 0.24),
                blurRadius: fontSize * 1.2,
              ),
          ],
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (typing)
              _typingDots(position, fontSize, secondary, reduceMotion)
            else
              Wrap(
                spacing: fontSize * 0.30,
                runSpacing: fontSize * 0.08,
                children: [
                  for (var i = 0; i < words.length; i++)
                    AnimatedScale(
                      scale: i == active ? 1.07 : 1,
                      duration: reduceMotion
                          ? Duration.zero
                          : const Duration(milliseconds: 180),
                      curve: Curves.easeOutCubic,
                      child: AnimatedDefaultTextStyle(
                        duration: reduceMotion
                            ? Duration.zero
                            : const Duration(milliseconds: 200),
                        curve: Curves.easeOutCubic,
                        style:
                            _wordStyle(
                              color: i < revealed || sung
                                  ? (i == active
                                        ? (outgoing ? Colors.white : accent)
                                        : textColor)
                                  : pending,
                              fontSize: fontSize,
                              glow: i == active,
                            ).copyWith(
                              fontWeight: FontWeight.w600,
                              letterSpacing: -0.1,
                              height: 1.14,
                            ),
                        child: Text(words[i].text),
                      ),
                    ),
                ],
              ),
            if (!typing) ...[
              SizedBox(height: fontSize * 0.18),
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    clock,
                    style: TextStyle(
                      fontSize: fontSize * 0.20,
                      fontWeight: FontWeight.w500,
                      letterSpacing: 0.2,
                      color: outgoing
                          ? Colors.white.withValues(alpha: 0.60)
                          : secondary.withValues(alpha: 0.65),
                    ),
                  ),
                  if (outgoing) ...[
                    SizedBox(width: fontSize * 0.22),
                    Text(
                      sung ? 'Read' : 'Delivered',
                      style: TextStyle(
                        fontSize: fontSize * 0.20,
                        fontWeight: FontWeight.w600,
                        letterSpacing: 0.2,
                        color: Colors.white.withValues(alpha: 0.78),
                      ),
                    ),
                  ],
                ],
              ),
            ],
          ],
        ),
      );

      final bubbleWithTail = Stack(
        clipBehavior: Clip.none,
        children: [
          bubble,
          Positioned(
            bottom: -1,
            left: outgoing ? null : -fontSize * 0.20,
            right: outgoing ? -fontSize * 0.20 : null,
            child: CustomPaint(
              size: Size(fontSize * 0.34, fontSize * 0.30),
              painter: _BubbleTailPainter(color: bubbleColor, right: outgoing),
            ),
          ),
        ],
      );

      return Align(
        alignment: outgoing ? Alignment.centerRight : Alignment.centerLeft,
        child: Padding(
          padding: EdgeInsets.only(bottom: fontSize * 0.34),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              if (!outgoing) ...[
                _threadAvatar(fontSize, accent),
                SizedBox(width: fontSize * 0.32),
              ],
              Flexible(child: bubbleWithTail),
            ],
          ),
        ),
      );
    },
  );
}

Widget _threadAvatar(double fontSize, Color accent) {
  return Container(
    width: fontSize * 0.78,
    height: fontSize * 0.78,
    decoration: BoxDecoration(
      shape: BoxShape.circle,
      gradient: LinearGradient(
        begin: Alignment.topLeft,
        end: Alignment.bottomRight,
        colors: [
          accent.withValues(alpha: 0.90),
          accent.withValues(alpha: 0.38),
        ],
      ),
      boxShadow: [
        BoxShadow(
          color: accent.withValues(alpha: 0.28),
          blurRadius: fontSize * 0.5,
        ),
      ],
    ),
    child: Icon(
      FluentIcons.music_note,
      size: fontSize * 0.40,
      color: Colors.white,
    ),
  );
}

Widget _typingDots(
  int position,
  double fontSize,
  Color color,
  bool reduceMotion,
) {
  return Row(
    mainAxisSize: MainAxisSize.min,
    children: [
      for (var i = 0; i < 3; i++)
        Padding(
          padding: EdgeInsets.symmetric(horizontal: fontSize * 0.05),
          child: Opacity(
            opacity: reduceMotion
                ? 0.55
                : 0.25 +
                      0.6 *
                          ((math.sin(position / 240 + i * 1.25) + 1) / 2).clamp(
                            0.0,
                            1.0,
                          ),
            child: Container(
              width: fontSize * 0.17,
              height: fontSize * 0.17,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: color.withValues(alpha: 0.85),
              ),
            ),
          ),
        ),
    ],
  );
}

String _clockLabel(int positionMs) {
  final total = (positionMs.abs() / 1000).floor();
  final minutes = total ~/ 60;
  final seconds = total % 60;
  return '$minutes:${seconds.toString().padLeft(2, '0')}';
}

class _BubbleTailPainter extends CustomPainter {
  final Color color;
  final bool right;

  const _BubbleTailPainter({required this.color, required this.right});

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width;
    final h = size.height;
    final path = Path()
      ..moveTo(w, 0)
      ..cubicTo(w * 0.34, h * 0.06, w * 0.10, h * 0.55, 0, h)
      ..cubicTo(w * 0.32, h * 0.90, w * 0.62, h * 0.90, w, h * 0.90)
      ..close();
    if (right) {
      canvas.translate(w, 0);
      canvas.scale(-1, 1);
    }
    canvas.drawPath(path, Paint()..color = color);
  }

  @override
  bool shouldRepaint(covariant _BubbleTailPainter oldDelegate) =>
      oldDelegate.color != color || oldDelegate.right != right;
}

/// Native Design: the song reads as a message thread, one bubble per line.
class _MessageBubbleLine extends StatelessWidget {
  final List<StudioLyricWord> words;
  final int positionMs;
  final ValueListenable<int>? positionListenable;
  final Color accent;
  final Color primary;
  final Color secondary;
  final double fontSize;
  final bool isRtl;
  final bool reduceMotion;

  const _MessageBubbleLine({
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
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth.isFinite
            ? constraints.maxWidth
            : MediaQuery.sizeOf(context).width;
        return SizedBox(
          width: width,
          child: Align(
            alignment: isRtl ? Alignment.centerRight : Alignment.centerLeft,
            child: ConstrainedBox(
              constraints: BoxConstraints(maxWidth: width * 0.9),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: isRtl
                    ? CrossAxisAlignment.end
                    : CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: EdgeInsets.only(
                      left: fontSize * 0.2,
                      bottom: fontSize * 0.18,
                    ),
                    child: Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          FluentIcons.music_note,
                          size: fontSize * 0.34,
                          color: accent.withValues(alpha: 0.85),
                        ),
                        SizedBox(width: fontSize * 0.18),
                        Text(
                          'LYRICS',
                          style: TextStyle(
                            fontSize: fontSize * 0.22,
                            fontWeight: FontWeight.w800,
                            letterSpacing: 1.4,
                            color: secondary.withValues(alpha: 0.7),
                          ),
                        ),
                      ],
                    ),
                  ),
                  buildNativeThreadBubble(
                    words: words,
                    positionMs: positionMs,
                    positionListenable: positionListenable,
                    accent: accent,
                    primary: primary,
                    secondary: secondary,
                    fontSize: fontSize * 0.9,
                    reduceMotion: reduceMotion,
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }
}

/// Orbit: the words circle a glowing centre, and each one brightens as the
/// playback arc sweeps past it.
class _OrbitLine extends StatelessWidget {
  final String lineText;
  final List<StudioLyricWord> words;
  final int positionMs;
  final ValueListenable<int>? positionListenable;
  final Color accent;
  final Color primary;
  final Color secondary;
  final double fontSize;

  const _OrbitLine({
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
        return LayoutBuilder(
          builder: (context, constraints) {
            final width = constraints.maxWidth.isFinite
                ? constraints.maxWidth
                : MediaQuery.sizeOf(context).width;
            final height = math.max(fontSize * 3.0, 130.0);
            final n = words.length;
            final center = Offset(width / 2, height / 2);
            final rx = math.min(width * 0.30, height * 0.34);
            final ry = math.min(height * 0.30, width * 0.22);
            final labelSize = math.max(
              10.0,
              math.min(fontSize * 0.38, rx * 0.34),
            );
            final progress = _wordProgressList(words, position);
            final preview = _isPreview(words, position);
            final revealed = _revealedCount(words, position);
            final active = preview ? -1 : revealed - 1;
            final activeProgress = active >= 0
                ? _wordProgress(words[active], position)
                : 0.0;
            var traced = 0.0;
            if (active >= 0) {
              traced = (active + activeProgress) / words.length;
            }
            final labels = <_WebLabel>[];
            for (var i = 0; i < n; i++) {
              final angle = -math.pi / 2 + (2 * math.pi * i) / n;
              final point = Offset(
                center.dx + math.cos(angle) * rx,
                center.dy + math.sin(angle) * ry,
              );
              final w = _visualLength(words[i].text) * labelSize * 0.62;
              labels.add(
                _WebLabel(
                  left: (point.dx - w / 2).clamp(0.0, math.max(0.0, width - w)),
                  top: (point.dy - labelSize * 0.55).clamp(
                    0.0,
                    math.max(0.0, height - labelSize * 1.1),
                  ),
                  width: w,
                ),
              );
            }
            return SizedBox(
              width: width,
              height: height,
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  Positioned.fill(
                    child: CustomPaint(
                      painter: _OrbitPainter(
                        center: center,
                        rx: rx,
                        ry: ry,
                        progress: progress,
                        traced: traced.clamp(0.0, 1.0),
                        revealed: revealed,
                        active: active,
                        activeProgress: activeProgress,
                        accent: accent,
                      ),
                    ),
                  ),
                  for (var i = 0; i < n; i++)
                    Positioned(
                      left: labels[i].left,
                      top: labels[i].top,
                      width: labels[i].width,
                      child: AnimatedScale(
                        scale: i == active ? 1.14 : 1,
                        duration: const Duration(milliseconds: 220),
                        curve: Curves.easeOutCubic,
                        child: AnimatedDefaultTextStyle(
                          duration: const Duration(milliseconds: 220),
                          curve: Curves.easeOutCubic,
                          style: _wordStyle(
                            color: i == active
                                ? accent
                                : i < revealed
                                ? primary
                                : secondary.withValues(alpha: 0.46),
                            fontSize: labelSize,
                            glow: i == active,
                          ).copyWith(letterSpacing: 0),
                          child: Text(
                            words[i].text.toUpperCase(),
                            textAlign: TextAlign.center,
                            maxLines: 1,
                          ),
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

class _OrbitPainter extends CustomPainter {
  final Offset center;
  final double rx;
  final double ry;
  final List<double> progress;
  final double traced;
  final int revealed;
  final int active;
  final double activeProgress;
  final Color accent;

  const _OrbitPainter({
    required this.center,
    required this.rx,
    required this.ry,
    required this.progress,
    required this.traced,
    required this.revealed,
    required this.active,
    required this.activeProgress,
    required this.accent,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final orbit = Rect.fromCenter(
      center: center,
      width: rx * 2,
      height: ry * 2,
    );

    // Dashed outer orbit plus a tilted inner one, like a small solar system.
    final dash = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1
      ..color = Colors.white.withValues(alpha: 0.12);
    const dashes = 72;
    for (var i = 0; i < dashes; i++) {
      canvas.drawArc(
        orbit,
        (2 * math.pi * i) / dashes,
        math.pi / dashes * 0.85,
        false,
        dash,
      );
    }
    canvas.save();
    canvas.translate(center.dx, center.dy);
    canvas.rotate(-0.55);
    canvas.drawOval(
      Rect.fromCenter(center: Offset.zero, width: rx * 1.55, height: ry * 1.45),
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 0.8
        ..color = Colors.white.withValues(alpha: 0.05),
    );
    canvas.restore();

    // The sung arc fades in from its tail, then a satellite sits on its head.
    if (traced > 0) {
      const segments = 40;
      final span = 2 * math.pi * traced;
      for (var i = 0; i < segments; i++) {
        final t = (i + 1) / segments;
        canvas.drawArc(
          orbit,
          -math.pi / 2 + span * (i / segments),
          span / segments * 1.06,
          false,
          Paint()
            ..style = PaintingStyle.stroke
            ..strokeWidth = 1.4 + 1.6 * t
            ..strokeCap = StrokeCap.round
            ..color = accent.withValues(alpha: 0.06 + 0.48 * t),
        );
      }
      final headAngle = -math.pi / 2 + 2 * math.pi * traced;
      final head = Offset(
        center.dx + math.cos(headAngle) * rx,
        center.dy + math.sin(headAngle) * ry,
      );
      canvas.drawCircle(
        head,
        rx * 0.10,
        Paint()..color = accent.withValues(alpha: 0.18),
      );
      canvas.drawCircle(
        head,
        rx * 0.035,
        Paint()..color = Colors.white.withValues(alpha: 0.95),
      );
    }

    for (var i = 0; i < progress.length; i++) {
      final angle = -math.pi / 2 + (2 * math.pi * i) / progress.length;
      final point = Offset(
        center.dx + math.cos(angle) * rx,
        center.dy + math.sin(angle) * ry,
      );
      final lit = i < revealed;
      if (i == active) {
        final open = activeProgress.clamp(0.0, 1.0);
        canvas.drawCircle(
          point,
          rx * (0.10 + 0.05 * open),
          Paint()..color = accent.withValues(alpha: 0.16),
        );
        canvas.drawCircle(
          point,
          rx * 0.07,
          Paint()
            ..style = PaintingStyle.stroke
            ..strokeWidth = 1.2
            ..color = accent.withValues(alpha: 0.65),
        );
      }
      canvas.drawCircle(
        point,
        lit ? 3.4 : 1.8,
        Paint()..color = lit ? accent : Colors.white.withValues(alpha: 0.26),
      );
    }

    final glowRect = Rect.fromCircle(center: center, radius: rx * 0.6);
    canvas.drawCircle(
      center,
      rx * 0.6,
      Paint()
        ..shader = RadialGradient(
          colors: [
            accent.withValues(alpha: traced > 0 ? 0.30 : 0.12),
            accent.withValues(alpha: 0),
          ],
        ).createShader(glowRect),
    );
    canvas.drawCircle(
      center,
      rx * 0.10,
      Paint()..color = accent.withValues(alpha: 0.35),
    );
    canvas.drawCircle(
      center,
      rx * 0.045,
      Paint()..color = Colors.white.withValues(alpha: 0.95),
    );
  }

  @override
  bool shouldRepaint(covariant _OrbitPainter oldDelegate) =>
      oldDelegate.traced != traced ||
      oldDelegate.revealed != revealed ||
      oldDelegate.active != active ||
      oldDelegate.activeProgress != activeProgress ||
      oldDelegate.accent != accent ||
      !listEquals(oldDelegate.progress, progress);
}

/// Rainfall: words hover as drops and settle onto the line as they are sung,
/// with a soft splash where each one lands.
class _RainLine extends StatelessWidget {
  final String lineText;
  final List<StudioLyricWord> words;
  final int positionMs;
  final ValueListenable<int>? positionListenable;
  final Color accent;
  final Color primary;
  final Color secondary;
  final double fontSize;
  final bool isRtl;

  const _RainLine({
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
  Widget build(BuildContext context) {
    return _positionAware(
      listenable: positionListenable,
      initial: positionMs,
      builder: (position) {
        return LayoutBuilder(
          builder: (context, constraints) {
            final width = constraints.maxWidth.isFinite
                ? constraints.maxWidth
                : MediaQuery.sizeOf(context).width;
            final height = math.max(fontSize * 3.0, 124.0);
            final layout = _layoutSpine(
              words: words,
              width: width,
              height: height,
              fontSize: fontSize,
              seedText: lineText,
              isRtl: isRtl,
              labelsBelow: true,
              amplitude: 0,
              baseline: 0.66,
            );
            final revealed = _revealedCount(words, position);
            final preview = _isPreview(words, position);
            final active = preview ? -1 : revealed - 1;
            final progress = _wordProgressList(words, position);
            final fall = fontSize * 1.9;
            final activeProgress = active >= 0
                ? _wordProgress(words[active], position)
                : 0.0;
            return SizedBox(
              width: width,
              height: height,
              child: Stack(
                clipBehavior: Clip.none,
                children: [
                  Positioned.fill(
                    child: CustomPaint(
                      painter: _RainPainter(
                        points: [for (final node in layout.nodes) node.point],
                        progress: progress,
                        size: layout.size,
                        fall: fall,
                        accent: accent,
                        seed: _hashText(lineText),
                      ),
                    ),
                  ),
                  for (var i = 0; i < words.length; i++)
                    Positioned(
                      left: layout.nodes[i].textLeft,
                      top:
                          layout.nodes[i].textTop -
                          _rainFall(progress[i]) * fall -
                          (i.isEven ? 0 : fontSize * 0.16),
                      child: Transform.scale(
                        scale: progress[i] > 0.78
                            ? 1 +
                                  0.10 *
                                      math.sin(
                                        (progress[i] - 0.78) / 0.22 * math.pi,
                                      )
                            : 1,
                        alignment: Alignment.bottomCenter,
                        child: Opacity(
                          opacity: (0.16 + 0.84 * progress[i]).clamp(0.0, 1.0),
                          child: AnimatedDefaultTextStyle(
                            duration: const Duration(milliseconds: 200),
                            curve: Curves.easeOutCubic,
                            style:
                                _wordStyle(
                                  color: i == active
                                      ? accent
                                      : progress[i] > 0
                                      ? primary
                                      : secondary.withValues(alpha: 0.5),
                                  fontSize: layout.size,
                                  glow:
                                      i == active ||
                                      (activeProgress > 0 && progress[i] >= 1),
                                ).copyWith(
                                  shadows: [
                                    Shadow(
                                      color: accent.withValues(alpha: 0.3),
                                      blurRadius: layout.size * 0.35,
                                      offset: Offset(0, layout.size * 0.12),
                                    ),
                                  ],
                                ),
                            child: Text(words[i].text.toUpperCase()),
                          ),
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

class _RainPainter extends CustomPainter {
  final List<Offset> points;
  final List<double> progress;
  final double size;
  final double fall;
  final Color accent;
  final int seed;

  const _RainPainter({
    required this.points,
    required this.progress,
    required this.size,
    required this.fall,
    required this.accent,
    required this.seed,
  });

  @override
  void paint(Canvas canvas, Size canvasSize) {
    final random = math.Random(seed ^ 0x2c1b3c6d);
    final streak = Paint()
      ..strokeWidth = 0.9
      ..strokeCap = StrokeCap.round;
    for (var i = 0; i < 34; i++) {
      final x = random.nextDouble() * canvasSize.width;
      final top = random.nextDouble() * canvasSize.height * 0.7;
      final length = size * (0.3 + random.nextDouble() * 0.8);
      streak.color = Colors.white.withValues(
        alpha: 0.03 + random.nextDouble() * 0.06,
      );
      canvas.drawLine(
        Offset(x, top),
        Offset(x - size * 0.08, top + length),
        streak,
      );
    }

    if (points.isEmpty) return;
    final baseline = points.first.dy + size * 0.55;
    final wet = Rect.fromLTRB(
      points.first.dx - size,
      baseline,
      points.last.dx + size,
      baseline + 1,
    );
    canvas.drawLine(
      Offset(points.first.dx - size, baseline),
      Offset(points.last.dx + size, baseline),
      Paint()
        ..strokeWidth = 1
        ..shader = LinearGradient(
          colors: [
            accent.withValues(alpha: 0),
            accent.withValues(alpha: 0.30),
            accent.withValues(alpha: 0),
          ],
        ).createShader(wet),
    );

    for (var i = 0; i < points.length && i < progress.length; i++) {
      final p = progress[i];
      if (p <= 0) continue;
      final x = points[i].dx;
      final drop = _rainFall(p);
      final wordTop =
          points[i].dy +
          size * 0.52 -
          drop * fall -
          (i.isEven ? 0 : size * 0.16);

      if (p < 1) {
        // The falling word keeps a thin streak and a droplet above it.
        final length = fall * 0.42 * drop;
        final head = wordTop - size * 0.28;
        canvas.drawLine(
          Offset(x, head),
          Offset(x, head - length),
          Paint()
            ..strokeWidth = 1.4
            ..strokeCap = StrokeCap.round
            ..color = accent.withValues(alpha: 0.10 + 0.32 * (1 - drop)),
        );
        canvas.drawCircle(
          Offset(x, head - length),
          1.8,
          Paint()..color = Colors.white.withValues(alpha: 0.55),
        );
      }

      if (p > 0.72 && p < 1) {
        final t = ((p - 0.72) / 0.28).clamp(0.0, 1.0);
        for (var ring = 0; ring < 2; ring++) {
          final rt = (t + ring * 0.32).clamp(0.0, 1.0);
          canvas.drawOval(
            Rect.fromCenter(
              center: Offset(x, baseline),
              width: size * (0.35 + 1.5 * rt),
              height: size * (0.10 + 0.35 * rt),
            ),
            Paint()
              ..style = PaintingStyle.stroke
              ..strokeWidth = 1
              ..color = accent.withValues(
                alpha: (1 - rt) * (ring == 0 ? 0.5 : 0.26),
              ),
          );
        }
      }

      if (p >= 1) {
        canvas.drawOval(
          Rect.fromCenter(
            center: Offset(x, baseline + size * 0.10),
            width: size * 1.05,
            height: size * 0.18,
          ),
          Paint()..color = accent.withValues(alpha: 0.10),
        );
      }
    }
  }

  @override
  bool shouldRepaint(covariant _RainPainter oldDelegate) =>
      !listEquals(oldDelegate.progress, progress) ||
      !listEquals(oldDelegate.points, points) ||
      oldDelegate.fall != fall ||
      oldDelegate.accent != accent;
}

/// Typewriter: the line types itself out character by character with a
/// blinking block cursor, in the accent, on a terminal strip.
class _TypewriterLine extends StatelessWidget {
  final List<StudioLyricWord> words;
  final int positionMs;
  final ValueListenable<int>? positionListenable;
  final Color accent;
  final Color primary;
  final double fontSize;
  final bool isRtl;

  const _TypewriterLine({
    required this.words,
    required this.positionMs,
    required this.positionListenable,
    required this.accent,
    required this.primary,
    required this.fontSize,
    required this.isRtl,
  });

  @override
  Widget build(BuildContext context) {
    return _positionAware(
      listenable: positionListenable,
      initial: positionMs,
      builder: (position) {
        final finished = words.isNotEmpty && position >= words.last.endMs;
        final blink = (math.sin(position / 380) + 1) / 2;
        final typeSize = fontSize * 0.74;
        final preview = words.isEmpty || _isPreview(words, position);
        final revealed = words.isEmpty ? 0 : _revealedCount(words, position);
        final active = preview ? -1 : revealed - 1;
        var fraction = 0.0;
        if (active >= 0) {
          fraction =
              (active + _wordProgress(words[active], position)) / words.length;
        }
        final mono = TextStyle(
          fontFamily: 'Consolas',
          fontSize: typeSize,
          height: 1.35,
          letterSpacing: 0.6,
          fontWeight: FontWeight.w500,
          color: primary,
          shadows: [
            Shadow(
              color: accent.withValues(alpha: 0.30),
              blurRadius: typeSize * 0.4,
            ),
          ],
        );
        return SizedBox(
          height: math.max(fontSize * 2.6, 116),
          width: double.infinity,
          child: Align(
            alignment: isRtl ? Alignment.centerRight : Alignment.centerLeft,
            child: Container(
              width: double.infinity,
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.34),
                borderRadius: BorderRadius.circular(fontSize * 0.22),
                border: Border.all(color: Colors.white.withValues(alpha: 0.10)),
                boxShadow: [
                  BoxShadow(
                    color: accent.withValues(alpha: 0.16),
                    blurRadius: fontSize * 0.9,
                  ),
                ],
              ),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: EdgeInsets.fromLTRB(
                      fontSize * 0.42,
                      fontSize * 0.26,
                      fontSize * 0.42,
                      0,
                    ),
                    child: Row(
                      children: [
                        for (final dot in [
                          accent.withValues(alpha: 0.90),
                          primary.withValues(alpha: 0.35),
                          Colors.white.withValues(alpha: 0.18),
                        ])
                          Container(
                            width: fontSize * 0.13,
                            height: fontSize * 0.13,
                            margin: EdgeInsets.only(right: fontSize * 0.12),
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: dot,
                            ),
                          ),
                        const Spacer(),
                        Text(
                          'lyrics.txt',
                          style: TextStyle(
                            fontFamily: 'Consolas',
                            fontSize: fontSize * 0.20,
                            letterSpacing: 0.6,
                            color: Colors.white.withValues(alpha: 0.35),
                          ),
                        ),
                      ],
                    ),
                  ),
                  Padding(
                    padding: EdgeInsets.fromLTRB(
                      fontSize * 0.5,
                      fontSize * 0.22,
                      fontSize * 0.5,
                      fontSize * 0.18,
                    ),
                    child: Text.rich(
                      TextSpan(
                        children: [
                          TextSpan(
                            text: '> ',
                            style: mono.copyWith(
                              fontWeight: FontWeight.w700,
                              color: accent.withValues(alpha: 0.85),
                              shadows: const [],
                            ),
                          ),
                          ..._spans(position, active, mono),
                          if (!finished)
                            TextSpan(
                              text: '▍',
                              style: mono.copyWith(
                                color: accent.withValues(
                                  alpha: 0.25 + 0.70 * blink,
                                ),
                                shadows: const [],
                              ),
                            ),
                        ],
                      ),
                    ),
                  ),
                  Padding(
                    padding: EdgeInsets.fromLTRB(
                      fontSize * 0.5,
                      0,
                      fontSize * 0.5,
                      fontSize * 0.28,
                    ),
                    child: Row(
                      children: [
                        Text(
                          finished ? 'SYNCED' : 'TYPING',
                          style: TextStyle(
                            fontFamily: 'Consolas',
                            fontSize: fontSize * 0.18,
                            letterSpacing: 1.2,
                            fontWeight: FontWeight.w700,
                            color: accent.withValues(alpha: 0.80),
                          ),
                        ),
                        SizedBox(width: fontSize * 0.26),
                        Expanded(
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(99),
                            child: Container(
                              height: 2,
                              color: Colors.white.withValues(alpha: 0.08),
                              child: FractionallySizedBox(
                                alignment: Alignment.centerLeft,
                                widthFactor: fraction.clamp(0.0, 1.0),
                                child: ColoredBox(
                                  color: accent.withValues(alpha: 0.75),
                                ),
                              ),
                            ),
                          ),
                        ),
                        SizedBox(width: fontSize * 0.26),
                        Text(
                          '${(fraction * 100).round().toString().padLeft(2, '0')}%',
                          style: TextStyle(
                            fontFamily: 'Consolas',
                            fontSize: fontSize * 0.18,
                            color: Colors.white.withValues(alpha: 0.40),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        );
      },
    );
  }

  /// Typed characters so far, with the current word in the accent.
  List<TextSpan> _spans(int position, int active, TextStyle mono) {
    final spans = <TextSpan>[];
    for (var i = 0; i < words.length; i++) {
      final word = words[i];
      if (position < word.startMs) break;
      final letters = word.text.runes.map(String.fromCharCode).toList();
      if (letters.isEmpty) continue;
      final count = (letters.length * _wordProgress(word, position))
          .ceil()
          .clamp(1, letters.length);
      if (spans.isNotEmpty) spans.add(TextSpan(text: ' ', style: mono));
      spans.add(
        TextSpan(
          text: letters.take(count).join(),
          style: i == active
              ? mono.copyWith(
                  color: accent,
                  shadows: [
                    Shadow(
                      color: accent.withValues(alpha: 0.55),
                      blurRadius: mono.fontSize! * 0.55,
                    ),
                  ],
                )
              : mono,
        ),
      );
    }
    return spans;
  }
}
