import 'dart:math' as math;

import 'package:fluent_ui/fluent_ui.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:katmusic_desktop/ui/theme/tokens.dart';

/// Neutral-ladder invariants for the graphite / pearl palettes.
///
/// These are the properties the UI depends on but nothing else asserts:
/// text tiers stay legible on every surface they are painted over, and the
/// surface ladder keeps its direction (the meta tier once sat at 2.9:1 and
/// the AMOLED glass sat *lighter* than its own base).
double _luminance(Color color) {
  double channel(double c) =>
      c <= 0.03928 ? c / 12.92 : math.pow((c + 0.055) / 1.055, 2.4).toDouble();
  final a = color.toARGB32();
  return 0.2126 * channel(((a >> 16) & 0xFF) / 255) +
      0.7152 * channel(((a >> 8) & 0xFF) / 255) +
      0.0722 * channel((a & 0xFF) / 255);
}

double _contrast(Color a, Color b) {
  final la = _luminance(a);
  final lb = _luminance(b);
  final hi = la > lb ? la : lb;
  final lo = la > lb ? lb : la;
  return (hi + 0.05) / (lo + 0.05);
}

void main() {
  group('dark text ladder', () {
    const surfaces = {
      'background': KatColors.background,
      'surface': KatColors.surface,
      'raised': KatColors.surfaceRaised,
      'overlay': KatColors.surfaceOverlay,
      'AMOLED surface': KatColors.amoledSurface,
    };

    test('every tier clears its floor on every surface', () {
      for (final entry in surfaces.entries) {
        expect(
          _contrast(KatColors.textPrimary, entry.value),
          greaterThan(7.0),
          reason: 'primary on ${entry.key}',
        );
        expect(
          _contrast(KatColors.textSecondary, entry.value),
          greaterThan(4.5),
          reason: 'secondary on ${entry.key}',
        );
        expect(
          _contrast(KatColors.textTertiary, entry.value),
          greaterThan(4.0),
          reason: 'meta tier on ${entry.key}',
        );
      }
    });

    test('tiers stay ordered primary > secondary > meta', () {
      for (final entry in surfaces.entries) {
        final primary = _contrast(KatColors.textPrimary, entry.value);
        final secondary = _contrast(KatColors.textSecondary, entry.value);
        final meta = _contrast(KatColors.textTertiary, entry.value);
        expect(primary, greaterThan(secondary), reason: entry.key);
        expect(secondary, greaterThan(meta), reason: entry.key);
      }
    });
  });

  group('light text ladder', () {
    const surfaces = {
      'background': KatColors.lightBackground,
      'content': KatColors.lightContent,
      'surface': KatColors.lightSurface,
      'overlay': KatColors.lightOverlay,
    };

    test('every tier clears its floor on every surface', () {
      for (final entry in surfaces.entries) {
        expect(
          _contrast(KatColors.lightTextPrimary, entry.value),
          greaterThan(7.0),
          reason: 'primary on ${entry.key}',
        );
        expect(
          _contrast(KatColors.lightTextSecondary, entry.value),
          greaterThan(4.5),
          reason: 'secondary on ${entry.key}',
        );
        expect(
          _contrast(KatColors.lightTextTertiary, entry.value),
          greaterThan(4.0),
          reason: 'meta tier on ${entry.key}',
        );
      }
    });
  });

  group('surface ladders', () {
    test('graphite and AMOLED both step up monotonically', () {
      for (final ladder in [
        (
          'graphite',
          [KatColors.background, KatColors.surface, KatColors.surfaceRaised],
        ),
        (
          'AMOLED',
          [
            KatColors.amoledBackground,
            KatColors.amoledSurface,
            KatColors.amoledSurfaceRaised,
          ],
        ),
      ]) {
        final (name, steps) = ladder;
        for (var i = 1; i < steps.length; i++) {
          expect(
            _luminance(steps[i]),
            greaterThan(_luminance(steps[i - 1])),
            reason: '$name step $i',
          );
        }
      }
    });

    test('AMOLED only ever collapses the dark base', () {
      expect(
        _luminance(KatColors.amoledBackground),
        lessThan(_luminance(KatColors.background)),
      );
      expect(
        _luminance(KatColors.amoledSurface),
        lessThan(_luminance(KatColors.surface)),
      );
      // Text tokens are shared, so contrast only improves on black.
      expect(
        _contrast(KatColors.textPrimary, KatColors.amoledBackground),
        greaterThan(_contrast(KatColors.textPrimary, KatColors.background)),
      );
    });
  });
}
