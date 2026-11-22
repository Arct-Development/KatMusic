import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter/services.dart';

import '../theme/tokens.dart';
import '../theme/kat_icons.dart';

/// Fluent-native buttons with KatMusic density.
///
/// - 38px hit areas, 16-20px glyphs, optical alignment.
/// - Native hover / pressed / disabled / focus via Fluent ButtonStyle.
/// - Smooth spring / cubic micro-interactions.
/// - Tooltips on every icon-only control.
class KatIconButton extends StatefulWidget {
  final String tooltip;
  final Widget icon;
  final VoidCallback? onPressed;
  final bool selected;
  final bool filled;
  const KatIconButton({
    super.key,
    required this.tooltip,
    required this.icon,
    required this.onPressed,
    this.selected = false,
    this.filled = false,
  });

  @override
  State<KatIconButton> createState() => _KatIconButtonState();
}

class _KatIconButtonState extends State<KatIconButton> {
  bool _hover = false;
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final accent = katAccent(context);
    final dark = katIsDark(context);
    final disabled = widget.onPressed == null;

    final style = ButtonStyle(
      padding: const WidgetStatePropertyAll(EdgeInsets.all(9)),
      backgroundColor: WidgetStateProperty.resolveWith((states) {
        if (states.isDisabled) return Colors.transparent;
        if (widget.filled && widget.selected) {
          return accent.withValues(alpha: 0.22);
        }
        if (states.isPressed) {
          return katPressedWash(context);
        }
        if (states.isHovered) {
          return katHoverWash(context);
        }
        if (widget.selected) return accent.withValues(alpha: 0.14);
        return Colors.transparent;
      }),
      foregroundColor: WidgetStateProperty.resolveWith((states) {
        if (states.isDisabled) {
          return (dark ? KatColors.textTertiary : KatColors.lightTextTertiary)
              .withValues(alpha: 0.5);
        }
        if (widget.selected) return accent;
        return dark ? KatColors.textSecondary : KatColors.lightTextSecondary;
      }),
      shape: const WidgetStatePropertyAll(
        RoundedRectangleBorder(
          borderRadius: BorderRadius.all(Radius.circular(KatRadius.controls)),
        ),
      ),
    );

    final button = MouseRegion(
      cursor: disabled ? SystemMouseCursors.basic : SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hover = true),
      onExit: (_) => setState(() {
        _hover = false;
        _pressed = false;
      }),
      child: Listener(
        onPointerDown: disabled ? null : (_) => setState(() => _pressed = true),
        onPointerUp: disabled ? null : (_) => setState(() => _pressed = false),
        onPointerCancel: disabled
            ? null
            : (_) => setState(() => _pressed = false),
        child: AnimatedScale(
          scale: disabled ? 1.0 : (_pressed ? 0.93 : (_hover ? 1.05 : 1.0)),
          duration: katMotion(context, KatMotion.fast),
          curve: Curves.easeOutCubic,
          child: SizedBox(
            width: KatDensity.hitArea,
            height: KatDensity.hitArea,
            child: IconButton(
              icon: widget.icon,
              onPressed: widget.onPressed,
              style: style,
            ),
          ),
        ),
      ),
    );

    return KatTooltip(message: widget.tooltip, child: button);
  }
}

/// Fluent's own push buttons resolve `MouseCursor.defer`, so they paint the
/// plain arrow — every Kat control sets the click cursor by hand, which made
/// "hover shows the pointer" true of some buttons and not others. These three
/// wrappers are that single rule for the plugin's buttons: use them instead of
/// a raw `Button` / `FilledButton` / `HyperlinkButton`. A disabled control
/// keeps the arrow, since there is nothing to click.
class KatButton extends StatelessWidget {
  final Widget child;
  final VoidCallback? onPressed;
  final VoidCallback? onLongPress;
  final ButtonStyle? style;
  final bool autofocus;
  final FocusNode? focusNode;

  const KatButton({
    super.key,
    required this.child,
    required this.onPressed,
    this.onLongPress,
    this.style,
    this.autofocus = false,
    this.focusNode,
  });

