import '../../../ui/theme/tokens.dart';

/// Typography — re-exports canonical Kat type with semantic aliases.
///
/// Artwork + type carry hierarchy, not boxes:
/// - pageTitle 22/700/-0.4 · sectionTitle 15/700 · trackTitle 13.5/600
/// - body 13 · meta 12 · label 12/600/0.2 · overline 10.5/700/1.0
/// - numeral tabular · lyricActive 21/700 · lyricIdle 16/500
class LwTypography {
  LwTypography._();

  static const pageTitle = KatType.pageTitle;
  static const sectionTitle = KatType.sectionTitle;
  static const trackTitle = KatType.trackTitle;
  static const body = KatType.body;
  static const meta = KatType.meta;
  static const label = KatType.label;
  static const overline = KatType.overline;
  static const numeral = KatType.numeral;
  static const lyricActive = KatType.lyricActive;
  static const lyricIdle = KatType.lyricIdle;
}
