// Response shapes of /api/captain-app, mirroring CaptainAppDtos on the backend.

double? _d(Object? v) => v == null ? null : (v as num).toDouble();
double _dz(Object? v) => _d(v) ?? 0;
int? _i(Object? v) => v == null ? null : (v as num).toInt();
int _iz(Object? v) => _i(v) ?? 0;
String? _s(Object? v) => v?.toString();
DateTime? _date(Object? v) => v == null ? null : DateTime.parse(v as String);
DateTime? _instant(Object? v) => v == null ? null : DateTime.parse(v as String).toUtc();
List<T> _list<T>(Object? v, T Function(Map<String, dynamic>) of) =>
    v == null ? <T>[] : (v as List).map((e) => of(e as Map<String, dynamic>)).toList();
T? _obj<T>(Object? v, T Function(Map<String, dynamic>) of) => v == null ? null : of(v as Map<String, dynamic>);

// ---- Sign-in ----

class OtpRequestResult {
  OtpRequestResult({required this.maskedPhone, required this.codeLength, required this.expiresInSeconds,
      required this.resendInSeconds, this.devCode});

  final String maskedPhone;
  final int codeLength;
  final int expiresInSeconds;
  final int resendInSeconds;
  final String? devCode;

  factory OtpRequestResult.fromJson(Map<String, dynamic> j) => OtpRequestResult(
        maskedPhone: j['maskedPhone'] as String,
        codeLength: _iz(j['codeLength']),
        expiresInSeconds: _iz(j['expiresInSeconds']),
        resendInSeconds: _iz(j['resendInSeconds']),
        devCode: _s(j['devCode']),
      );
}

class CaptainSession {
  CaptainSession({required this.token, required this.expiresIn, required this.captain});

  final String token;
  final int expiresIn;
  final Profile captain;

  factory CaptainSession.fromJson(Map<String, dynamic> j) => CaptainSession(
        token: j['token'] as String,
        expiresIn: _iz(j['expiresIn']),
        captain: Profile.fromJson(j['captain']),
      );
}

// ---- Profile ----

class Profile {
  Profile({required this.id, required this.kamalCode, this.captainType, required this.fullNameAr, this.fullNameEn,
      this.phone, this.email, this.appId, required this.supplierIds, this.supervisor, this.workLocation,
      this.bankName, this.bankAccountLast4, this.joinDate, this.status, this.tenantName});

  final String id;
  final String kamalCode;
  final String? captainType;
  final String fullNameAr;
  final String? fullNameEn;
  final String? phone;
  final String? email;
  final String? appId;
  final List<String> supplierIds;
  final String? supervisor;
  final String? workLocation;
  final String? bankName;
  final String? bankAccountLast4;
  final DateTime? joinDate;
  final String? status;
  final String? tenantName;

  factory Profile.fromJson(Map<String, dynamic> j) => Profile(
        id: j['id'] as String,
        kamalCode: j['kamalCode'] as String,
        captainType: _s(j['captainType']),
        fullNameAr: j['fullNameAr'] as String,
        fullNameEn: _s(j['fullNameEn']),
        phone: _s(j['phone']),
        email: _s(j['email']),
        appId: _s(j['appId']),
        supplierIds: ((j['supplierIds'] as List?) ?? const []).map((e) => '$e').toList(),
        supervisor: _s(j['supervisor']),
        workLocation: _s(j['workLocation']),
        bankName: _s(j['bankName']),
        bankAccountLast4: _s(j['bankAccountLast4']),
        joinDate: _date(j['joinDate']),
        status: _s(j['status']),
        tenantName: _s(j['tenantName']),
      );
}

class ProfileStats {
  ProfileStats({this.yearsOfService, this.reliabilityPercent, required this.ordersLastMonth});

  final double? yearsOfService;
  final int? reliabilityPercent;
  final int ordersLastMonth;

  factory ProfileStats.fromJson(Map<String, dynamic> j) => ProfileStats(
        yearsOfService: _d(j['yearsOfService']),
        reliabilityPercent: _i(j['reliabilityPercent']),
        ordersLastMonth: _iz(j['ordersLastMonth']),
      );
}

class CustodyItem {
  CustodyItem({required this.id, this.assetType, this.assetLabel, this.assignedAt, this.notes});

