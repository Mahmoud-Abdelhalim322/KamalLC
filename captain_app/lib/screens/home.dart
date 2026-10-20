import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app/session.dart';
import '../app/strings.dart';
import '../app/theme.dart';
import '../widgets/money.dart';
import '../widgets/page.dart';
import '../widgets/shift_tiles.dart';
import '../widgets/ui.dart';
import 'documents.dart';
import 'loan.dart';
import 'notifications.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key, required this.onTab});

  /// Switches the dock to another tab (1 shifts, 2 earnings, 3 documents).
  final ValueChanged<int> onTab;

  @override
  Widget build(BuildContext context) {
    final api = context.read<Session>().api;
    return Loader<HomeResponse>(
      load: api.home,
      builder: (context, home, reload) => _HomeBody(home: home, onTab: onTab, reload: reload),
    );
  }
}

class _HomeBody extends StatelessWidget {
  const _HomeBody({required this.home, required this.onTab, required this.reload});

  final HomeResponse home;
  final ValueChanged<int> onTab;
  final Future<void> Function() reload;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final e = home.earnings;
    final name = s.name(home.fullNameAr, home.fullNameEn);
    final monthDays = e.to.difference(e.from).inDays + 1;
    final elapsed = e.asOf.difference(e.from).inDays + 1;
    final week = home.week;

    return HeroPage(
      bottom: dockClearance,
      hero: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Row(children: [
          Avatar(name: name, size: 50),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(s.greeting(), style: TextStyle(fontSize: 12.5, color: K.ink.withValues(alpha: 0.65))),
              Text(name, maxLines: 1, overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900, color: K.ink)),
            ]),
          ),
          HeroButton(
            icon: Icons.notifications_none_rounded,
            badge: home.notificationCount,
            onTap: () async {
              await Navigator.of(context).push(MaterialPageRoute(builder: (_) => const NotificationsScreen()));
              reload();
            },
          ),
        ]),
        const SizedBox(height: 22),
        Row(children: [
          Text(s.t('earningsThisMonth'), style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: K.ink)),
          const SizedBox(width: 8),
          Chip2(s.payStatus(e.status), tone: Tone.ink),
        ]),
        const SizedBox(height: 4),
        GestureDetector(
          onTap: () => onTab(2),
          child: Align(
            alignment: AlignmentDirectional.centerStart,
            child: Money(e.net, size: 42, weight: FontWeight.w900, color: K.ink),
          ),
        ),
        const SizedBox(height: 4),
        Text(
          e.status == 'ESTIMATE' ? s.f('netUntil', {'date': s.dayMonth(e.asOf)}) : s.t('netFinal'),
          style: TextStyle(fontSize: 12, color: K.ink.withValues(alpha: 0.7)),
        ),
        const SizedBox(height: 10),
        RouteProgress(value: elapsed / monthDays, dark: true),
        Row(children: [
          Text(s.f('todayIs', {'date': s.dayMonth(e.asOf)}), style: const TextStyle(fontSize: 11.5, color: K.ink)),
          const Spacer(),
          Text(s.dayMonth(e.to), style: const TextStyle(fontSize: 11.5, color: K.ink)),
        ]),
        const SizedBox(height: 16),
        StatStrip([
          (statText('${e.attendedShifts}'), s.t('shiftsLabel')),
          (statText('${e.totalOrders}'), s.t('ordersLabel')),
          (statText(percent(week.deliveryOntime)), s.t('onTime')),
        ]),
      ]),
      children: [
        const SizedBox(height: 18),
        padded(home.todayShift != null
            ? TodayShiftCard(home.todayShift!)
            : EmptyCard(icon: Icons.free_breakfast_rounded, text: '${s.t('noShiftToday')}\n${s.t('noShiftTodaySub')}')),
        if (home.documentAlert != null) ...[
          const SizedBox(height: 14),
          padded(_DocumentAlert(home.documentAlert!, onChanged: reload)),
        ],
        SectionTitle(s.t('weekPerformance'), action: s.t('details'), onAction: () => onTab(1)),
        padded(MetricGrid([
          MetricTile(icon: Icons.timer_outlined, label: s.t('onTimeDelivery'), value: percent(week.deliveryOntime), tone: Tone.warning),
          MetricTile(icon: Icons.speed_rounded, label: s.t('utilization'), value: percent(week.busyUtilization)),
          MetricTile(icon: Icons.trending_up_rounded, label: s.t('ordersPerHour'), value: num1(week.ordersPerHour), tone: Tone.danger),
          MetricTile(icon: Icons.schedule_rounded, label: s.t('hoursLabel'), value: num1(week.totalHours), tone: Tone.success),
        ])),
        if (home.loan != null) ...[
          const SizedBox(height: 14),
          padded(_LoanCard(home.loan!)),
        ],
        if (home.recentShifts.isNotEmpty) ...[
          SectionTitle(s.t('recentShifts'), action: s.t('viewAll'), onAction: () => onTab(1)),
          padded(ShiftList(home.recentShifts.take(4).toList())),
        ],
      ],
    );
  }
}

