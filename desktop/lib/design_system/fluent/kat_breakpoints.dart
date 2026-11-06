import '../../../ui/theme/tokens.dart';

/// Breakpoints (window width px):
/// compact <900 · normal 900–1300 · expanded 1300–1700 · ultrawide 1700+.
enum LwBreakpoint { compact, normal, expanded, ultrawide }

LwBreakpoint lwBreakpointFor(double width) {
  final w = katBreakpointFor(width);
  return switch (w) {
    KatBreakpoint.compact => LwBreakpoint.compact,
    KatBreakpoint.normal => LwBreakpoint.normal,
    KatBreakpoint.expanded => LwBreakpoint.expanded,
    KatBreakpoint.ultrawide => LwBreakpoint.ultrawide,
  };
}

/// Density — desktop music player zones.
class LwDensity {
  LwDensity._();
  static const double railCollapsed = KatDensity.railCollapsed; // 60
  static const double railExpanded = KatDensity.railExpanded; // 200
  static const double titleBar = KatDensity.titleBar; // 44
  static const double dock = KatDensity.dock; // 80
  static const double trackRow = KatDensity.trackRow; // 54
  static const double trackArt = KatDensity.trackArt; // 40
  static const double contextPanel = KatDensity.contextPanel; // 360
  static const double contextPanelWide = KatDensity.contextPanelWide; // 380
  static const double contentMax = KatDensity.contentMax; // 1280
  static const double lyricMax = KatDensity.lyricMax; // 620
  static const double hitArea = KatDensity.hitArea; // 36
  static const double iconPrimary = KatDensity.iconPrimary; // 18
  static const double iconSecondary = KatDensity.iconSecondary; // 15
}