  @override
  Widget build(BuildContext context) => MouseRegion(
    cursor: onPressed == null
        ? SystemMouseCursors.basic
        : SystemMouseCursors.click,
    child: Button(
      onPressed: onPressed,
      onLongPress: onLongPress,
      style: style,
      autofocus: autofocus,
      focusNode: focusNode,
      child: child,
    ),
  );
}

/// See [KatButton] — the accent-filled variant.
class KatFilledButton extends StatelessWidget {
  final Widget child;
  final VoidCallback? onPressed;
  final VoidCallback? onLongPress;
  final ButtonStyle? style;
  final bool autofocus;
  final FocusNode? focusNode;

  const KatFilledButton({
    super.key,
    required this.child,
    required this.onPressed,
    this.onLongPress,
    this.style,
    this.autofocus = false,
    this.focusNode,
  });

  @override
  Widget build(BuildContext context) => MouseRegion(
    cursor: onPressed == null
        ? SystemMouseCursors.basic
        : SystemMouseCursors.click,
    child: FilledButton(
      onPressed: onPressed,
      onLongPress: onLongPress,
      style: style,
      autofocus: autofocus,
      focusNode: focusNode,
      child: child,
    ),
  );
}

/// See [KatButton] — the borderless text-link variant.
class KatLink extends StatelessWidget {
  final Widget child;
  final VoidCallback? onPressed;
  final VoidCallback? onLongPress;
  final ButtonStyle? style;
  final bool autofocus;
  final FocusNode? focusNode;

  const KatLink({
    super.key,
    required this.child,
    required this.onPressed,
    this.onLongPress,
    this.style,
    this.autofocus = false,
    this.focusNode,
  });

  @override
  Widget build(BuildContext context) => MouseRegion(
    cursor: onPressed == null
        ? SystemMouseCursors.basic
        : SystemMouseCursors.click,
    child: HyperlinkButton(
      onPressed: onPressed,
      onLongPress: onLongPress,
      style: style,
      autofocus: autofocus,
      focusNode: focusNode,
      child: child,
    ),
  );
}

/// Pointer feedback for a tappable surface that has no choreography of its
/// own: the pointer cursor and the house press dip.
///
/// Discover's rails each hand-rolled this before; this is the shared version.
/// It wraps whatever it is given, so a card keeps its own shape and a row dips
/// by a smaller amount — pass [pressedScale].
///
/// The dip is what makes the pointer-anchored route transition read as one
/// motion: the page grows out of a card that has already reacted under the
/// finger. A surface with its own hover choreography (a morphing artist cover,
/// a hero pill) should keep it rather than wrap here.
class KatPressable extends StatefulWidget {
  const KatPressable({
    super.key,
    required this.child,
    this.onTap,
    this.pressedScale = 0.97,
    this.cursor = SystemMouseCursors.click,
    this.behavior = HitTestBehavior.opaque,
    this.semanticLabel,
  });

  final Widget child;
  final VoidCallback? onTap;

  /// What a screen reader calls this control. When null the text inside the
  /// card (title, artist…) is merged into one button label instead.
  final String? semanticLabel;

  /// Scale held while the pointer is down (the house dip is 0.97; a list row
  /// reads better at ~0.985 because it scales from its centre).
  final double pressedScale;

  final MouseCursor cursor;

  /// Opaque by default (the [KatMediaCard] convention) so the gaps inside a
  /// card — between its cover and its title, say — still take the tap.
  final HitTestBehavior behavior;

  @override
  State<KatPressable> createState() => _KatPressableState();
}

