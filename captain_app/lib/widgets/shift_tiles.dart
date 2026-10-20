import 'package:flutter/material.dart';

import '../api/models.dart';
import '../app/strings.dart';
import '../app/theme.dart';
import '../screens/shift_detail.dart';
import 'money.dart';
import 'ui.dart';

Tone shiftTone(String state) => switch (state) {
      'LIVE' || 'PRESENT' => Tone.success,
      'LATE' => Tone.warning,
      'ABSENT' => Tone.danger,
      'EXCLUDED' => Tone.purple,
      'UPCOMING' => Tone.info,
      _ => Tone.neutral,
    };

/// The state chip of a shift; a late shift says by how much.
Widget shiftChip(S s, ShiftItem shift) {
  final late = shift.state == 'LATE' && (shift.delayMinutes ?? 0) > 0;
  return Chip2(
    late ? s.f('lateBy', {'n': shift.delayMinutes!.round()}) : s.shiftState(shift.state),
    tone: shiftTone(shift.state),
    dot: shift.state == 'LIVE',
  );
}

void openShift(BuildContext context, ShiftItem shift) {
  Navigator.of(context).push(MaterialPageRoute(builder: (_) => ShiftDetailScreen(shiftId: shift.id)));
}

/// Day number over the short weekday, as on every shift row.
class DateBadge extends StatelessWidget {
  const DateBadge(this.date, {super.key, this.highlight = false});

  final DateTime date;
  final bool highlight;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return Container(
      width: 48,
      padding: const EdgeInsets.symmetric(vertical: 7),
      decoration: BoxDecoration(
        color: highlight ? K.brand : const Color(0xFFF7F5F0),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: highlight ? K.brand : K.line),
      ),
      child: Column(children: [
        Text('${date.day}', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800, height: 1.15)),
        Text(s.weekdayShort(date), style: TextStyle(fontSize: 10.5, color: highlight ? K.ink : K.muted)),
      ]),
    );
  }
}

/// One shift in a list: date, hours, a one-line summary and the pay with its state.
class ShiftRow extends StatelessWidget {
  const ShiftRow(this.shift, {super.key});

  final ShiftItem shift;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final sub = switch (shift.state) {
      'ABSENT' => s.t('noCheckin'),
      'EXCLUDED' => shift.exclusionReason ?? s.shiftState('EXCLUDED'),
      'AWAITING' => s.t('awaitingSub'),
      'UPCOMING' || 'LIVE' => [shift.zoneName, shift.supplierName].whereType<String>().join(' · '),
      _ => '${s.hours(shift.workingHours)} · ${s.f('ordersSoFar', {'n': shift.totalDelivered ?? 0})}',
    };
    return InkWell(
      onTap: () => openShift(context, shift),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        child: Row(children: [
          DateBadge(shift.date),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(s.timeRange(shift.scheduledStart, shift.scheduledEnd),
                  style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700)),
              const SizedBox(height: 2),
              Text(sub, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, color: K.muted)),
            ]),
          ),
          const SizedBox(width: 8),
          Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
            if (shift.netShiftPay != null)
              Money(shift.netShiftPay!, size: 14, signed: true, color: K.text)
            else
              const Text('—', style: TextStyle(color: K.muted)),
            const SizedBox(height: 6),
            shiftChip(s, shift),
          ]),
        ]),
      ),
    );
  }
}

/// A card holding several [ShiftRow]s separated by hairlines.
class ShiftList extends StatelessWidget {
  const ShiftList(this.shifts, {super.key});

  final List<ShiftItem> shifts;

  @override
  Widget build(BuildContext context) {
    return CardBox(
      padding: EdgeInsets.zero,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(K.radius),
        child: Column(children: [
          for (var i = 0; i < shifts.length; i++) ...[
            if (i > 0) const Divider(height: 1, color: K.line),
            ShiftRow(shifts[i]),
          ],
        ]),
      ),
    );
  }
}

