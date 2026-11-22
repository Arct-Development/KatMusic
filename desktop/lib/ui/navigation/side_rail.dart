import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter/services.dart';

import '../components/buttons.dart' show KatTooltip;
import '../theme/haze.dart';
import '../theme/motion.dart';
import '../theme/tokens.dart';
import 'destinations.dart';

/// Compact secondary navigation — MUSIC stays primary.
///
/// - 200px expanded / 60px collapsed rail
/// - small 36px rows, no giant rounded rectangles
/// - selected = subtle wash + 3px accent bar, nothing loud
/// - bottom: Friends / Settings pinned, history hidden
class KatSideRail extends StatefulWidget {
  final bool expanded;
  final String active;
  final void Function(String) onGo;
  const KatSideRail({
    super.key,
    required this.expanded,
    required this.active,
    required this.onGo,
  });
  @override
  State<KatSideRail> createState() => _KatSideRailState();
}

class _KatSideRailState extends State<KatSideRail> {
  bool get expanded => widget.expanded;
  String get active => widget.active;
  void Function(String) get onGo => widget.onGo;

  /// Row cache: per navigation only the two rows whose selection flips
  /// miss (old + new active); the other ~10 rows — entrance wrapper,
  /// tooltips, semantics, hover regions included — are reused verbatim
  /// instead of reconstructed. Cleared on expand/collapse (every row
  /// changes shape); bounded at 4 instances per destination max.
  final Map<String, Widget> _rowCache = {};

