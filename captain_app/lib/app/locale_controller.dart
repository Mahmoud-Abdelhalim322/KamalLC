import 'package:flutter/widgets.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// The app language. Arabic unless the captain picked English.
class LocaleController extends ChangeNotifier {
  static const _key = 'locale';

  Locale _locale = const Locale('ar');
  Locale get locale => _locale;
  bool get isAr => _locale.languageCode == 'ar';

  Future<void> load() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final code = prefs.getString(_key);
      if (code != null) _locale = Locale(code);
    } catch (_) {
      // Keep Arabic if preferences are unavailable.
    }
  }

  Future<void> toggle() => set(isAr ? const Locale('en') : const Locale('ar'));

  Future<void> set(Locale locale) async {
    _locale = locale;
    notifyListeners();
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString(_key, locale.languageCode);
    } catch (_) {}
  }
}