  final String id;
  final String? assetType;
  final String? assetLabel;
  final DateTime? assignedAt;
  final String? notes;

  factory CustodyItem.fromJson(Map<String, dynamic> j) => CustodyItem(
        id: j['id'] as String,
        assetType: _s(j['assetType']),
        assetLabel: _s(j['assetLabel']),
        assignedAt: _instant(j['assignedAt']),
        notes: _s(j['notes']),
      );
}

class MeResponse {
  MeResponse({required this.profile, required this.stats, required this.custody});

  final Profile profile;
  final ProfileStats stats;
  final List<CustodyItem> custody;

  factory MeResponse.fromJson(Map<String, dynamic> j) => MeResponse(
        profile: Profile.fromJson(j['profile']),
        stats: ProfileStats.fromJson(j['stats']),
        custody: _list(j['custody'], CustodyItem.fromJson),
      );
}

// ---- Shifts ----

class ShiftItem {
  ShiftItem({required this.id, required this.date, required this.state, this.supplierName, this.city, this.zoneName,
      this.shiftName, this.scheduledStart, this.scheduledEnd, this.actualCheckin, this.delayMinutes,
      this.workingHours, this.totalDelivered, this.netShiftPay, this.exclusionReason});

  final String id;
  final DateTime date;
  /// UPCOMING, LIVE, AWAITING, PRESENT, LATE, ABSENT or EXCLUDED.
  final String state;
  final String? supplierName;
  final String? city;
  final String? zoneName;
  final String? shiftName;
  final DateTime? scheduledStart;
  final DateTime? scheduledEnd;
  final DateTime? actualCheckin;
  final double? delayMinutes;
  final double? workingHours;
  final int? totalDelivered;
  final double? netShiftPay;
  final String? exclusionReason;

  factory ShiftItem.fromJson(Map<String, dynamic> j) => ShiftItem(
        id: j['id'] as String,
        date: _date(j['date'])!,
        state: j['state'] as String,
        supplierName: _s(j['supplierName']),
        city: _s(j['city']),
        zoneName: _s(j['zoneName']),
        shiftName: _s(j['shiftName']),
        scheduledStart: _instant(j['scheduledStart']),
        scheduledEnd: _instant(j['scheduledEnd']),
        actualCheckin: _instant(j['actualCheckin']),
        delayMinutes: _d(j['delayMinutes']),
        workingHours: _d(j['workingHours']),
        totalDelivered: _i(j['totalDelivered']),
        netShiftPay: _d(j['netShiftPay']),
        exclusionReason: _s(j['exclusionReason']),
      );
}

class ShiftDetail {
  ShiftDetail({required this.shift, this.extraOrders, this.shiftTypeHours, this.busyTime, this.freeTime,
      this.offlineTime, this.awayFromZone, this.busyUtilization, this.deliveryOntime, this.hourlyUtr,
      this.dailyWage, this.dailyIncentive, this.extraOrdersPay, this.fuelAllowance, this.carAllowance,
      this.lateDeduction, this.supplierDeduction, this.supplierDeductionReason, this.qualifiesFullRate,
      this.calcNote});

  final ShiftItem shift;
  final int? extraOrders;
  final double? shiftTypeHours;
  final double? busyTime;
  final double? freeTime;
  final double? offlineTime;
  final double? awayFromZone;
  final double? busyUtilization;
  final double? deliveryOntime;
  final double? hourlyUtr;
  final double? dailyWage;
  final double? dailyIncentive;
  final double? extraOrdersPay;
  final double? fuelAllowance;
  final double? carAllowance;
  final double? lateDeduction;
  final double? supplierDeduction;
  final String? supplierDeductionReason;
  final bool? qualifiesFullRate;
  final String? calcNote;

