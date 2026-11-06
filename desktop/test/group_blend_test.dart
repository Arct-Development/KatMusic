import 'package:flutter_test/flutter_test.dart';
import 'package:katmusic_desktop/features/blend/group_blend.dart';

BlendProfile profile(
  String name, {
  Map<String, int> artists = const {},
  List<BlendTrack> tracks = const [],
}) => BlendProfile(
  name: name,
  artists: artists,
  tracks: {for (final track in tracks) track.key: track},
);

void main() {
  test('taste codes round-trip names, artists and tracks', () {
    final original = profile(
      'Ayesha',
      artists: const {'Prateek Kuhad': 9, 'The Local Train': 4},
      tracks: const [
        BlendTrack(title: 'Cold/Mess', artist: 'Prateek Kuhad', videoId: 'v1'),
      ],
    );
    final decoded = BlendProfile.decode(original.code);
    expect(decoded, isNotNull);
    expect(decoded!.name, 'Ayesha');
    expect(decoded.artists['Prateek Kuhad'], 9);
    expect(decoded.tracks.values.single.title, 'Cold/Mess');
    expect(decoded.tracks.values.single.videoId, 'v1');
  });

  test('invalid or empty codes are rejected', () {
    expect(BlendProfile.decode(''), isNull);
    expect(BlendProfile.decode('KMB1.not-base64'), isNull);
  });

  test('shared artists raise the match and appear in the result', () {
    final me = profile(
      'Me',
      artists: const {'Prateek Kuhad': 10, 'The Local Train': 6},
      tracks: const [BlendTrack(title: 'Cold/Mess', artist: 'Prateek Kuhad')],
    );
    final friend = profile(
      'Friend',
      artists: const {'Prateek Kuhad': 8, 'Anuv Jain': 5},
      tracks: const [BlendTrack(title: 'Cold/Mess', artist: 'Prateek Kuhad')],
    );
    final result = computeBlend(me, [friend]);
    expect(result.percent, greaterThan(0));
    expect(result.sharedArtists, contains('Prateek Kuhad'));
    expect(result.recommendations, isEmpty);
  });

  test('recommendations exclude tracks already in my profile', () {
    final me = profile(
      'Me',
      artists: const {'Prateek Kuhad': 5},
      tracks: const [BlendTrack(title: 'Cold/Mess', artist: 'Prateek Kuhad')],
    );
    final friend = profile(
      'Friend',
      artists: const {'Prateek Kuhad': 5, 'Anuv Jain': 4},
      tracks: const [
        BlendTrack(title: 'Cold/Mess', artist: 'Prateek Kuhad'),
        BlendTrack(title: 'Husn', artist: 'Anuv Jain', videoId: 'v2'),
      ],
    );
    final result = computeBlend(me, [friend]);
    expect(result.recommendations.map((t) => t.title), ['Husn']);
    expect(result.recommendations.single.videoId, 'v2');
  });

  test('no friends means no match and no recommendations', () {
    final result = computeBlend(
      profile('Me', artists: const {'Anuv Jain': 3}),
      const [],
    );
    expect(result.percent, 0);
    expect(result.recommendations, isEmpty);
  });
}
