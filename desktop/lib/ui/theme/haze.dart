import 'dart:ui';

import 'package:fluent_ui/fluent_ui.dart';
import 'package:liquid_glass_widgets/liquid_glass_widgets.dart' as glass;

import '../../design_system/fluent/fluent.dart';
import 'tokens.dart';

export '../../design_system/fluent/fluent.dart'
    show LwHaze, LwHazeLevel, LwAmbientWash, lwHazeSpecs;

/// UI-layer Haze convenience.
///
/// New pages should use [KatHaze] directly (L0–L3 levels). It honours
/// the user's Material setting (Automatic / Haze / Solid) and intensity
/// (Low / Medium / High) via [KatHazeScope] — pages never branch on
/// prefs themselves.
enum KatMaterialMode { automatic, haze, solid }

enum KatHazeIntensity { low, medium, high }

class KatHazeScope extends InheritedWidget {
  final KatMaterialMode material;
  final KatHazeIntensity intensity;

  /// Shader-backed Liquid Glass chrome. Set from Settings and read by
  /// [KatLiquidGlass] / [KatGlass]; Solid mode and Reduce Transparency
  /// always fall back to the tonal surface.
  final bool liquidGlass;

  const KatHazeScope({
    super.key,
    required this.material,
    required this.intensity,
    this.liquidGlass = false,
    required super.child,
  });

  static KatHazeScope of(BuildContext context) {
    return context.dependOnInheritedWidgetOfExactType<KatHazeScope>() ??
        const KatHazeScope(
          material: KatMaterialMode.automatic,
          intensity: KatHazeIntensity.medium,
          child: SizedBox.shrink(),
        );
  }

  /// Blur is disabled in Solid mode (static tonal fallback everywhere).
  static bool blurEnabled(BuildContext context) =>
      of(context).material != KatMaterialMode.solid;

  /// Whether [level] may filter on this device/settings combination.
  ///
  /// A caller's `enableBlur` is a *request*, never an override: Solid keeps
  /// every surface static whatever the surface asks for, and L1 — the thinnest
  /// material, a 12px filter over chrome — stays tonal at low intensity
  /// instead of paying for a faint blur nobody can see.
  static bool allowsBlur(BuildContext context, LwHazeLevel level) {
    final scope = of(context);
    if (scope.material == KatMaterialMode.solid) return false;
    if (level == LwHazeLevel.l1 && scope.intensity == KatHazeIntensity.low) {
      return false;
    }
    return true;
  }

  /// Intensity scales the L1–L3 sigma: low 0.6× · medium 1.0× · high 1.25×.
  /// L1 skips the filter entirely on low (see [allowsBlur]); the remaining
  /// levels get the multiplier, which is all `LwHaze` needs.
  static double blurScale(BuildContext context) =>
      switch (of(context).intensity) {
        KatHazeIntensity.low => 0.6,
        KatHazeIntensity.medium => 1.0,
        KatHazeIntensity.high => 1.25,
      };

  /// Fill multiplier for translucent surfaces: extra translucency keeps 15%
  /// less surface under the blur. 1.0 when the experiment is off.
  static double fillScale(BuildContext context) =>
      of(context).liquidGlass ? 0.85 : 1.0;

  /// Extra-translucency blur multiplier (1.0 when the experiment is off).
  /// The thinner fill needs a wider filter to still read as glass.
  static double liquidBlurScale(BuildContext context) =>
      of(context).liquidGlass ? 1.45 : 1.0;

  @override
  bool updateShouldNotify(KatHazeScope old) =>
      material != old.material ||
      intensity != old.intensity ||
      liquidGlass != old.liquidGlass;
}

/// Haze material container honouring the user's material setting.
///
/// Thin wrapper over [LwHaze]: pages pass a level + child, the scope
/// decides blur vs. static tonal fallback.
class KatHaze extends StatelessWidget {
  final LwHazeLevel level;
  final Widget child;
  final Color? base;
  final Color? artworkTint;
  final BorderRadiusGeometry borderRadius;
  final BoxBorder? border;
  final List<BoxShadow>? shadow;
  final bool? enableBlur;

