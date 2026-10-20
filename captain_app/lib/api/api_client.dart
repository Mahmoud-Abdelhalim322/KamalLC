import 'package:dio/dio.dart';

import 'models.dart';

/// The backend's address, set at build time:
/// `flutter run --dart-define=API_BASE=https://api.example.com`.
/// An Android emulator reaches the host machine at http://10.0.2.2:8082.
const apiBase = String.fromEnvironment('API_BASE', defaultValue: 'http://localhost:8082');

/// A failed call, with the backend's error code and (Arabic) message.
class ApiException implements Exception {
  ApiException(this.code, this.message, {this.status});

  final String code;
  final String message;
  final int? status;

  bool get isUnauthorized => status == 401;
  bool get isNetwork => code == 'NETWORK';

  @override
  String toString() => '$code: $message';
}

/// The captain app API (`/api/captain-app`). Every call unwraps `ApiResponse.data`.
class ApiClient {
  ApiClient({required this.tokenOf, required this.onUnauthorized})
      : _dio = Dio(BaseOptions(
          baseUrl: '$apiBase/api/captain-app',
          connectTimeout: const Duration(seconds: 15),
          receiveTimeout: const Duration(seconds: 30),
        )) {
    _dio.interceptors.add(InterceptorsWrapper(
      onRequest: (options, handler) {
        final token = tokenOf();
        if (token != null) options.headers['Authorization'] = 'Bearer $token';
        handler.next(options);
      },
    ));
  }

  final Dio _dio;
  final String? Function() tokenOf;
  final void Function() onUnauthorized;

  // ---- Sign-in ----

  Future<OtpRequestResult> requestOtp(String phone) async =>
      OtpRequestResult.fromJson(await _send('POST', '/auth/otp/request', data: {'phone': phone}));

  Future<CaptainSession> verifyOtp(String phone, String code) async =>
      CaptainSession.fromJson(await _send('POST', '/auth/otp/verify', data: {'phone': phone, 'code': code}));

  Future<CaptainSession> refresh() async => CaptainSession.fromJson(await _send('POST', '/auth/refresh'));

  // ---- Captain data ----

  Future<MeResponse> me() async => MeResponse.fromJson(await _send('GET', '/me'));

  Future<HomeResponse> home() async => HomeResponse.fromJson(await _send('GET', '/home'));

  Future<ShiftWeek> shifts(DateTime from, DateTime to) async => ShiftWeek.fromJson(
      await _send('GET', '/shifts', query: {'from': isoDate(from), 'to': isoDate(to)}));

  Future<ShiftDetail> shift(String id) async => ShiftDetail.fromJson(await _send('GET', '/shifts/$id'));

  Future<EarningsPeriod> earnings(int year, int month) async => EarningsPeriod.fromJson(
      await _send('GET', '/earnings', query: {'month': '$year-${month.toString().padLeft(2, '0')}'}));

  Future<List<PayslipSummary>> payslips() async =>
      (await _send('GET', '/payslips') as List).map((e) => PayslipSummary.fromJson(e)).toList();

  Future<PayslipDetail> payslip(String id) async => PayslipDetail.fromJson(await _send('GET', '/payslips/$id'));

  Future<List<LoanItem>> loans() async =>
      (await _send('GET', '/loans') as List).map((e) => LoanItem.fromJson(e)).toList();

  Future<DocumentsResponse> documents() async => DocumentsResponse.fromJson(await _send('GET', '/documents'));

  Future<DocumentRequestItem> submitDocument({
    required String kind,
    String? number,
    required DateTime newExpiry,
    required UploadFile front,
    UploadFile? back,
  }) async {
    final form = FormData.fromMap({
      'kind': kind,
      if (number != null && number.trim().isNotEmpty) 'number': number.trim(),
      'newExpiry': isoDate(newExpiry),
      'front': MultipartFile.fromBytes(front.bytes, filename: front.name),
      if (back != null) 'back': MultipartFile.fromBytes(back.bytes, filename: back.name),
    });
    return DocumentRequestItem.fromJson(await _send('POST', '/documents/requests', data: form));
  }

  Future<List<NotificationItem>> notifications() async =>
      (await _send('GET', '/notifications') as List).map((e) => NotificationItem.fromJson(e)).toList();

  Future<void> registerDevice(String token, String platform, String locale, String appVersion) => _send(
      'POST', '/devices', data: {'token': token, 'platform': platform, 'locale': locale, 'appVersion': appVersion});

  Future<void> unregisterDevice(String token) => _send('DELETE', '/devices/${Uri.encodeComponent(token)}');

  Future<dynamic> _send(String method, String path, {Object? data, Map<String, dynamic>? query}) async {
    try {
      final res = await _dio.request<dynamic>(path,
          data: data, queryParameters: query, options: Options(method: method));
      final body = res.data;
      return body is Map ? body['data'] : body;
    } on DioException catch (e) {
      final status = e.response?.statusCode;
      final body = e.response?.data;
      if (status == 401) {
        onUnauthorized();
      }
      if (body is Map && body['errorCode'] != null) {
        throw ApiException('${body['errorCode']}', '${body['message'] ?? ''}', status: status);
      }
      if (e.response == null) {
        throw ApiException('NETWORK', '', status: null);
      }
      throw ApiException('HTTP_$status', body is Map ? '${body['message'] ?? ''}' : '', status: status);
    }
  }
}

class UploadFile {
  UploadFile(this.name, this.bytes);

  final String name;
  final List<int> bytes;
}

String isoDate(DateTime d) =>
    '${d.year}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
