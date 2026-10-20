import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import '../api/api_client.dart';
import '../api/models.dart';

enum SessionStatus { restoring, signedOut, signedIn }

/// Who is signed in. The token lives in secure storage and is refreshed on every
/// launch, so a captain who opens the app at least once a month never signs out.
class Session extends ChangeNotifier {
  Session() {
    api = ApiClient(tokenOf: () => _token, onUnauthorized: _expire);
  }

  static const _tokenKey = 'captain_token';
  static const _storage = FlutterSecureStorage();

  late final ApiClient api;

  SessionStatus _status = SessionStatus.restoring;
  SessionStatus get status => _status;

  String? _token;
  Profile? _profile;
  Profile? get profile => _profile;

  /// Set when the backend rejected the token, so the login screen can say why.
  bool expired = false;

  Future<void> restore() async {
    try {
      _token = await _storage.read(key: _tokenKey);
    } catch (_) {
      _token = null;
    }
    if (_token == null) {
      _set(SessionStatus.signedOut);
      return;
    }
    try {
      await _accept(await api.refresh());
    } on ApiException catch (e) {
      // Offline at launch: keep the stored token and let screens retry.
      if (e.isNetwork) {
        _set(SessionStatus.signedIn);
      } else {
        await signOut();
      }
    }
  }

  Future<void> signIn(String phone, String code) async {
    await _accept(await api.verifyOtp(phone, code));
  }

  Future<void> signOut() async {
    _token = null;
    _profile = null;
    try {
      await _storage.delete(key: _tokenKey);
    } catch (_) {}
    _set(SessionStatus.signedOut);
  }

  /// Keeps the cached profile in step after the profile screen reloads it.
  void updateProfile(Profile profile) {
    _profile = profile;
    notifyListeners();
  }

  Future<void> _accept(CaptainSession session) async {
    _token = session.token;
    _profile = session.captain;
    expired = false;
    try {
      await _storage.write(key: _tokenKey, value: session.token);
    } catch (_) {}
    _set(SessionStatus.signedIn);
  }

  void _expire() {
    if (_status != SessionStatus.signedIn) return;
    expired = true;
    signOut();
  }

  void _set(SessionStatus status) {
    _status = status;
    notifyListeners();
  }
}
