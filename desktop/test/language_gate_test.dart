import 'package:flutter_test/flutter_test.dart';
import 'package:katmusic_desktop/core/matching/match_vocab.dart';

void main() {
  test('languages are read from words and scripts', () {
    expect(MatchVocab.languagesOf('Ayudha Pooja (Telugu)'), {'telugu'});
    expect(MatchVocab.languagesOf('तुम ही हो'), {'hindi'});
    expect(MatchVocab.languagesOf('Plain Title'), isEmpty);
  });
}
