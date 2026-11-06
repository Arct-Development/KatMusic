import 'package:fluent_ui/fluent_ui.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../features/innertube/yt_phone_pairing.dart';
import '../components/buttons.dart';

/// Desktop receiver UI for the Android YouTube session handoff.
class YtPhonePairingDialog extends StatefulWidget {
  final YtPhonePairing pairing;

  const YtPhonePairingDialog({super.key, required this.pairing});

  @override
  State<YtPhonePairingDialog> createState() => _YtPhonePairingDialogState();
}

class _YtPhonePairingDialogState extends State<YtPhonePairingDialog> {
  @override
  void initState() {
    super.initState();
    widget.pairing.result.then((transfer) {
      if (!mounted) return;
      Navigator.of(context).pop(transfer);
    });
  }

  @override
  Widget build(BuildContext context) {
    return ContentDialog(
      constraints: const BoxConstraints(maxWidth: 540),
      title: const Text('Sign in from Android'),
      content: ValueListenableBuilder<YtPhonePairingSnapshot>(
        valueListenable: widget.pairing.snapshot,
        builder: (context, snapshot, _) {
          final approval =
              snapshot.phase == YtPhonePairingPhase.awaitingApproval;
          final terminal =
              snapshot.phase == YtPhonePairingPhase.expired ||
              snapshot.phase == YtPhonePairingPhase.rejected;
          return Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (!terminal) ...[
                Container(
                  width: 280,
                  height: 280,
                  padding: const EdgeInsets.all(12),
                  color: const Color(0xFFFFFFFF),
                  child: QrImageView(
                    data: widget.pairing.qrData,
                    version: QrVersions.auto,
                    errorCorrectionLevel: QrErrorCorrectLevel.M,
                    backgroundColor: const Color(0xFFFFFFFF),
                    eyeStyle: const QrEyeStyle(
                      eyeShape: QrEyeShape.square,
                      color: Color(0xFF111111),
                    ),
                    dataModuleStyle: const QrDataModuleStyle(
                      dataModuleShape: QrDataModuleShape.square,
                      color: Color(0xFF111111),
                    ),
                  ),
                ),
                const SizedBox(height: 14),
                Text(
                  approval
                      ? '${snapshot.deviceName ?? 'Android phone'} is requesting access.'
                      : 'Scan this code in KatMusic on your Android phone.',
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 5),
                Text(
                  approval
                      ? 'Only approve a phone you recognise. The QR expires in five minutes.'
                      : 'Both devices must be connected to the same Wi-Fi network.',
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 12),
                ),
                const SizedBox(height: 8),
                Text(
                  'Computer address: ${widget.pairing.hostLabel}',
                  textAlign: TextAlign.center,
                  style: const TextStyle(fontSize: 11),
                ),
              ] else
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 32),
                  child: Column(
                    children: [
                      Icon(
                        snapshot.phase == YtPhonePairingPhase.expired
                            ? FluentIcons.clock
                            : FluentIcons.blocked,
                        size: 34,
                      ),
                      const SizedBox(height: 12),
                      Text(
                        snapshot.phase == YtPhonePairingPhase.expired
                            ? 'This QR code has expired.'
                            : 'The phone request was declined.',
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 6),
                      const Text(
                        'Close this window and create a new code to try again.',
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 12),
                      ),
                    ],
                  ),
                ),
            ],
          );
        },
      ),
      actions: [
        ValueListenableBuilder<YtPhonePairingSnapshot>(
          valueListenable: widget.pairing.snapshot,
          builder: (context, snapshot, _) {
            if (snapshot.phase == YtPhonePairingPhase.awaitingApproval) {
              return Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  KatButton(
                    onPressed: () {
                      widget.pairing.reject();
                    },
                    child: const Text('Decline'),
                  ),
                  const SizedBox(width: 8),
                  KatFilledButton(
                    onPressed: widget.pairing.approve,
                    child: const Text('Allow phone'),
                  ),
                ],
              );
            }
            return KatButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Cancel'),
            );
          },
        ),
      ],
    );
  }
}
