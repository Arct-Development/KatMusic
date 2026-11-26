
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// One named family of lyric animations. Settings and the Now Playing menu
/// show these as separate groups so the Essentials, Studio and Aesthetic
/// styles don't read as one long collapsed list.
class LyricsAnimationFamily {
  final String label;
  final String subtitle;
  final List<String> styles;

  const LyricsAnimationFamily(this.label, this.subtitle, this.styles);
}

/// Settings + session persistence.
///
/// Key names mirror KatMusic `SettingsPreferences` /
/// `SessionPreferences` (`lw_*`) so behaviour stays comparable.
class Prefs {
  final SharedPreferences _sp;
  Prefs(this._sp);

  static Future<Prefs> load() async =>
      Prefs(await SharedPreferences.getInstance());

  // -- Last.fm BYOK (bring your own keys) ---------------------------------
  // No bundled API keys ship with the app: the user enters their own
  // Last.fm API key + shared secret (from last.fm/api/account/create),
  // stored here in SharedPreferences. All Last.fm features stay
  // disabled until both are set.
  String get lastFmApiKey => _sp.getString('lw_lastfm_api_key') ?? '';
  String get lastFmApiSecret => _sp.getString('lw_lastfm_api_secret') ?? '';
  bool get isLastFmConfigured =>
      lastFmApiKey.isNotEmpty && lastFmApiSecret.isNotEmpty;

  Future<void> saveLastFmKeys({
    required String apiKey,
    required String apiSecret,
  }) async {
    await _sp.setString('lw_lastfm_api_key', apiKey);
    await _sp.setString('lw_lastfm_api_secret', apiSecret);
  }

  Future<void> clearLastFmKeys() async {
    await _sp.remove('lw_lastfm_api_key');
    await _sp.remove('lw_lastfm_api_secret');
  }

  // -- Guest mode (keyless entry) -------------------------------------------
  // Set by the welcome Skip button: the user enters the shell without
  // Last.fm keys or session. Everything except Last.fm features works;
  // reconnect happens in Settings only. Sticky across restarts; cleared
  // by full sign-out and by saving a real session.
  bool get isGuest => _sp.getBool('lw_guest_mode') ?? false;
  Future<void> setGuestMode(bool v) =>
      _sp.setBool('lw_guest_mode', v);

  // -- Last.fm session (web auth under the user's own API key) ------------
  // Authentication is compulsory: there is no guest or anonymous mode.
  // A session is valid only when both the username and the session key
  // are stored. Session keys are bound to the API key that minted them,
  // so saving different API keys signs the session out (see
  // AuthRepository.saveCustomKeys).
  String get sessionKey => _sp.getString('lw_sessionkey') ?? '';
  String get username => _sp.getString('lw_username') ?? '';
  bool get isAuthenticated => username.isNotEmpty && sessionKey.isNotEmpty;

  Future<void> saveSession({
    required String sessionKey,
    required String username,
  }) async {
    await _sp.setString('lw_sessionkey', sessionKey);
    await _sp.setString('lw_username', username);
    // Drop any legacy guest flag from older builds.
    await _sp.remove('lw_guest_mode');
  }

  Future<void> signOut() async {
    await _sp.remove('lw_sessionkey');
    await _sp.remove('lw_username');
    await _sp.remove('lw_guest_mode');
  }

  // -- Audio quality (YouTube tiers) ---------------------------------------
  /// -1 = YouTube Opus (best available), 4 = data saver (smallest
  /// YouTube audio).
  static const allowedQualities = [-1, 4];

  int get streamQuality => _clampQuality(
      _sp.getInt('lw_stream_quality') ??
          _sp.getInt('lw_lossless_quality') ??
          -1);
  int get downloadQuality =>
      _clampQuality(_sp.getInt('lw_download_quality') ?? -1);

  static int _clampQuality(int q) => allowedQualities.contains(q) ? q : -1;

  Future<void> setStreamQuality(int q) =>
      _sp.setInt('lw_stream_quality', _clampQuality(q));
  Future<void> setDownloadQuality(int q) =>
      _sp.setInt('lw_download_quality', _clampQuality(q));

  // -- Window behaviour ---------------------------------------------------
  /// Close button / Alt+F4 hides to the tray instead of quitting.
  /// Real quit stays in tray → Quit (ordered player teardown first).
  /// Default off (clicking X closes the application and stops audio).
  bool get closeToTray =>
      _sp.getBool('lw_close_to_tray') ?? false;
  Future<void> setCloseToTray(bool v) =>
      _sp.setBool('lw_close_to_tray', v);

  /// Floating miniplayer look: `'classic'` bar, `'compact'` strip,
  /// `'card'` artwork square, `'ambient'` wide blurred-artwork panel,
  /// `'lyrics'` the ambient panel plus the current synced lyric line
  /// (the default).
  static const miniPlayerStyles = [
    'classic',
    'compact',
    'card',
    'ambient',
    'lyrics',
  ];
  String get miniPlayerStyle {
    final raw = _sp.getString('lw_mini_style') ?? 'lyrics';
    return miniPlayerStyles.contains(raw) ? raw : 'lyrics';
  }

