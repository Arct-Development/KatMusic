import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:katmusic_desktop/core/storage/app_database.dart';
import 'package:katmusic_desktop/core/storage/prefs.dart';
import 'package:katmusic_desktop/features/backup/backup_repository.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  late Prefs prefs;

  setUp(() async {
    SharedPreferences.setMockInitialValues({
      'kat_sessionkey': 'SESSION',
      'kat_lastfm_api_secret': 'SECRET',
      'kat_addon_urls': 'https://example.invalid/addon',
      'ytm_cookies': 'COOKIE',
      'spotify_sp_dc': 'SPDC',
      'kat_download_dir': r'C:\Music',
      'kat_audio_device_id': 'device-1',
      'kat_username': 'someone',
      'kat_theme_mode': 'midnight',
      'kat_lyrics_text_scale': 1.2,
      'kat_amoled': true,
    });
    prefs = Prefs(await SharedPreferences.getInstance());
  });

  test('export keeps secrets and device-bound settings out of the file', () {
    final json = BackupRepository().build(
      prefs: prefs,
      db: AppDatabase.inMemory(),
      playlists: const [],
    );
    for (final leaked in [
      'SESSION',
      'SECRET',
      'example.invalid',
      'COOKIE',
      'SPDC',
      r'C:\\Music',
      'device-1',
      'someone',
    ]) {
      expect(json.contains(leaked), isFalse, reason: leaked);
    }
    final decoded = jsonDecode(json) as Map<String, dynamic>;
    expect(decoded['type'], backupType);
    final saved = decoded['prefs'] as Map<String, dynamic>;
    expect((saved['strings'] as Map)['kat_theme_mode'], 'midnight');
    expect((saved['doubles'] as Map)['kat_lyrics_text_scale'], 1.2);
    expect((saved['booleans'] as Map)['kat_amoled'], true);
  });

  test('import cannot write a secret or device-bound setting', () async {
    await prefs.setPortablePref('kat_sessionkey', 'ATTACKER');
    await prefs.setPortablePref('kat_download_dir', r'D:\elsewhere');
    await prefs.setPortablePref('kat_theme_mode', 'pearl');
    expect(prefs.sessionKey, 'SESSION');
    expect(prefs.downloadDir, r'C:\Music');
    expect(prefs.themeMode, 'pearl');
  });

  test('read rejects other files and newer schemas', () {
    final repo = BackupRepository();
    expect(repo.read('not json', const []), isA<BackupReadInvalid>());
    expect(repo.read('{"type":"other"}', const []), isA<BackupReadInvalid>());
    expect(
      repo.read(
        jsonEncode({'type': backupType, 'schemaVersion': backupSchema + 1}),
        const [],
      ),
      isA<BackupReadUnsupported>(),
    );
  });

  test('a backup reads back as a preview', () {
    final repo = BackupRepository();
    final json = repo.build(
      prefs: prefs,
      db: AppDatabase.inMemory(),
      playlists: const [],
    );
    final parsed = repo.read(json, const []);
    expect(parsed, isA<BackupReadOk>());
    expect((parsed as BackupReadOk).preview.playlists, isEmpty);
  });
}
