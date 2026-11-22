import 'dart:math' as math;

import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:katmusic_desktop/ui/theme/tokens.dart';

double _contrast(Color a, Color b) {
  final la = a.computeLuminance();
  final lb = b.computeLuminance();
  return (math.max(la, lb) + 0.05) / (math.min(la, lb) + 0.05);
}

void main() {
  group('KatSurfaces.fromCustomBase', () {
    test('every random base keeps body text readable on every surface', () {
      final rnd = math.Random(3);
      for (var i = 0; i < 500; i++) {
        final base = Color(0xFF000000 | rnd.nextInt(0xFFFFFF));
        final s = KatSurfaces.fromCustomBase(base);
        final light = KatSurfaces.isLightBase(base);
        final text = light ? KatColors.lightTextPrimary : KatColors.textPrimary;
        final text2 = light
            ? KatColors.lightTextSecondary
            : KatColors.textSecondary;
        for (final surface in [
          s.background,
          s.surface,
          s.surfaceRaised,
          s.surfaceOverlay,
        ]) {
          expect(
            _contrast(text, surface),
            greaterThanOrEqualTo(7.0),
            reason: 'primary text on $surface from $base',
          );
          expect(
            _contrast(text2, surface),
            greaterThanOrEqualTo(4.45),
            reason: 'secondary text on $surface from $base',
          );
        }
        expect(s.isCustom, isTrue);
      }
    });

    test(
      'dark ladder steps get lighter, light pages sit under their cards',
      () {
        final dark = KatSurfaces.fromCustomBase(const Color(0xFF1A2A44));
        expect(
          dark.surface.computeLuminance(),
          greaterThan(dark.background.computeLuminance()),
        );
        expect(
          dark.surfaceRaised.computeLuminance(),
          greaterThan(dark.surface.computeLuminance()),
        );
        expect(
          dark.surfaceOverlay.computeLuminance(),
          greaterThan(dark.surfaceRaised.computeLuminance()),
        );

        final light = KatSurfaces.fromCustomBase(const Color(0xFFEFE7DA));
        expect(KatSurfaces.isLightBase(const Color(0xFFEFE7DA)), isTrue);
        expect(
          light.surface.computeLuminance(),
          greaterThan(light.background.computeLuminance()),
        );
        expect(
          light.surfaceOverlay.computeLuminance(),
          lessThan(light.background.computeLuminance()),
        );
      },
    );

    test('stock palettes are untouched', () {
      expect(KatSurfaces.graphite.isCustom, isFalse);
      expect(KatSurfaces.graphite.surfaceOverlay, KatColors.surfaceOverlay);
      expect(KatSurfaces.graphite.outlineSoft, KatColors.outlineSoft);
      expect(KatSurfaces.amoled.isCustom, isFalse);
    });
  });
}
