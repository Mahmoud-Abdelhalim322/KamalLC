import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app/session.dart';
import '../app/strings.dart';
import '../app/theme.dart';
import '../widgets/money.dart';
import '../widgets/page.dart';
import '../widgets/ui.dart';
import 'documents.dart';
import 'earnings.dart';
import 'shift_detail.dart';

class NotificationsScreen extends StatelessWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final api = context.read<Session>().api;
    return Scaffold(
      body: Loader<List<NotificationItem>>(
        load: api.notifications,
        builder: (context, items, _) {
          final s = S.of(context);
          return HeroPage(
            hero: HeroTitle(leading: const BackHeroButton(), title: s.t('notifications')),
            children: [
              const SizedBox(height: 16),
              if (items.isEmpty)
                padded(EmptyCard(icon: Icons.notifications_off_outlined, text: s.t('noNotifications')))
              else
                padded(CardBox(
                  padding: EdgeInsets.zero,
                  child: Column(children: [
                    for (var i = 0; i < items.length; i++) ...[
                      if (i > 0) const Divider(height: 1, color: K.line),
                      _NotificationRow(items[i]),
                    ],
                  ]),
                )),
            ],
          );
        },
      ),
    );
  }
}

class _NotificationRow extends StatelessWidget {
  const _NotificationRow(this.n);

  final NotificationItem n;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final d = n.data;
    final kind = d['kind'] as String?;
    final doc = kind == null ? '' : s.docKind(kind);

    late final IconData icon;
    late final Tone tone;
    late final String title;
    Widget? body;
    VoidCallback? onTap;

    switch (n.type) {
      case 'SALARY_PAID':
        icon = Icons.payments_rounded;
        tone = Tone.success;
        title = (d['title'] as String?) ?? s.t('n_salaryPaid');
        body = Row(mainAxisSize: MainAxisSize.min, children: [
          Text('${s.t('netSalary')} ', style: const TextStyle(fontSize: 12.5, color: K.muted)),
          Money((d['net'] as num?) ?? 0, size: 12.5, color: K.muted, weight: FontWeight.w600),
        ]);
        final id = d['payslipId'] as String?;
        if (id != null) onTap = () => openPayslip(context, id);
      case 'SHIFT_UPCOMING':
        icon = Icons.delivery_dining_rounded;
        tone = Tone.brand;
        final start = d['scheduledStart'] == null ? null : DateTime.tryParse('${d['scheduledStart']}');
        title = s.f('n_shiftUpcoming', {'t': start == null ? '' : s.time(start)});
        if (d['zoneName'] != null) body = _text(s.f('n_shiftUpcomingBody', {'zone': d['zoneName']}));
        final id = d['shiftId'] as String?;
        if (id != null) {
          onTap = () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => ShiftDetailScreen(shiftId: id)));
        }
      case 'DOCUMENT_REVIEWED':
        final approved = d['status'] == 'APPROVED';
        icon = approved ? Icons.verified_rounded : Icons.cancel_rounded;
        tone = approved ? Tone.success : Tone.danger;
        title = s.f(approved ? 'n_docApproved' : 'n_docRejected', {'doc': doc});
        final note = d['note'] as String?;
        if (note != null && note.isNotEmpty) body = _text(s.f(approved ? 'reviewNote' : 'rejectNote', {'n': note}));
        onTap = () => _openDocuments(context);
      default:
        final status = d['status'] as String?;
        icon = Icons.badge_rounded;
        tone = status == 'EXPIRING' ? Tone.warning : Tone.danger;
        title = switch (status) {
          'EXPIRED' => s.f('docExpired', {'doc': doc}),
          'MISSING' => s.f('docMissing', {'doc': doc}),
          _ => s.f('docExpiresIn', {'doc': doc, 'n': d['daysLeft'] ?? 0}),
        };
        body = _text(s.t('docAlertSub'));
        onTap = () => _openDocuments(context);
    }

    final at = S.riyadh(n.at);
    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
        child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
          IconTile(icon, tone: tone, size: 42),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title, style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700)),
              if (body != null) ...[const SizedBox(height: 2), body],
              const SizedBox(height: 4),
              Text('${s.dayMonth(at)} · ${s.time(n.at)}', style: const TextStyle(fontSize: 11, color: K.muted)),
            ]),
          ),
          if (n.severity == 'HIGH')
            Container(
              margin: const EdgeInsets.only(top: 6),
              width: 8,
              height: 8,
              decoration: const BoxDecoration(color: K.danger, shape: BoxShape.circle),
            ),
        ]),
      ),
    );
  }

  static Widget _text(String t) => Text(t, style: const TextStyle(fontSize: 12.5, color: K.muted));

  static void _openDocuments(BuildContext context) {
    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const DocumentsScreen(pushed: true)));
  }
}
