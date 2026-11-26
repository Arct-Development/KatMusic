import 'package:flutter_test/flutter_test.dart';
import 'package:katmusic_desktop/core/storage/prefs.dart';
import 'package:katmusic_desktop/features/settings/theme_files.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  late Prefs prefs;

  setUp(() async {
    SharedPreferences.setMockInitialValues({});
    prefs = Prefs(await SharedPreferences.getInstance());
  });

  test('a theme file round-trips through JSON', () {
    const theme = AppThemeFile(
      name: 'Night Swim',
      author: 'Kat',
      accent: 0xFF3D91FF,
      base: 0xFF101A2A,
      glass: true,
      font: 'inter',
      wallpaper: '/home/kat/pictures/night-swim.jpg',
    );
    final parsed = AppThemeFile.fromJson(theme.toJson());
    expect(parsed, isNotNull);
    expect(parsed!.name, 'Night Swim');
    expect(parsed.author, 'Kat');
    expect(parsed.accent, theme.accent);
    expect(parsed.base, theme.base);
    expect(parsed.mode, 'custom');
    expect(parsed.glass, isTrue);
    expect(parsed.font, 'inter');
    expect(parsed.wallpaper, '/home/kat/pictures/night-swim.jpg');
  });

  test('accepts #RRGGBB, #AARRGGBB and plain ints', () {
    final short = AppThemeFile.fromJson({
      'name': 'Short',
      'accent': '#3D91FF',
      'base': '#101A2A',
    });
    expect(short?.accent, 0xFF3D91FF);
    expect(short?.base, 0xFF101A2A);

    final ints = AppThemeFile.fromJson({
      'name': 'Ints',
      'accent': 0xFFE0507A,
      'base': 0xFF1B1018,
      'mode': 'light',
    });
    expect(ints?.accent, 0xFFE0507A);
    expect(ints?.mode, 'light');
  });

  test('rejects files without a name or colours, and unknown modes', () {
    expect(AppThemeFile.fromJson({'accent': '#FFFFFF', 'base': '#000000'}), isNull);
    expect(AppThemeFile.fromJson({'name': 'No colour'}), isNull);
    expect(AppThemeFile.fromJson('not a map'), isNull);
    final oddMode = AppThemeFile.fromJson({
      'name': 'Odd',
      'accent': '#FFFFFF',
      'base': '#000000',
      'mode': 'sepia',
    });
    expect(oddMode?.mode, 'custom');
  });

  test('install saves, replaces by name and removes', () async {
    const first = AppThemeFile(
      name: 'Ocean',
      accent: 0xFF3D91FF,
      base: 0xFF101A2A,
    );
    await saveThemeFile(prefs, first);
    expect(readThemeFiles(prefs).map((t) => t.name), ['Ocean']);

    const replacement = AppThemeFile(
      name: 'ocean',
      accent: 0xFF6B9E6B,
      base: 0xFF101F19,
    );
    await saveThemeFile(prefs, replacement);
    expect(readThemeFiles(prefs).length, 1);
    expect(readThemeFiles(prefs).single.accent, 0xFF6B9E6B);

    await deleteThemeFile(prefs, 'OCEAN');
    expect(readThemeFiles(prefs), isEmpty);
  });

  test('corrupt stored JSON yields no themes instead of throwing', () async {
    await prefs.setCustomThemesJson('{not json');
    expect(readThemeFiles(prefs), isEmpty);
  });
}
