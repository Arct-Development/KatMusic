import 'package:fluent_ui/fluent_ui.dart';

import '../../../ui/theme/tokens.dart';

/// Semantic Fluent surfaces — single source of truth for every page.
///
/// New code should read surfaces through [LwSurfaces] (context-aware) or
/// the raw [LwColors] constants below. Do NOT scatter hex literals in
/// pages: import this file.
///
/// Mapping to the canonical Kat tokens (`lib/ui/theme/tokens.dart`):
/// - surfaceBase      → scaffold / Mica base
/// - surfaceLayer1/2  → content layering (cards stay flat, layers differ)
/// - surfaceMica      → native Mica base (window.dart paints this)
/// - surfaceAcrylic   → title chrome / suggestion flyout base
/// - surfaceHaze      → Haze Level 1/2 tonal wash (see kat_materials.dart)
/// - surfaceElevated  → player dock / raised controls
/// - surfaceFlyout    → MenuFlyout / ContentDialog / popover (Haze L3)
/// - surfacePlayer    → persistent player dock (Haze L1 + hairline)
///
/// Text + interaction tokens mirror WinUI rest visuals:
/// hover 7% wash · pressed 12% · selected accent wash · focus 2px ring
/// ([katFocusRing], the accent — a fixed dark grey was invisible on the
/// graphite base).
class LwColors {
  LwColors._();

  // -- Dark (graphite, zero navy) -----------------------------------------
  static const surfaceBase = KatColors.background; // #0E0E0E
  static const surfaceBaseDeep = KatColors.backgroundDeep; // #0A0A0A
  static const surfaceLayer1 = KatColors.surface; // #161616
  static const surfaceLayer2 = KatColors.surfaceRaised; // #1E1E1E
  static const surfaceMica = KatColors.background;
  static const surfaceAcrylic = KatColors.panelTranslucent; // 0xE6161616
  static const surfaceHaze = KatColors.panelTranslucent;
  static const surfaceElevated = KatColors.surfaceOverlay; // #262626
  static const surfaceFlyout = KatColors.surfaceRaised; // #1E1E1E
  static const surfacePlayer = KatColors.dockTranslucent; // 0xF2141414

  static const textPrimary = KatColors.textPrimary;
  static const textSecondary = KatColors.textSecondary;
  static const textTertiary = KatColors.textTertiary;

  static const divider = KatColors.outlineSoft; // #1F1F1F hairline
  static const dividerStrong = KatColors.outline; // #2B2B2B

  static const accent = KatColors.defaultAccent; // off-white neutral
  static const success = KatColors.success;
  static const warn = KatColors.warn;
  static const danger = KatColors.danger;

  // -- Light (neutral off-white, not beige / not pure white) ---------------
  static const lightSurfaceBase = KatColors.lightBackground; // #F3F3F3
  static const lightSurfaceNav = KatColors.lightNavBackground; // #ECECEC
  static const lightSurfaceLayer1 = KatColors.lightContent; // #F9F9F9
  static const lightSurfaceLayer2 = KatColors.lightSurface; // #FFFFFF
  static const lightSurfaceFlyout = KatColors.lightSurface;
  static const lightSurfacePlayer = KatColors.lightSurface;
  static const lightTextPrimary = KatColors.lightTextPrimary;
  static const lightTextSecondary = KatColors.lightTextSecondary;
  static const lightTextTertiary = KatColors.lightTextTertiary;
  // Black 10%, matching `katDivider`: a fixed grey could only ever read on
  // the white cards or on the #F3F3F3 page, never on both.
  static const lightDivider = Color(0x1A000000);
  static const lightDividerStrong = KatColors.lightOutline;
}

/// Context-aware surface resolution (dark ↔ light).
class LwSurfaces {
  LwSurfaces._();

  static bool isDark(BuildContext context) => katIsDark(context);

  static Color base(BuildContext context) =>
      isDark(context) ? LwColors.surfaceBase : LwColors.lightSurfaceBase;

  static Color layer1(BuildContext context) =>
      isDark(context) ? LwColors.surfaceLayer1 : LwColors.lightSurfaceLayer1;

  static Color layer2(BuildContext context) =>
      isDark(context) ? LwColors.surfaceLayer2 : LwColors.lightSurfaceLayer2;

  static Color flyout(BuildContext context) =>
      isDark(context) ? LwColors.surfaceFlyout : LwColors.lightSurfaceFlyout;

  static Color player(BuildContext context) => isDark(context)
      ? LwColors.surfacePlayer
      : LwColors.lightSurfacePlayer.withValues(alpha: 0.97);

  static Color textPrimary(BuildContext context) => katTextPrimary(context);
  static Color textSecondary(BuildContext context) => katTextSecondary(context);
  static Color textTertiary(BuildContext context) => katTextTertiary(context);
  static Color divider(BuildContext context) => katDivider(context);

  /// Artwork accent: runtime tint applied sparingly (Now Playing wash,
  /// hero wash ≤12%). Base surfaces stay neutral — artwork adds
  /// atmosphere, never floods the app.
  static Color artworkAccent(BuildContext context, Color? tint) =>
      tint ?? katAccent(context);
}