  Future<void> setMiniPlayerStyle(String style) => _sp.setString(
        'lw_mini_style',
        miniPlayerStyles.contains(style) ? style : 'lyrics',
      );

  // -- Playback behaviour -------------------------------------------------
  /// Keep playing similar tracks (endless radio) after a queue runs
  /// out. Default on — matches Spotify/YTM autoplay behaviour.
  bool get autoplaySimilar =>
      _sp.getBool('lw_autoplay_similar') ?? true;
  Future<void> setAutoplaySimilar(bool v) =>
      _sp.setBool('lw_autoplay_similar', v);

  bool get crossfadeEnabled => _sp.getBool('lw_crossfade_enabled') ?? false;
  int get crossfadeSeconds {
    final v = _sp.getInt('lw_crossfade_seconds') ?? 5;
    return v.clamp(3, 12);
  }

  Future<void> setCrossfade(bool enabled, [int? seconds]) async {
    await _sp.setBool('lw_crossfade_enabled', enabled);
    if (enabled) {
      await _sp.setBool('lw_automix_enabled', false);
    }
    if (seconds != null) {
      await _sp.setInt('lw_crossfade_seconds', seconds.clamp(3, 12));
    }
  }

  Future<void> setCrossfadeSeconds(int seconds) async {
    await _sp.setInt('lw_crossfade_seconds', seconds.clamp(3, 12));
  }

  bool get bitPerfect => _sp.getBool('lw_bit_perfect') ?? false;
  Future<void> setBitPerfect(bool v) => _sp.setBool('lw_bit_perfect', v);

  /// Empty = Windows default render endpoint.
  String get audioDeviceId => _sp.getString('lw_audio_device_id') ?? '';
  Future<void> setAudioDeviceId(String v) =>
      _sp.setString('lw_audio_device_id', v);

  /// Each playlist's last track sort, as `<sort>:<asc|desc>` (like the
  /// phone app), kept until the user picks another one.
  String playlistSort(int id) =>
      _sp.getString('lw_playlist_sort_$id') ?? 'default:asc';
  Future<void> setPlaylistSort(int id, String v) =>
      _sp.setString('lw_playlist_sort_$id', v);

  bool get wasapiExclusive => _sp.getBool('lw_wasapi_exclusive') ?? false;
  Future<void> setWasapiExclusive(bool v) =>
      _sp.setBool('lw_wasapi_exclusive', v);

  // -- Loudness (ReplayGain, not a file scan) ------------------------------
  /// `off`, `track`, or `album`.
  String get loudnessMode {
    final raw = (_sp.getString('lw_loudness_mode') ?? 'off').toLowerCase();
    if (raw == 'track' || raw == 'album') return raw;
    return 'off';
  }

  Future<void> setLoudnessMode(String mode) {
    final raw = mode.toLowerCase();
    final stored = (raw == 'track' || raw == 'album') ? raw : 'off';
    return _sp.setString('lw_loudness_mode', stored);
  }

  /// Headphone correction id. `off` or a built-in profile id.
  String get headphoneProfile =>
      _sp.getString('lw_headphone_profile') ?? 'off';

  Future<void> setHeadphoneProfile(String id) =>
      _sp.setString('lw_headphone_profile', id.trim());

  // -- Timed AutoMix (not beat-matched). Mutually exclusive with crossfade.
  bool get autoMixEnabled => _sp.getBool('lw_automix_enabled') ?? false;

  /// AutoMix blend length in bars: 8 or 16.
  int get autoMixBars => (_sp.getInt('lw_automix_bars') ?? 16) >= 16 ? 16 : 8;

  bool get autoMixSpectral => _sp.getBool('lw_automix_spectral') ?? true;

  Future<void> setAutoMixEnabled(bool enabled) async {
    await _sp.setBool('lw_automix_enabled', enabled);
    if (enabled) {
      await _sp.setBool('lw_crossfade_enabled', false);
    }
  }

  Future<void> setAutoMixBars(int bars) =>
      _sp.setInt('lw_automix_bars', bars >= 16 ? 16 : 8);

  Future<void> setAutoMixSpectral(bool enabled) =>
      _sp.setBool('lw_automix_spectral', enabled);

  bool get downloadLyrics => _sp.getBool('lw_download_lyrics') ?? true;
  Future<void> setDownloadLyrics(bool v) =>
      _sp.setBool('lw_download_lyrics', v);

  /// Custom download folder. Empty = default (`Music/KatMusic`, else
  /// app documents). Set via Settings → Downloads or the Downloads
  /// page; changeable at any time, applies to new downloads.
  String get downloadDir => _sp.getString('lw_download_dir') ?? '';
  Future<void> setDownloadDir(String v) =>
      _sp.setString('lw_download_dir', v.trim());
  Future<void> resetDownloadDir() =>
      _sp.remove('lw_download_dir');

