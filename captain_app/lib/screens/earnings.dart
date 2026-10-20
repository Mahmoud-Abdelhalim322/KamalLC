import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app/session.dart';
import '../app/strings.dart';
import '../app/theme.dart';
import '../widgets/money.dart';
import '../widgets/page.dart';
import '../widgets/ui.dart';
import 'payslip.dart';

class EarningsScreen extends StatefulWidget {
  const EarningsScreen({super.key});

  @override
  State<EarningsScreen> createState() => _EarningsScreenState();
}

class _EarningsScreenState extends State<EarningsScreen> {
  late final DateTime _now = S.riyadh(DateTime.now());
  late DateTime _month = DateTime(_now.year, _now.month);
  EarningsPeriod? _period;
  List<PayslipSummary> _payslips = const [];
  Object? _error;
  bool _loading = true;

  List<DateTime> get _months => List.generate(4, (i) => DateTime(_now.year, _now.month - 3 + i));

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final api = context.read<Session>().api;
    setState(() => _loading = true);
    try {
      final results = await Future.wait([api.earnings(_month.year, _month.month), api.payslips()]);
      if (!mounted) return;
      setState(() {
        _period = results[0] as EarningsPeriod;
        _payslips = results[1] as List<PayslipSummary>;
        _error = null;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  void _select(DateTime m) {
    if (m == _month) return;
    setState(() => _month = m);
    _load();
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final p = _period;
    if (p == null) {
      if (_loading) return const Center(child: CircularProgressIndicator(color: K.brand));
      return ErrorView(error: _error, onRetry: _load);
    }
    final pay = p.pay;
    final current = _month.year == _now.year && _month.month == _now.month;
    final sources = [
      (Icons.payments_rounded, s.t('basePay'), pay.basePay),
      (Icons.bolt_rounded, s.t('dailyIncentives'), pay.dailyIncentives),
      (Icons.beach_access_rounded, s.t('vacationCommission'), pay.vacationCommission),
      (Icons.add_shopping_cart_rounded, s.t('extraOrderPay'), pay.extraOrderPay),
      (Icons.emoji_events_rounded, s.t('targetBonus'), pay.targetBonus),
      (Icons.local_gas_station_rounded, s.t('fuelAllowances'), pay.fuelAllowances),
      (Icons.directions_car_rounded, s.t('carAllowances'), pay.carAllowances),
      for (final b in pay.bonuses) (Icons.card_giftcard_rounded, b.labelAr ?? s.t('targetBonus'), b.amount),
    ].where((x) => x.$3 > 0).toList();
    final income = sources.fold(0.0, (a, x) => a + x.$3);
    final period = s.f(current ? 'periodCurrent' : 'period', {'a': s.dayMonth(p.from).split(' ').first, 'b': s.dayMonth(p.to)});

    return RefreshIndicator(
      color: K.ink,
      backgroundColor: K.brand,
      onRefresh: _load,
      child: HeroPage(
        bottom: dockClearance,
        hero: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          HeroTitle(title: s.t('myEarnings'), subtitle: s.t('salariesAndDues'), subtitleFirst: true),
          const SizedBox(height: 14),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            reverse: true,
            child: Row(children: [
              for (final m in _months)
                Padding(
                  padding: const EdgeInsetsDirectional.only(start: 8),
                  child: _MonthChip(label: s.month(m.month), selected: m == _month, onTap: () => _select(m)),
                ),
            ]),
          ),
          const SizedBox(height: 14),
          Row(children: [
            Flexible(child: Text(period, style: TextStyle(fontSize: 12.5, color: K.ink.withValues(alpha: 0.75)))),
            const SizedBox(width: 8),
            Chip2(s.t(p.status == 'ESTIMATE' ? 'status_ESTIMATE_long' : 'status_${p.status}'), tone: Tone.ink, dot: true),
          ]),
          Align(alignment: AlignmentDirectional.centerStart, child: Money(pay.net, size: 42, weight: FontWeight.w900, color: K.ink)),
          Text(
            p.status == 'ESTIMATE' ? s.f('netUntil', {'date': s.dayMonth(p.asOf)}) : s.t('netFinal'),
            style: TextStyle(fontSize: 12, color: K.ink.withValues(alpha: 0.7)),
          ),
          const SizedBox(height: 16),
          StatStrip([
            (Money(pay.gross, size: 17, weight: FontWeight.w800, color: Colors.white), s.t('gross')),
            (Money(-pay.totalDeductions, size: 17, weight: FontWeight.w800, color: const Color(0xFFFF8A80)), s.t('deductions')),
            (statText('${pay.attendedShifts}'), s.t('shiftsLabel')),
          ]),
        ]),
        children: [
          if (_loading) const LinearProgressIndicator(color: K.brand, backgroundColor: Colors.transparent, minHeight: 2),
          if (p.monthlyOrderTarget > 0) ...[
            const SizedBox(height: 16),
            padded(CardBox(
              child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Row(children: [
                  const IconTile(Icons.flag_rounded, size: 36),
                  const SizedBox(width: 10),
                  Expanded(child: Text(s.t('monthlyTarget'), style: const TextStyle(fontWeight: FontWeight.w800))),
                  Text(s.f('targetProgress', {'a': pay.totalOrders, 'b': p.monthlyOrderTarget}),
                      style: const TextStyle(fontSize: 12.5, color: K.muted, fontWeight: FontWeight.w600)),
                ]),
                const SizedBox(height: 10),
                RouteProgress(value: pay.totalOrders / p.monthlyOrderTarget, marker: Icons.emoji_events_rounded),
              ]),
            )),
          ],
          SectionTitle(s.t('incomeSources')),
          padded(sources.isEmpty
              ? EmptyCard(icon: Icons.account_balance_wallet_rounded, text: s.t('noEarnings'))
              : CardBox(
                  child: Column(children: [
                    for (var i = 0; i < sources.length; i++)
                      _SourceRow(
                        icon: sources[i].$1,
                        label: sources[i].$2,
                        amount: sources[i].$3,
                        share: income == 0 ? 0 : sources[i].$3 / income,
                        first: i == 0,
                      ),
                  ]),
                )),
          if (pay.totalDeductions > 0) ...[
            SectionTitle(s.t('deductions')),
            padded(CardBox(
              child: Column(children: [
                if (pay.lateDeductions != 0) AmountRow(label: s.t('lateDeductions'), value: -pay.lateDeductions.abs(), color: K.danger),
                if (pay.supplierDeductions != 0)
                  AmountRow(label: s.t('supplierDeductions'), value: -pay.supplierDeductions.abs(), color: K.danger),
                for (final a in pay.deductionAdjustments)
                  AmountRow(label: adjustmentLabel(s, a), value: -a.amount.abs(), color: K.danger),
              ]),
            )),
          ],
          if (p.payslipId != null) ...[
            const SizedBox(height: 14),
            padded(InkButton(
              label: s.t('viewPayslip'),
              icon: Icons.receipt_long_rounded,
              onTap: () => openPayslip(context, p.payslipId!),
            )),
          ],
          SectionTitle(s.t('previousPayslips')),
          padded(_payslips.isEmpty
              ? EmptyCard(icon: Icons.receipt_long_rounded, text: s.t('noPayslips'))
              : CardBox(
                  padding: EdgeInsets.zero,
                  child: Column(children: [
                    for (var i = 0; i < _payslips.length; i++) ...[
                      if (i > 0) const Divider(height: 1, color: K.line),
                      _PayslipRow(_payslips[i]),
                    ],
                  ]),
                )),
        ],
      ),
    );
  }
}

