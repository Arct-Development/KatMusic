/// Cross-device share links.
///
/// A shared track has one canonical page. The page opens the native app
/// through `katmusic://track?...` on Android, Windows, macOS and Linux, and
/// falls back to YouTube Music when the app is not installed. Keeping the
/// query keys in one place means the website and every share surface agree.
const katShareSite = 'katmusic.pages.dev';
const katSharePath = '/share.html';
final _videoIdPattern = RegExp(r'^[A-Za-z0-9_-]{6,24}$');

Map<String, String> _trackParams({
  required String videoId,
  required String title,
  required String artist,
  String album = '',
}) => {
  'v': videoId.trim(),
  if (title.trim().isNotEmpty) 'title': title.trim(),
  if (artist.trim().isNotEmpty) 'artist': artist.trim(),
  if (album.trim().isNotEmpty) 'album': album.trim(),
  // Omit 'art' to keep share links concise, clean and short.
  // Both the native app and web landing page derive artwork directly from videoId.
};

/// Web share page for a track. Null when the track has no stable video id:
/// local-only files cannot be opened on another device.
String? katTrackShareUrl({
  required String videoId,
  required String title,
  required String artist,
  String album = '',
  String artworkUrl = '',
}) {
  if (!_videoIdPattern.hasMatch(videoId.trim())) return null;
  return Uri.https(
    katShareSite,
    katSharePath,
    _trackParams(
      videoId: videoId,
      title: title,
      artist: artist,
      album: album,
    ),
  ).toString();
}

/// Native handoff for the same track.
String? katTrackDeepLink({
  required String videoId,
  required String title,
  required String artist,
  String album = '',
  String artworkUrl = '',
}) {
  if (!_videoIdPattern.hasMatch(videoId.trim())) return null;
  return Uri(
    scheme: 'katmusic',
    host: 'track',
    queryParameters: _trackParams(
      videoId: videoId,
      title: title,
      artist: artist,
      album: album,
    ),
  ).toString();
}
