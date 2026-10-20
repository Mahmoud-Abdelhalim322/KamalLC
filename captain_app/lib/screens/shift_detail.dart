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

class ShiftDetailScreen extends StatelessWidget {
  const ShiftDetailScreen({super.key, required this.shiftId});

  final String shiftId;

  @override
  Widget build(BuildContext context) {
    final api = context.read<Session>().api;
    return Scaffold(
      body: Loader<ShiftDetail>(
        load: () => api.shift(shiftId),
        builder: (context, d, _) => _Body(d),
      ),
      bottomNavigationBar: const SupervisorBar(),
    );
  }
}

class _Body extends StatelessWidget {
  const _Body(this.d);

  final ShiftDetail d;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final x = d.shift;
    final late = (x.delayMinutes ?? 0) > 0;
    final stateLine = x.state == 'PRESENT' || x.state == 'LATE'
        ? '${s.shiftState('PRESENT')} · ${late ? s.f('lateBy', {'n': x.delayMinutes!.round()}) : s.t('onTimeChip')}'
        : s.shiftState(x.state);

    return HeroPage(
      hero: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        HeroTitle(
          leading: const BackHeroButton(),
          title: s.t('shiftDetails'),
          subtitle: '#${x.id.substring(0, 8).toUpperCase()}',
          trailing: [
            HeroButton(
              icon: Icons.help_outline_rounded,
              onTap: () => showInfoSheet(context, icon: Icons.help_outline_rounded, title: s.t('help'), body: s.t('helpBody')),
            ),
          ],
        ),
        const SizedBox(height: 18),
        Align(alignment: AlignmentDirectional.centerStart, child: Chip2(stateLine, tone: Tone.ink, dot: true)),
        const SizedBox(height: 6),
        Text(s.fullDay(x.date), style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w900, color: K.ink)),
        Text([x.shiftName, x.zoneName, x.city].whereType<String>().join(' · '),
            style: TextStyle(fontSize: 12.5, color: K.ink.withValues(alpha: 0.65))),
        const SizedBox(height: 14),
        Text(s.t('shiftNet'), style: TextStyle(fontSize: 13, color: K.ink.withValues(alpha: 0.7))),
        Align(
          alignment: AlignmentDirectional.centerStart,
          child: x.netShiftPay == null
              ? const Text('—', style: TextStyle(fontSize: 40, fontWeight: FontWeight.w900))
              : Money(x.netShiftPay!, size: 40, weight: FontWeight.w900, color: K.ink),
        ),
        const SizedBox(height: 16),
        StatStrip([
          (statText(num1(x.workingHours)), s.t('hours')),
          (statText(x.actualCheckin == null ? '—' : s.time(x.actualCheckin!)), s.t('checkin')),
          (statText(s.timeRange(x.scheduledStart, x.scheduledEnd)), s.t('schedule')),
        ]),
      ]),
      children: [
        if (x.state == 'EXCLUDED' && x.exclusionReason != null) ...[
          const SizedBox(height: 16),
          padded(NoteBox(s.f('excludedReason', {'r': x.exclusionReason}), icon: Icons.block_rounded, tone: Tone.purple)),
        ],
        if (x.state == 'AWAITING' || x.state == 'UPCOMING' || x.state == 'LIVE') ...[
          const SizedBox(height: 16),
          padded(x.state == 'AWAITING' ? NoteBox(s.t('awaitingSub'), icon: Icons.hourglass_top_rounded) : TodayShiftCard(x)),
        ],
        SectionTitle(s.t('performance')),
        padded(MetricGrid([
          MetricTile(icon: Icons.timer_outlined, label: s.t('onTimeDelivery'), value: percent(d.deliveryOntime), tone: Tone.warning),
          MetricTile(
            icon: Icons.inventory_2_outlined,
            label: s.t('deliveredOrders'),
            value: '${x.totalDelivered ?? '—'}',
            trailing: (d.extraOrders ?? 0) > 0 ? Chip2(s.f('extra', {'n': d.extraOrders}), tone: Tone.success) : null,
          ),
          MetricTile(icon: Icons.trending_up_rounded, label: s.t('ordersPerHour'), value: num1(d.hourlyUtr), tone: Tone.danger),
          MetricTile(icon: Icons.speed_rounded, label: s.t('utilization'), value: percent(d.busyUtilization)),
        ])),
        if ((d.busyTime ?? 0) + (d.freeTime ?? 0) + (d.offlineTime ?? 0) > 0) ...[
          SectionTitle(s.t('timeSplit')),
          padded(_TimeSplit(d)),
        ],
        SectionTitle(s.t('payDetails')),
        padded(CardBox(
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            AmountRow(label: s.t('dailyWage'), value: d.dailyWage ?? 0),
            AmountRow(label: s.t('dailyIncentive'), value: d.dailyIncentive ?? 0, signed: true, color: K.success),
            if ((d.extraOrdersPay ?? 0) != 0)
              AmountRow(label: s.f('extraOrdersPay', {'n': d.extraOrders ?? 0}), value: d.extraOrdersPay!, signed: true, color: K.success),
            if ((d.fuelAllowance ?? 0) != 0) AmountRow(label: s.t('fuelAllowance'), value: d.fuelAllowance!, signed: true, color: K.success),
            if ((d.carAllowance ?? 0) != 0) AmountRow(label: s.t('carAllowance'), value: d.carAllowance!, signed: true, color: K.success),
            AmountRow(label: s.t('lateDeduction'), value: -(d.lateDeduction ?? 0).abs(), color: (d.lateDeduction ?? 0) == 0 ? K.muted : K.danger),
            AmountRow(label: s.t('supplierDeduction'), value: -(d.supplierDeduction ?? 0).abs(), color: (d.supplierDeduction ?? 0) == 0 ? K.muted : K.danger),
            if (d.supplierDeductionReason != null) ...[
              const SizedBox(height: 6),
              NoteBox(s.f('supplierReason', {'r': d.supplierDeductionReason}), icon: Icons.info_rounded),
            ],
            const SizedBox(height: 12),
            TotalBar(label: s.t('shiftNet'), value: x.netShiftPay ?? 0),
            if (d.qualifiesFullRate != null) ...[
              const SizedBox(height: 12),
              NoteBox(
                d.qualifiesFullRate! ? s.t('qualifiesFull') : s.t('notQualifiesFull'),
                icon: d.qualifiesFullRate! ? Icons.verified_rounded : Icons.error_outline_rounded,
                tone: d.qualifiesFullRate! ? Tone.success : Tone.warning,
              ),
            ],
          ]),
        )),
      ],
    );
  }
}

