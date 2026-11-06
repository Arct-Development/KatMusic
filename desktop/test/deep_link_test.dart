import 'package:flutter_test/flutter_test.dart';
import 'package:katmusic_desktop/app/deep_link.dart';

void main() {
  test('reads the room code from a jam link', () {
    expect(jamCodeFromLink('katmusic://jam?code=abc123'), 'ABC123');
    expect(routeForLink('katmusic://jam?code=ABC123'), '/jam?code=ABC123');
    expect(jamCodeFromLink('KATMUSIC://JAM?code=xy9z'), 'XY9Z');
  });

  test('reads a shared track into the landing route', () {
    final route = routeForLink(
      'katmusic://track?v=abc123XYZ_&title=Cold%2FMess&artist=Prateek%20Kuhad',
    );
    expect(route, contains('/shared-track?'));
    expect(route, contains('v=abc123XYZ_'));
    expect(route, contains('title=Cold%2FMess'));
    expect(
      trackFromLink('katmusic://track?v=abc123XYZ_')?.videoId,
      'abc123XYZ_',
    );
  });

  test('rejects anything else', () {
    expect(jamCodeFromLink('katmusic://jam'), isNull);
    expect(jamCodeFromLink('katmusic://jam?code='), isNull);
    expect(jamCodeFromLink('katmusic://jam?code=../../x'), isNull);
    expect(jamCodeFromLink('katmusic://other?code=ABC123'), isNull);
    expect(jamCodeFromLink('https://example.com/jam?code=ABC123'), isNull);
    expect(routeForLink('not a link'), isNull);
    expect(trackFromLink('katmusic://track?v='), isNull);
    expect(trackFromLink('katmusic://track?v=../x'), isNull);
  });
}