class _KatPressableState extends State<KatPressable> {
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final live = widget.onTap != null;
    // Reduce motion: the dip is the only feedback, so keep the button
    // still rather than snapping it 3% smaller and back.
    final dip = widget.pressedScale;
    final reduce = katReduceMotion(context);
    final pressable = _pressable(context, live, dip, reduce);
    if (!live) return pressable;
    // A card is one control to a screen reader and the keyboard: one named
    // button (its own text merged into the label) that Tab reaches and
    // Enter / Space activates — not a pile of loose, unreachable text.
    return MergeSemantics(
      child: Semantics(
        button: true,
        label: widget.semanticLabel,
        onTap: widget.onTap,
        child: FocusableActionDetector(
          onShowFocusHighlight: (v) => setState(() => _ring = v),
          actions: {
            ActivateIntent: CallbackAction<ActivateIntent>(
              onInvoke: (_) {
                widget.onTap?.call();
                return null;
              },
            ),
          },
          child: DecoratedBox(
            position: DecorationPosition.foreground,
            decoration: BoxDecoration(
              borderRadius: KatRadius.controlsRadius,
              border: _ring
                  ? Border.all(color: katAccent(context), width: 2)
                  : null,
            ),
            child: pressable,
          ),
        ),
      ),
    );
  }

  bool _ring = false;

  Widget _pressable(BuildContext context, bool live, double dip, bool reduce) {
    return MouseRegion(
      cursor: live ? widget.cursor : MouseCursor.defer,
      onExit: (_) {
        // A pointer that left mid-press will never deliver the up event here.
        if (_pressed) setState(() => _pressed = false);
      },
      child: GestureDetector(
        behavior: widget.behavior,
        onTapDown: live ? (_) => setState(() => _pressed = true) : null,
        onTapUp: live ? (_) => setState(() => _pressed = false) : null,
        onTapCancel: live ? () => setState(() => _pressed = false) : null,
        onTap: widget.onTap,
        child: AnimatedScale(
          scale: _pressed && !reduce ? dip : 1.0,
          duration: katMotion(context, KatMotion.fast),
          curve: KatMotion.standard,
          child: widget.child,
        ),
      ),
    );
  }
}

/// The hero's primary action: accent pill, contrast glyph, pressed dip.
///
/// Accent fill with a contrast glyph (`onAccentFor` — the accent can be
/// off-white, so a hardcoded white or black glyph would vanish on it), hover
/// 93%, pressed 82%. Home and Discover lead with the same pill.
class KatPlayPill extends StatefulWidget {
  final VoidCallback onTap;
  final String label;
  const KatPlayPill({super.key, required this.onTap, this.label = 'Play'});

  @override
  State<KatPlayPill> createState() => _KatPlayPillState();
}

