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
            var fraction = 0.0;
            if (active >= 0) {
              fraction =
                  (active + _wordProgress(words[active], position)) /
                  words.length;
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
                        accent: accent,
                      ),
                    ),
                  ),
                  for (var i = 0; i < words.length; i++)
                    Positioned(
                      left: layout.nodes[i].textLeft,
                      top: layout.nodes[i].textTop,
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
                              : secondary.withValues(alpha: 0.5),
                          fontSize: layout.size,
                          glow: i == active,
                        ),
                        child: Text(words[i].text.toUpperCase()),
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
  final Color accent;

  const _BranchesPainter({
    required this.points,
    required this.size,
    required this.fraction,
    required this.revealed,
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
      if (i == points.length - 1) path.quadraticBezierTo(b.dx, b.dy, b.dx, b.dy);
    }
    return path;
  }

  @override
  void paint(Canvas canvas, Size canvasSize) {
    final metrics = _vine().computeMetrics().toList();
    if (metrics.isEmpty) return;
    final metric = metrics.first;
    canvas.drawPath(
      metric.extractPath(0, metric.length),
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2
        ..strokeCap = StrokeCap.round
        ..color = Colors.white.withValues(alpha: 0.14),
    );
    if (fraction > 0) {
      canvas.drawPath(
        metric.extractPath(0, metric.length * fraction),
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 3
          ..strokeCap = StrokeCap.round
          ..color = accent.withValues(alpha: 0.85),
      );
    }
    for (var i = 0; i < points.length; i++) {
      final p = points[i];
      if (i < revealed) {
        canvas.drawCircle(p, size * 0.24, Paint()..color = accent.withValues(alpha: 0.16));
        canvas.drawCircle(p, size * 0.10, Paint()..color = accent);
        final leaf = Path()
          ..moveTo(p.dx, p.dy)
          ..quadraticBezierTo(
            p.dx + size * 0.34,
            p.dy - size * 0.30,
            p.dx + size * 0.50,
            p.dy - size * 0.02,
          )
          ..quadraticBezierTo(
            p.dx + size * 0.30,
            p.dy + size * 0.20,
            p.dx,
            p.dy,
          )
          ..close();
        canvas.drawPath(leaf, Paint()..color = accent.withValues(alpha: 0.28));
      } else {
        canvas.drawCircle(
          p,
          size * 0.07,
          Paint()..color = Colors.white.withValues(alpha: 0.26),
        );
      }
    }
  }

  @override
  bool shouldRepaint(covariant _BranchesPainter oldDelegate) =>
      oldDelegate.revealed != revealed ||
      oldDelegate.fraction != fraction ||
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
            final nodes = <Offset>[];
            final labels = <_WebLabel>[];
            final phase = (_hashText(lineText) % 628) / 100;
            for (var i = 0; i < n; i++) {
              final angle =
                  -math.pi / 2 + (2 * math.pi * i) / n + phase * 0.02;
              final r = radius * (i.isEven ? 0.8 : 1.0);
              final point = Offset(
                center.dx + math.cos(angle) * r,
                center.dy + math.sin(angle) * r,
              );
              nodes.add(point);
              final outward = labelSize * 1.15;
              final dx = point.dx + math.cos(angle) * outward;
              final dy = point.dy + math.sin(angle) * outward;
              final w = _visualLength(words[i].text) * labelSize * 0.62;
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
                        accent: accent,
                      ),
                    ),
                  ),
                  for (var i = 0; i < n; i++)
                    Positioned(
                      left: labels[i].left,
                      top: labels[i].top,
                      width: labels[i].width,
                      child: AnimatedDefaultTextStyle(
                        duration: const Duration(milliseconds: 220),
                        curve: Curves.easeOutCubic,
                        style: _wordStyle(
                          color: i == active
                              ? accent
                              : i < revealed
                              ? primary
                              : secondary.withValues(alpha: 0.5),
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

  const _WebLabel({
    required this.left,
    required this.top,
    required this.width,
  });
}

class _WebPainter extends CustomPainter {
  final Offset hub;
  final List<Offset> nodes;
  final List<double> progress;
  final double radius;
  final int revealed;
  final Color accent;

  const _WebPainter({
    required this.hub,
    required this.nodes,
    required this.progress,
    required this.radius,
    required this.revealed,
    required this.accent,
  });

  @override
  void paint(Canvas canvas, Size canvasSize) {
    final ring = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1
      ..color = Colors.white.withValues(alpha: 0.08);
    for (final factor in const [0.34, 0.62, 0.9]) {
      canvas.drawCircle(hub, radius * factor, ring);
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
        canvas.drawCircle(
          nodes[i],
          radius * 0.045,
          Paint()..color = accent.withValues(alpha: 0.5),
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
        final metric = (Path()
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
      }
    }

    final lit = revealed > 0;
    canvas.drawCircle(
      hub,
      radius * (lit ? 0.10 : 0.07),
      Paint()..color = accent.withValues(alpha: lit ? 0.22 : 0.10),
    );
    canvas.drawCircle(
      hub,
      radius * 0.045,
      Paint()..color = accent.withValues(alpha: lit ? 0.95 : 0.45),
    );
  }

  @override
  bool shouldRepaint(covariant _WebPainter oldDelegate) =>
      oldDelegate.revealed != revealed ||
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
            var traced = 0.0;
            if (active >= 0) {
              traced =
                  (active + _wordProgress(words[active], position)) /
                  words.length;
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
                      child: AnimatedDefaultTextStyle(
                        duration: const Duration(milliseconds: 220),
                        curve: Curves.easeOutCubic,
                        style: _wordStyle(
                          color: i == active
                              ? accent
                              : i < revealed
                              ? primary
                              : secondary.withValues(alpha: 0.45),
                          fontSize: layout.size,
                          glow: i == active,
                        ),
                        child: Text(words[i].text.toUpperCase()),
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
  final Color accent;
  final Color ink;
  final int seed;

  const _ConstellationPainter({
    required this.points,
    required this.size,
    required this.traced,
    required this.revealed,
    required this.accent,
    required this.ink,
    required this.seed,
  });

  @override
  void paint(Canvas canvas, Size canvasSize) {
    final random = math.Random(seed ^ 0x1f123bb5);
    final sky = Paint();
    for (var i = 0; i < 42; i++) {
      sky.color = Colors.white.withValues(
        alpha: 0.04 + random.nextDouble() * 0.10,
      );
      canvas.drawCircle(
        Offset(
          random.nextDouble() * canvasSize.width,
          random.nextDouble() * canvasSize.height,
        ),
        0.5 + random.nextDouble() * 1.2,
        sky,
      );
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
            ..color = Colors.white.withValues(alpha: 0.10),
        );
        continue;
      }
      final end = Offset.lerp(points[i - 1], points[i], coverage)!;
      canvas.drawLine(
        points[i - 1],
        end,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1.5
          ..strokeCap = StrokeCap.round
          ..color = Color.lerp(ink, accent, coverage)!.withValues(
            alpha: 0.35 + 0.45 * coverage,
          ),
      );
    }

    for (var i = 0; i < points.length; i++) {
      final p = points[i];
      if (i < revealed) {
        final isActive = i == revealed - 1;
        canvas.drawCircle(
          p,
          size * (isActive ? 0.56 : 0.36),
          Paint()..color = accent.withValues(alpha: isActive ? 0.26 : 0.12),
        );
        _sparkle(
          canvas,
          p,
          size * (isActive ? 0.34 : 0.24),
          Paint()..color = isActive ? Colors.white : accent,
        );
      } else {
        canvas.drawCircle(
          p,
          size * 0.07,
          Paint()..color = Colors.white.withValues(alpha: 0.35),
        );
        canvas.drawCircle(
          p,
          size * 0.17,
          Paint()
            ..style = PaintingStyle.stroke
            ..strokeWidth = 0.8
            ..color = Colors.white.withValues(alpha: 0.10),
        );
      }
    }
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
      return Align(
        alignment: Alignment.centerLeft,
        child: Container(
          margin: EdgeInsets.only(bottom: fontSize * 0.24),
          padding: EdgeInsets.symmetric(
            horizontal: fontSize * 0.62,
            vertical: fontSize * 0.44,
          ),
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: sung ? 0.10 : 0.07),
            borderRadius: BorderRadius.only(
              topLeft: Radius.circular(fontSize * 0.9),
              topRight: Radius.circular(fontSize * 0.9),
              bottomRight: Radius.circular(fontSize * 0.9),
              bottomLeft: Radius.circular(fontSize * 0.22),
            ),
            border: Border.all(
              color: Colors.white.withValues(alpha: sung ? 0.12 : 0.10),
            ),
          ),
          child: typing
              ? Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    for (var i = 0; i < 3; i++)
                      Padding(
                        padding: EdgeInsets.symmetric(
                          horizontal: fontSize * 0.05,
                        ),
                        child: Opacity(
                          opacity: reduceMotion
                              ? 0.55
                              : 0.25 +
                                    0.6 *
                                        ((math.sin(position / 240 + i * 1.25) +
                                                    1) /
                                                2)
                                            .clamp(0.0, 1.0),
                          child: Container(
                            width: fontSize * 0.17,
                            height: fontSize * 0.17,
                            decoration: BoxDecoration(
                              shape: BoxShape.circle,
                              color: secondary,
                            ),
                          ),
                        ),
                      ),
                  ],
                )
              : Wrap(
                  spacing: fontSize * 0.30,
                  runSpacing: fontSize * 0.08,
                  children: [
                    for (var i = 0; i < words.length; i++)
                      AnimatedDefaultTextStyle(
                        duration: reduceMotion
                            ? Duration.zero
                            : const Duration(milliseconds: 200),
                        curve: Curves.easeOutCubic,
                        style: _wordStyle(
                          color: sung || i < revealed
                              ? (i == active ? accent : primary)
                              : secondary.withValues(alpha: 0.45),
                          fontSize: fontSize,
                          glow: i == active,
                        ).copyWith(
                          fontWeight: FontWeight.w700,
                          letterSpacing: -0.2,
                          height: 1.12,
                        ),
                        child: Text(words[i].text),
                      ),
                  ],
                ),
        ),
      );
    },
  );
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
            var traced = 0.0;
            if (active >= 0) {
              traced =
                  (active + _wordProgress(words[active], position)) /
                  words.length;
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
                  left: (point.dx - w / 2).clamp(
                    0.0,
                    math.max(0.0, width - w),
                  ),
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
                        accent: accent,
                      ),
                    ),
                  ),
                  for (var i = 0; i < n; i++)
                    Positioned(
                      left: labels[i].left,
                      top: labels[i].top,
                      width: labels[i].width,
                      child: AnimatedDefaultTextStyle(
                        duration: const Duration(milliseconds: 220),
                        curve: Curves.easeOutCubic,
                        style: _wordStyle(
                          color: i == active
                              ? accent
                              : i < revealed
                              ? primary
                              : secondary.withValues(alpha: 0.5),
                          fontSize: i == active ? labelSize * 1.15 : labelSize,
                          glow: i == active,
                        ).copyWith(letterSpacing: 0),
                        child: Text(
                          words[i].text.toUpperCase(),
                          textAlign: TextAlign.center,
                          maxLines: 1,
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
  final Color accent;

  const _OrbitPainter({
    required this.center,
    required this.rx,
    required this.ry,
    required this.progress,
    required this.traced,
    required this.revealed,
    required this.accent,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final orbit = Rect.fromCenter(
      center: center,
      width: rx * 2,
      height: ry * 2,
    );
    canvas.drawOval(
      orbit,
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1
        ..color = Colors.white.withValues(alpha: 0.10),
    );
    canvas.drawOval(
      Rect.fromCenter(center: center, width: rx * 1.25, height: ry * 1.25),
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 0.8
        ..color = Colors.white.withValues(alpha: 0.05),
    );

    if (traced > 0) {
      final arc = Path()
        ..addArc(orbit, -math.pi / 2, 2 * math.pi * traced);
      canvas.drawPath(
        arc,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1.8
          ..strokeCap = StrokeCap.round
          ..color = accent.withValues(alpha: 0.55),
      );
    }

    for (var i = 0; i < progress.length; i++) {
      final angle = -math.pi / 2 + (2 * math.pi * i) / progress.length;
      final point = Offset(
        center.dx + math.cos(angle) * rx,
        center.dy + math.sin(angle) * ry,
      );
      final lit = i < revealed;
      canvas.drawCircle(
        point,
        lit ? 3.2 : 1.8,
        Paint()
          ..color = lit
              ? accent
              : Colors.white.withValues(alpha: 0.28),
      );
    }

    canvas.drawCircle(
      center,
      rx * 0.16,
      Paint()..color = accent.withValues(alpha: traced > 0 ? 0.20 : 0.08),
    );
    canvas.drawCircle(
      center,
      rx * 0.05,
      Paint()..color = accent.withValues(alpha: 0.9),
    );
  }

  @override
  bool shouldRepaint(covariant _OrbitPainter oldDelegate) =>
      oldDelegate.traced != traced ||
      oldDelegate.revealed != revealed ||
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
                          (1 - Curves.easeInCubic.transform(progress[i])) *
                              fall -
                          (i.isEven ? 0 : fontSize * 0.16),
                      child: Opacity(
                        opacity: (0.22 + 0.78 * progress[i]).clamp(0.0, 1.0),
                        child: AnimatedDefaultTextStyle(
                          duration: const Duration(milliseconds: 200),
                          curve: Curves.easeOutCubic,
                          style: _wordStyle(
                            color: i == active
                                ? accent
                                : progress[i] > 0
                                ? primary
                                : secondary.withValues(alpha: 0.55),
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

class _RainPainter extends CustomPainter {
  final List<Offset> points;
  final List<double> progress;
  final double size;
  final Color accent;
  final int seed;

  const _RainPainter({
    required this.points,
    required this.progress,
    required this.size,
    required this.accent,
    required this.seed,
  });

  @override
  void paint(Canvas canvas, Size canvasSize) {
    final random = math.Random(seed ^ 0x2c1b3c6d);
    final streak = Paint()
      ..strokeWidth = 0.9
      ..strokeCap = StrokeCap.round;
    for (var i = 0; i < 26; i++) {
      final x = random.nextDouble() * canvasSize.width;
      final top = random.nextDouble() * canvasSize.height * 0.6;
      final length = size * (0.25 + random.nextDouble() * 0.55);
      streak.color = Colors.white.withValues(
        alpha: 0.03 + random.nextDouble() * 0.05,
      );
      canvas.drawLine(
        Offset(x, top),
        Offset(x - size * 0.06, top + length),
        streak,
      );
    }

    if (points.isEmpty) return;
    final baseline = points.first.dy;
    canvas.drawLine(
      Offset(points.first.dx - size, baseline + size * 0.5),
      Offset(points.last.dx + size, baseline + size * 0.5),
      Paint()
        ..strokeWidth = 1
        ..color = Colors.white.withValues(alpha: 0.10),
    );

    for (var i = 0; i < points.length && i < progress.length; i++) {
      final p = progress[i];
      if (p <= 0.62 || p >= 1) continue;
      final t = ((p - 0.62) / 0.38).clamp(0.0, 1.0);
      canvas.drawOval(
        Rect.fromCenter(
          center: Offset(points[i].dx, baseline + size * 0.62),
          width: size * (0.4 + 1.1 * t),
          height: size * (0.12 + 0.28 * t),
        ),
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 1
          ..color = accent.withValues(alpha: (1 - t) * 0.55),
      );
    }
  }

  @override
  bool shouldRepaint(covariant _RainPainter oldDelegate) =>
      !listEquals(oldDelegate.progress, progress) ||
      !listEquals(oldDelegate.points, points) ||
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
        final typed = _typedText(position);
        final finished =
            words.isNotEmpty && position >= words.last.endMs;
        final blink = (math.sin(position / 380) + 1) / 2;
        final typeSize = fontSize * 0.78;
        return SizedBox(
          height: math.max(fontSize * 1.9, 84),
          width: double.infinity,
          child: Align(
            alignment: isRtl ? Alignment.centerRight : Alignment.centerLeft,
            child: Container(
              padding: EdgeInsets.symmetric(
                horizontal: fontSize * 0.5,
                vertical: fontSize * 0.32,
              ),
              decoration: BoxDecoration(
                color: Colors.black.withValues(alpha: 0.22),
                borderRadius: BorderRadius.circular(fontSize * 0.18),
                border: Border(
                  left: BorderSide(
                    color: accent.withValues(alpha: 0.65),
                    width: 2,
                  ),
                ),
              ),
              child: Text.rich(
                TextSpan(
                  children: [
                    TextSpan(
                      text: '> ',
                      style: TextStyle(
                        fontFamily: 'Consolas',
                        fontSize: typeSize,
                        fontWeight: FontWeight.w700,
                        color: accent.withValues(alpha: 0.75),
                      ),
                    ),
                    TextSpan(
                      text: typed,
                      style: TextStyle(
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
                      ),
                    ),
                    if (!finished)
                      TextSpan(
                        text: '▍',
                        style: TextStyle(
                          fontFamily: 'Consolas',
                          fontSize: typeSize,
                          color: accent.withValues(
                            alpha: 0.30 + 0.65 * blink,
                          ),
                        ),
                      ),
                  ],
                ),
              ),
            ),
          ),
        );
      },
    );
  }

  String _typedText(int position) {
    final parts = <String>[];
    for (final word in words) {
      if (position < word.startMs) break;
      final letters = word.text.runes.map(String.fromCharCode).toList();
      if (letters.isEmpty) continue;
      final progress = _wordProgress(word, position);
      final count = (letters.length * progress).ceil().clamp(
        1,
        letters.length,
      );
      parts.add(letters.take(count).join());
    }
    return parts.join(' ');
  }
}
