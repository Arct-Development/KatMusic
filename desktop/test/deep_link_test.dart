import 'package:flutter_test/flutter_test.dart';
import 'package:katmusic_desktop/app/deep_link.dart';

void main() {
  test('reads the room code from a jam link', () {
    expect(jamCodeFromLink('katmusic://jam?code=abc123'), 'ABC123');
    expect(routeForLink('katmusic://jam?code=ABC123'), '/jam?code=ABC123');
    expect(jamCodeFromLink('KATMUSIC://JAM?code=xy9z'), 'XY9Z');
  });

  test('rejects anything else', () {
    expect(jamCodeFromLink('katmusic://jam'), isNull);
    expect(jamCodeFromLink('katmusic://jam?code='), isNull);
    expect(jamCodeFromLink('katmusic://jam?code=../../x'), isNull);
    expect(jamCodeFromLink('katmusic://other?code=ABC123'), isNull);
    expect(jamCodeFromLink('https://example.com/jam?code=ABC123'), isNull);
    expect(routeForLink('not a link'), isNull);
  });
}