  @override
  void didUpdateWidget(covariant KatSideRail oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.expanded != widget.expanded) _rowCache.clear();
  }

  Widget _row(KatDestination destination, int index, {double rise = 6}) {
    final selected = active == destination.path;
    return _rowCache.putIfAbsent(
      '${destination.path}|$expanded|$selected',
      () => KatEntrance(
        index: index,
        rise: rise,
        child: _RailItem(
          destination: destination,
          expanded: expanded,
          selected: selected,
          onTap: () => onGo(destination.path),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final width = expanded ? KatDensity.railExpanded : KatDensity.railCollapsed;
    // Haze Level 1 — subtle tonal navigation material, never a solid slab.
    // The expanded 200px drawer floats over the page (content only reserves
    // the 60px collapsed strip), so it has to frost what it covers; the
    // collapsed strip has the shell behind it and blurring it would buy
    // nothing. Haze=Solid still short-circuits the filter upstream.
    //
    // Expanded it wears the lyrics drawer's frost verbatim — L2's 20px blur
    // under a 40% tint (60% in light themes, matching that panel's fill)
    // instead of L1's 88% fill. At 88% the drawer read as an opaque slab
    // parked next to a translucent panel that covers the same page.
    // Fully opaque in both states: the expanded rail is just the collapsed
    // strip grown wider, and the page sits beside it, never under it.
    return KatLiquidGlass(
      base: katRailTranslucent(context).withValues(alpha: 0.72),
      border: Border(right: BorderSide(color: katDivider(context))),
      child: AnimatedContainer(
        duration: katMotion(context, KatMotion.normal),
        curve: KatMotion.standard,
        width: width,
        // The rail persists across navigation, so this group's timeline
        // runs exactly once — a startup cascade for the destinations.
        child: KatEntranceGroup(
          child: Column(
            children: [
              _RailBrand(expanded: expanded),
              Expanded(
                child: ListView(
                  padding: const EdgeInsets.symmetric(horizontal: 6),
                  children: [
                    _RailSeparator(expanded: expanded, label: 'Listen'),
                    // One-shot startup cascade — standalone entrances run once
                    // on mount; in-place rebuilds (selection/hover) never replay.
                    for (var i = 0; i < katListenDestinations.length; i++)
                      _row(katListenDestinations[i], i),
                    KatEntrance(
                      index: katListenDestinations.length,
                      rise: 4,
                      child: _RailSeparator(
                        expanded: expanded,
                        label: 'Your library',
                      ),
                    ),
                    for (var i = 0; i < katCollectionDestinations.length; i++)
                      _row(
                        katCollectionDestinations[i],
                        katListenDestinations.length + i,
                      ),
                    KatEntrance(
                      index:
                          katListenDestinations.length +
                          katCollectionDestinations.length,
                      rise: 4,
                      child: _RailSeparator(expanded: expanded, label: 'Offline'),
                    ),
                    for (var i = 0; i < katOfflineDestinations.length; i++)
                      _row(
                        katOfflineDestinations[i],
                        katListenDestinations.length +
                            katCollectionDestinations.length +
                            i,
                      ),
                  ],
                ),
              ),
              Container(
                height: 1,
                margin: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
                color: katDivider(context),
              ),
              for (var i = 0; i < katSystemDestinations.length; i++)
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 6),
                  child: _row(
                    katSystemDestinations[i],
                    katListenDestinations.length +
                        katCollectionDestinations.length +
                        katOfflineDestinations.length +
                        i,
                  ),
                ),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }
}

/// Small identity lockup for the rail. The mark stays visible in compact mode
/// so the collapsed rail still reads as KatMusic rather than an unexplained
/// column of icons. The wordmark fades in only after the rail has room for it.
class _RailBrand extends StatelessWidget {
  final bool expanded;
  const _RailBrand({required this.expanded});

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: katMotion(context, KatMotion.normal),
      curve: KatMotion.standard,
      height: 54,
      padding: EdgeInsets.symmetric(horizontal: expanded ? 14 : 0),
      child: Center(
        child: Row(
          mainAxisSize: expanded ? MainAxisSize.max : MainAxisSize.min,
          children: [
            ClipRRect(
              borderRadius: BorderRadius.circular(8),
              child: Image.asset(
                'assets/brand/katmusic-logo.png',
                width: 28,
                height: 28,
                fit: BoxFit.cover,
              ),
            ),
            if (expanded) ...[
              const SizedBox(width: 10),
              Expanded(
                child: AnimatedOpacity(
                  duration: katMotion(context, KatMotion.fast),
                  opacity: expanded ? 1 : 0,
                  child: Text(
                    'KATMUSIC',
                    maxLines: 1,
                    overflow: TextOverflow.clip,
                    style: KatType.overline.copyWith(
                      color: katTextPrimary(context),
                      letterSpacing: 1.35,
                    ),
                  ),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

class _RailSeparator extends StatelessWidget {
  final bool expanded;
  final String? label;
  const _RailSeparator({required this.expanded, this.label});
  @override
  Widget build(BuildContext context) {
    if (expanded && label != null) {
      return Padding(
        padding: const EdgeInsets.fromLTRB(10, 12, 10, 5),
        child: Text(
          label!.toUpperCase(),
          maxLines: 1,
          overflow: TextOverflow.clip,
          style: KatType.overline.copyWith(
            fontSize: 9,
            letterSpacing: 1.1,
            color: katTextTertiary(context),
          ),
        ),
      );
    }
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 7),
      child: Center(
        child: Container(
          width: expanded ? double.infinity : 20,
          height: 1,
          margin: expanded
              ? const EdgeInsets.symmetric(horizontal: 10)
              : EdgeInsets.zero,
          color: katDivider(context),
        ),
      ),
    );
  }
}

class _RailItem extends StatefulWidget {
  final KatDestination destination;
  final bool expanded;
  final bool selected;
  final VoidCallback onTap;
  const _RailItem({
    required this.destination,
    required this.expanded,
    required this.selected,
    required this.onTap,
  });

  @override
  State<_RailItem> createState() => _RailItemState();
}

class _RailItemState extends State<_RailItem> {
  bool _hover = false;
  final FocusNode _focus = FocusNode();

  @override
  void dispose() {
    _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final dark = katIsDark(context);
    final accent = katAccent(context);
    final bg = widget.selected
        ? (dark ? Colors.white : Colors.black).withValues(alpha: 0.08)
        : _hover
        ? (dark ? Colors.white : Colors.black).withValues(alpha: 0.04)
        : Colors.transparent;
    final fg = widget.selected
        ? (dark ? KatColors.textPrimary : KatColors.lightTextPrimary)
        : (dark ? KatColors.textSecondary : KatColors.lightTextSecondary);
    final hasFocus = _focus.hasFocus;
    final itemHeight = widget.expanded ? 36.0 : 40.0;

    final content = Focus(
      focusNode: _focus,
      onKeyEvent: (node, event) {
        if (event is KeyDownEvent &&
            (event.logicalKey == LogicalKeyboardKey.enter ||
                event.logicalKey == LogicalKeyboardKey.numpadEnter ||
                event.logicalKey == LogicalKeyboardKey.space)) {
          widget.onTap();
          return KeyEventResult.handled;
        }
        return KeyEventResult.ignored;
      },
      onFocusChange: (_) => setState(() {}),
      child: MouseRegion(
        cursor: SystemMouseCursors.click,
        onEnter: (_) => setState(() => _hover = true),
        onExit: (_) => setState(() => _hover = false),
        child: GestureDetector(
          onTap: () {
            _focus.requestFocus();
            widget.onTap();
          },
          child: AnimatedContainer(
            duration: katMotion(context, KatMotion.fast),
            height: itemHeight,
            margin: const EdgeInsets.symmetric(vertical: 1.5),
            padding: EdgeInsets.zero,
            decoration: BoxDecoration(
              color: bg,
              borderRadius: KatRadius.controlsRadius,
              border: hasFocus
                  ? Border.all(color: accent.withValues(alpha: 0.6), width: 1)
                  : Border.all(color: Colors.transparent, width: 1),
            ),
            child: Stack(
              alignment: Alignment.centerLeft,
              children: [
                AnimatedPositioned(
                  duration: katMotion(context, KatMotion.fast),
                  curve: Curves.easeOutCubic,
                  left: widget.selected ? 0 : -4,
                  top: (itemHeight - 16) / 2,
                  height: 16,
                  child: AnimatedOpacity(
                    duration: katMotion(context, KatMotion.fast),
                    opacity: widget.selected ? 1.0 : 0.0,
                    child: Container(
                      width: 3,
                      decoration: BoxDecoration(
                        color: accent,
                        borderRadius: KatRadius.tinyRadius,
                      ),
                    ),
                  ),
                ),
                Positioned.fill(
                  child: Padding(
                    padding: EdgeInsets.symmetric(
                      horizontal: widget.expanded ? 14 : 0,
                    ),
                    child: Center(
                      child: LayoutBuilder(
                        builder: (context, constraints) {
                          // Follow the animating rail width, not the
                          // instant `expanded` flag: the rail container
                          // animates 60<->200px over KatMotion.normal
                          // while the flag flips immediately, which
                          // overflowed the Row mid-flight.
                          final isWide = constraints.maxWidth > 100;
                          return isWide
                              ? Row(
                                  children: [
                                    AnimatedScale(
                                      scale: widget.selected
                                          ? 1.05
                                          : (_hover ? 1.02 : 1.0),
                                      duration: katMotion(
                                        context,
                                        KatMotion.fast,
                                      ),
                                      curve: Curves.easeOutCubic,
                                      child: Icon(
                                        widget.destination.icon,
                                        size: 16,
                                        color: widget.selected ? accent : fg,
                                      ),
                                    ),
                                    const SizedBox(width: 12),
                                    Expanded(
                                      child: Text(
                                        widget.destination.label,
                                        maxLines: 1,
                                        overflow: TextOverflow.ellipsis,
                                        style: KatType.label.copyWith(
                                          fontSize: 13,
                                          fontWeight: widget.selected
                                              ? FontWeight.w600
                                              : FontWeight.w400,
                                          color: fg,
                                        ),
                                      ),
                                    ),
                                  ],
                                )
                              : AnimatedScale(
                                  scale: widget.selected
                                      ? 1.06
                                      : (_hover ? 1.03 : 1.0),
                                  duration: katMotion(context, KatMotion.fast),
                                  curve: Curves.easeOutCubic,
                                  child: Icon(
                                    widget.destination.icon,
                                    size: 18,
                                    color: widget.selected ? accent : fg,
                                  ),
                                );
                        },
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    final labelled = Semantics(
      button: true,
      selected: widget.selected,
      label: widget.destination.label,
      onTap: widget.onTap,
      excludeSemantics: true,
      child: content,
    );
    if (widget.expanded) return labelled;
    return KatTooltip(message: widget.destination.label, child: labelled);
  }
}