String adjustmentLabel(S s, AdjustmentItem a) {
  if (a.type == 'LOAN') return s.t('loanInstallmentPlain');
  return a.labelAr ?? a.reason ?? s.t('deductions');
}

void openPayslip(BuildContext context, String id) {
  Navigator.of(context).push(MaterialPageRoute(builder: (_) => PayslipScreen(id: id)));
}

class _MonthChip extends StatelessWidget {
  const _MonthChip({required this.label, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? K.ink : Colors.white.withValues(alpha: 0.4),
      shape: StadiumBorder(side: BorderSide(color: Colors.white.withValues(alpha: selected ? 0 : 0.6))),
      child: InkWell(
        customBorder: const StadiumBorder(),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 9),
          child: Text(label, style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13.5, color: selected ? K.brand : K.ink)),
        ),
      ),
    );
  }
}

class _SourceRow extends StatelessWidget {
  const _SourceRow({required this.icon, required this.label, required this.amount, required this.share, required this.first});

  final IconData icon;
  final String label;
  final double amount;
  final double share;
  final bool first;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Column(children: [
        Row(children: [
          IconTile(icon, size: 34, filled: first),
          const SizedBox(width: 10),
          Expanded(child: Text(label, style: const TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600))),
          Money(amount, size: 13.5, weight: FontWeight.w700),
          const SizedBox(width: 8),
          SizedBox(
            width: 34,
            child: Text('${(share * 100).round()}%', textAlign: TextAlign.end, style: const TextStyle(fontSize: 11, color: K.muted)),
          ),
        ]),
        const SizedBox(height: 6),
        Bar(value: share, color: first ? K.brandDark : K.brand),
      ]),
    );
  }
}

class _PayslipRow extends StatelessWidget {
  const _PayslipRow(this.p);

  final PayslipSummary p;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return InkWell(
      onTap: () => openPayslip(context, p.id),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        child: Row(children: [
          const IconTile(Icons.receipt_long_rounded, size: 44),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(s.monthYear(p.periodFrom.year, p.periodFrom.month), style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
              Text(s.f('shiftsOrders', {'s': p.attendedShifts, 'o': p.totalOrders}),
                  style: const TextStyle(fontSize: 12, color: K.muted)),
            ]),
          ),
          Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Money(p.net, size: 14.5, weight: FontWeight.w800),
            const SizedBox(height: 4),
            Chip2(s.payStatus(p.status), tone: p.status == 'PAID' ? Tone.success : Tone.warning),
          ]),
          const SizedBox(width: 6),
          Icon(Icons.chevron_right_rounded,
              color: K.muted),
        ]),
      ),
    );
  }
}
