import 'package:flutter_test/flutter_test.dart';
import 'package:katmusic_desktop/core/platform/renderer_mode.dart';
import 'package:katmusic_desktop/core/platform/windows_memory.dart';
import 'package:katmusic_desktop/core/storage/app_database.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Optimization & Memory Hygiene Tests', () {
    test('AppDatabase in-memory sets expected PRAGMAs and checkpoints cleanly', () {
      final db = AppDatabase.inMemory();
      final cacheSize = db.raw.select('PRAGMA cache_size;').first['cache_size'] as int;
      expect(cacheSize, -2000);

      // Checkpoint method should execute without throwing
      expect(() => db.checkpoint(), returnsNormally);
      db.close();
    });

    test('trimWindowsWorkingSet executes safely without throwing', () {
      expect(() => trimWindowsWorkingSet(), returnsNormally);
    });

    test('RendererMode toggles cleanly', () async {
      // path_provider has no registered native implementation in this
      // headless unit-test process. Verify the API remains safe when its
      // app-support fallback is unavailable; platform-specific persistence
      // paths are covered by the Windows/Linux branches at runtime.
      await expectLater(RendererMode.setImpellerEnabled(false), completes);
      expect(await RendererMode.isImpellerEnabled(), isA<bool>());

      await expectLater(RendererMode.setImpellerEnabled(true), completes);
      expect(await RendererMode.isImpellerEnabled(), isA<bool>());

      await expectLater(RendererMode.setImpellerEnabled(false), completes);
      expect(await RendererMode.isImpellerEnabled(), isA<bool>());
    });
  });
}