  // -- Lyrics --------------------------------------------------------------
  bool get wordByWord => _sp.getBool('lw_word_by_word') ?? true;
  Future<void> setWordByWord(bool v) => _sp.setBool('lw_word_by_word', v);

  /// Primary lyrics provider id (`auto` default). Explicit picks get a
  /// head start; the rest of the chain stays as automatic fallback.
  String get lyricsProviderId =>
      _sp.getString('lw_lyrics_provider') ?? 'auto';
  Future<void> setLyricsProviderId(String v) =>
      _sp.setString('lw_lyrics_provider', v);

  static const lyricsAnimationOptions = <String, String>{
    'apple_fluid': 'Liquid Glass',
    'glow_pulse': 'Aurora Bloom',
    'karaoke_bounce': 'Pulse Bounce',
    'kinetic_pop': 'Hyper Pop',
    'neon_cyber': 'Neon Circuit',
    'italic_wave': 'Silk Tide',
    'glitch_snap': 'Broken Signal',
    'elastic_tilt': 'Stage Lean',
    'smooth_fade': 'Quiet Fade',
    'lyrical_studio': 'Studio Auto',
    'lyrical_studio_stack': 'Skyline Stack',
    'lyrical_studio_slide': 'Runway Sweep',
    'lyrical_studio_zoom': 'Warp In',
    'lyrical_studio_letters': 'Flip Letters',
    'lyrical_studio_scatter': 'Paper Collage',
    'lyrical_studio_flow': 'Steady Flow',
    'lyrical_studio_graph': 'Lyric Tree',
    'aesthetic_ship': 'Drift Wall',
    'aesthetic_fisheye': 'Fisheye Stack',
    'aesthetic_fisheye_visual': 'Fisheye Icons',
    'aesthetic_visual': 'Icon Spark',
    'design_branches': 'Branches',
    'design_web': 'Spider Web',
    'design_constellation': 'Constellation',
    'design_native_thread': 'Message Thread',
    'design_orbit': 'Orbit',
    'design_rain': 'Rainfall',
    'design_typewriter': 'Typewriter',
  };

  /// Animation families in picker order. Every id in
  /// [lyricsAnimationOptions] belongs to exactly one family.
  static const lyricsAnimationFamilies = <LyricsAnimationFamily>[
    LyricsAnimationFamily(
      'Essentials',
      'Clean, readable motion for everyday listening',
      [
        'apple_fluid',
        'glow_pulse',
        'karaoke_bounce',
        'kinetic_pop',
        'neon_cyber',
        'italic_wave',
        'glitch_snap',
        'elastic_tilt',
        'smooth_fade',
      ],
    ),
    LyricsAnimationFamily(
      'Kinetic Studio',
      'Bold type-in layouts ported from LyricalStudio',
      [
        'lyrical_studio',
        'lyrical_studio_stack',
        'lyrical_studio_slide',
        'lyrical_studio_zoom',
        'lyrical_studio_letters',
        'lyrical_studio_scatter',
        'lyrical_studio_flow',
        'lyrical_studio_graph',
      ],
    ),
    LyricsAnimationFamily(
      'Aesthetic 3D',
      'Depth and perspective ported from theaestheticslyrics',
      [
        'aesthetic_ship',
        'aesthetic_fisheye',
        'aesthetic_fisheye_visual',
        'aesthetic_visual',
      ],
    ),
    LyricsAnimationFamily(
      'Signature Designs',
      'Illustrated lines: vines, webs, constellations and messages',
      [
        'design_branches',
        'design_web',
        'design_constellation',
        'design_native_thread',
        'design_orbit',
        'design_rain',
        'design_typewriter',
      ],
    ),
  ];

  /// Album-lit glow around the full-screen Studio stage.
  static const studioGlowModes = <(String, String)>[
    ('off', 'Off'),
    ('pulse', 'Music Sync'),
    ('breath', 'Idle breathing'),
    ('steady', 'Steady'),
  ];

  String get studioGlowMode {
    final value = _sp.getString('lw_studio_glow_mode') ?? 'pulse';
    return studioGlowModes.any((mode) => mode.$1 == value) ? value : 'pulse';
  }

  Future<void> setStudioGlowMode(String value) => _sp.setString(
        'lw_studio_glow_mode',
        studioGlowModes.any((mode) => mode.$1 == value) ? value : 'pulse',
      );

  /// Edge band size as a fraction of the shorter screen side.
  double get studioGlowThickness =>
      (_sp.getDouble('lw_studio_glow_thickness') ?? 0.28).clamp(0.1, 0.5);

  Future<void> setStudioGlowThickness(double value) =>
      _sp.setDouble('lw_studio_glow_thickness', value.clamp(0.1, 0.5));

  double get studioGlowIntensity =>
      (_sp.getDouble('lw_studio_glow_intensity') ?? 0.55).clamp(0.15, 1.0);

  Future<void> setStudioGlowIntensity(double value) =>
      _sp.setDouble('lw_studio_glow_intensity', value.clamp(0.15, 1.0));