  factory ShiftDetail.fromJson(Map<String, dynamic> j) => ShiftDetail(
        shift: ShiftItem.fromJson(j['shift']),
        extraOrders: _i(j['extraOrders']),
        shiftTypeHours: _d(j['shiftTypeHours']),
        busyTime: _d(j['busyTime']),
        freeTime: _d(j['freeTime']),
        offlineTime: _d(j['offlineTime']),
        awayFromZone: _d(j['awayFromZone']),
        busyUtilization: _d(j['busyUtilization']),
        deliveryOntime: _d(j['deliveryOntime']),
        hourlyUtr: _d(j['hourlyUtr']),
        dailyWage: _d(j['dailyWage']),
        dailyIncentive: _d(j['dailyIncentive']),
        extraOrdersPay: _d(j['extraOrdersPay']),
        fuelAllowance: _d(j['fuelAllowance']),
        carAllowance: _d(j['carAllowance']),
        lateDeduction: _d(j['lateDeduction']),
        supplierDeduction: _d(j['supplierDeduction']),
        supplierDeductionReason: _s(j['supplierDeductionReason']),
        qualifiesFullRate: j['qualifiesFullRate'] as bool?,
        calcNote: _s(j['calcNote']),
      );
}

class Performance {
  Performance({required this.from, required this.to, required this.attendedShifts, required this.absentShifts,
      required this.totalOrders, this.totalHours, this.busyUtilization, this.deliveryOntime, this.ordersPerHour});

  final DateTime from;
  final DateTime to;
  final int attendedShifts;
  final int absentShifts;
  final int totalOrders;
  final double? totalHours;
  final double? busyUtilization;
  final double? deliveryOntime;
  final double? ordersPerHour;

  factory Performance.fromJson(Map<String, dynamic> j) => Performance(
        from: _date(j['from'])!,
        to: _date(j['to'])!,
        attendedShifts: _iz(j['attendedShifts']),
        absentShifts: _iz(j['absentShifts']),
        totalOrders: _iz(j['totalOrders']),
        totalHours: _d(j['totalHours']),
        busyUtilization: _d(j['busyUtilization']),
        deliveryOntime: _d(j['deliveryOntime']),
        ordersPerHour: _d(j['ordersPerHour']),
      );
}

class ShiftWeek {
  ShiftWeek({required this.from, required this.to, required this.totals, required this.shifts});

  final DateTime from;
  final DateTime to;
  final Performance totals;
  final List<ShiftItem> shifts;

  factory ShiftWeek.fromJson(Map<String, dynamic> j) => ShiftWeek(
        from: _date(j['from'])!,
        to: _date(j['to'])!,
        totals: Performance.fromJson(j['totals']),
        shifts: _list(j['shifts'], ShiftItem.fromJson),
      );
}

// ---- Earnings & payslips ----

class AdjustmentItem {
  AdjustmentItem({this.id, required this.kind, this.type, this.labelAr, required this.amount, this.reason});

  final String? id;
  /// BONUS or DEDUCTION.
  final String kind;
  final String? type;
  final String? labelAr;
  final double amount;
  final String? reason;

  factory AdjustmentItem.fromJson(Map<String, dynamic> j) => AdjustmentItem(
        id: _s(j['id']),
        kind: j['kind'] as String,
        type: _s(j['type']),
        labelAr: _s(j['labelAr']),
        amount: _dz(j['amount']),
        reason: _s(j['reason']),
      );
}

class PayBreakdown {
  PayBreakdown({required this.attendedShifts, required this.absentShifts, required this.totalOrders,
      required this.totalHours, required this.basePay, required this.dailyIncentives,
      required this.vacationCommission, required this.extraOrderPay, required this.targetBonus,
      required this.fuelAllowances, required this.carAllowances, required this.lateDeductions,
      required this.supplierDeductions, required this.adjustmentsTotal, required this.gross, required this.net,
      required this.adjustments});

  final int attendedShifts;
  final int absentShifts;
  final int totalOrders;
  final double totalHours;
  final double basePay;
  final double dailyIncentives;
  final double vacationCommission;
  final double extraOrderPay;
  final double targetBonus;
  final double fuelAllowances;
  final double carAllowances;
  final double lateDeductions;
  final double supplierDeductions;
  final double adjustmentsTotal;
  final double gross;
  final double net;
  final List<AdjustmentItem> adjustments;

  List<AdjustmentItem> get bonuses => adjustments.where((a) => a.kind == 'BONUS').toList();
  List<AdjustmentItem> get deductionAdjustments => adjustments.where((a) => a.kind != 'BONUS').toList();

