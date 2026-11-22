import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:katmusic_desktop/design_system/fluent/kat_scrollbar.dart';

void main() {
  test('KatScrollBehavior includes mouse and trackpad in dragDevices', () {
    const behavior = KatScrollBehavior();
    expect(behavior.dragDevices, contains(PointerDeviceKind.mouse));
    expect(behavior.dragDevices, contains(PointerDeviceKind.trackpad));
    expect(behavior.dragDevices, contains(PointerDeviceKind.touch));
  });

  testWidgets(
    'KatScrollBehavior returns smooth bouncy/always scrollable physics',
    (tester) async {
      const behavior = KatScrollBehavior();
      await tester.pumpWidget(
        Builder(
          builder: (context) {
            final physics = behavior.getScrollPhysics(context);
            expect(physics, isA<BouncingScrollPhysics>());
            expect(physics.parent, isA<AlwaysScrollableScrollPhysics>());
            return const SizedBox.shrink();
          },
        ),
      );
    },
  );

  testWidgets(
    'KatScrollBehavior buildScrollbar wraps scroll view in KatSmoothScroll',
    (tester) async {
      const behavior = KatScrollBehavior();
      final controller = ScrollController();
      await tester.pumpWidget(
        MaterialApp(
          home: Builder(
            builder: (context) {
              final widget = behavior.buildScrollbar(
                context,
                const SizedBox.shrink(),
                ScrollableDetails(
                  direction: AxisDirection.down,
                  controller: controller,
                ),
              );
              expect(widget, isA<KatSmoothScroll>());
              return widget;
            },
          ),
        ),
      );
    },
  );
}
