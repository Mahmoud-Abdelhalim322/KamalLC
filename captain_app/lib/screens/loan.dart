import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app/session.dart';
import '../app/strings.dart';
import '../app/theme.dart';
import '../widgets/money.dart';
import '../widgets/page.dart';
import '../widgets/ui.dart';
import 'shift_detail.dart';

class LoanScreen extends StatelessWidget {
  const LoanScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final api = context.read<Session>().api;
    return Scaffold(
      body: Loader<List<LoanItem>>(load: api.loans, builder: (context, loans, _) => _Body(loans)),
    );
  }
}

class _Body extends StatelessWidget {
  const _Body(this.loans);

  final List<LoanItem> loans;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final loan = loans.where((l) => l.isActive).firstOrNull ?? loans.firstOrNull;
    final others = loans.where((l) => l != loan).toList();
    final title = HeroTitle(
      leading: const BackHeroButton(),
      title: s.t('myLoan'),
      subtitle: s.t('loanSub'),
      trailing: [
        HeroButton(
          icon: Icons.help_outline_rounded,
          onTap: () => showSupervisorSheet(context),
        ),
      ],
    );
    if (loan == null) {
      return HeroPage(hero: title, children: [
        const SizedBox(height: 20),
        padded(EmptyCard(icon: Icons.account_balance_rounded, text: s.t('noLoan'))),
      ]);
    }

    final now = S.riyadh(DateTime.now());
    return HeroPage(
      hero: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        title,
        const SizedBox(height: 20),
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: Chip2(s.loanStatus(loan.status), tone: Tone.ink, dot: loan.isActive),
        ),
        const SizedBox(height: 4),
        Text(s.t('loanRemaining'), style: TextStyle(fontSize: 13, color: K.ink.withValues(alpha: 0.7))),
        Align(alignment: AlignmentDirectional.centerStart, child: Money(loan.remaining, size: 42, weight: FontWeight.w900, color: K.ink)),
        Row(children: [
          Text(s.f('ofOriginal', {'a': ''}).trim(), style: TextStyle(fontSize: 12.5, color: K.ink.withValues(alpha: 0.7))),
          const SizedBox(width: 4),
          Money(loan.originalAmount, size: 12.5, weight: FontWeight.w600, color: K.ink.withValues(alpha: 0.7)),
        ]),
        const SizedBox(height: 10),
        RouteProgress(value: loan.repaidPercent / 100, dark: true),
        Row(children: [
          Money(loan.repaid, size: 12, weight: FontWeight.w700, color: K.ink),
          Text(' · ${loan.repaidPercent}%', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: K.ink)),
          const Spacer(),
          if (loan.installmentsLeft != null)
            Text(s.f('installmentsLeft', {'n': loan.installmentsLeft}), style: const TextStyle(fontSize: 12, color: K.ink)),
        ]),
        const SizedBox(height: 16),
        StatStrip([
          (statText(_payoff(s, loan.expectedPayoff)), s.t('expectedPayoff')),
          (
            loan.monthlyDeduction == null
                ? statText('—')
                : Money(loan.monthlyDeduction!, size: 17, weight: FontWeight.w800, color: Colors.white),
            s.t('monthlyDeduction')
          ),
        ]),
      ]),
      children: [
        SectionTitle(s.t('repaymentLog')),
        padded(CardBox(
          padding: EdgeInsets.zero,
          child: Column(children: [
            if (loan.isActive && loan.monthlyDeduction != null && loan.remaining > 0)
              _LogRow(
                icon: Icons.schedule_rounded,
                tone: Tone.brand,
                title: s.monthYear(now.year, now.month),
                subtitle: s.f('deductedFrom', {'m': s.month(now.month)}),
                amount: loan.monthlyDeduction! < loan.remaining ? loan.monthlyDeduction! : loan.remaining,
                chip: Chip2(s.t('upcoming'), tone: Tone.warning),
              ),
            for (final p in loan.payments)
              _LogRow(
                icon: Icons.check_rounded,
                tone: Tone.success,
                title: p.payrollPeriodFrom != null
                    ? s.monthYear(p.payrollPeriodFrom!.year, p.payrollPeriodFrom!.month)
                    : (p.createdAt != null ? s.dayMonth(S.riyadh(p.createdAt!)) : '—'),
                subtitle: p.source == 'PAYROLL_DEDUCTION'
                    ? s.f('deductedFromPast', {'m': p.payrollTitle ?? (p.payrollPeriodFrom != null ? s.month(p.payrollPeriodFrom!.month) : '')})
                    : (p.notes ?? s.t('manualPayment')),
                amount: -p.amount.abs(),
                chip: Chip2(s.t('done'), tone: Tone.success),
              ),
            _LogRow(
              icon: Icons.payments_rounded,
              tone: Tone.ink,
              title: loan.createdAt == null ? '—' : '${S.riyadh(loan.createdAt!).day} ${s.monthYear(S.riyadh(loan.createdAt!).year, S.riyadh(loan.createdAt!).month)}',
              subtitle: s.f('loanDisbursed', {'by': loan.createdByName ?? ''}).replaceAll(RegExp(r' · $'), ''),
              amount: loan.originalAmount,
              chip: Chip2(s.t('disbursed'), tone: Tone.info),
              last: true,
            ),
          ]),
        )),
        const SizedBox(height: 14),
        padded(NoteBox(s.t('loanHelp'), icon: Icons.support_agent_rounded)),
        if (others.isNotEmpty) ...[
          SectionTitle(s.t('otherLoans')),
          padded(CardBox(
            padding: EdgeInsets.zero,
            child: Column(children: [
              for (final o in others)
                _LogRow(
                  icon: Icons.account_balance_rounded,
                  tone: Tone.neutral,
                  title: o.createdAt == null ? '—' : s.monthYear(S.riyadh(o.createdAt!).year, S.riyadh(o.createdAt!).month),
                  subtitle: s.loanStatus(o.status),
                  amount: o.originalAmount,
                  chip: Chip2(s.loanStatus(o.status), tone: o.status == 'PAID_OFF' ? Tone.success : Tone.neutral),
                  last: o == others.last,
                ),
            ]),
          )),
        ],
      ],
    );
  }

  String _payoff(S s, String? ym) {
    if (ym == null) return '—';
    final parts = ym.split('-');
    if (parts.length != 2) return ym;
    return s.monthYear(int.parse(parts[0]), int.parse(parts[1]));
  }
}

class _LogRow extends StatelessWidget {
  const _LogRow({required this.icon, required this.tone, required this.title, required this.subtitle, required this.amount,
      required this.chip, this.last = false});

  final IconData icon;
  final Tone tone;
  final String title;
  final String subtitle;
  final double amount;
  final Widget chip;
  final bool last;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(border: last ? null : const Border(bottom: BorderSide(color: K.line))),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
      child: Row(children: [
        Container(
          width: 42,
          height: 42,
          decoration: BoxDecoration(color: tone.bg, shape: BoxShape.circle),
          child: Icon(icon, size: 20, color: tone == Tone.ink ? K.brand : (tone == Tone.brand ? K.brandDark : tone.fg)),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700)),
            Text(subtitle, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, color: K.muted)),
          ]),
        ),
        Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Money(amount, size: 14, weight: FontWeight.w700),
          const SizedBox(height: 4),
          chip,
        ]),
      ]),
    );
  }
}