  /// Late and supplier deductions plus deduction adjustments, as a positive number.
  double get totalDeductions =>
      lateDeductions + supplierDeductions + deductionAdjustments.fold(0.0, (s, a) => s + a.amount.abs());

  factory PayBreakdown.fromJson(Map<String, dynamic> j) => PayBreakdown(
        attendedShifts: _iz(j['attendedShifts']),
        absentShifts: _iz(j['absentShifts']),
        totalOrders: _iz(j['totalOrders']),
        totalHours: _dz(j['totalHours']),
        basePay: _dz(j['basePay']),
        dailyIncentives: _dz(j['dailyIncentives']),
        vacationCommission: _dz(j['vacationCommission']),
        extraOrderPay: _dz(j['extraOrderPay']),
        targetBonus: _dz(j['targetBonus']),
        fuelAllowances: _dz(j['fuelAllowances']),
        carAllowances: _dz(j['carAllowances']),
        lateDeductions: _dz(j['lateDeductions']),
        supplierDeductions: _dz(j['supplierDeductions']),
        adjustmentsTotal: _dz(j['adjustmentsTotal']),
        gross: _dz(j['gross']),
        net: _dz(j['net']),
        adjustments: _list(j['adjustments'], AdjustmentItem.fromJson),
      );
}

class EarningsPeriod {
  EarningsPeriod({required this.from, required this.to, required this.asOf, required this.status, this.payslipId,
      required this.monthlyOrderTarget, required this.pay});

  final DateTime from;
  final DateTime to;
  final DateTime asOf;
  /// ESTIMATE, DRAFT, APPROVED or PAID.
  final String status;
  final String? payslipId;
  final int monthlyOrderTarget;
  final PayBreakdown pay;

  factory EarningsPeriod.fromJson(Map<String, dynamic> j) => EarningsPeriod(
        from: _date(j['from'])!,
        to: _date(j['to'])!,
        asOf: _date(j['asOf'])!,
        status: j['status'] as String,
        payslipId: _s(j['payslipId']),
        monthlyOrderTarget: _iz(j['monthlyOrderTarget']),
        pay: PayBreakdown.fromJson(j['pay']),
      );
}

class PayslipSummary {
  PayslipSummary({required this.id, this.title, required this.periodFrom, required this.periodTo,
      required this.status, this.paidAt, required this.attendedShifts, required this.totalOrders, required this.net});

  final String id;
  final String? title;
  final DateTime periodFrom;
  final DateTime periodTo;
  final String status;
  final DateTime? paidAt;
  final int attendedShifts;
  final int totalOrders;
  final double net;

  factory PayslipSummary.fromJson(Map<String, dynamic> j) => PayslipSummary(
        id: j['id'] as String,
        title: _s(j['title']),
        periodFrom: _date(j['periodFrom'])!,
        periodTo: _date(j['periodTo'])!,
        status: j['status'] as String,
        paidAt: _instant(j['paidAt']),
        attendedShifts: _iz(j['attendedShifts']),
        totalOrders: _iz(j['totalOrders']),
        net: _dz(j['net']),
      );
}

class LoanInstallment {
  LoanInstallment({required this.loanId, required this.amount, this.number, this.count});

  final String loanId;
  final double amount;
  final int? number;
  final int? count;

  factory LoanInstallment.fromJson(Map<String, dynamic> j) => LoanInstallment(
        loanId: j['loanId'] as String,
        amount: _dz(j['amount']),
        number: _i(j['number']),
        count: _i(j['count']),
      );
}

class PayslipDetail {
  PayslipDetail({required this.summary, required this.pay, required this.loanInstallments, this.bankName,
      this.bankAccountLast4});

  final PayslipSummary summary;
  final PayBreakdown pay;
  final List<LoanInstallment> loanInstallments;
  final String? bankName;
  final String? bankAccountLast4;

  factory PayslipDetail.fromJson(Map<String, dynamic> j) => PayslipDetail(
        summary: PayslipSummary.fromJson(j['summary']),
        pay: PayBreakdown.fromJson(j['pay']),
        loanInstallments: _list(j['loanInstallments'], LoanInstallment.fromJson),
        bankName: _s(j['bankName']),
        bankAccountLast4: _s(j['bankAccountLast4']),
      );
}

// ---- Loans ----