  static LyricsAnimationFamily lyricsAnimationFamilyOf(String id) =>
      lyricsAnimationFamilies.firstWhere(
        (family) => family.styles.contains(id),
        orElse: () => lyricsAnimationFamilies.first,
      );

  String get lyricsAnimation {
    final v = _sp.getString('lw_lyrics_animation') ?? 'apple_fluid';
    return lyricsAnimationOptions.containsKey(v) ? v : 'apple_fluid';
  }

  Future<void> setLyricsAnimation(String v) => _sp.setString(
        'lw_lyrics_animation',
        lyricsAnimationOptions.containsKey(v) ? v : 'apple_fluid',
      );

  /// The style's family label, for the Now Playing hint.
  String get lyricsAnimationFamilyLabel =>
      lyricsAnimationFamilyOf(lyricsAnimation).label;

  /// Multiplier on the lyrics type size. Snapped to the steps the
  /// lyrics view and Settings both offer.
  static const lyricsTextScales = <(double, String)>[
    (0.85, 'Small'),
    (1.0, 'Medium'),
    (1.2, 'Large'),
    (1.4, 'Extra large'),
    (1.6, 'Huge'),
  ];

  double get lyricsTextScale => _nearestDouble(
        _sp.getDouble('lw_lyrics_text_scale') ?? 1.0,
        [for (final step in lyricsTextScales) step.$1],
      );

  Future<void> setLyricsTextScale(double v) => _sp.setDouble(
        'lw_lyrics_text_scale',
        _nearestDouble(v, [for (final step in lyricsTextScales) step.$1]),
      );

  /// `off` or a short language code. Lyric sources in this app do not
  /// return a translation; the choice is stored for the lyrics view.
  static const lyricsTranslationOptions = <String, String>{
    'off': 'Off',
    'en': 'English',
    'es': 'Spanish',
    'fr': 'French',
    'de': 'German',
    'pt': 'Portuguese',
    'hi': 'Hindi',
    'ja': 'Japanese',
    'ko': 'Korean',
  };

  String get lyricsTranslationLang {
    final v = _sp.getString('lw_lyrics_translation') ?? 'off';
    return lyricsTranslationOptions.containsKey(v) ? v : 'off';
  }

  Future<void> setLyricsTranslationLang(String v) => _sp.setString(
        'lw_lyrics_translation',
        lyricsTranslationOptions.containsKey(v) ? v : 'off',
      );

  /// `0` keeps the lyrics toolbar visible. Otherwise idle seconds
  /// before it fades (2–10).
  static const lyricsAutoHideOptions = <int, String>{
    0: 'Always on',
    2: '2 seconds',
    4: '4 seconds',
    6: '6 seconds',
    8: '8 seconds',
    10: '10 seconds',
  };

  int get lyricsAutoHideSeconds {
    final v = _sp.getInt('lw_lyrics_autohide_seconds') ?? 0;
    if (v <= 0) return 0;
    return _nearestInt(v, const [2, 4, 6, 8, 10]);
  }

  Future<void> setLyricsAutoHideSeconds(int seconds) {
    final v = seconds <= 0 ? 0 : _nearestInt(seconds, const [2, 4, 6, 8, 10]);
    return _sp.setInt('lw_lyrics_autohide_seconds', v);
  }

  static double _nearestDouble(double value, Iterable<double> steps) {
    var best = steps.first;
    var bestDist = double.infinity;
    for (final step in steps) {
      final dist = (step - value).abs();
      if (dist < bestDist) {
        bestDist = dist;
        best = step;
      }
    }
    return best;
  }

  static int _nearestInt(int value, List<int> steps) {
    var best = steps.first;
    var bestDist = 1 << 30;
    for (final step in steps) {
      final dist = (step - value).abs();
      if (dist < bestDist) {
        bestDist = dist;
        best = step;
      }
    }
    return best;
  }

  // -- Appearance ------------------------------------------------------------
  bool get amoled => _sp.getBool('lw_amoled') ?? false;
  Future<void> setAmoled(bool v) => _sp.setBool('lw_amoled', v);

  /// 'dark' (midnight observatory), 'light' (pearl white) or 'custom'
  /// (surfaces generated from [customThemeBase]).
  String get themeMode => _sp.getString('lw_theme_mode') ?? 'dark';
  Future<void> setThemeMode(String v) => _sp.setString('lw_theme_mode', v);
  bool get isLight => themeMode == 'light';
  bool get isCustomTheme => themeMode == 'custom';

  /// 'artwork': surfaces generated from the playing cover (see
  /// `artworkSurfacesProvider`), Midnight while nothing plays.
  bool get isArtworkTheme => themeMode == 'artwork';

  /// Base colour (ARGB) of the Custom theme. Default: the Ocean preset.
  int get customThemeBase => _sp.getInt('lw_custom_theme_base') ?? 0xFF1A2A44;
  Future<void> setCustomThemeBase(int v) =>
      _sp.setInt('lw_custom_theme_base', v);