class _KatPlayPillState extends State<KatPlayPill> {
  bool _hover = false;
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final dark = katIsDark(context);
    final accent = katAccent(context);
    final onAccent = onAccentFor(accent);
    final bg = _pressed
        ? accent.withValues(alpha: 0.82)
        : _hover
        ? accent.withValues(alpha: 0.93)
        : accent;
    return MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hover = true),
      onExit: (_) => setState(() {
        _hover = false;
        _pressed = false;
      }),
      child: GestureDetector(
        onTapDown: (_) => setState(() => _pressed = true),
        onTapUp: (_) => setState(() => _pressed = false),
        onTapCancel: () => setState(() => _pressed = false),
        onTap: widget.onTap,
        child: AnimatedScale(
          scale: _pressed ? 0.96 : 1.0,
          duration: katMotion(context, KatMotion.fast),
          curve: Curves.easeOutCubic,
          child: AnimatedContainer(
            duration: katMotion(context, KatMotion.fast),
            padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 10),
            decoration: BoxDecoration(
              color: bg,
              borderRadius: BorderRadius.circular(999),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: dark ? 0.45 : 0.18),
                  blurRadius: 8,
                  offset: const Offset(0, 2),
                ),
              ],
            ),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Icon(KatIcons.play, size: 14, color: onAccent),
                const SizedBox(width: 8),
                Text(
                  widget.label,
                  style: KatType.label.copyWith(color: onAccent),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Large circular transport button (play/pause only).
class KatPlayButton extends StatefulWidget {
  final bool playing;
  final bool buffering;
  final bool large;
  final VoidCallback? onPressed;
  final String tooltip;
  const KatPlayButton({
    super.key,
    required this.playing,
    this.buffering = false,
    this.large = false,
    required this.onPressed,
    this.tooltip = 'Play',
  });

  @override
  State<KatPlayButton> createState() => _KatPlayButtonState();
}

class _KatPlayButtonState extends State<KatPlayButton> {
  bool _hover = false;
  bool _pressed = false;

  @override
  Widget build(BuildContext context) {
    final accent = katAccent(context);
    final dark = katIsDark(context);
    // Black glyph on light (off-white) accents, white on dark ones.
    final onAccent = onAccentFor(accent);
    final size = widget.large ? 48.0 : 40.0;
    final glyphSize = widget.large ? 22.0 : 19.0;
    final disabled = widget.onPressed == null;

    final button = MouseRegion(
      cursor: disabled ? SystemMouseCursors.basic : SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hover = true),
      onExit: (_) => setState(() {
        _hover = false;
        _pressed = false;
      }),
      child: Listener(
        onPointerDown: disabled ? null : (_) => setState(() => _pressed = true),
        onPointerUp: disabled ? null : (_) => setState(() => _pressed = false),
        onPointerCancel: disabled
            ? null
            : (_) => setState(() => _pressed = false),
        child: AnimatedScale(
          scale: disabled ? 1.0 : (_pressed ? 0.92 : (_hover ? 1.06 : 1.0)),
          duration: katMotion(context, KatMotion.fast),
          curve: Curves.easeOutCubic,
          child: SizedBox(
            width: size,
            height: size,
            child: FilledButton(
              onPressed: widget.onPressed,
              style: ButtonStyle(
                padding: const WidgetStatePropertyAll(EdgeInsets.zero),
                backgroundColor: WidgetStateProperty.resolveWith((states) {
                  if (states.isDisabled) {
                    return (dark ? Colors.white : Colors.black).withValues(
                      alpha: 0.08,
                    );
                  }
                  if (states.isPressed) return accent.withValues(alpha: 0.85);
                  if (states.isHovered) return accent.withValues(alpha: 0.92);
                  return accent;
                }),
                foregroundColor: WidgetStatePropertyAll(onAccent),
                shape: const WidgetStatePropertyAll(
                  RoundedRectangleBorder(
                    borderRadius: BorderRadius.all(Radius.circular(999)),
                  ),
                ),
              ),
              child: Center(
                child: widget.buffering
                    ? SizedBox(
                        width: glyphSize,
                        height: glyphSize,
                        child: ProgressRing(
                          strokeWidth: 2.5,
                          activeColor: onAccent,
                          backgroundColor: onAccent.withValues(alpha: 0.25),
                        ),
                      )
                    : AnimatedSwitcher(
                        duration: katMotion(
                          context,
                          const Duration(milliseconds: 180),
                        ),
                        transitionBuilder: (child, animation) =>
                            ScaleTransition(
                              scale: CurvedAnimation(
                                parent: animation,
                                curve: Curves.easeOutBack,
                              ),
                              child: FadeTransition(
                                opacity: animation,
                                child: child,
                              ),
                            ),
                        child: Icon(
                          widget.playing ? KatIcons.pause : KatIcons.play,
                          key: ValueKey(widget.playing),
                          size: glyphSize,
                          color: onAccent,
                        ),
                      ),
              ),
            ),
          ),
        ),
      ),
    );

    return KatTooltip(
      message: widget.playing ? 'Pause' : 'Play',
      child: button,
    );
  }
}

class KatPrimaryButton extends StatelessWidget {
  final String label;
  final IconData? icon;
  final VoidCallback? onPressed;
  const KatPrimaryButton({
    super.key,
    required this.label,
    this.icon,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return KatFilledButton(
      onPressed: onPressed,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[Icon(icon, size: 15), const SizedBox(width: 6)],
          Text(label, style: KatType.label),
        ],
      ),
    );
  }
}

