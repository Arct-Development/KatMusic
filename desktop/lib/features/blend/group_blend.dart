import 'dart:convert';
import 'dart:math' as math;

import '../../core/audio/artist_parser.dart' show splitArtistCredits;
import '../library/playlists.dart';

/// A portable taste profile for Group Blend.
///
/// The Android app keeps the same idea server-side; desktop can share a
/// compact code instead, so a blend works without an account or a server.
/// The code contains artist names and track identity only — no timestamps,
/// account details or listening history.
class BlendTrack {
  final String title;
  final String artist;
  final String videoId;
  final String artworkUrl;
  final int durationSeconds;
  final int weight;

  const BlendTrack({
    required this.title,
    required this.artist,
    this.videoId = '',
    this.artworkUrl = '',
    this.durationSeconds = 0,
    this.weight = 1,
  });

  String get key =>
      '${title.trim().toLowerCase()}|${artist.trim().toLowerCase()}';

  BlendTrack copyWith({int? weight}) => BlendTrack(
    title: title,
    artist: artist,
    videoId: videoId,
    artworkUrl: artworkUrl,
    durationSeconds: durationSeconds,
    weight: weight ?? this.weight,
  );

  Map<String, Object?> toJson() => {
    't': title,
    'a': artist,
    'v': videoId,
    'art': artworkUrl,
    'd': durationSeconds,
    'w': weight,
  };

  static BlendTrack? fromJson(Object? raw) {
    if (raw is! Map) return null;
    final title = (raw['t'] as String?)?.trim() ?? '';
    final artist = (raw['a'] as String?)?.trim() ?? '';
    if (title.isEmpty) return null;
    return BlendTrack(
      title: title,
      artist: artist,
      videoId: (raw['v'] as String?) ?? '',
      artworkUrl: (raw['art'] as String?) ?? '',
      durationSeconds: (raw['d'] as num?)?.toInt() ?? 0,
      weight: ((raw['w'] as num?)?.toInt() ?? 1).clamp(1, 99),
    );
  }
}

class BlendProfile {
  final String name;
  final Map<String, int> artists;
  final Map<String, BlendTrack> tracks;

  const BlendProfile({
    required this.name,
    required this.artists,
    required this.tracks,
  });

  /// A short, URL-safe code that another KatMusic desktop can decode.
  String get code {
    final raw = jsonEncode({
      'v': 1,
      'n': name,
      'a': artists,
      't': [for (final track in tracks.values) track.toJson()],
    });
    final encoded = base64Url.encode(utf8.encode(raw)).replaceAll('=', '');
    return 'KMB1.$encoded';
  }

  static BlendProfile? decode(String input) {
    var raw = input.trim();
    if (raw.startsWith('KMB1.')) raw = raw.substring(5);
    if (raw.isEmpty || raw.length > 200000) return null;
    try {
      final padding = (4 - raw.length % 4) % 4;
      final padded = raw + ('=' * padding);
      final decoded = jsonDecode(utf8.decode(base64Url.decode(padded)));
      if (decoded is! Map) return null;
      final artists = <String, int>{};
      final rawArtists = decoded['a'];
      if (rawArtists is Map) {
        for (final entry in rawArtists.entries) {
          final key = '${entry.key}'.trim();
          final weight = (entry.value as num?)?.toInt() ?? 0;
          if (key.isNotEmpty && weight > 0) {
            artists[key] = weight.clamp(1, 9999).toInt();
          }
        }
      }
      final tracks = <String, BlendTrack>{};
      final rawTracks = decoded['t'];
      if (rawTracks is List) {
        for (final item in rawTracks) {
          final track = BlendTrack.fromJson(item);
          if (track != null) tracks[track.key] = track;
        }
      }
      final name = (decoded['n'] as String?)?.trim() ?? '';
      if (artists.isEmpty && tracks.isEmpty) return null;
      return BlendProfile(
        name: name.isEmpty ? 'Friend' : name,
        artists: artists,
        tracks: tracks,
      );
    } catch (_) {
      return null;
    }
  }

  int affinity(String artist) {
    final wanted = artist.trim().toLowerCase();
    for (final entry in artists.entries) {
      if (entry.key.trim().toLowerCase() == wanted) return entry.value;
    }
    return 0;
  }

  bool hasTrack(BlendTrack track) => tracks.containsKey(track.key);
}

