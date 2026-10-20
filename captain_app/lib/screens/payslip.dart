import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app/session.dart';
import '../app/strings.dart';
import '../app/theme.dart';
import '../widgets/money.dart';
import '../widgets/page.dart';
import '../widgets/ui.dart';
import 'earnings.dart';

class PayslipScreen extends StatelessWidget {
  const PayslipScreen({super.key, required this.id});

  final String id;

  @override
  Widget build(BuildContext context) {
    final api = context.read<Session>().api;
    return Scaffold(
      body: Loader<PayslipDetail>(load: () => api.payslip(id), builder: (context, d, _) => _Body(d)),
    );
  }
}

class _Body extends StatelessWidget {
  const _Body(this.d);

  final PayslipDetail d;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final p = d.summary;
    final pay = d.pay;
    final m = s.monthYear(p.periodFrom.year, p.periodFrom.month);
    final paid = p.status == 'PAID';
    final earnings = [
      (s.f('basePayShifts', {'n': pay.attendedShifts}), pay.basePay),
      (s.t('dailyIncentives'), pay.dailyIncentives),
      (s.t('vacationCommission'), pay.vacationCommission),
      (s.t('targetBonus'), pay.targetBonus),
      (s.t('extraOrderPay'), pay.extraOrderPay),
      (s.t('fuelAllowances'), pay.fuelAllowances),
      (s.t('carAllowances'), pay.carAllowances),
      for (final b in pay.bonuses) (b.labelAr ?? s.t('targetBonus'), b.amount),
    ].where((x) => x.$2 != 0).toList();
    final installments = {for (final i in d.loanInstallments) i.loanId: i};
    final deductions = [
      if (pay.lateDeductions != 0) (s.t('lateDeductions'), pay.lateDeductions.abs()),
      if (pay.supplierDeductions != 0) (s.t('supplierDeductions'), pay.supplierDeductions.abs()),
      for (final a in pay.deductionAdjustments) (_deductionLabel(s, a, installments), a.amount.abs()),
    ];
    final totalEarnings = earnings.fold(0.0, (a, x) => a + x.$2);
    final bank = d.bankName ?? '';
    final last4 = d.bankAccountLast4;

    return HeroPage(
      hero: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        HeroTitle(
          leading: const BackHeroButton(),
          title: s.f('payslipOf', {'m': m}),
          subtitle: s.f('fromTo', {'a': s.dayMonth(p.periodFrom), 'b': s.dayMonth(p.periodTo)}),
        ),
        const SizedBox(height: 18),
        Align(alignment: AlignmentDirectional.centerStart, child: Chip2(s.payStatus(p.status), tone: Tone.ink, dot: true)),
        const SizedBox(height: 4),
        Text(s.t('netSalary'), style: TextStyle(fontSize: 13, color: K.ink.withValues(alpha: 0.7))),
        Align(alignment: AlignmentDirectional.centerStart, child: Money(pay.net, size: 42, weight: FontWeight.w900, color: K.ink)),
        if (last4 != null) ...[
          const SizedBox(height: 4),
          Row(children: [
            const Icon(Icons.account_balance_rounded, size: 15, color: K.ink),
            const SizedBox(width: 6),
            Expanded(
              child: Text(
                s.f(paid ? 'transferredTo' : 'payTo', {'bank': bank, 'last4': last4}) +
                    (paid && p.paidAt != null ? ' · ${s.dayMonth(S.riyadh(p.paidAt!))}' : ''),
                style: TextStyle(fontSize: 12, color: K.ink.withValues(alpha: 0.75)),
              ),
            ),
          ]),
        ],
        const SizedBox(height: 16),
        StatStrip([
          (statText('${pay.attendedShifts}'), s.t('shiftsLabel')),
          (statText('${pay.totalOrders}'), s.t('ordersLabel')),
          (statText(num1(pay.totalHours)), s.t('hourLabel')),
        ]),
      ]),
      children: [
        const SizedBox(height: 16),
        padded(_Section(
          icon: Icons.trending_up_rounded,
          tone: Tone.success,
          title: s.t('earningsTitle'),
          total: totalEarnings,
          rows: [for (final e in earnings) AmountRow(label: e.$1, value: e.$2)],
        )),
        if (deductions.isNotEmpty) ...[
          const SizedBox(height: 14),
          padded(_Section(
            icon: Icons.trending_down_rounded,
            tone: Tone.danger,
            title: s.t('deductions'),
            total: -pay.totalDeductions,
            rows: [for (final x in deductions) AmountRow(label: x.$1, value: -x.$2, color: K.danger)],
          )),
        ],
        const SizedBox(height: 14),
        padded(CardBox(
          child: Column(children: [
            AmountRow(label: s.t('gross'), value: totalEarnings),
            AmountRow(label: s.t('deductions'), value: -pay.totalDeductions, color: K.danger),
            const SizedBox(height: 10),
            TotalBar(label: s.t('netSalary'), value: pay.net),
          ]),
        )),
      ],
    );
  }

  String _deductionLabel(S s, AdjustmentItem a, Map<String, LoanInstallment> installments) {
    if (a.type == 'LOAN') {
      final i = installments.values.firstOrNull;
      if (i?.number != null && i?.count != null) return s.f('loanInstallment', {'a': i!.number, 'b': i.count});
    }
    return adjustmentLabel(s, a);
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.icon, required this.tone, required this.title, required this.total, required this.rows});

  final IconData icon;
  final Tone tone;
  final String title;
  final double total;
  final List<Widget> rows;

  @override
  Widget build(BuildContext context) {
    return CardBox(
      child: Column(children: [
        Row(children: [
          IconTile(icon, tone: tone, size: 38),
          const SizedBox(width: 10),
          Expanded(child: Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800))),
          Money(total, size: 17, weight: FontWeight.w900, color: tone.fg),
        ]),
        const SizedBox(height: 8),
        const Divider(color: K.line),
        ...rows,
      ]),
    );
  }
}