class LoanPaymentItem {
  LoanPaymentItem({required this.id, required this.amount, required this.source, this.payrollTitle,
      this.payrollPeriodFrom, this.createdAt, this.notes});

  final String id;
  final double amount;
  /// PAYROLL_DEDUCTION or MANUAL.
  final String source;
  final String? payrollTitle;
  final DateTime? payrollPeriodFrom;
  final DateTime? createdAt;
  final String? notes;

  factory LoanPaymentItem.fromJson(Map<String, dynamic> j) => LoanPaymentItem(
        id: j['id'] as String,
        amount: _dz(j['amount']),
        source: '${j['source']}',
        payrollTitle: _s(j['payrollTitle']),
        payrollPeriodFrom: _date(j['payrollPeriodFrom']),
        createdAt: _instant(j['createdAt']),
        notes: _s(j['notes']),
      );
}

class LoanItem {
  LoanItem({required this.id, required this.status, required this.originalAmount, required this.remaining,
      required this.repaid, required this.repaidPercent, this.monthlyDeduction, this.installmentsTotal,
      this.installmentsPaid, this.installmentsLeft, this.expectedPayoff, this.createdAt, this.createdByName,
      this.notes, required this.payments});

  final String id;
  final String status;
  final double originalAmount;
  final double remaining;
  final double repaid;
  final int repaidPercent;
  final double? monthlyDeduction;
  final int? installmentsTotal;
  final int? installmentsPaid;
  final int? installmentsLeft;
  /// "2027-01"
  final String? expectedPayoff;
  final DateTime? createdAt;
  final String? createdByName;
  final String? notes;
  final List<LoanPaymentItem> payments;

  bool get isActive => status == 'ACTIVE';

  factory LoanItem.fromJson(Map<String, dynamic> j) => LoanItem(
        id: j['id'] as String,
        status: j['status'] as String,
        originalAmount: _dz(j['originalAmount']),
        remaining: _dz(j['remaining']),
        repaid: _dz(j['repaid']),
        repaidPercent: _iz(j['repaidPercent']),
        monthlyDeduction: _d(j['monthlyDeduction']),
        installmentsTotal: _i(j['installmentsTotal']),
        installmentsPaid: _i(j['installmentsPaid']),
        installmentsLeft: _i(j['installmentsLeft']),
        expectedPayoff: _s(j['expectedPayoff']),
        createdAt: _instant(j['createdAt']),
        createdByName: _s(j['createdByName']),
        notes: _s(j['notes']),
        payments: _list(j['payments'], LoanPaymentItem.fromJson),
      );
}

// ---- Documents ----

class DocumentRequestItem {
  DocumentRequestItem({required this.id, required this.kind, this.documentNumber, required this.newExpiry,
      required this.status, this.reviewNote, this.createdAt, this.reviewedAt});

  final String id;
  final String kind;
  final String? documentNumber;
  final DateTime newExpiry;
  /// PENDING, APPROVED or REJECTED.
  final String status;
  final String? reviewNote;
  final DateTime? createdAt;
  final DateTime? reviewedAt;

  factory DocumentRequestItem.fromJson(Map<String, dynamic> j) => DocumentRequestItem(
        id: j['id'] as String,
        kind: j['kind'] as String,
        documentNumber: _s(j['documentNumber']),
        newExpiry: _date(j['newExpiry'])!,
        status: j['status'] as String,
        reviewNote: _s(j['reviewNote']),
        createdAt: _instant(j['createdAt']),
        reviewedAt: _instant(j['reviewedAt']),
      );
}

class DocumentItem {
  DocumentItem({required this.kind, this.labelAr, this.numberLast4, this.expiry, required this.status,
      this.daysLeft, this.pendingRequest});

  final String kind;
  final String? labelAr;
  final String? numberLast4;
  final DateTime? expiry;
  /// VALID, EXPIRING, EXPIRED or MISSING.
  final String status;
  final int? daysLeft;
  final DocumentRequestItem? pendingRequest;

  bool get needsAction => status != 'VALID';

  factory DocumentItem.fromJson(Map<String, dynamic> j) => DocumentItem(
        kind: j['kind'] as String,
        labelAr: _s(j['labelAr']),
        numberLast4: _s(j['numberLast4']),
        expiry: _date(j['expiry']),
        status: j['status'] as String,
        daysLeft: _i(j['daysLeft']),
        pendingRequest: _obj(j['pendingRequest'], DocumentRequestItem.fromJson),
      );
}

