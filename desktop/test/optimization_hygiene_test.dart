import 'dart:io';

import 'package:flutter/services.dart';
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
      const channel = MethodChannel('plugins.flutter.io/path_provider');
      final supportDir = await Directory.systemTemp.createTemp('katmusic_renderer_test_');
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(channel, (call) async {
        if (call.method == 'getApplicationSupportDirectory') {
          return supportDir.path;
        }
        return null;
      });

      try {
        await RendererMode.setImpellerEnabled(false);
        expect(await RendererMode.isImpellerEnabled(), isFalse);

        await RendererMode.setImpellerEnabled(true);
        expect(await RendererMode.isImpellerEnabled(), isTrue);

        // Clean up back to default (false / Skia).
        await RendererMode.setImpellerEnabled(false);
        expect(await RendererMode.isImpellerEnabled(), isFalse);
      } finally {
        TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
            .setMockMethodCallHandler(channel, null);
        await supportDir.delete(recursive: true);
      }
    });
  });
}