  /// Absolute fill alpha for surfaces that have to match a hand-tuned frost
  /// recipe instead of a spec level (see [LwHaze.fillAlpha]). Ignored when the
  /// filter is off — Solid / reduced transparency keeps the tonal alpha.
  final double? fillAlpha;

  const KatHaze({
    super.key,
    required this.level,
    required this.child,
    this.base,
    this.artworkTint,
    this.borderRadius = BorderRadius.zero,
    this.border,
    this.shadow,
    this.enableBlur,
    this.fillAlpha,
  });

  const KatHaze.chrome({
    super.key,
    required this.child,
    this.base,
    this.artworkTint,
    this.border,
    this.shadow,
    this.enableBlur,
    this.fillAlpha,
  }) : level = LwHazeLevel.l1,
       borderRadius = BorderRadius.zero;

  const KatHaze.panel({
    super.key,
    required this.child,
    this.base,
    this.artworkTint,
    this.border,
    this.shadow,
    this.enableBlur,
    this.fillAlpha,
  }) : level = LwHazeLevel.l2,
       borderRadius = BorderRadius.zero;

  const KatHaze.flyout({
    super.key,
    required this.child,
    this.base,
    this.artworkTint,
    this.border,
    this.shadow,
    this.enableBlur,
    this.fillAlpha,
  }) : level = LwHazeLevel.l3,
       borderRadius = const BorderRadius.all(Radius.circular(KatRadius.menu));

  @override
  Widget build(BuildContext context) {
    return LwHaze(
      level: level,
      base: base,
      artworkTint: artworkTint,
      borderRadius: borderRadius,
      border: border,
      shadow: shadow,
      // The flag says "this surface wants a filter"; the scope says whether
      // it may have one. Passing it straight through meant an explicit `true`
      // (the rail's `enableBlur: expanded`) blurred even in Solid mode.
      enableBlur:
          (enableBlur ?? true) && KatHazeScope.allowsBlur(context, level),
      blurScale: KatHazeScope.liquidBlurScale(context),
      alphaScale: KatHazeScope.fillScale(context),
      fillAlpha: fillAlpha,
      child: child,
    );
  }
}

/// Shader-backed liquid-glass chrome for navigation and transport surfaces.
///
/// This is deliberately opt-in at the surface level: content cards stay
/// opaque, while the title bar, rail, dock and floating control chips sit in
/// the glass layer above the page. Falls back to [KatHaze] in Solid mode,
/// when the user disables Liquid Glass, or under Reduce Transparency.
class KatLiquidGlass extends StatelessWidget {
  final Widget child;
  final Color base;
  final BorderRadius borderRadius;
  final BoxBorder? border;
  final bool? enabled;

  const KatLiquidGlass({
    super.key,
    required this.child,
    required this.base,
    this.borderRadius = BorderRadius.zero,
    this.border,
    this.enabled,
  });

  @override
  Widget build(BuildContext context) {
    final scope = KatHazeScope.of(context);
    final reduceT = MediaQuery.maybeOf(context)?.highContrast ?? false;
    final active =
        (enabled ?? scope.liquidGlass) &&
        scope.material != KatMaterialMode.solid &&
        !reduceT;
    if (!active) {
      return KatHaze(
        level: LwHazeLevel.l1,
        base: base,
        borderRadius: borderRadius,
        border: border,
        enableBlur: false,
        fillAlpha: 0.92,
        child: child,
      );
    }
    final dark = katIsDark(context);
    final radius = borderRadius.topLeft.x;
    final tint = Color.lerp(
      base,
      dark ? Colors.black : Colors.white,
      dark ? 0.16 : 0.40,
    )!.withValues(alpha: dark ? 0.30 : 0.48);
    return RepaintBoundary(
      child: glass.GlassContainer(
        useOwnLayer: true,
        quality: glass.GlassQuality.standard,
        shape: glass.LiquidRoundedSuperellipse(borderRadius: radius),
        settings: glass.LiquidGlassSettings(
          thickness: 24,
          blur: 9,
          glassColor: tint,
          saturation: 1.24,
          lightIntensity: dark ? 0.52 : 0.68,
          ambientStrength: dark ? 0.16 : 0.10,
          chromaticAberration: 0.006,
        ),
        child: DecoratedBox(
          decoration: BoxDecoration(borderRadius: borderRadius, border: border),
          child: child,
        ),
      ),
    );
  }
}