class DocumentsResponse {
  DocumentsResponse({required this.worstStatus, required this.alertCount, required this.documents,
      required this.requests});

  final String? worstStatus;
  final int alertCount;
  final List<DocumentItem> documents;
  final List<DocumentRequestItem> requests;

  factory DocumentsResponse.fromJson(Map<String, dynamic> j) => DocumentsResponse(
        worstStatus: _s(j['worstStatus']),
        alertCount: _iz(j['alertCount']),
        documents: _list(j['documents'], DocumentItem.fromJson),
        requests: _list(j['requests'], DocumentRequestItem.fromJson),
      );
}

// ---- Home ----

class EarningsSnapshot {
  EarningsSnapshot({required this.from, required this.to, required this.asOf, required this.status,
      required this.net, required this.attendedShifts, required this.totalOrders});

  final DateTime from;
  final DateTime to;
  final DateTime asOf;
  final String status;
  final double net;
  final int attendedShifts;
  final int totalOrders;

  factory EarningsSnapshot.fromJson(Map<String, dynamic> j) => EarningsSnapshot(
        from: _date(j['from'])!,
        to: _date(j['to'])!,
        asOf: _date(j['asOf'])!,
        status: j['status'] as String,
        net: _dz(j['net']),
        attendedShifts: _iz(j['attendedShifts']),
        totalOrders: _iz(j['totalOrders']),
      );
}

class LoanSnapshot {
  LoanSnapshot({required this.originalAmount, required this.remaining, required this.repaid, this.monthlyDeduction});

  final double originalAmount;
  final double remaining;
  final double repaid;
  final double? monthlyDeduction;

  factory LoanSnapshot.fromJson(Map<String, dynamic> j) => LoanSnapshot(
        originalAmount: _dz(j['originalAmount']),
        remaining: _dz(j['remaining']),
        repaid: _dz(j['repaid']),
        monthlyDeduction: _d(j['monthlyDeduction']),
      );
}

class HomeResponse {
  HomeResponse({required this.fullNameAr, this.fullNameEn, required this.kamalCode, this.todayShift,
      required this.earnings, this.documentAlert, required this.week, this.loan, required this.recentShifts,
      required this.notificationCount});

  final String fullNameAr;
  final String? fullNameEn;
  final String kamalCode;
  final ShiftItem? todayShift;
  final EarningsSnapshot earnings;
  final DocumentItem? documentAlert;
  final Performance week;
  final LoanSnapshot? loan;
  final List<ShiftItem> recentShifts;
  final int notificationCount;

  factory HomeResponse.fromJson(Map<String, dynamic> j) => HomeResponse(
        fullNameAr: j['fullNameAr'] as String,
        fullNameEn: _s(j['fullNameEn']),
        kamalCode: j['kamalCode'] as String,
        todayShift: _obj(j['todayShift'], ShiftItem.fromJson),
        earnings: EarningsSnapshot.fromJson(j['earnings']),
        documentAlert: _obj(j['documentAlert'], DocumentItem.fromJson),
        week: Performance.fromJson(j['week']),
        loan: _obj(j['loan'], LoanSnapshot.fromJson),
        recentShifts: _list(j['recentShifts'], ShiftItem.fromJson),
        notificationCount: _iz(j['notificationCount']),
      );
}

// ---- Notifications ----

class NotificationItem {
  NotificationItem({required this.id, required this.type, required this.severity, required this.at,
      required this.data});

  final String id;
  /// DOCUMENT, SALARY_PAID, SHIFT_UPCOMING or DOCUMENT_REVIEWED.
  final String type;
  /// HIGH, MEDIUM or LOW.
  final String severity;
  final DateTime at;
  final Map<String, dynamic> data;

  factory NotificationItem.fromJson(Map<String, dynamic> j) => NotificationItem(
        id: j['id'] as String,
        type: j['type'] as String,
        severity: j['severity'] as String,
        at: _instant(j['at'])!,
        data: (j['data'] as Map?)?.cast<String, dynamic>() ?? const {},
      );
}
