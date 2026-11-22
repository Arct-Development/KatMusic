import 'package:fluent_ui/fluent_ui.dart';

import '../../../ui/theme/tokens.dart';

/// Radii — monotonic soft ladder (see [KatRadius] for the contract).
///
/// tiny 6 · controls 10 · artwork 12 · menu 14 · floating 20 · hero 24 ·
/// structural 0. Pills (999) only for chips + circular transport.
class LwRadius {
  LwRadius._();

  static const double tiny = KatRadius.tiny;
  static const double controls = KatRadius.controls;
  static const double artwork = KatRadius.artwork;
  static const double menu = KatRadius.menu;
  static const double floating = KatRadius.floating;
  static const double hero = KatRadius.hero;
  static const double structural = KatRadius.structural;

  static BorderRadius get tinyRadius => KatRadius.tinyRadius;
  static BorderRadius get controlsRadius => KatRadius.controlsRadius;
  static BorderRadius get artworkRadius => KatRadius.artworkRadius;
  static BorderRadius get menuRadius => KatRadius.menuRadius;
  static BorderRadius get floatingRadius => KatRadius.floatingRadius;
  static BorderRadius get heroRadius => KatRadius.heroRadius;
}
