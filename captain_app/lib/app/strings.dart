import 'package:flutter/widgets.dart';
import 'package:provider/provider.dart';

import 'locale_controller.dart';

/// Arabic and English wording. The API returns codes and raw numbers, so all user
/// text lives here.
class S {
  S(this.locale);

  final Locale locale;

  static S of(BuildContext context) => S(context.watch<LocaleController>().locale);
  static S read(BuildContext context) => S(context.read<LocaleController>().locale);

  bool get isAr => locale.languageCode == 'ar';

  String t(String key) {
    final pair = _t[key];
    if (pair == null) return key;
    return isAr ? pair[0] : pair[1];
  }

  /// [t] with `{name}` placeholders filled in.
  String f(String key, Map<String, Object?> args) {
    var s = t(key);
    args.forEach((k, v) => s = s.replaceAll('{$k}', '${v ?? ''}'));
    return s;
  }

  // ---- Dates (Latin digits in both languages, as in the designs) ----

  static const _monthsAr = ['يناير', 'فبراير', 'مارس', 'أبريل', 'مايو', 'يونيو', 'يوليو', 'أغسطس', 'سبتمبر', 'أكتوبر', 'نوفمبر', 'ديسمبر'];
  static const _monthsEn = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];
  static const _daysAr = ['الإثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت', 'الأحد'];
  static const _daysEn = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
  static const _daysShortAr = ['إثنين', 'ثلاثاء', 'أربعاء', 'خميس', 'جمعة', 'سبت', 'أحد'];
  static const _daysShortEn = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

  String month(int m) => (isAr ? _monthsAr : _monthsEn)[m - 1];
  String monthShort(int m) => isAr ? _monthsAr[m - 1] : _monthsEn[m - 1].substring(0, 3);
  String weekday(DateTime d) => (isAr ? _daysAr : _daysEn)[d.weekday - 1];
  String weekdayShort(DateTime d) => (isAr ? _daysShortAr : _daysShortEn)[d.weekday - 1];

  /// 20 October
  String dayMonth(DateTime d) => '${d.day} ${month(d.month)}';

  /// Tuesday 20 October
  String fullDay(DateTime d) => '${weekday(d)} ${dayMonth(d)}';

  /// October 2026
  String monthYear(int year, int m) => '${month(m)} $year';

  /// 20/10/2026
  String numericDate(DateTime d) => '${_pad2(d.day)}/${_pad2(d.month)}/${d.year}';

  static String _pad2(int n) => n.toString().padLeft(2, '0');

  /// 4:00 م / 4:00 PM, in Riyadh time.
  String time(DateTime d) {
    final r = riyadh(d);
    final h = r.hour % 12 == 0 ? 12 : r.hour % 12;
    final mm = r.minute.toString().padLeft(2, '0');
    final suffix = r.hour < 12 ? (isAr ? 'ص' : 'AM') : (isAr ? 'م' : 'PM');
    return '$h:$mm $suffix';
  }

  String timeRange(DateTime? a, DateTime? b) {
    if (a == null || b == null) return '—';
    return '${time(a)} – ${time(b)}';
  }

  /// 1:48 h / 1:48 س
  String duration(Duration d) {
    final h = d.inHours;
    final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
    return '$h:$m ${isAr ? 'س' : 'h'}';
  }

  /// Hours as h:mm (2.48 → 2:29 h).
  String hours(num? h) => h == null ? '—' : duration(Duration(minutes: (h * 60).round()));

  String greeting() {
    final h = riyadh(DateTime.now()).hour;
    if (h < 12) return t('goodMorning');
    if (h < 18) return t('goodAfternoon');
    return t('goodEvening');
  }

  /// The captain's name in the app language, falling back to Arabic.
  String name(String? ar, String? en) {
    if (!isAr && en != null && en.trim().isNotEmpty) return en;
    return ar ?? en ?? '';
  }

  /// Riyadh is UTC+3 all year, with no daylight saving.
  static DateTime riyadh(DateTime d) {
    final u = d.toUtc().add(const Duration(hours: 3));
    return DateTime(u.year, u.month, u.day, u.hour, u.minute, u.second);
  }

  String docKind(String kind) => t('doc_$kind');
  String shiftState(String state) => t('state_$state');

  static const Map<String, List<String>> _t = {
    'appName': ['كمال كابتن', 'Kamal Captain'],
    'appTagline': ['شريكك في كل توصيلة', 'Your partner on every delivery'],
    'company': ['كمال بن خلف السلمي للخدمات اللوجستية', 'Kamal bin Khalaf Al-Sulami Logistics'],
    'version': ['الإصدار', 'Version'],
    'captainApp': ['تطبيق الكابتن', 'Captain app'],
    'kamal': ['كمال', 'Kamal'],

    // Login
    'welcome': ['أهلاً بك يا كابتن', 'Welcome, Captain'],
    'welcomeSub': ['سجّل دخولك برقم جوالك المسجل لدى الشركة', 'Sign in with the mobile number the company has on file'],
    'phone': ['رقم الجوال', 'Mobile number'],
    'phoneHint': ['05X XXX XXXX', '05X XXX XXXX'],
    'editNumber': ['تعديل الرقم', 'Change'],
    'sendCode': ['أرسل رمز التحقق', 'Send code'],
    'otp': ['رمز التحقق', 'Verification code'],
    'otpSentTo': ['أرسلنا الرمز إلى {phone}', 'We sent a code to {phone}'],
    'resendIn': ['إعادة الإرسال خلال', 'Resend in'],
    'resend': ['إعادة إرسال الرمز', 'Resend code'],
    'verify': ['تحقق ودخول', 'Verify & sign in'],
    'trouble': ['تواجه مشكلة؟', 'Having trouble?'],
    'contactSupervisor': ['تواصل مع مشرفك', 'Contact your supervisor'],
    'troubleBody': [
      'إذا لم يصلك الرمز أو كان رقمك غير مسجل، تواصل مع مشرفك أو الموارد البشرية لتحديث رقم جوالك في ملفك.',
      'If the code does not arrive or your number is not registered, ask your supervisor or HR to update the mobile number in your file.'
    ],
    'devCode': ['رمز التطوير', 'Dev code'],
    'ok': ['حسناً', 'OK'],

    // Common
    'retry': ['إعادة المحاولة', 'Retry'],
    'loadFailed': ['تعذر تحميل البيانات', 'Could not load data'],
    'offline': ['تعذر الاتصال بالخادم. تحقق من اتصالك بالإنترنت', 'Cannot reach the server. Check your connection'],
    'sessionEnded': ['انتهت الجلسة. سجّل دخولك مجدداً', 'Your session ended. Please sign in again'],
    'viewAll': ['عرض الكل', 'View all'],
    'details': ['التفاصيل', 'Details'],
    'cancel': ['إلغاء', 'Cancel'],
    'close': ['إغلاق', 'Close'],
    'shifts': ['شفت', 'shifts'],
    'shiftsLabel': ['شفتات', 'Shifts'],
    'orders': ['طلب', 'orders'],
    'ordersLabel': ['طلبات', 'Orders'],
    'hoursShort': ['س', 'h'],
    'hoursLabel': ['ساعات العمل', 'Hours'],
    'hourLabel': ['ساعة', 'Hours'],
    'onTime': ['في الوقت', 'On time'],
    'nothingYet': ['لا يوجد شيء بعد', 'Nothing here yet'],
    'goodMorning': ['صباح الخير', 'Good morning'],
    'goodAfternoon': ['مساء الخير', 'Good afternoon'],
    'goodEvening': ['مساء الخير', 'Good evening'],

    // Nav
    'navHome': ['الرئيسية', 'Home'],
    'navShifts': ['شفتاتي', 'Shifts'],
    'navEarnings': ['أرباحي', 'Earnings'],
    'navDocuments': ['مستنداتي', 'Documents'],
    'navProfile': ['حسابي', 'Profile'],

    // Home
    'earningsThisMonth': ['أرباحك هذا الشهر', 'Your earnings this month'],
    'netUntil': ['صافي حتى {date} · يُعتمد عند صدور المسير', 'Net up to {date} · final once payroll is issued'],
    'netFinal': ['صافي الشهر المعتمد', 'Approved net for the month'],
    'todayIs': ['اليوم {date}', 'Today {date}'],
    'todayShift': ['شفت اليوم', 'Today\'s shift'],
    'noShiftToday': ['لا يوجد شفت اليوم', 'No shift today'],
    'noShiftTodaySub': ['استمتع بيومك، سنعرض شفتك القادم هنا', 'Enjoy your day — your next shift will show here'],
    'remaining': ['متبقي {d}', '{d} left'],
    'startsIn': ['يبدأ خلال {d}', 'Starts in {d}'],
    'checkinAt': ['حضور {t}', 'Checked in {t}'],
    'ordersSoFar': ['{n} طلبات', '{n} orders'],
    'zone': ['منطقة {z}', '{z} zone'],
    'docExpiresIn': ['{doc} تنتهي خلال {n} يوم', '{doc} expires in {n} days'],
    'docExpired': ['{doc} منتهية', '{doc} has expired'],
    'docMissing': ['{doc} غير مسجلة', '{doc} is missing'],
    'docAlertSub': ['ارفع المستند المجدد لتجنب إيقاف الحساب', 'Upload the renewed document to avoid suspension'],
    'docPendingSub': ['طلب التحديث قيد المراجعة لدى المشرف', 'Your update is being reviewed by your supervisor'],
    'update': ['تحديث', 'Update'],
    'weekPerformance': ['أداء هذا الأسبوع', 'This week\'s performance'],
    'onTimeDelivery': ['التسليم في الوقت', 'On-time delivery'],
    'utilization': ['نسبة الانشغال', 'Utilization'],
    'ordersPerHour': ['طلبات / ساعة', 'Orders / hour'],
    'loan': ['السلفة', 'Loan'],
    'monthlyInstallment': ['قسط شهري {a}', 'Monthly {a}'],
    'remainingLabel': ['متبقي', 'Remaining'],
    'repaidOf': ['سدد {a} من {b}', 'Repaid {a} of {b}'],
    'recentShifts': ['آخر الشفتات', 'Recent shifts'],
    'today': ['اليوم', 'Today'],
    'yesterday': ['أمس', 'Yesterday'],
    'notifications': ['الإشعارات', 'Notifications'],

    // Shift states
    'state_UPCOMING': ['قادم', 'Upcoming'],
    'state_LIVE': ['جارٍ الآن', 'Live now'],
    'state_AWAITING': ['بانتظار الاعتماد', 'Awaiting'],
    'state_PRESENT': ['حضر', 'Present'],
    'state_LATE': ['متأخر', 'Late'],
    'state_ABSENT': ['غياب', 'Absent'],
    'state_EXCLUDED': ['مستبعد', 'Excluded'],
    'lateBy': ['متأخر {n} د', 'Late {n} min'],
    'onTimeChip': ['في الموعد', 'On time'],
    'noCheckin': ['لم تسجل الحضور', 'No check-in recorded'],
    'awaitingSub': ['تظهر النتائج بعد استيراد بيانات المورد', 'Results appear once the supplier data is imported'],

    // Shifts
    'myShifts': ['شفتاتي', 'My shifts'],
    'attended': ['حضور', 'Present'],
    'absences': ['غياب', 'Absent'],
    'workHours': ['ساعة عمل', 'Work hours'],
    'filterAll': ['الكل', 'All'],
    'filterPresent': ['حضور', 'Present'],
    'filterAbsent': ['غياب', 'Absent'],
    'filterExcluded': ['مستبعد', 'Excluded'],
    'thisWeek': ['هذا الأسبوع', 'This week'],
    'weekOf': ['الأسبوع', 'Week'],
    'noShiftsDay': ['لا توجد شفتات في هذا اليوم', 'No shifts on this day'],
    'noShiftsWeek': ['لا توجد شفتات في هذا الأسبوع', 'No shifts this week'],

    // Shift detail
    'shiftDetails': ['تفاصيل الشيفت', 'Shift details'],
    'shiftNet': ['صافي الشيفت', 'Shift net'],
    'hours': ['الساعات', 'Hours'],
    'checkin': ['الحضور', 'Check-in'],
    'schedule': ['المجدول', 'Scheduled'],
    'performance': ['الأداء', 'Performance'],
    'deliveredOrders': ['الطلبات المسلّمة', 'Delivered orders'],
    'extra': ['+{n} إضافية', '+{n} extra'],
    'timeSplit': ['توزيع الوقت', 'Time split'],
    'busy': ['مشغول (توصيل)', 'Busy (delivering)'],
    'free': ['متاح', 'Available'],
    'offlineTime': ['غير متصل', 'Offline'],
    'awayFromZone': ['خارج المنطقة', 'Outside zone'],
    'payDetails': ['تفاصيل الأجر', 'Pay details'],
    'dailyWage': ['الأجر اليومي', 'Daily wage'],
    'dailyIncentive': ['الحافز اليومي', 'Daily incentive'],
    'extraOrdersPay': ['طلبات إضافية ({n})', 'Extra orders ({n})'],
    'fuelAllowance': ['بدل الوقود', 'Fuel allowance'],
    'carAllowance': ['بدل السيارة', 'Car allowance'],
    'lateDeduction': ['خصم التأخير', 'Late deduction'],
    'supplierDeduction': ['خصم المورد', 'Supplier deduction'],
    'supplierReason': ['سبب خصم المورد: {r}', 'Supplier deduction reason: {r}'],
    'qualifiesFull': ['مؤهل للأجر الكامل — حققت الحد الأدنى من الطلبات والساعات', 'Qualifies for full pay — you met the minimum orders and hours'],
    'notQualifiesFull': ['لم يتحقق الحد الأدنى للأجر الكامل', 'Minimum for full pay was not met'],
    'excludedReason': ['سبب الاستبعاد: {r}', 'Excluded: {r}'],
    'objection': ['لديك اعتراض؟', 'Disagree with something?'],
    'contactSupervisorBtn': ['تواصل مع المشرف', 'Contact supervisor'],
    'supervisorSheetBody': [
      'مشرفك {name} هو من يراجع الشفتات والخصومات. تواصل معه مباشرة واذكر رقم الشيفت أو الشهر.',
      'Your supervisor {name} reviews shifts and deductions. Reach out directly and mention the shift or month.'
    ],
    'supervisorUnknown': ['مشرفك', 'your supervisor'],
    'shiftRef': ['رقم الشيفت', 'Shift reference'],

    // Earnings
    'myEarnings': ['أرباحي', 'My earnings'],
    'salariesAndDues': ['الرواتب والمستحقات', 'Salary & dues'],
    'periodCurrent': ['الفترة الحالية · {a} – {b}', 'Current period · {a} – {b}'],
    'period': ['الفترة · {a} – {b}', 'Period · {a} – {b}'],
    'status_ESTIMATE': ['تقديري', 'Estimate'],
    'status_ESTIMATE_long': ['قيد الاحتساب', 'Being calculated'],
    'status_DRAFT': ['قيد المراجعة', 'In review'],
    'status_APPROVED': ['معتمد', 'Approved'],
    'status_PAID': ['مدفوع', 'Paid'],
    'gross': ['الإجمالي', 'Gross'],
    'deductions': ['الخصومات', 'Deductions'],
    'incomeSources': ['مصادر الدخل', 'Income sources'],
    'basePay': ['الأجر الأساسي', 'Base pay'],
    'dailyIncentives': ['الحوافز اليومية', 'Daily incentives'],
    'vacationCommission': ['عمولة الإجازة', 'Leave commission'],
    'extraOrderPay': ['الطلبات الإضافية', 'Extra orders'],
    'targetBonus': ['مكافأة الهدف', 'Target bonus'],
    'fuelAllowances': ['بدل الوقود', 'Fuel allowance'],
    'carAllowances': ['بدل السيارة', 'Car allowance'],
    'lateDeductions': ['خصم التأخير', 'Late deductions'],
    'supplierDeductions': ['خصومات المورد', 'Supplier deductions'],
    'previousPayslips': ['القسائم السابقة', 'Previous payslips'],
    'noPayslips': ['ستظهر قسائم الرواتب هنا بعد صرف أول راتب', 'Payslips appear here after your first salary is paid'],
    'monthlyTarget': ['هدف الطلبات الشهري', 'Monthly order target'],
    'targetProgress': ['{a} من {b} طلب', '{a} of {b} orders'],
    'shiftsOrders': ['{s} شفت · {o} طلب', '{s} shifts · {o} orders'],
    'viewPayslip': ['عرض القسيمة', 'View payslip'],
    'noEarnings': ['لا توجد أرباح في هذا الشهر', 'No earnings this month'],

    // Payslip
    'payslipOf': ['قسيمة {m}', 'Payslip · {m}'],
    'fromTo': ['من {a} إلى {b}', '{a} to {b}'],
    'netSalary': ['صافي الراتب', 'Net salary'],
    'transferredTo': ['حُوّل إلى {bank} •••• {last4}', 'Paid to {bank} •••• {last4}'],
    'payTo': ['يُحوّل إلى {bank} •••• {last4}', 'Will be paid to {bank} •••• {last4}'],
    'earningsTitle': ['المستحقات', 'Earnings'],
    'basePayShifts': ['الأجر الأساسي ({n} شفت)', 'Base pay ({n} shifts)'],
    'loanInstallment': ['قسط السلفة ({a} من {b})', 'Loan installment ({a} of {b})'],
    'loanInstallmentPlain': ['قسط السلفة', 'Loan installment'],

    // Loan
    'myLoan': ['سلفتي', 'My loan'],
    'loanSub': ['تُخصم تلقائياً من راتبك الشهري', 'Deducted automatically from your salary'],
    'loanRemaining': ['المتبقي من السلفة', 'Remaining balance'],
    'ofOriginal': ['من أصل {a}', 'of {a}'],
    'paidPercent': ['سدد {a} · {p}%', 'Paid {a} · {p}%'],
    'installmentsLeft': ['{n} أقساط متبقية', '{n} installments left'],
    'expectedPayoff': ['السداد المتوقع', 'Expected payoff'],
    'monthlyDeduction': ['الخصم الشهري', 'Monthly deduction'],
    'repaymentLog': ['سجل السداد', 'Repayment history'],
    'upcoming': ['قادم', 'Upcoming'],
    'done': ['تم', 'Done'],
    'disbursed': ['صرف', 'Paid out'],
    'deductedFrom': ['يُخصم من مسير {m}', 'Deducted from {m} payroll'],
    'deductedFromPast': ['خُصم من مسير {m}', 'Deducted from {m} payroll'],
    'manualPayment': ['سداد يدوي', 'Manual payment'],
    'loanDisbursed': ['صرف السلفة · {by}', 'Loan paid out · {by}'],
    'loanHelp': ['لطلب سلفة جديدة أو تعديل الخصم الشهري تواصل مع مشرفك', 'To request a new loan or change the monthly deduction, contact your supervisor'],
    'noLoan': ['لا توجد سلف نشطة', 'You have no active loans'],
    'loan_ACTIVE': ['نشطة', 'Active'],
    'loan_PAID_OFF': ['مسددة', 'Paid off'],
    'loan_CANCELLED': ['ملغاة', 'Cancelled'],
    'otherLoans': ['سلف سابقة', 'Previous loans'],

    // Documents
    'myDocuments': ['مستنداتي', 'My documents'],
    'documentsSub': ['حافظ على مستنداتك سارية لتستمر في العمل', 'Keep your documents valid to keep working'],
    'expired': ['منتهية', 'Expired'],
    'expiringSoon': ['تنتهي قريباً', 'Expiring soon'],
    'valid': ['سارية', 'Valid'],
    'missing': ['غير مسجلة', 'Missing'],
    'needsAction': ['تحتاج إجراء', 'Needs action'],
    'expiredOn': ['انتهى {d}', 'Expired {d}'],
    'expiresOn': ['تنتهي {d}', 'Expires {d}'],
    'daysAgo': ['منذ {n} يوم', '{n} days ago'],
    'daysLeft': ['باقي {n} يوم', '{n} days left'],
    'uploadRenewed': ['رفع {doc} المجددة', 'Upload renewed {doc}'],
    'underReview': ['قيد المراجعة', 'Under review'],
    'underReviewSub': ['أرسلت {doc} بانتهاء {d} — بانتظار موافقة المشرف', 'You sent {doc} expiring {d} — waiting for approval'],
    'numberEnding': ['رقم •••• {n}', 'No. •••• {n}'],
    'myRequests': ['طلباتي', 'My requests'],
    'req_PENDING': ['قيد المراجعة', 'Pending'],
    'req_APPROVED': ['مقبول', 'Approved'],
    'req_REJECTED': ['مرفوض', 'Rejected'],
    'rejectNote': ['سبب الرفض: {n}', 'Reason: {n}'],
    'reviewNote': ['ملاحظة المشرف: {n}', 'Supervisor note: {n}'],
    'newExpiry': ['الانتهاء الجديد {d}', 'New expiry {d}'],
    'pickDocument': ['أي مستند تريد تحديثه؟', 'Which document are you updating?'],

    'doc_IQAMA': ['الإقامة', 'Iqama'],
    'doc_WORK_PERMIT': ['تصريح العمل', 'Work permit'],
    'doc_INSURANCE': ['التأمين الطبي', 'Medical insurance'],
    'doc_PASSPORT': ['جواز السفر', 'Passport'],
    'doc_DRIVING_LICENCE': ['رخصة القيادة', 'Driving licence'],

    // Upload
    'updateDoc': ['تحديث {doc}', 'Update {doc}'],
    'updateDocSub': ['صوّر {doc} المجددة بوضوح من الوجهين', 'Take clear photos of both sides of the renewed {doc}'],
    'front': ['الوجه الأمامي', 'Front side'],
    'back': ['الوجه الخلفي', 'Back side'],
    'tapToPick': ['التقط أو اختر صورة', 'Take or choose a photo'],
    'optional': ['اختياري', 'Optional'],
    'docNumber': ['رقم {doc}', '{doc} number'],
    'newExpiryLabel': ['تاريخ الانتهاء الجديد', 'New expiry date'],
    'pickDate': ['اختر التاريخ', 'Choose a date'],
    'reviewNotice': ['سيراجع المشرف المستند خلال 24 ساعة وتصلك رسالة بالنتيجة', 'Your supervisor reviews it within 24 hours and you will be notified'],
    'sendForReview': ['إرسال للمراجعة', 'Send for review'],
    'camera': ['الكاميرا', 'Camera'],
    'gallery': ['معرض الصور', 'Photo library'],
    'sentForReview': ['تم إرسال المستند للمراجعة', 'Document sent for review'],
    'frontRequired': ['أضف صورة الوجه الأمامي', 'Add the front photo'],
    'expiryRequired': ['اختر تاريخ الانتهاء الجديد', 'Choose the new expiry date'],

    // Profile
    'myAccount': ['حسابي', 'My account'],
    'active': ['نشط', 'Active'],
    'employee': ['موظف', 'Employee'],
    'freelancer': ['مستقل', 'Freelancer'],
    'ordersLastMonth': ['طلب الشهر الماضي', 'Orders last month'],
    'reliability': ['الالتزام', 'Reliability'],
    'yearsOfService': ['سنة مدة العمل', 'Years of service'],
    'myInfo': ['معلوماتي', 'My info'],
    'appId': ['رقم تطبيق المورد', 'Supplier app ID'],
    'supervisor': ['المشرف', 'Supervisor'],
    'workLocation': ['موقع العمل', 'Work location'],
    'salaryAccount': ['حساب الراتب', 'Salary account'],
    'custody': ['العهد المستلمة', 'Items in my custody'],
    'inYourCustody': ['بعهدتك', 'With you'],
    'since': ['منذ {d}', 'Since {d}'],
    'language': ['اللغة', 'Language'],
    'otherLanguage': ['English', 'العربية'],
    'help': ['المساعدة والدعم', 'Help & support'],
    'helpBody': [
      'لأي استفسار عن الشفتات أو الرواتب أو المستندات تواصل مع مشرفك. لتحديث بياناتك الشخصية تواصل مع الموارد البشرية.',
      'For questions about shifts, pay or documents, contact your supervisor. To update personal details, contact HR.'
    ],
    'logout': ['تسجيل الخروج', 'Sign out'],
    'logoutConfirm': ['هل تريد تسجيل الخروج؟', 'Sign out of the app?'],
    'notSet': ['غير مسجل', 'Not set'],

    // Notifications
    'noNotifications': ['لا توجد إشعارات حالياً', 'No notifications right now'],
    'n_salaryPaid': ['تم صرف راتبك', 'Your salary was paid'],
    'n_salaryPaidBody': ['صافي الراتب {a}', 'Net salary {a}'],
    'n_shiftUpcoming': ['شفت قادم {t}', 'Upcoming shift at {t}'],
    'n_shiftUpcomingBody': ['منطقة {zone}', '{zone} zone'],
    'n_docApproved': ['تم قبول تحديث {doc}', '{doc} update approved'],
    'n_docRejected': ['تم رفض تحديث {doc}', '{doc} update rejected'],
  };
}

extension StatusLabel on S {
  String payStatus(String status) => t('status_$status');
  String requestStatus(String status) => t('req_$status');
  String loanStatus(String status) => t('loan_$status');
}