class _TimeSplit extends StatelessWidget {
  const _TimeSplit(this.d);

  final ShiftDetail d;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final parts = [
      (s.t('busy'), d.busyTime ?? 0, K.ink),
      (s.t('free'), d.freeTime ?? 0, K.brand),
      (s.t('offlineTime'), d.offlineTime ?? 0, const Color(0xFFD9D5CC)),
      if ((d.awayFromZone ?? 0) > 0) (s.t('awayFromZone'), d.awayFromZone!, K.danger),
    ];
    final total = parts.fold(0.0, (a, p) => a + p.$2);
    return CardBox(
      child: Column(children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(5),
          child: SizedBox(
            height: 10,
            child: Row(children: [
              for (final p in parts)
                if (p.$2 > 0)
                  Expanded(
                    flex: (p.$2 / total * 1000).round().clamp(1, 1000),
                    child: Container(margin: const EdgeInsets.symmetric(horizontal: 1), color: p.$3),
                  ),
            ]),
          ),
        ),
        const SizedBox(height: 12),
        for (final p in parts)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 5),
            child: Row(children: [
              Container(width: 8, height: 8, decoration: BoxDecoration(color: p.$3, shape: BoxShape.circle)),
              const SizedBox(width: 8),
              Expanded(child: Text(p.$1, style: const TextStyle(fontSize: 13))),
              Text(s.hours(p.$2), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
            ]),
          ),
      ]),
    );
  }
}

/// "Disagree with something?" and the supervisor button pinned to the bottom.
class SupervisorBar extends StatelessWidget {
  const SupervisorBar({super.key});

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return Container(
      decoration: const BoxDecoration(color: Colors.white, border: Border(top: BorderSide(color: K.line))),
      padding: EdgeInsets.fromLTRB(16, 10, 16, 12 + MediaQuery.paddingOf(context).bottom),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Text(s.t('objection'), style: const TextStyle(fontSize: 12, color: K.muted)),
        const SizedBox(height: 8),
        PrimaryButton(
          label: s.t('contactSupervisorBtn'),
          icon: Icons.support_agent_rounded,
          onTap: () => showSupervisorSheet(context),
        ),
      ]),
    );
  }
}

void showSupervisorSheet(BuildContext context) {
  final s = S.read(context);
  final name = context.read<Session>().profile?.supervisor;
  showInfoSheet(
    context,
    icon: Icons.support_agent_rounded,
    title: s.t('contactSupervisor'),
    body: s.f('supervisorSheetBody', {'name': name ?? s.t('supervisorUnknown')}),
  );
}