/// Today's shift with the route-style progress through it.
class TodayShiftCard extends StatelessWidget {
  const TodayShiftCard(this.shift, {super.key, this.title, this.highlight = false});

  final ShiftItem shift;
  final String? title;
  final bool highlight;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final start = shift.scheduledStart, end = shift.scheduledEnd;
    final now = DateTime.now().toUtc();
    double progress = 0;
    String? caption;
    if (start != null && end != null) {
      final total = end.difference(start).inSeconds;
      if (now.isBefore(start)) {
        caption = s.f('startsIn', {'d': s.duration(start.difference(now))});
      } else if (now.isBefore(end)) {
        progress = total <= 0 ? 0 : now.difference(start).inSeconds / total;
        caption = s.f('remaining', {'d': s.duration(end.difference(now))});
      } else {
        progress = 1;
      }
    }
    return CardBox(
      onTap: () => openShift(context, shift),
      border: highlight ? K.brand : null,
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Row(children: [
          if (highlight) DateBadge(shift.date, highlight: true) else const IconTile(Icons.delivery_dining_rounded, filled: true, size: 44),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title ?? s.t('todayShift'), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
              Text(
                highlight
                    ? [shift.zoneName, shift.supplierName].whereType<String>().join(' · ')
                    : [s.fullDay(shift.date), shift.supplierName].whereType<String>().join(' · '),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 12, color: K.muted),
              ),
            ]),
          ),
          shiftChip(s, shift),
        ]),
        const SizedBox(height: 14),
        RouteProgress(value: progress),
        const SizedBox(height: 4),
        Row(children: [
          Text(start == null ? '—' : s.time(start), style: const TextStyle(fontSize: 12, color: K.muted)),
          Expanded(
            child: Text(caption ?? '', textAlign: TextAlign.center,
                style: const TextStyle(fontSize: 12, color: K.brandDark, fontWeight: FontWeight.w700)),
          ),
          Text(end == null ? '—' : s.time(end), style: const TextStyle(fontSize: 12, color: K.muted)),
        ]),
        if (!highlight) ...[
          const SizedBox(height: 12),
          Wrap(spacing: 8, runSpacing: 8, children: [
            if (shift.actualCheckin != null)
              _Pill(
                icon: Icons.how_to_reg_rounded,
                text: '${s.f('checkinAt', {'t': s.time(shift.actualCheckin!)})} · '
                    '${(shift.delayMinutes ?? 0) > 0 ? s.f('lateBy', {'n': shift.delayMinutes!.round()}) : s.t('onTimeChip')}',
                tone: (shift.delayMinutes ?? 0) > 0 ? Tone.warning : Tone.success,
              ),
            if (shift.totalDelivered != null)
              _Pill(icon: Icons.inventory_2_outlined, text: s.f('ordersSoFar', {'n': shift.totalDelivered}), tone: Tone.neutral),
          ]),
          if (shift.zoneName != null) ...[
            const SizedBox(height: 10),
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              decoration: BoxDecoration(color: const Color(0xFFF7F5F0), borderRadius: BorderRadius.circular(14)),
              child: Row(children: [
                const Icon(Icons.location_on_rounded, size: 18, color: K.brandDark),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(s.f('zone', {'z': shift.zoneName}), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
                ),
                if (shift.city != null) Text(shift.city!, style: const TextStyle(fontSize: 12, color: K.muted)),
              ]),
            ),
          ],
        ],
      ]),
    );
  }
}

class _Pill extends StatelessWidget {
  const _Pill({required this.icon, required this.text, required this.tone});

  final IconData icon;
  final String text;
  final Tone tone;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(color: tone.bg, borderRadius: BorderRadius.circular(10)),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(icon, size: 15, color: tone == Tone.neutral ? K.text : tone.fg),
        const SizedBox(width: 6),
        Text(text, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: tone == Tone.neutral ? K.text : tone.fg)),
      ]),
    );
  }
}
