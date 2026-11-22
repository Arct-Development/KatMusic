import 'dart:io';

import 'package:fluent_ui/fluent_ui.dart';

import '../../ui/theme/tokens.dart';
import 'output_path_status.dart';

/// Compact WinUI 3 stream-path summary for Settings.
class KatStreamPathPanel extends StatelessWidget {
  final OutputPathStatus path;
  const KatStreamPathPanel({super.key, required this.path});

  @override
  Widget build(BuildContext context) {
    final secondary = katTextSecondary(context);
    Widget row(String key, String value) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(
          children: [
            SizedBox(
              width: 72,
              child: Text(
                key,
                style: KatType.overline.copyWith(color: secondary),
              ),
            ),
            Expanded(
              child: Text(
                value,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: KatType.meta,
              ),
            ),
          ],
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        row('Source', path.sourceLabel),
        row('Output', path.outputLabel),
        row(
          Platform.isWindows ? 'WASAPI' : 'Mode',
          path.exclusiveActive ? 'Exclusive' : 'Shared',
        ),
        row('Mixer', path.exclusiveActive ? 'Bypassed' : 'In path'),
        row('DSP', path.dspActive || path.peqActive ? 'Active' : 'Bypassed'),
        row(
          'Volume',
          path.hardwareVolume
              ? 'DAC hardware'
              : (path.softwareVolume ? 'Software' : 'Unity'),
        ),
        const SizedBox(height: 6),
        Text(
          path.bitPerfect ? 'Bit-Perfect' : path.reason.label,
          style: KatType.label.copyWith(
            color: path.bitPerfect ? KatColors.success : KatColors.warn,
          ),
        ),
        if (path.error != null) ...[
          const SizedBox(height: 4),
          Text(
            path.error!,
            style: KatType.meta.copyWith(color: KatColors.danger),
          ),
        ],
      ],
    );
  }
}