class KatGhostButton extends StatelessWidget {
  final String label;
  final IconData? icon;
  final VoidCallback? onPressed;
  const KatGhostButton({
    super.key,
    required this.label,
    this.icon,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    return KatButton(
      onPressed: onPressed,
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[Icon(icon, size: 15), const SizedBox(width: 6)],
          Text(label, style: KatType.label),
        ],
      ),
    );
  }
}

/// Small quality / meta chip (codec, bitrate, source).
class KatChip extends StatelessWidget {
  final String label;
  final bool highlight;
  const KatChip({super.key, required this.label, this.highlight = false});

  @override
  Widget build(BuildContext context) {
    final dark = katIsDark(context);
    final accent = katAccent(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
      decoration: BoxDecoration(
        color: highlight
            ? accent.withValues(alpha: 0.16)
            : (dark ? Colors.white : Colors.black).withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(
          color: highlight
              ? accent.withValues(alpha: 0.45)
              : (dark ? katOutlineSoft(context) : katOutline(context)),
        ),
      ),
      child: Text(
        label,
        style: KatType.overline.copyWith(
          fontSize: 9.5,
          color: highlight
              ? accent
              : (dark ? KatColors.textSecondary : KatColors.lightTextSecondary),
        ),
      ),
    );
  }
}

/// Canonical tooltip wrapper: 400ms hover wait per [KatState.tooltipDelay].
///
/// Use for every icon-only control instead of raw [Tooltip].
///
/// The tooltip machinery (`Tooltip > RawTooltip > MouseRegion > Semantics
/// > Focus` + global pointer listeners + timers) mounts one frame late:
/// on first build only the bare child is emitted, then a post-frame
/// callback arms the real tooltip. ~20 of these mount on pages like Now
/// Playing, and eager mounting showed up in open-frame traces. Deferral
/// is invisible by construction — no tooltip can display before the
/// 400ms hover dwell, while arming lands ~16ms after mount.
class KatTooltip extends StatefulWidget {
  final String message;
  final Widget child;
  const KatTooltip({super.key, required this.message, required this.child});

  @override
  State<KatTooltip> createState() => _LWTooltipState();
}

class _LWTooltipState extends State<KatTooltip> {
  bool _armed = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) setState(() => _armed = true);
    });
  }

  @override
  Widget build(BuildContext context) {
    if (!_armed) return widget.child;
    return Tooltip(
      message: widget.message,
      style: const TooltipThemeData(waitDuration: KatState.tooltipDelay),
      child: widget.child,
    );
  }
}

/// Accessible wrapper for custom (GestureDetector-based) controls.
///
/// Gives screen readers a named button with enabled / selected state and an
/// activate action, makes the control reachable by Tab, and activates on
/// Enter. A focus ring is painted over the child only for keyboard focus, so
/// mouse users see nothing different.
class KatA11yButton extends StatefulWidget {
  final String label;
  final VoidCallback? onTap;
  final bool selected;
  final Widget child;
  const KatA11yButton({
    super.key,
    required this.label,
    required this.onTap,
    required this.child,
    this.selected = false,
  });

  @override
  State<KatA11yButton> createState() => _KatA11yButtonState();
}

class _KatA11yButtonState extends State<KatA11yButton> {
  bool _ring = false;

  @override
  Widget build(BuildContext context) {
    final enabled = widget.onTap != null;
    return Semantics(
      button: true,
      enabled: enabled,
      selected: widget.selected,
      label: widget.label,
      onTap: widget.onTap,
      excludeSemantics: true,
      child: FocusableActionDetector(
        enabled: enabled,
        onShowFocusHighlight: (v) => setState(() => _ring = v),
        actions: {
          ActivateIntent: CallbackAction<ActivateIntent>(
            onInvoke: (_) {
              widget.onTap?.call();
              return null;
            },
          ),
        },
        child: DecoratedBox(
          position: DecorationPosition.foreground,
          decoration: BoxDecoration(
            borderRadius: KatRadius.controlsRadius,
            border: _ring
                ? Border.all(color: katAccent(context), width: 2)
                : null,
          ),
          child: widget.child,
        ),
      ),
    );
  }
}