  String get accentMode => _sp.getString('lw_accent_mode') ?? 'manual';
  Future<void> setAccentMode(String v) => _sp.setString('lw_accent_mode', v);

  int get accentColor => _sp.getInt('lw_accent') ?? 0xFF3D91FF;
  Future<void> setAccentColor(int v) => _sp.setInt('lw_accent', v);

  bool get dynamicNowPlaying => _sp.getBool('lw_dynamic_now_playing') ?? false;
  Future<void> setDynamicNowPlaying(bool v) =>
      _sp.setBool('lw_dynamic_now_playing', v);
  bool get macOSNowPlaying => _sp.getBool('lw_macos_now_playing') ?? false;
  Future<void> setMacOSNowPlaying(bool v) =>
      _sp.setBool('lw_macos_now_playing', v);

  // -- Haze / material (spec §42 — only settings that actually work) ------
  // -- Performance Mode (Smooth / Low-Spec mode) --------------------------
  /// Disables heavy GPU blurs, background video decoding (canvas),
  /// high-refresh lyrics tickers, and ambient mesh animations to ensure
  /// smooth, low-latency playback on modest hardware (Intel HD Graphics,
  /// dual-core CPUs, laptops on battery).
  bool get performanceMode => _sp.getBool('lw_performance_mode') ?? false;
  Future<void> setPerformanceMode(bool v) =>
      _sp.setBool('lw_performance_mode', v);

  /// Has the low-spec hardware prompt been dismissed?
  bool get performancePromptDismissed =>
      _sp.getBool('lw_performance_prompt_dismissed') ?? false;
  Future<void> setPerformancePromptDismissed(bool v) =>
      _sp.setBool('lw_performance_prompt_dismissed', v);

  /// 'automatic' | 'haze' | 'solid'. Solid disables BackdropFilter
  /// everywhere (static tonal fallback); automatic uses Haze L1–L3 as
  /// designed, honouring reduce-transparency.
  String get hazeMaterial => _sp.getString('lw_haze_material') ?? 'automatic';
  Future<void> setHazeMaterial(String v) =>
      _sp.setString('lw_haze_material', v);

  /// 'low' | 'medium' | 'high'. Scales L1–L3 blur sigma.
  String get hazeIntensity => _sp.getString('lw_haze_intensity') ?? 'medium';
  Future<void> setHazeIntensity(String v) =>
      _sp.setString('lw_haze_intensity', v);

  /// Interface zoom scaling factor (0.75 to 1.50, default 1.0).
  double get uiScale => _sp.getDouble('lw_ui_scale') ?? 1.0;
  Future<void> setUiScale(double v) => _sp.setDouble('lw_ui_scale', v);

  /// Accent source: 'system' | 'katmusic' | 'artwork' | 'custom'.
  /// 'manual' (legacy) is treated as 'custom'. The theme controller
  /// resolves the effective accent; artwork mode tints from the current
  /// palette seed where available, otherwise falls back to custom.
  String get accentSource {
    final v =
        _sp.getString('lw_accent_source') ??
        _sp.getString('lw_accent_mode') ??
        'katmusic';
    if (v == 'manual') return 'custom';
    if (v == 'lastwave') return 'katmusic';
    return v;
  }

  Future<void> setAccentSource(String v) async {
    await _sp.setString('lw_accent_source', v);
    await _sp.setString('lw_accent_mode', v);
  }

  bool get liquidGlass => _sp.getBool('lw_liquid_glass') ?? false;
  Future<void> setLiquidGlass(bool v) => _sp.setBool('lw_liquid_glass', v);

  /// Glassmorphism: the whole app frosted over the playing artwork.
  bool get glassmorphism => _sp.getBool('lw_glassmorphism') ?? false;
  Future<void> setGlassmorphism(bool v) => _sp.setBool('lw_glassmorphism', v);

  /// App font: '' Windows default, else an id from `appFonts`.
  String get appFont => _sp.getString('lw_app_font') ?? '';
  Future<void> setAppFont(String v) => _sp.setString('lw_app_font', v);

  /// App wallpaper: a local image painted behind the shell, or ''.
  String get wallpaperPath => _sp.getString('lw_wallpaper_path') ?? '';
  Future<void> setWallpaperPath(String v) =>
      _sp.setString('lw_wallpaper_path', v.trim());

  /// Scrim over the wallpaper, 0 = untouched image, 0.9 = nearly hidden.
  double get wallpaperDim =>
      (_sp.getDouble('lw_wallpaper_dim') ?? 0.55).clamp(0.0, 0.9);
  Future<void> setWallpaperDim(double v) =>
      _sp.setDouble('lw_wallpaper_dim', v.clamp(0.0, 0.9));

  /// Installed theme files (an `AppThemeFile.toJson` list as raw JSON, so core
  /// storage stays free of the feature model).
  String get customThemesJson => _sp.getString('lw_custom_themes') ?? '[]';
  Future<void> setCustomThemesJson(String v) =>
      _sp.setString('lw_custom_themes', v);