/// Frosted chip for small chrome sitting on a colorful or animated
/// backdrop (Now Playing controls, lyrics toolbar, quality / up-next).
///
/// Bounded blur only. Falls back to a stronger tonal fill when Haze is
/// Solid or the user requested reduced transparency.
class KatGlass extends StatelessWidget {
  final Widget child;
  final BorderRadius borderRadius;
  final EdgeInsetsGeometry? padding;

  /// When false, skips the [BackdropFilter] and renders the tonal fill
  /// only. Use on pages that already composite over a full-window ambient
  /// stage (e.g. Now Playing): each live backdrop blur retains a GPU
  /// surface sampling everything behind it, which is the dominant RSS
  /// cost on Windows ANGLE.
  final bool blur;

  const KatGlass({
    super.key,
    required this.child,
    this.borderRadius = const BorderRadius.all(Radius.circular(KatRadius.menu)),
    this.padding,
    this.blur = true,
  });

  @override
  Widget build(BuildContext context) {
    final dark = katIsDark(context);
    final reduceT = MediaQuery.maybeOf(context)?.highContrast ?? false;
    final blurOn = blur && KatHazeScope.blurEnabled(context) && !reduceT;
    // Haze fill rides the surface ladder, so AMOLED collapses it with the
    // rest of the chrome (it used to be a fixed #121212 / #F4F4F4, which
    // sat *above* the AMOLED base instead of lifting from it).
    final base = dark ? katSurface(context) : katBackground(context);
    // "Extra translucency" thins the fill and widens the filter; only
    // meaningful while blurring — without a backdrop there is nothing to
    // let through, so the opaque fallback keeps its density.
    final liquid = KatHazeScope.of(context).liquidGlass;
    final fill = base.withValues(alpha: blurOn ? (liquid ? 0.32 : 0.46) : 0.78);
    final edge = (dark ? Colors.white : Colors.black).withValues(
      alpha: dark ? 0.16 : 0.10,
    );

    final content = Container(
      padding: padding,
      decoration: BoxDecoration(
        color: fill,
        borderRadius: borderRadius,
        border: Border.all(color: edge),
      ),
      child: child,
    );

    if (!blurOn) return content;
    if (liquid && !reduceT) {
      final radius = borderRadius.topLeft.x;
      final tint = base.withValues(alpha: dark ? 0.28 : 0.46);
      return RepaintBoundary(
        child: glass.GlassContainer(
          useOwnLayer: true,
          quality: glass.GlassQuality.standard,
          shape: glass.LiquidRoundedSuperellipse(borderRadius: radius),
          settings: glass.LiquidGlassSettings(
            thickness: 16,
            blur: 7,
            glassColor: tint,
            saturation: 1.2,
            lightIntensity: dark ? 0.48 : 0.62,
            chromaticAberration: 0.004,
          ),
          child: Container(
            padding: padding,
            decoration: BoxDecoration(
              borderRadius: borderRadius,
              border: Border.all(color: edge),
            ),
            child: child,
          ),
        ),
      );
    }
    final sigma =
        18 *
        KatHazeScope.blurScale(context) *
        KatHazeScope.liquidBlurScale(context);
    return RepaintBoundary(
      child: ClipRRect(
        borderRadius: borderRadius,
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: sigma, sigmaY: sigma),
          child: content,
        ),
      ),
    );
  }
}
