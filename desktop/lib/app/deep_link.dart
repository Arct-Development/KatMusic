import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';

/// Room code carried by a `katmusic://jam?code=ABC123` link, upper-cased,
/// or null when [link] is anything else.
String? jamCodeFromLink(String link) {
  final uri = Uri.tryParse(link.trim());
  if (uri == null || uri.scheme.toLowerCase() != 'katmusic') return null;
  if (uri.host.toLowerCase() != 'jam') return null;
  final code = (uri.queryParameters['code'] ?? '').trim().toUpperCase();
  return RegExp(r'^[A-Z0-9]{3,12}$').hasMatch(code) ? code : null;
}

/// Track carried by a `katmusic://track?v=…` link.
class SharedTrackLink {
  final String videoId;
  final String title;
  final String artist;
  final String album;
  final String artworkUrl;

  const SharedTrackLink({
    required this.videoId,
    required this.title,
    required this.artist,
    required this.album,
    required this.artworkUrl,
  });
}

SharedTrackLink? trackFromLink(String link) {
  final uri = Uri.tryParse(link.trim());
  if (uri == null || uri.scheme.toLowerCase() != 'katmusic') return null;
  if (uri.host.toLowerCase() != 'track') return null;
  final videoId = (uri.queryParameters['v'] ?? '').trim();
  if (!RegExp(r'^[A-Za-z0-9_-]{6,24}$').hasMatch(videoId)) return null;
  return SharedTrackLink(
    videoId: videoId,
    title: (uri.queryParameters['title'] ?? '').trim(),
    artist: (uri.queryParameters['artist'] ?? '').trim(),
    album: (uri.queryParameters['album'] ?? '').trim(),
    artworkUrl: (uri.queryParameters['art'] ?? '').trim(),
  );
}

/// In-app route a `katmusic://` link opens, or null for unknown links.
String? routeForLink(String link) {
  final code = jamCodeFromLink(link);
  if (code != null) return '/jam?code=$code';
  final track = trackFromLink(link);
  if (track == null) return null;
  return '/shared-track?${Uri(queryParameters: {'v': track.videoId, if (track.title.isNotEmpty) 'title': track.title, if (track.artist.isNotEmpty) 'artist': track.artist, if (track.album.isNotEmpty) 'album': track.album, if (track.artworkUrl.isNotEmpty) 'art': track.artworkUrl}).query}';
}

/// `katmusic://` links: the one in the launch arguments, plus links that a
/// second launch forwards to the running window (Windows, via the runner's
/// `katmusic/deeplink` channel). Links are never logged.
class DeepLinks {
  DeepLinks._();

  static const _channel = MethodChannel('katmusic/deeplink');
  static final _controller = StreamController<String>.broadcast();
  static String? _initial;

  static Stream<String> get stream => _controller.stream;

  /// The link this process was launched with, once.
  static String? takeInitial() {
    final link = _initial;
    _initial = null;
    return link;
  }

  static void start(List<String> args) {
    for (final arg in args) {
      if (routeForLink(arg) != null) {
        _initial = arg;
        break;
      }
    }
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'link' && call.arguments is String) {
        _controller.add(call.arguments as String);
      }
    });
    if (Platform.isWindows) _registerWindowsScheme();
  }

  /// Points `katmusic://` at this executable (per-user, no admin rights).
  /// Best-effort: a failure only means links open nothing.
  static Future<void> _registerWindowsScheme() async {
    try {
      final exe = Platform.resolvedExecutable;
      const root = r'HKCU\Software\Classes\katmusic';
      const commandKey = root + r'\shell\open\command';
      final command = '"$exe" "%1"';
      final current = await Process.run('reg', ['query', commandKey, '/ve']);
      if (current.exitCode == 0 && '${current.stdout}'.contains(command)) {
        return;
      }
      Future<void> add(String key, List<String> rest) =>
          Process.run('reg', ['add', key, ...rest, '/f']);
      await add(root, ['/ve', '/d', 'URL:KatMusic']);
      await add(root, ['/v', 'URL Protocol', '/d', '']);
      await add(commandKey, ['/ve', '/d', command]);
    } catch (_) {}
  }
}