  bool get wavySeekbar => _sp.getBool('lw_wavy_seekbar') ?? false;
  Future<void> setWavySeekbar(bool v) => _sp.setBool('lw_wavy_seekbar', v);

  // -- Scrobbler --------------------------------------------------------------
  bool get scrobblerEnabled => _sp.getBool('lw_scrobbler_enabled') ?? false;

  // -- Other scrobblers (ListenBrainz, Libre.fm) ----------------------------
  // Empty server = the public service. Cleared together on disconnect.
  String get listenBrainzToken => _sp.getString('lw_lb_token') ?? '';
  String get listenBrainzUser => _sp.getString('lw_lb_user') ?? '';
  String get listenBrainzServer => _sp.getString('lw_lb_server') ?? '';
  Future<void> setListenBrainz({
    required String token,
    required String user,
    required String server,
  }) async {
    await _sp.setString('lw_lb_token', token);
    await _sp.setString('lw_lb_user', user);
    await _sp.setString('lw_lb_server', server);
  }

  String get libreFmSession => _sp.getString('lw_librefm_session') ?? '';
  String get libreFmUser => _sp.getString('lw_librefm_user') ?? '';
  String get libreFmServer => _sp.getString('lw_librefm_server') ?? '';
  Future<void> setLibreFm({
    required String session,
    required String user,
    required String server,
  }) async {
    await _sp.setString('lw_librefm_session', session);
    await _sp.setString('lw_librefm_user', user);
    await _sp.setString('lw_librefm_server', server);
  }
  bool get scrobbleNowPlaying => _sp.getBool('lw_submit_now_playing') ?? true;
  int get scrobblePercent {
    final v = _sp.getInt('lw_scrobble_percent') ?? 50;
    return v.clamp(25, 90);
  }

  Future<void> setScrobbler({
    bool? enabled,
    bool? nowPlaying,
    int? percent,
  }) async {
    if (enabled != null) {
      await _sp.setBool('lw_scrobbler_enabled', enabled);
    }
    if (nowPlaying != null) {
      await _sp.setBool('lw_submit_now_playing', nowPlaying);
    }
    if (percent != null) {
      await _sp.setInt('lw_scrobble_percent', percent.clamp(25, 90));
    }
  }

  // -- YouTube Music --------------------------------------------------------
  /// Push played tracks into the account's YT Music watch history.
  /// Default off: writing to the Google account is opt-in.
  bool get syncYtHistory => _sp.getBool('lw_sync_yt_history') ?? true;

  /// Two-way YouTube Music sync of Liked Songs and playlists.
  bool get syncYtLibrary => _sp.getBool('lw_sync_yt_library') ?? true;

  Future<void> setSyncYtLibrary(bool v) =>
      _sp.setBool('lw_sync_yt_library', v);

  Future<void> setSyncYtHistory(bool v) =>
      _sp.setBool('lw_sync_yt_history', v);

  /// Skip BotGuard poToken minting entirely (direct-URL clients only).
  /// Default off (poTokens enabled). Escape hatch for machines where the
  /// hidden `KatMusic BotGuard` WebView misbehaves: playback then never
  /// opens a WebView and fails open to direct streams; some
  /// ciphered-only tracks may not resolve.
  bool get disablePoToken => _sp.getBool('lw_disable_potoken') ?? false;

  Future<void> setDisablePoToken(bool v) =>
      _sp.setBool('lw_disable_potoken', v);

  // -- Desktop-app Parity (Karaoke Lyrics & Visualizer & CD Mode) ------------
  int getLyricsOffset(String trackKey) =>
      _sp.getInt('lw_lyrics_offset_$trackKey') ?? 0;

  Future<void> setLyricsOffset(String trackKey, int offsetMs) =>
      _sp.setInt('lw_lyrics_offset_$trackKey', offsetMs);

  Future<void> resetLyricsOffset(String trackKey) =>
      _sp.remove('lw_lyrics_offset_$trackKey');

  bool get lyricsTransliteration =>
      _sp.getBool('lw_lyrics_transliteration') ?? true;

  Future<void> setLyricsTransliteration(bool v) =>
      _sp.setBool('lw_lyrics_transliteration', v);

  bool get visualizerEnabled => _sp.getBool('lw_visualizer_enabled') ?? true;

  Future<void> setVisualizerEnabled(bool v) =>
      _sp.setBool('lw_visualizer_enabled', v);

  /// Now Playing ambient style: 'apple' (vivid artwork colours, always on
  /// a dark stage with white text, the same in every theme) or 'winui'
  /// (the calm tonal stage that follows the app theme).
  String get ambientStyle => _sp.getString('lw_ambient_style') ?? 'apple';
  Future<void> setAmbientStyle(String v) =>
      _sp.setString('lw_ambient_style', v);

