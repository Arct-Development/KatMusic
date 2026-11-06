import 'package:fluent_ui/fluent_ui.dart';

import '../theme/tokens.dart';
import 'buttons.dart'
    show KatTooltip, KatButton, KatGhostButton, KatPrimaryButton;
import 'menus.dart';
import '../theme/kat_icons.dart';

/// WinUI-inspired command surface for music collections.
///
/// Renders primary actions (Play / Shuffle / Download) as Fluent
/// buttons plus an overflow [DropDownButton] for secondary commands,
/// matching the spec: "icons, compact labels where useful, overflow
/// menu for secondary commands". Never giant cards — a single compact
/// row that wraps on narrow widths.
///
/// Usage:
/// ```dart
/// KatCommandBar(
///   onPlay: ...,
///   onShuffle: ...,
///   onDownload: ...,
///   overflowItems: [...],
/// )
/// ```
class KatCommandBar extends StatelessWidget {
  final VoidCallback? onPlay;
  final VoidCallback? onShuffle;
  final VoidCallback? onDownload;
  final String playLabel;
  final List<KatMenuEntry> overflowItems;
  final List<Widget> trailing;
  const KatCommandBar({
    super.key,
    this.onPlay,
    this.onShuffle,
    this.onDownload,
    this.playLabel = 'Play',
    this.overflowItems = const [],
    this.trailing = const [],
  });

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: KatSpacing.x4,
      runSpacing: KatSpacing.x4,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        if (onPlay != null)
          KatPrimaryButton(
            label: playLabel,
            icon: FluentIcons.play,
            onPressed: onPlay,
          ),
        if (onShuffle != null)
          KatGhostButton(
            label: 'Shuffle',
            icon: KatIcons.shuffle,
            onPressed: onShuffle,
          ),
        if (onDownload != null)
          KatGhostButton(
            label: 'Download',
            icon: FluentIcons.download,
            onPressed: onDownload,
          ),
        if (overflowItems.isNotEmpty)
          KatTooltip(
            message: 'More actions',
            child: SizedBox(
              width: KatDensity.hitArea,
              height: 32,
              child: KatMenuButton(
                entries: overflowItems,
                buttonBuilder: (context, onOpen) => KatButton(
                  onPressed: onOpen,
                  child: const Icon(FluentIcons.more, size: 14),
                ),
              ),
            ),
          ),
        ...trailing,
      ],
    );
  }
}

/// Compact library toolbar: sort + filter + view toggle slot.
///
/// Used by Library / Playlists / Albums / Artists headers so sort,
/// filter and view controls share one Fluent pattern instead of ad-hoc
/// DropDownButtons scattered per page.
class KatLibraryToolbar extends StatelessWidget {
  final Widget? sortSlot;
  final Widget? viewSlot;
  final Widget? filterSlot;
  const KatLibraryToolbar({
    super.key,
    this.sortSlot,
    this.viewSlot,
    this.filterSlot,
  });

  @override
  Widget build(BuildContext context) {
    if (sortSlot == null && viewSlot == null && filterSlot == null) {
      return const SizedBox.shrink();
    }
    return Row(
      children: [
        ?filterSlot,
        const Spacer(),
        if (sortSlot != null) ...[
          sortSlot!,
          if (viewSlot != null) const SizedBox(width: 8),
        ],
        ?viewSlot,
      ],
    );
  }
}
