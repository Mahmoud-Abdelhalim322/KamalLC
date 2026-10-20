import 'package:flutter/material.dart';

/// Brand tokens from the approved "Brand Yellow" Figma designs: yellow leads,
/// ink is only an accent (stat strips, pills, the dock).
class K {
  K._();

  static const brand = Color(0xFFF5B000);
  static const brandLight = Color(0xFFFFD866);
  static const brandDeep = Color(0xFFEE9600);
  static const brandDark = Color(0xFFC98A00);
  static const brandTint = Color(0xFFFFF6DC);

  static const ink = Color(0xFF16171B);
  static const inkSoft = Color(0xFF2A2C33);
  static const canvas = Color(0xFFF8F6F1);
  static const card = Colors.white;
  static const line = Color(0xFFEDEAE3);
  static const muted = Color(0xFF8A8F99);
  static const text = Color(0xFF1E1F24);

  static const success = Color(0xFF1E9E5A);
  static const successBg = Color(0xFFE3F6EA);
  static const danger = Color(0xFFC62828);
  static const dangerBg = Color(0xFFFDE7E7);
  static const warning = Color(0xFFB7791F);
  static const warningBg = Color(0xFFFFF1D2);
  static const purple = Color(0xFF6B4FBB);
  static const purpleBg = Color(0xFFEFEAFB);
  static const info = Color(0xFF2F6FD6);
  static const infoBg = Color(0xFFE6EFFC);

  static const heroGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [Color(0xFFFFE38A), brand, brandDeep],
    stops: [0, 0.45, 1],
  );

  static const buttonGradient = LinearGradient(
    begin: Alignment.centerRight,
    end: Alignment.centerLeft,
    colors: [Color(0xFFFFD25E), brand, brandDark],
  );

  static const radius = 22.0;

  static List<BoxShadow> get softShadow => [
        BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 18, offset: const Offset(0, 6)),
      ];

  static ThemeData theme(Locale locale) {
    final family = locale.languageCode == 'ar' ? 'Cairo' : 'Inter';
    final base = ThemeData(
      useMaterial3: true,
      fontFamily: family,
      colorScheme: ColorScheme.fromSeed(seedColor: brand, primary: brand, onPrimary: ink, surface: canvas),
      scaffoldBackgroundColor: canvas,
    );
    return base.copyWith(
      textTheme: base.textTheme.apply(bodyColor: text, displayColor: text, fontFamily: family, fontFamilyFallback: const ['Cairo', 'SaudiRiyal']),
      splashFactory: InkSparkle.splashFactory,
      dividerColor: line,
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: const Color(0xFFFAF9F6),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: const BorderSide(color: line)),
        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: const BorderSide(color: line)),
        focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: const BorderSide(color: brand, width: 1.6)),
      ),
      snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating, backgroundColor: ink),
      datePickerTheme: const DatePickerThemeData(backgroundColor: Colors.white),
      bottomSheetTheme: const BottomSheetThemeData(backgroundColor: Colors.white, surfaceTintColor: Colors.white),
    );
  }
}
