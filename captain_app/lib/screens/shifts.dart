import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app/session.dart';
import '../app/strings.dart';
import '../app/theme.dart';
import '../widgets/page.dart';
import '../widgets/shift_tiles.dart';
import '../widgets/ui.dart';

/// The week (Saturday to Friday) around a picked day, with the month's totals.
class ShiftsScreen extends StatefulWidget {
  const ShiftsScreen({super.key});

  @override
  State<ShiftsScreen> createState() => _ShiftsScreenState();
}

class _ShiftsScreenState extends State<ShiftsScreen> {
  late DateTime _day = _today();
  String _filter = 'ALL';
  ShiftWeek? _week;
  Performance? _month;
  Object? _error;
  bool _loading = true;

  static DateTime _today() {
    final r = S.riyadh(DateTime.now());
    return DateTime(r.year, r.month, r.day);
  }

  /// Saturday on or before [d].
  static DateTime _weekStart(DateTime d) => d.subtract(Duration(days: (d.weekday + 1) % 7));

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final api = context.read<Session>().api;
    final from = _weekStart(_day);
    setState(() => _loading = true);
    try {
      final results = await Future.wait([
        api.shifts(from, from.add(const Duration(days: 6))),
        api.shifts(DateTime(_day.year, _day.month, 1), DateTime(_day.year, _day.month + 1, 0)),
      ]);
      if (!mounted) return;
      setState(() {
        _week = results[0];
        _month = results[1].totals;
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

  void _pick(DateTime day) {
    final reload = _weekStart(day) != _weekStart(_day) || day.month != _day.month;
    setState(() => _day = day);
    if (reload) _load();
  }

  Future<void> _calendar() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _day,
      firstDate: DateTime(2023),
      lastDate: _today().add(const Duration(days: 60)),
    );
    if (picked != null) _pick(picked);
  }

  bool _matches(ShiftItem s) => switch (_filter) {
        'PRESENT' => s.state == 'PRESENT' || s.state == 'LATE',
        'ABSENT' => s.state == 'ABSENT',
        'EXCLUDED' => s.state == 'EXCLUDED',
        _ => true,
      };

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final week = _week;
    if (week == null) {
      if (_loading) return const Center(child: CircularProgressIndicator(color: K.brand));
      return ErrorView(error: _error, onRetry: _load);
    }
    final start = _weekStart(_day);
    final days = List.generate(7, (i) => start.add(Duration(days: i)));
    final today = _today();
    final ofDay = week.shifts.where((x) => DateUtils.isSameDay(x.date, _day)).toList();
    final rest = week.shifts.where((x) => !DateUtils.isSameDay(x.date, _day) && _matches(x)).toList()
      ..sort((a, b) => b.date.compareTo(a.date));
    final month = _month;

    return RefreshIndicator(
      color: K.ink,
      backgroundColor: K.brand,
      onRefresh: _load,
      child: HeroPage(
        bottom: dockClearance,
        hero: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          HeroTitle(
            title: s.t('myShifts'),
            subtitle: s.monthYear(_day.year, _day.month),
            subtitleFirst: true,
            trailing: [HeroButton(icon: Icons.calendar_month_rounded, onTap: _calendar)],
          ),
          const SizedBox(height: 16),
          Row(children: [
            for (final d in days)
              Expanded(
                child: _DayPill(
                  day: d,
                  selected: DateUtils.isSameDay(d, _day),
                  isToday: DateUtils.isSameDay(d, today),
                  dot: week.shifts.where((x) => DateUtils.isSameDay(x.date, d)).map((x) => shiftTone(x.state).fg).firstOrNull,
                  onTap: () => _pick(d),
                ),
              ),
          ]),
          const SizedBox(height: 16),
          StatStrip([
            (statText('${month?.attendedShifts ?? '—'}'), s.t('attended')),
            (Text('${month?.absentShifts ?? '—'}', style: const TextStyle(color: Color(0xFFFF8A80))), s.t('absences')),
            (statText(num1(month?.totalHours)), s.t('workHours')),
          ]),
        ]),
        children: [
          if (_loading) const LinearProgressIndicator(color: K.brand, backgroundColor: Colors.transparent, minHeight: 2),
          const SizedBox(height: 16),
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Row(children: [
              for (final f in const ['ALL', 'PRESENT', 'ABSENT', 'EXCLUDED'])
                Padding(
                  padding: const EdgeInsetsDirectional.only(end: 8),
                  child: _FilterChip(
                    label: s.t('filter${f[0]}${f.substring(1).toLowerCase()}'),
                    selected: _filter == f,
                    onTap: () => setState(() => _filter = f),
                  ),
                ),
            ]),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 18, 20, 10),
            child: Text(
              DateUtils.isSameDay(_day, today) ? '${s.t('today')} · ${s.fullDay(_day)}' : s.fullDay(_day),
              style: const TextStyle(fontSize: 13, color: K.muted, fontWeight: FontWeight.w600),
            ),
          ),
          if (ofDay.isEmpty)
            padded(EmptyCard(icon: Icons.event_busy_rounded, text: s.t('noShiftsDay')))
          else
            for (final x in ofDay)
              Padding(
                padding: const EdgeInsets.only(bottom: 10),
                child: padded(x.state == 'LIVE' || x.state == 'UPCOMING'
                    ? TodayShiftCard(x, title: s.timeRange(x.scheduledStart, x.scheduledEnd), highlight: true)
                    : ShiftList([x])),
              ),
          SectionTitle(DateUtils.isSameDay(start, _weekStart(today)) ? s.t('thisWeek') : '${s.t('weekOf')} ${s.dayMonth(start)}'),
          if (rest.isEmpty) padded(EmptyCard(icon: Icons.calendar_today_rounded, text: s.t('noShiftsWeek'))) else padded(ShiftList(rest)),
        ],
      ),
    );
  }
}

class _DayPill extends StatelessWidget {
  const _DayPill({required this.day, required this.selected, required this.isToday, required this.onTap, this.dot});

  final DateTime day;
  final bool selected;
  final bool isToday;
  final Color? dot;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return GestureDetector(
      onTap: onTap,
      child: Container(
        margin: const EdgeInsets.symmetric(horizontal: 2.5),
        padding: const EdgeInsets.symmetric(vertical: 8),
        decoration: BoxDecoration(
          color: selected ? K.ink : Colors.white.withValues(alpha: 0.38),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: Colors.white.withValues(alpha: selected ? 0 : 0.5)),
        ),
        child: Column(children: [
          FittedBox(
            child: Text(s.weekdayShort(day),
                style: TextStyle(fontSize: 11, color: selected ? Colors.white70 : K.ink.withValues(alpha: 0.7))),
          ),
          const SizedBox(height: 2),
          Text('${day.day}',
              style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800, color: selected ? K.brand : K.ink, height: 1.2)),
          const SizedBox(height: 3),
          Container(
            width: 5,
            height: 5,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              color: selected && dot != null ? K.brand : (dot ?? Colors.transparent),
            ),
          ),
        ]),
      ),
    );
  }
}

class _FilterChip extends StatelessWidget {
  const _FilterChip({required this.label, required this.selected, required this.onTap});

  final String label;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected ? K.ink : Colors.white,
      shape: StadiumBorder(side: BorderSide(color: selected ? K.ink : K.line)),
      child: InkWell(
        customBorder: const StadiumBorder(),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 9),
          child: Text(label,
              style: TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: selected ? K.brand : K.text)),
        ),
      ),
    );
  }
}