/// Builds a profile from the data desktop already has locally: liked songs
/// (with playback metadata) plus recent plays (the broader taste signal).
BlendProfile buildLocalBlendProfile({
  required String name,
  required List<StoredTrack> liked,
  required List<({String title, String artist, int atMillis})> recent,
}) {
  final artistWeights = <String, int>{};
  final trackWeights = <String, BlendTrack>{};

  void addArtist(String raw, int weight) {
    for (final artist in splitArtistCredits(raw)) {
      final clean = artist.trim();
      if (clean.isEmpty) continue;
      artistWeights[clean] = (artistWeights[clean] ?? 0) + weight;
    }
  }

  void addTrack(BlendTrack track, int weight) {
    final existing = trackWeights[track.key];
    trackWeights[track.key] = existing == null
        ? track.copyWith(weight: weight)
        : existing.copyWith(weight: existing.weight + weight);
  }

  for (final track in liked) {
    if (track.name.trim().isEmpty) continue;
    addArtist(track.artist, 3);
    addTrack(
      BlendTrack(
        title: track.name,
        artist: track.artist,
        videoId: track.videoId,
        artworkUrl: track.artworkUrl,
        durationSeconds: track.durationSeconds,
        weight: 3,
      ),
      3,
    );
  }
  for (final play in recent) {
    if (play.title.trim().isEmpty) continue;
    addArtist(play.artist, 1);
    addTrack(BlendTrack(title: play.title, artist: play.artist), 1);
  }

  final sortedArtists = artistWeights.entries.toList()
    ..sort((a, b) => b.value.compareTo(a.value));
  final sortedTracks = trackWeights.values.toList()
    ..sort((a, b) => b.weight.compareTo(a.weight));

  return BlendProfile(
    name: name.trim().isEmpty ? 'You' : name.trim(),
    artists: {
      for (final entry in sortedArtists.take(80)) entry.key: entry.value,
    },
    tracks: {for (final track in sortedTracks.take(80)) track.key: track},
  );
}

class BlendResult {
  final int percent;
  final List<String> sharedArtists;
  final List<BlendTrack> recommendations;

  const BlendResult({
    required this.percent,
    required this.sharedArtists,
    required this.recommendations,
  });

  String get label {
    if (percent >= 85) return 'Musical twins';
    if (percent >= 65) return 'Strong overlap';
    if (percent >= 40) return 'Some common ground';
    if (percent > 0) return 'Different lanes';
    return 'No overlap yet';
  }
}

BlendResult computeBlend(BlendProfile me, List<BlendProfile> others) {
  if (others.isEmpty) {
    return const BlendResult(
      percent: 0,
      sharedArtists: [],
      recommendations: [],
    );
  }

  var dot = 0.0;
  var meNorm = 0.0;
  var othersNorm = 0.0;
  final shared = <String, int>{};
  for (final entry in me.artists.entries) {
    final mine = math.sqrt(entry.value.toDouble());
    meNorm += mine * mine;
    var best = 0;
    for (final other in others) {
      final theirWeight = other.affinity(entry.key);
      if (theirWeight > 0) best = math.max(best, theirWeight);
    }
    if (best > 0) {
      dot += mine * math.sqrt(best.toDouble());
      shared[entry.key] = best;
    }
  }
  for (final other in others) {
    for (final weight in other.artists.values) {
      final value = math.sqrt(weight.toDouble());
      othersNorm += value * value;
    }
  }

  final otherKeys = <String>{};
  for (final other in others) {
    otherKeys.addAll(other.tracks.keys);
  }
  final sharedKeys = otherKeys.where(me.tracks.containsKey).toSet();
  final unionKeys = {...me.tracks.keys, ...otherKeys};
  final trackOverlap = unionKeys.isEmpty
      ? 0.0
      : sharedKeys.length / unionKeys.length;
  final artistSimilarity = (meNorm == 0 || othersNorm == 0)
      ? 0.0
      : dot / (math.sqrt(meNorm) * math.sqrt(othersNorm));
  final percent = ((artistSimilarity * 0.7 + trackOverlap * 0.3) * 100)
      .round()
      .clamp(0, 100)
      .toInt();

  final sharedArtists = shared.entries.toList()
    ..sort((a, b) => b.value.compareTo(a.value));

  final recommendations = <String, BlendTrack>{};
  for (final other in others) {
    for (final track in other.tracks.values) {
      if (me.hasTrack(track)) continue;
      final score =
          other.affinity(track.artist) +
          me.affinity(track.artist) +
          track.weight;
      final existing = recommendations[track.key];
      if (existing == null || score > existing.weight) {
        recommendations[track.key] = track.copyWith(weight: score);
      }
    }
  }
  final sortedRecommendations = recommendations.values.toList()
    ..sort((a, b) => b.weight.compareTo(a.weight));

  return BlendResult(
    percent: percent,
    sharedArtists: [for (final entry in sharedArtists.take(12)) entry.key],
    recommendations: sortedRecommendations.take(30).toList(),
  );
}
