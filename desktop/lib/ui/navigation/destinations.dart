import 'package:fluent_ui/fluent_ui.dart';

import '../theme/kat_icons.dart';

/// Rebuilt navigation — music first, panels secondary.
///
/// Layout:
/// - Top: Home / Discover / Stats / Search (daily music use)
/// - Middle: Liked / Albums / Artists / Playlists (your collections)
/// - Then: Downloads
/// - Bottom (pinned): Friends / Settings
///
/// History stays routable but is not on the rail.
/// Mix Lab is a listen destination, so [/mixes] matches the loop above
/// before the fallback at the bottom of [katActivePath].
class KatDestination {
  final String path;
  final String label;
  final IconData icon;
  const KatDestination(this.path, this.label, this.icon);
}

const katListenDestinations = [
  KatDestination('/home', 'Listen', KatIcons.home),
  KatDestination('/discover', 'Discover', KatIcons.discover),
  KatDestination('/stats', 'Soundprint', KatIcons.stats),
  KatDestination('/search', 'Explore', KatIcons.search),
  KatDestination('/mixes', 'Blend Lab', KatIcons.mixes),
  KatDestination('/group-blend', 'Group Blend', KatIcons.friends),
];

const katCollectionDestinations = [
  KatDestination('/liked', 'Favorites', KatIcons.liked),
  KatDestination('/playlists', 'Collections', KatIcons.playlists),
  KatDestination('/podcasts', 'Shows', KatIcons.podcasts),
];

const katOfflineDestinations = [
  KatDestination('/downloads', 'Offline', KatIcons.downloads),
];

const katSystemDestinations = [
  KatDestination('/friends', 'Listening Circle', KatIcons.friends),
  KatDestination('/settings', 'Settings', KatIcons.settings),
];

List<KatDestination> get allWaveDestinations => [
  ...katListenDestinations,
  ...katCollectionDestinations,
  ...katOfflineDestinations,
  ...katSystemDestinations,
];

String katRoutePath(String location) => location.split('?').first;

String katActivePath(String location) {
  final path = katRoutePath(location);
  for (final d in allWaveDestinations) {
    if (path == d.path || path.startsWith('${d.path}/')) {
      return d.path;
    }
  }
  if (path.startsWith('/album')) return '/albums';
  if (path.startsWith('/artist')) return '/artists';
  if (path.startsWith('/now')) return '/home';
  if (path.startsWith('/history')) return '/stats';
  if (path.startsWith('/stats')) return '/stats';
  if (path == '/home-layout' || path.startsWith('/home-layout/')) {
    return '/home';
  }
  if (path.startsWith('/mixes')) return '/home';
  if (path.startsWith('/profile')) return '/friends';
  return '/home';
}
