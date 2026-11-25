import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:katmusic_desktop/core/storage/prefs.dart';
import 'package:katmusic_desktop/features/lyrics/lyrics_studio_styles.dart';

const _words = <StudioLyricWord>[
  StudioLyricWord(text: 'We', startMs: 1000, endMs: 1300),
  StudioLyricWord(text: 'were', startMs: 1300, endMs: 1700),
  StudioLyricWord(text: 'dancing', startMs: 1700, endMs: 2400),
  StudioLyricWord(text: 'in', startMs: 2400, endMs: 2600),
  StudioLyricWord(text: 'the', startMs: 2600, endMs: 2800),
  StudioLyricWord(text: 'light', startMs: 2800, endMs: 3600),
];

void main() {
  test('every ported style is available in the animation picker', () {
    for (final id in studioLyricsStyleIds) {
      expect(
        Prefs.lyricsAnimationOptions.containsKey(id),
        isTrue,
        reason: '$id is missing from Prefs.lyricsAnimationOptions',
      );
    }
  });

  for (final id in studioLyricsStyleIds) {
    testWidgets('renders $id in a narrow lyrics rail', (tester) async {
      final position = ValueNotifier<int>(2000);
      addTearDown(position.dispose);
      await tester.binding.setSurfaceSize(const Size(900, 700));
      addTearDown(() => tester.binding.setSurfaceSize(null));

      await tester.pumpWidget(
        Directionality(
          textDirection: TextDirection.ltr,
          child: Align(
            alignment: Alignment.topLeft,
            child: SizedBox(
              width: 360,
              height: 420,
              child: buildStudioLyricsLine(
                style: id,
                lineText: 'We were dancing in the light',
                words: _words,
                positionMs: 2000,
                positionListenable: position,
                accent: const Color(0xFF3D91FF),
                primary: const Color(0xFFF6F4EF),
                secondary: const Color(0x88F6F4EF),
                fontSize: 30,
                isRtl: false,
                reduceMotion: true,
              ),
            ),
          ),
        ),
      );

      await tester.pump(const Duration(milliseconds: 700));
      expect(tester.takeException(), isNull, reason: id);

      final texts = tester.widgetList<Text>(find.byType(Text));
      expect(texts, isNotEmpty, reason: '$id rendered no lyric text');
    });
  }
}