  /// Now Playing presentation style: 'fullBleed' (default edge-to-edge canvas + lyrics),
  /// 'minimal' (sharp cover + duet lyrics), 'standard' (Old dual pane layout).
  static const nowPlayingStyles = [
    'fullBleed',
    'minimal',
    'standard',
  ];
  String get nowPlayingStyle {
    final raw = _sp.getString('lw_now_playing_style') ?? 'fullBleed';
    return nowPlayingStyles.contains(raw) ? raw : 'fullBleed';
  }
  Future<void> setNowPlayingStyle(String style) => _sp.setString(
        'lw_now_playing_style',
        nowPlayingStyles.contains(style) ? style : 'fullBleed',
      );

  /// Inactivity timeout (seconds) before Now Playing controls auto-hide.
  /// `0` disables auto-hide; allowed values are 3, 4, 5, and 0 (Off).
  static const nowPlayingAutoHideOptions = [3, 4, 5, 0];
  int get nowPlayingAutoHideSeconds {
    final raw = _sp.getInt('lw_now_playing_autohide_sec') ?? 3;
    return nowPlayingAutoHideOptions.contains(raw) ? raw : 3;
  }

  Future<void> setNowPlayingAutoHideSeconds(int seconds) => _sp.setInt(
        'lw_now_playing_autohide_sec',
        nowPlayingAutoHideOptions.contains(seconds) ? seconds : 3,
      );

  bool get cdMode => _sp.getBool('lw_cd_mode') ?? false;

  Future<void> setCdMode(bool v) => _sp.setBool('lw_cd_mode', v);

  // -- Discord Rich Presence --------------------------------------------------
  /// Show the current track in Discord status. Default on (Spotify parity).
  bool get discordRichPresence =>
      _sp.getBool('lw_discord_rich_presence') ?? true;

  Future<void> setDiscordRichPresence(bool v) =>
      _sp.setBool('lw_discord_rich_presence', v);

  // -- Diagnostics -----------------------------------------------------------
  /// Per-request detail in the session log (stream clients, audio probes).
  /// Off by default: the always-on lines already name every stage, and
  /// verbose adds a few lines per player request.
  bool get verboseLogs => _sp.getBool('lw_verbose_logs') ?? false;

  Future<void> setVerboseLogs(bool v) => _sp.setBool('lw_verbose_logs', v);

  // -- Equalizer & Studio Master Clarity ------------------------------------
  bool get equalizerEnabled => _sp.getBool('lw_eq_enabled') ?? false;
  Future<void> setEqualizerEnabled(bool v) => _sp.setBool('lw_eq_enabled', v);

  String get equalizerPreset => _sp.getString('lw_eq_preset') ?? 'Default';
  Future<void> setEqualizerPreset(String v) =>
      _sp.setString('lw_eq_preset', v);

  String get equalizerGains => _sp.getString('lw_eq_gains') ?? '';
  Future<void> setEqualizerGains(String v) =>
      _sp.setString('lw_eq_gains', v);

  bool get studioClarityEnabled =>
      _sp.getBool('lw_studio_clarity_enabled') ?? false;
  Future<void> setStudioClarityEnabled(bool v) =>
      _sp.setBool('lw_studio_clarity_enabled', v);

  String get studioClarityPreset =>
      _sp.getString('lw_studio_clarity_preset') ?? 'reference';
  Future<void> setStudioClarityPreset(String v) =>
      _sp.setString('lw_studio_clarity_preset', v);

  // -- App Updates ------------------------------------------------------------
  /// GitHub release version the user opted to suppress automatic startup popups for.
  String get ignoredUpdateVersion =>
      _sp.getString('lw_ignored_update_version') ?? '';

  Future<void> setIgnoredUpdateVersion(String v) =>
      _sp.setString('lw_ignored_update_version', v);

  Future<void> clearIgnoredUpdateVersion() =>
      _sp.remove('lw_ignored_update_version');

  /// YouTube Music podcast shelves. Off hides them without deleting resume
  /// positions.
  bool get podcastsEnabled => _sp.getBool('lw_podcasts_enabled') ?? true;
  Future<void> setPodcastsEnabled(bool v) =>
      _sp.setBool('lw_podcasts_enabled', v);

  // -- Language (Android AppLanguage.SYNC_KEY) ------------------------------
  /// BCP-47 tag or `system`. See [AppLanguage] in lib/l10n.
  String get appLanguageTag => _sp.getString('app_language_tag') ?? 'system';
  Future<void> setAppLanguageTag(String tag) =>
      _sp.setString('app_language_tag', tag.trim());

  // -- Jam --------------------------------------------------------------------
  String get jamServerUrl {
    final raw = _sp.getString('jamServerUrl')?.trim() ?? '';
    // The Jam server moved; saved old defaults follow it.
    if (raw.isEmpty ||
        raw.contains('jam.arctdevelopment.qd.je') ||
        raw.contains('jam.clashgram.org')) {
      return 'wss://katmusic.onrender.com/ws';
    }
    return raw;
  }

  Future<void> setJamServerUrl(String url) =>
      _sp.setString('jamServerUrl', url.trim());

