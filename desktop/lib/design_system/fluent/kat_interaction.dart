import 'package:fluent_ui/fluent_ui.dart';

import '../../../ui/theme/tokens.dart';

/// Interaction — hover / pressed / focus / cursor contract.
///
/// hover 7% wash over 110ms · pressed 12% · focus 2px ring ·
/// clickable targets use click cursor · tooltip 400ms wait.
///
/// The washes delegate to the canonical [katHoverWash] / [katPressedWash]
/// (theme-aware: white on dark chrome, black on pearl) so this bridge and
/// every hand-written hover fill can never drift apart.
class LwInteraction {
  LwInteraction._();

  static const double hoverAlpha = KatState.hoverAlpha; // 0.07
  static const double pressedAlpha = KatState.pressedAlpha; // 0.12
  static const double focusRing = KatState.focusRing; // 2
  static const Duration tooltipDelay = KatState.tooltipDelay; // 400ms

  static Color hoverWash(BuildContext context) => katHoverWash(context);

  static Color pressedWash(BuildContext context) => katPressedWash(context);
}