/// Canonical progress slider: 3px track, white fill, 10px thumb visible
/// on hover/drag only. Expands to available width.
class KatProgressSlider extends StatelessWidget {
  final double value;
  final String semanticLabel;
  final ValueChanged<double>? onChanged;
  final ValueChanged<double>? onChangeEnd;
  const KatProgressSlider({
    super.key,
    required this.value,
    this.semanticLabel = 'Seek',
    this.onChanged,
    this.onChangeEnd,
  });

  @override
  Widget build(BuildContext context) {
    return _SliderSemantics(
      label: semanticLabel,
      value: value,
      onChanged: onChanged,
      onChangeEnd: onChangeEnd,
      child: _KatSliderBase(
        value: value,
        onChanged: onChanged,
        onChangeEnd: onChangeEnd,
      ),
    );
  }
}

/// Canonical volume slider: 3px track, configurable width (defaults to 84px),
/// 8px knob at rest growing to 10px on hover/drag/focus.
class KatVolumeSlider extends StatelessWidget {
  final double value;
  final String semanticLabel;
  final ValueChanged<double>? onChanged;
  final ValueChanged<double>? onChangeEnd;
  final double width;
  const KatVolumeSlider({
    super.key,
    required this.value,
    this.semanticLabel = 'Volume',
    this.onChanged,
    this.onChangeEnd,
    this.width = 84,
  });

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      child: _SliderSemantics(
        label: semanticLabel,
        value: value,
        onChanged: onChanged,
        onChangeEnd: onChangeEnd,
        child: _KatSliderBase(
          value: value,
          onChanged: onChanged,
          onChangeEnd: onChangeEnd,
        ),
      ),
    );
  }
}

/// Screen-reader view of a 0..1 slider: label, percent value, and
/// increase / decrease actions in 5% steps (what NVDA's arrow keys invoke).
class _SliderSemantics extends StatelessWidget {
  final String label;
  final double value;
  final ValueChanged<double>? onChanged;
  final ValueChanged<double>? onChangeEnd;
  final Widget child;
  const _SliderSemantics({
    required this.label,
    required this.value,
    required this.onChanged,
    required this.onChangeEnd,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    void step(double d) {
      final next = (value + d).clamp(0.0, 1.0).toDouble();
      onChanged?.call(next);
      onChangeEnd?.call(next);
    }

    final enabled = onChanged != null;
    return Semantics(
      slider: true,
      enabled: enabled,
      label: label,
      value: '${(value * 100).round()} percent',
      increasedValue:
          '${((value + 0.05).clamp(0.0, 1.0) * 100).round()} percent',
      decreasedValue:
          '${((value - 0.05).clamp(0.0, 1.0) * 100).round()} percent',
      onIncrease: enabled ? () => step(0.05) : null,
      onDecrease: enabled ? () => step(-0.05) : null,
      excludeSemantics: true,
      child: child,
    );
  }
}

class _KatSliderBase extends StatefulWidget {
  final double value;
  final ValueChanged<double>? onChanged;
  final ValueChanged<double>? onChangeEnd;
  const _KatSliderBase({required this.value, this.onChanged, this.onChangeEnd});

  @override
  State<_KatSliderBase> createState() => _KatSliderBaseState();
}

class _KatSliderBaseState extends State<_KatSliderBase> {
  bool _hover = false;
  bool _dragging = false;
  final FocusNode _focus = FocusNode();

  @override
  void dispose() {
    _focus.dispose();
    super.dispose();
  }

  double _fractionFor(double dx, double width) {
    if (width <= 0) return widget.value.clamp(0.0, 1.0);
    return (dx / width).clamp(0.0, 1.0).toDouble();
  }

  void _step(double delta) {
    final next = (widget.value + delta).clamp(0.0, 1.0).toDouble();
    widget.onChanged?.call(next);
    widget.onChangeEnd?.call(next);
  }