  String get jamDisplayName => _sp.getString('lw_jam_display_name') ?? '';
  Future<void> setJamDisplayName(String name) =>
      _sp.setString('lw_jam_display_name', name.trim());

  // -- Canvas & animated artwork --------------------------------------------
  /// Master toggle for looping Canvas / animated cover art in Now Playing.
  bool get canvasEnabled => _sp.getBool('lw_canvas_enabled') ?? true;
  Future<void> setCanvasEnabled(bool v) => _sp.setBool('lw_canvas_enabled', v);

  // -- Spotify canvas (cookie itself lives in secure storage) ---------------
  bool get spotifyCanvasEnabled =>
      _sp.getBool('spotify_canvas_enabled') ?? true;
  Future<void> setSpotifyCanvasEnabled(bool v) =>
      _sp.setBool('spotify_canvas_enabled', v);

  /// When true, Full Bleed prefers Spotify Canvas ahead of Apple Music /
  /// Tidal / Community motion art. When false, higher-bitrate Apple/Tidal
  /// motion art is preferred and Spotify Canvas fills the gaps.
  bool get preferSpotifyCanvas =>
      _sp.getBool('lw_prefer_spotify_canvas') ?? false;
  Future<void> setPreferSpotifyCanvas(bool v) =>
      _sp.setBool('lw_prefer_spotify_canvas', v);

  /// Which service picks the songs autoplay / endless radio adds:
  /// `'youtube'` (YouTube Music related songs, default) or `'spotify'`
  /// (Spotify's own song radio, needs a connected Spotify account). The
  /// two are never mixed in one refill.
  String get recommendationSource =>
      _sp.getString('lw_reco_source') == 'spotify' ? 'spotify' : 'youtube';
  Future<void> setRecommendationSource(String v) => _sp.setString(
      'lw_reco_source', v == 'spotify' ? 'spotify' : 'youtube');

  /// Per-install id presented to Spotify's client-token endpoint.
  String get spotifyDeviceId => _sp.getString('spotify_device_id') ?? '';
  Future<void> setSpotifyDeviceId(String v) =>
      _sp.setString('spotify_device_id', v);

  /// Last one-time-password secret Spotify accepted, as JSON
  /// `{"v": version, "c": [char codes]}`; '' when none yet. Tried first on
  /// the next sign-in so it works even when the secret sources are down.
  String get spotifyOtpSecret => _sp.getString('spotify_otp_secret') ?? '';
  Future<void> setSpotifyOtpSecret(String v) =>
      _sp.setString('spotify_otp_secret', v);

  // -- Download folder layout (Android DownloadFolderStructure ids) ---------
  String get downloadStructure {
    final raw = _sp.getString('lw_download_structure') ?? 'flat';
    const known = {
      'flat',
      'artist_album',
      'artist_year_album',
      'album_only',
      'year_album',
    };
    return known.contains(raw) ? raw : 'flat';
  }

  Future<void> setDownloadStructure(String id) =>
      _sp.setString('lw_download_structure', id);

  // -- Portable backup (non-secret prefs only) ------------------------------
  static bool isSecretPref(String key) {
    final k = key.toLowerCase();
    const exact = {
      'lw_sessionkey',
      'lw_lastfm_api_key',
      'lw_lastfm_api_secret',
      'lw_apikey',
      'lw_apisecret',
      'ytm_cookies',
      'spotify_sp_dc',
      'spotify_personal_token',
      'spotify_client_token',
      'spotify_personal_token_expires',
      'spotify_client_token_expires',
    };
    if (exact.contains(k)) return true;
    if (k.contains('cookie')) return true;
    if (k.contains('secret')) return true;
    if (k.contains('sp_dc')) return true;
    if (k.contains('sessionkey')) return true;
    return false;
  }

  /// Settings that only make sense on the machine they were set on (a
  /// folder path, an audio device id, the signed-in name).
  static bool isDeviceBoundPref(String key) => const {
        'lw_download_dir',
        'lw_audio_device_id',
        'lw_username',
      }.contains(key.toLowerCase());

  static bool _isPortable(String key) =>
      !isSecretPref(key) && !isDeviceBoundPref(key);

  Set<String> get portablePrefKeys =>
      _sp.getKeys().where(_isPortable).toSet();

  Object? portablePref(String key) =>
      _isPortable(key) ? _sp.get(key) : null;

  Future<void> setPortablePref(String key, Object value) async {
    if (!_isPortable(key)) return;
    if (value is bool) {
      await _sp.setBool(key, value);
    } else if (value is int) {
      await _sp.setInt(key, value);
    } else if (value is double) {
      await _sp.setDouble(key, value);
    } else if (value is String) {
      await _sp.setString(key, value);
    } else if (value is List) {
      await _sp.setStringList(
        key,
        value.map((e) => e.toString()).toList(),
      );
    }
  }
}

final prefsProvider = Provider<Prefs>((_) {
  throw UnimplementedError('Prefs not initialised — override in main()');
});
