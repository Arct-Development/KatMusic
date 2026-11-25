import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:katmusic_desktop/core/audio/stream_models.dart';
import 'package:katmusic_desktop/core/storage/prefs.dart';
import 'package:katmusic_desktop/features/lyrics/lyrics_models.dart';
import 'package:katmusic_desktop/features/lyrics/lyrics_studio_styles.dart';
import 'package:katmusic_desktop/features/lyrics/studio_lyrics_stage.dart';
import 'package:shared_preferences/shared_preferences.dart';

const _track = PlayableTrack(title: 'Dancing', artist: 'Kat');

LyricsResult _lyrics() => const LyricsResult(
  isSynced: true,
  isWordSynced: true,
  lines: [
    LyricLine(
      timeMs: 0,
      durationMs: 1200,
      text: 'We were dancing',
      syllables: [
        LyricSyllable(timeMs: 0, durationMs: 400, text: 'We '),
        LyricSyllable(timeMs: 400, durationMs: 400, text: 'were '),
        LyricSyllable(timeMs: 800, durationMs: 400, text: 'dancing'),
      ],
    ),
    LyricLine(
      timeMs: 1200,
      durationMs: 1400,
      text: 'in the light',
      syllables: [
        LyricSyllable(timeMs: 1200, durationMs: 400, text: 'in '),
        LyricSyllable(timeMs: 1600, durationMs: 400, text: 'the '),
        LyricSyllable(timeMs: 2000, durationMs: 600, text: 'light'),
      ],
    ),
    LyricLine(
      timeMs: 2800,
      durationMs: 1200,
      text: 'all night long',
      syllables: [
        LyricSyllable(timeMs: 2800, durationMs: 400, text: 'all '),
        LyricSyllable(timeMs: 3200, durationMs: 400, text: 'night '),
        LyricSyllable(timeMs: 3600, durationMs: 400, text: 'long'),
      ],
    ),
  ],
);

void main() {
  testWidgets('full-screen stage renders every ported style', (tester) async {
    SharedPreferences.setMockInitialValues({});
    final prefs = Prefs(await SharedPreferences.getInstance());
    final position = ValueNotifier<int>(2000);
    final active = ValueNotifier<int>(1);
    addTearDown(position.dispose);
    addTearDown(active.dispose);
    await tester.binding.setSurfaceSize(const Size(1200, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));

    for (final style in studioLyricsStyleIds) {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [prefsProvider.overrideWithValue(prefs)],
          child: MaterialApp(
            home: StudioLyricsStage(
              track: _track,
              result: _lyrics(),
              positionListenable: position,
              activeLineListenable: active,
              initialStyle: style,
              showBackdrop: false,
            ),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 500));
      expect(tester.takeException(), isNull, reason: style);
      expect(find.byType(StudioLyricsStage), findsOneWidget, reason: style);
    }
  });
}
