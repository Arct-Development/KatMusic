import 'package:flutter_test/flutter_test.dart';
import 'package:katmusic_desktop/features/updates/update_service.dart';

AppUpdateInfo releaseWith(String tag, List<String> assetNames) => AppUpdateInfo(
      tagName: tag,
      version: tag,
      title: tag,
      changelog: '',
      htmlUrl: '',
      publishedAt: null,
      assets: [
        for (final name in assetNames)
          AppReleaseAsset(
            name: name,
            downloadUrl: 'https://example.com/$name',
            size: 1,
          ),
      ],
    );

void main() {
  group('desktop releases', () {
    test('desktop-v tags are parsed as plain versions', () {
      final info = AppUpdateInfo.fromGitHubJson({
        'tag_name': 'desktop-v2.6.0',
        'name': 'KatMusic Desktop 2.6.0',
        'assets': const <dynamic>[],
      });

      expect(info.tagName, 'desktop-v2.6.0');
      expect(info.version, '2.6.0');
    });

    test('semver compares desktop builds by build number', () {
      expect(SemVer.parse('2.6.0+20').isGreaterThan(SemVer.parse('2.6.0+19')),
          isTrue);
      expect(SemVer.parse('2.6.1').isGreaterThan(SemVer.parse('2.6.0+20')),
          isTrue);
      expect(SemVer.parse('2.6.0').isGreaterThan(SemVer.parse('2.6.5')),
          isFalse);
    });

    test('releases with only Android assets are not desktop releases', () {
      final apkOnly = releaseWith('2.6.5', ['KatMusic-v2.6.5-universal.apk']);

      expect(apkOnly.hasDesktopAssets, isFalse);
    });

    test('releases with desktop packages are desktop releases', () {
      final desktop = releaseWith('desktop-v2.6.0', [
        'KatMusic-v2.6.0-windows-x64.zip',
        'KatMusic-v2.6.0-linux-x64.tar.gz',
        'KatMusic-v2.6.0-macos.zip',
      ]);

      expect(desktop.hasDesktopAssets, isTrue);
    });

    test('Linux falls back to the portable tarball', () {
      final desktop = releaseWith('desktop-v2.6.0', [
        'KatMusic-v2.6.0-windows-x64.zip',
        'KatMusic-v2.6.0-linux-x64.tar.gz',
        'KatMusic-v2.6.0-macos.zip',
      ]);

      final asset = desktop.findBestAsset(AppInstallType.linuxAppImage);

      expect(asset?.name, 'KatMusic-v2.6.0-linux-x64.tar.gz');
    });
  });
}