  @override
  Widget build(BuildContext context) {
    final dark = katIsDark(context);
    final base = dark ? Colors.white : Colors.black;
    final fill = dark ? KatColors.textPrimary : KatColors.lightTextPrimary;
    final active = _hover || _dragging || _focus.hasFocus;
    // A level you cannot see is not a control: at 100% the fill is the whole
    // track, so a knob that only appeared on hover left a plain hairline
    // between two glyphs. Any non-zero value keeps its knob; hover grows it.
    final thumbSize = active ? 10.0 : 8.0;
    final showThumb = active || widget.value > 0.001;
    final slider = MouseRegion(
      cursor: SystemMouseCursors.click,
      onEnter: (_) => setState(() => _hover = true),
      onExit: (_) => setState(() => _hover = false),
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTapDown: (details) {
          _focus.requestFocus();
          final box = context.findRenderObject() as RenderBox?;
          if (box == null || widget.onChanged == null) return;
          final local = box.globalToLocal(details.globalPosition);
          widget.onChanged!(_fractionFor(local.dx, box.size.width));
        },
        onHorizontalDragStart: (_) => setState(() => _dragging = true),
        onHorizontalDragUpdate: (details) {
          final box = context.findRenderObject() as RenderBox?;
          if (box == null || widget.onChanged == null) return;
          final local = box.globalToLocal(details.globalPosition);
          widget.onChanged!(_fractionFor(local.dx, box.size.width));
        },
        onHorizontalDragEnd: (_) {
          setState(() => _dragging = false);
          widget.onChangeEnd?.call(widget.value.clamp(0.0, 1.0));
        },
        child: Focus(
          focusNode: _focus,
          onFocusChange: (_) => setState(() {}),
          onKeyEvent: (node, event) {
            if (event is! KeyDownEvent) return KeyEventResult.ignored;
            if (event.logicalKey == LogicalKeyboardKey.arrowRight ||
                event.logicalKey == LogicalKeyboardKey.arrowUp) {
              _step(0.05);
              return KeyEventResult.handled;
            }
            if (event.logicalKey == LogicalKeyboardKey.arrowLeft ||
                event.logicalKey == LogicalKeyboardKey.arrowDown) {
              _step(-0.05);
              return KeyEventResult.handled;
            }
            if (event.logicalKey == LogicalKeyboardKey.home) {
              _step(-widget.value);
              return KeyEventResult.handled;
            }
            if (event.logicalKey == LogicalKeyboardKey.end) {
              _step(1.0 - widget.value);
              return KeyEventResult.handled;
            }
            return KeyEventResult.ignored;
          },
          child: SizedBox(
            height: 16,
            child: Center(
              child: LayoutBuilder(
                builder: (context, constraints) {
                  final fraction = widget.value.clamp(0.0, 1.0).toDouble();
                  final width = constraints.maxWidth;
                  final fillWidth = width * fraction;
                  final maxThumbLeft = (width - thumbSize)
                      .clamp(0.0, double.infinity)
                      .toDouble();
                  final thumbLeft = (fillWidth - thumbSize / 2)
                      .clamp(0.0, maxThumbLeft)
                      .toDouble();
                  return SizedBox(
                    height: 10,
                    child: Stack(
                      alignment: Alignment.centerLeft,
                      children: [
                        Container(
                          height: 3,
                          width: width,
                          decoration: BoxDecoration(
                            color: base.withValues(alpha: 0.14),
                            borderRadius: BorderRadius.circular(KatRadius.tiny),
                          ),
                        ),
                        Container(
                          height: 3,
                          width: fillWidth,
                          decoration: BoxDecoration(
                            color: fill,
                            borderRadius: BorderRadius.circular(KatRadius.tiny),
                          ),
                        ),
                        if (showThumb)
                          Positioned(
                            left: thumbLeft,
                            child: AnimatedContainer(
                              duration: katMotion(context, KatMotion.fast),
                              curve: Curves.easeOutCubic,
                              width: thumbSize,
                              height: thumbSize,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: fill,
                              ),
                            ),
                          ),
                      ],
                    ),
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
    return slider;
  }
}