class _DocumentAlert extends StatelessWidget {
  const _DocumentAlert(this.doc, {required this.onChanged});

  final DocumentItem doc;
  final Future<void> Function() onChanged;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final name = s.docKind(doc.kind);
    final pending = doc.pendingRequest != null;
    final title = switch (doc.status) {
      'EXPIRED' => s.f('docExpired', {'doc': name}),
      'MISSING' => s.f('docMissing', {'doc': name}),
      _ => s.f('docExpiresIn', {'doc': name, 'n': doc.daysLeft ?? 0}),
    };
    final danger = doc.status == 'EXPIRED' || doc.status == 'MISSING';
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: danger ? K.dangerBg : const Color(0xFFFFF8E6),
        borderRadius: BorderRadius.circular(K.radius),
        border: Border.all(color: danger ? const Color(0xFFF5C2C2) : const Color(0xFFF7DE9C)),
      ),
      child: Row(children: [
        IconTile(Icons.badge_rounded, tone: danger ? Tone.danger : Tone.brand, size: 44),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(title, style: TextStyle(fontSize: 15, fontWeight: FontWeight.w800, color: danger ? K.danger : K.brandDark)),
            const SizedBox(height: 2),
            Text(pending ? s.t('docPendingSub') : s.t('docAlertSub'), style: const TextStyle(fontSize: 12, color: Color(0xFF555A64))),
          ]),
        ),
        const SizedBox(width: 8),
        if (pending)
          Chip2(s.t('underReview'), tone: Tone.info)
        else
          Material(
            color: K.ink,
            borderRadius: BorderRadius.circular(12),
            child: InkWell(
              borderRadius: BorderRadius.circular(12),
              onTap: () async {
                if (await showUploadSheet(context, doc.kind) == true) onChanged();
              },
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 9),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  const Icon(Icons.upload_rounded, size: 16, color: K.brand),
                  const SizedBox(width: 4),
                  Text(s.t('update'), style: const TextStyle(color: Colors.white, fontSize: 12.5, fontWeight: FontWeight.w700)),
                ]),
              ),
            ),
          ),
      ]),
    );
  }
}

class _LoanCard extends StatelessWidget {
  const _LoanCard(this.loan);

  final LoanSnapshot loan;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final paid = loan.originalAmount <= 0 ? 0.0 : loan.repaid / loan.originalAmount;
    return CardBox(
      onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const LoanScreen())),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Row(children: [
          const IconTile(Icons.account_balance_rounded, size: 44),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(s.t('loan'), style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
              if (loan.monthlyDeduction != null)
                Row(children: [
                  Text(s.f('monthlyInstallment', {'a': ''}).trim(), style: const TextStyle(fontSize: 12, color: K.muted)),
                  const SizedBox(width: 4),
                  Money(loan.monthlyDeduction!, size: 12, color: K.muted, weight: FontWeight.w600),
                ]),
            ]),
          ),
          Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            Money(loan.remaining, size: 18, weight: FontWeight.w900),
            Text(s.t('remainingLabel'), style: const TextStyle(fontSize: 11.5, color: K.muted)),
          ]),
        ]),
        const SizedBox(height: 14),
        Bar(value: paid, height: 8),
        const SizedBox(height: 8),
        Row(children: [
          Text(s.t('repaidOf').split('{a}').first.trim(), style: const TextStyle(fontSize: 12, color: K.muted)),
          const SizedBox(width: 4),
          Money(loan.repaid, size: 12, color: K.muted, weight: FontWeight.w600, whole: true),
          const Text(' / ', style: TextStyle(fontSize: 12, color: K.muted)),
          Money(loan.originalAmount, size: 12, color: K.muted, weight: FontWeight.w600, whole: true),
          const Spacer(),
          Text('${(paid * 100).round()}%', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: K.brandDark)),
        ]),
      ]),
    );
  }
}
