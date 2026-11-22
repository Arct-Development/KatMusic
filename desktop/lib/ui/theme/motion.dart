import 'dart:math' as math;

import 'package:fluent_ui/fluent_ui.dart';

import 'tokens.dart';

/// WinUI 3 motion layer — staggered "Entrance" choreography.
///
/// Mirrors the WinUI `EntranceThemeTransition`: content fades in while
/// drifting upward a short distance, with a small per-item stagger so
/// sections cascade instead of popping as one slab.
///
/// Recycling safety: [KatEntranceGroup] owns ONE controller started when
/// the content mounts (typically when async data first arrives). Every
/// [KatEntrance] maps its index onto an interval of that single
/// timeline, so lazily-built list/grid rows that mount AFTER the timeline
/// finished render fully visible — no fade-replay when scrolling back up
/// in virtualized lists ([SuperSliverList], [SliverGrid], …).
///
/// Usage:
/// ```dart
/// KatEntranceGroup(
///   child: CustomScrollView(slivers: [
///     SliverToBoxAdapter(child: KatEntrance(index: 0, child: header)),
///     SliverGrid(... KatEntrance(index: i, child: card) ...),
///   ]),
/// )
/// ```
class KatEntranceGroup extends StatefulWidget {
  final Widget child;

  /// Set false while data loads and flip to true when content is ready —
  /// the timeline starts on the first `true` and never restarts.
  final bool start;

  const KatEntranceGroup({super.key, required this.child, this.start = true});

  @override
  State<KatEntranceGroup> createState() => _KatEntranceGroupState();
}

class _KatEntranceGroupState extends State<KatEntranceGroup>
    with SingleTickerProviderStateMixin {
  /// Total window: must exceed max stagger + item duration.
  static const _window = Duration(milliseconds: 220);

  late final AnimationController _controller;
  bool _started = false;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(vsync: this, duration: _window);
    if (widget.start) _go();
  }

  @override
  void didUpdateWidget(KatEntranceGroup oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.start) _go();
  }

  void _go() {
    if (_started) return;
    _started = true;
    _controller.forward();
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    // Reduce motion: the whole point of this widget is the movement, so
    // there is no "instant" version to fall back to — hand the child over.
    if (katReduceMotion(context)) return widget.child;
    // The scope stays for the group's whole life. Dropping it once the
    // timeline finished looked like a saving, but a descendant that rebuilds
    // afterwards — a rail row on expand/collapse, a table on sort, a row whose
    // like just flipped — then fell back to the standalone entrance and
    // replayed its fade, so the content blinked back in. A completed group
    // already hands plain children back (`_groupDriven` short-circuits), so
    // keeping the scope costs one inherited lookup and nothing else.
    return _KatEntranceScope(controller: _controller, child: widget.child);
  }
}

class _KatEntranceScope extends InheritedWidget {
  final AnimationController controller;
  const _KatEntranceScope({required this.controller, required super.child});

  static AnimationController? of(BuildContext context) => context
      .dependOnInheritedWidgetOfExactType<_KatEntranceScope>()
      ?.controller;

  @override
  bool updateShouldNotify(_KatEntranceScope oldWidget) =>
      oldWidget.controller != controller;
}

/// Staggered fade + rise entrance for one child.
///
/// - Inside a [KatEntranceGroup]: driven by the shared timeline
///   (recycle-safe for virtualized lists).
/// - Standalone (no group): animates once on mount, but only for
///   [index] < [_standaloneCap] so deep rows in lazy lists never replay.
class KatEntrance extends StatelessWidget {
  final int index;
  final Widget child;

  /// Rise distance in logical pixels (WinUI entrance drift).
  final double rise;

  /// When true, always run a local one-shot entrance even inside a
  /// [KatEntranceGroup] — for content that must animate at page mount
  /// instead of waiting for the group's start gate (e.g. page headers).
  final bool local;

  const KatEntrance({
    super.key,
    this.index = 0,
    this.rise = 14,
    this.local = false,
    required this.child,
  });

  /// Per-item stagger step.
  static const stepMs = 20;

  /// One item's fade/rise duration.
  static const itemMs = 180;

  /// Items past this index share the last stagger slot (grouped).
  static const maxStagger = 6;

  /// Standalone mode animates only the first few rows (lazy-list safety).
  static const _standaloneCap = 6;

  @override
  Widget build(BuildContext context) {
    // Reduce motion: skip the stagger entirely (see [KatEntranceGroup]).
    if (katReduceMotion(context)) return child;
    final group = local ? null : _KatEntranceScope.of(context);
    if (group != null) return _groupDriven(group);
    if (index >= _standaloneCap) return child;
    return _standalone();
  }

  Widget _groupDriven(AnimationController controller) {
    if (controller.isCompleted || controller.value >= 1.0) return child;
    final totalMs = controller.duration?.inMilliseconds ?? 220;
    final slot = math.min(index, maxStagger);
    final startMs = math.min(slot * stepMs, totalMs - itemMs - 20);
    final begin = (startMs / totalMs).clamp(0.0, 0.97);
    final end = ((startMs + itemMs) / totalMs).clamp(begin + 0.01, 1.0);
    final curved = CurvedAnimation(
      parent: controller,
      curve: Interval(begin, end, curve: Curves.easeOutCubic),
    );
    return AnimatedBuilder(
      animation: controller,
      child: child,
      builder: (context, child) {
        if (controller.isCompleted || controller.value >= 1.0) return child!;
        return _paint(curved.value, child!);
      },
    );
  }

  Widget _standalone() {
    // Bake the stagger delay into an Interval curve so the one-shot
    // TweenAnimationBuilder needs no timers or extra controllers.
    final delayMs = index * stepMs;
    final totalMs = delayMs + itemMs;
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: Duration(milliseconds: totalMs),
      curve: Interval(delayMs / totalMs, 1, curve: Curves.easeOutCubic),
      child: child,
      builder: (context, value, child) {
        if (value >= 1.0) return child!;
        return _paint(value, child!);
      },
    );
  }

  Widget _paint(double t, Widget child) {
    if (t >= 1.0) return child;
    if (t <= 0.0) {
      return Opacity(opacity: 0.0, child: child);
    }
    return Opacity(
      opacity: t,
      child: Transform.translate(
        offset: Offset(0, rise * (1 - t)),
        child: child,
      ),
    );
  }
}
