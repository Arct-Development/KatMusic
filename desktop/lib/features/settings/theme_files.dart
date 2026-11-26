import 'dart:convert';
import 'dart:io';

import 'package:fluent_ui/fluent_ui.dart' show Color;

import '../../core/storage/prefs.dart';
import 'accent.dart' show AccentSource;
import 'theme_controller.dart';

/// A shareable KatMusic look, saved as a small JSON file so themes can be
/// traded the way Discord/Vencord themes are: one file, drop it in, apply it.
///
/// A theme only carries the knobs Appearance exposes (accent, base surface,
/// brightness mode, glass, font) and is applied through the same setters, so
/// it can never put the UI into a state the normal controls can't reach.
class AppThemeFile {
  static const format = 'katmusic-theme/1';
  static const modes = <String>{'dark', 'light', 'custom', 'artwork'};

  final String name;
  final String author;
  final int accent;
  final int base;
  final String mode;
  final bool glass;
  final String font;

  /// Optional local wallpaper image. Only applied when the file exists on
  /// this machine, so shared themes don't point at someone else's drive.
  final String wallpaper;

  const AppThemeFile({
    required this.name,
    this.author = '',
    required this.accent,
    required this.base,
    this.mode = 'custom',
    this.glass = false,
    this.font = '',
    this.wallpaper = '',
  });

  Color get accentColor => Color(accent);
  Color get baseColor => Color(base);

  Map<String, Object?> toJson() => {
    'format': format,
    'name': name,
    'author': author,
    'accent': _hex(accent),
    'base': _hex(base),
    'mode': mode,
    'glass': glass,
    if (font.isNotEmpty) 'font': font,
    if (wallpaper.isNotEmpty) 'wallpaper': wallpaper,
  };

  static AppThemeFile? fromJson(Object? json) {
    if (json is! Map) return null;
    final name = (json['name'] as Object?)?.toString().trim() ?? '';
    final accent = _parseColor(json['accent']);
    final base = _parseColor(json['base']);
    if (name.isEmpty || accent == null || base == null) return null;
    final mode = json['mode']?.toString() ?? 'custom';
    return AppThemeFile(
      name: name.length > 60 ? name.substring(0, 60) : name,
      author: (json['author']?.toString() ?? '').trim(),
      accent: accent,
      base: base,
      mode: modes.contains(mode) ? mode : 'custom',
      glass: json['glass'] == true,
      font: (json['font']?.toString() ?? '').trim(),
      wallpaper: (json['wallpaper']?.toString() ?? '').trim(),
    );
  }

  /// Accepts `#AARRGGBB`, `#RRGGBB` or a plain int, so hand-written and
  /// exported files both load.
  static int? _parseColor(Object? value) {
    if (value is int) return value;
    if (value is String) {
      var hex = value.replaceAll('#', '').trim();
      if (hex.length == 6) hex = 'FF$hex';
      if (hex.length != 8) return null;
      return int.tryParse(hex, radix: 16);
    }
    return null;
  }

  static String _hex(int argb) =>
      '#${argb.toRadixString(16).padLeft(8, '0').toUpperCase()}';
}

/// Themes that ship with the app, shown beside installed files.
const builtInThemeFiles = <AppThemeFile>[
  AppThemeFile(
    name: 'Midnight Rose',
    author: 'KatMusic',
    accent: 0xFFE0507A,
    base: 0xFF1B1018,
  ),
  AppThemeFile(
    name: 'Blue Hour',
    author: 'KatMusic',
    accent: 0xFF3D91FF,
    base: 0xFF101A2A,
  ),
  AppThemeFile(
    name: 'Moonlit Moss',
    author: 'KatMusic',
    accent: 0xFF6B9E6B,
    base: 0xFF101F19,
    glass: true,
  ),
  AppThemeFile(
    name: 'Amber Studio',
    author: 'KatMusic',
    accent: 0xFFE0A030,
    base: 0xFF201810,
  ),
  AppThemeFile(
    name: 'Paper & Ink',
    author: 'KatMusic',
    accent: 0xFF18181B,
    base: 0xFFFAF6EE,
    mode: 'light',
  ),
];

/// Installed theme files, newest last. A corrupt entry is skipped rather than
/// taking the whole gallery down.
List<AppThemeFile> readThemeFiles(Prefs prefs) {
  try {
    final decoded = jsonDecode(prefs.customThemesJson);
    if (decoded is! List) return const [];
    return [for (final entry in decoded) ?AppThemeFile.fromJson(entry)];
  } on FormatException {
    return const [];
  }
}

Future<void> writeThemeFiles(Prefs prefs, List<AppThemeFile> themes) =>
    prefs.setCustomThemesJson(
      jsonEncode([for (final theme in themes) theme.toJson()]),
    );

/// Saves [theme], replacing an installed theme with the same name.
Future<void> saveThemeFile(Prefs prefs, AppThemeFile theme) async {
  final themes = readThemeFiles(prefs)
    ..removeWhere(
      (existing) => existing.name.toLowerCase() == theme.name.toLowerCase(),
    )
    ..add(theme);
  await writeThemeFiles(prefs, themes);
}

Future<void> deleteThemeFile(Prefs prefs, String name) async {
  final themes = readThemeFiles(prefs)
    ..removeWhere((theme) => theme.name.toLowerCase() == name.toLowerCase());
  await writeThemeFiles(prefs, themes);
}

/// Applies every knob in [theme] through the normal Appearance setters.
Future<void> applyThemeFile(
  Prefs prefs,
  ThemeController controller,
  AppThemeFile theme,
) async {
  await prefs.setAccentColor(theme.accent);
  await prefs.setAccentSource(AccentSource.custom.id);
  await prefs.setCustomThemeBase(theme.base);
  await prefs.setGlassmorphism(theme.glass);
  await prefs.setAppFont(theme.font);
  // A theme can bring a wallpaper, but only one that exists locally; a
  // missing path leaves whatever the user already had.
  if (theme.wallpaper.isNotEmpty && File(theme.wallpaper).existsSync()) {
    await prefs.setWallpaperPath(theme.wallpaper);
  }
  await controller.setThemeMode(theme.mode);
  controller.refresh();
}

/// The current look as a shareable file.
AppThemeFile themeFileFromCurrent(Prefs prefs) {
  return AppThemeFile(
    name: 'My KatMusic theme',
    author: '',
    accent: prefs.accentColor,
    base: prefs.customThemeBase,
    mode: prefs.isLight
        ? 'light'
        : prefs.isArtworkTheme
        ? 'artwork'
        : prefs.isCustomTheme
        ? 'custom'
        : 'dark',
    glass: prefs.glassmorphism,
    font: prefs.appFont,
    wallpaper: prefs.wallpaperPath,
  );
}
