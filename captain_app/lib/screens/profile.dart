import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app/locale_controller.dart';
import '../app/session.dart';
import '../app/strings.dart';
import '../app/theme.dart';
import '../widgets/page.dart';
import '../widgets/ui.dart';
import 'notifications.dart';

class ProfileScreen extends StatelessWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final session = context.read<Session>();
    return Loader<MeResponse>(
      load: () async {
        final me = await session.api.me();
        session.updateProfile(me.profile);
        return me;
      },
      builder: (context, me, _) => _Body(me),
    );
  }
}

class _Body extends StatelessWidget {
  const _Body(this.me);

  final MeResponse me;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final p = me.profile;
    final st = me.stats;
    final chevron = Icon(Icons.chevron_right_rounded, color: K.muted);
    final secondName = s.isAr ? p.fullNameEn : p.fullNameAr;
    final type = switch (p.captainType?.toUpperCase()) {
      'EMPLOYEE' || 'EMPLOYEES' => s.t('employee'),
      'FREELANCER' || 'FREELANCE' => s.t('freelancer'),
      final t => t,
    };

    return HeroPage(
      bottom: dockClearance,
      hero: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        HeroTitle(title: s.t('myAccount')),
        const SizedBox(height: 16),
        Row(children: [
          Avatar(name: s.name(p.fullNameAr, p.fullNameEn), size: 68),
          const SizedBox(width: 14),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(s.name(p.fullNameAr, p.fullNameEn),
                  style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w900, color: K.ink)),
              if (secondName != null && secondName.isNotEmpty)
                Text(secondName, style: TextStyle(fontSize: 12, color: K.ink.withValues(alpha: 0.65))),
              const SizedBox(height: 6),
              Wrap(spacing: 6, runSpacing: 4, crossAxisAlignment: WrapCrossAlignment.center, children: [
                if (p.status == null || p.status == 'ACTIVE') Chip2(s.t('active'), tone: Tone.ink, dot: true),
                if (type != null) Text(type, style: const TextStyle(fontSize: 12, color: K.ink)),
                Text(p.kamalCode, style: TextStyle(fontSize: 12, color: K.ink.withValues(alpha: 0.7))),
              ]),
            ]),
          ),
        ]),
        const SizedBox(height: 16),
        StatStrip([
          (statText('${st.ordersLastMonth}'), s.t('ordersLastMonth')),
          (statText(st.reliabilityPercent == null ? '—' : '${st.reliabilityPercent}%'), s.t('reliability')),
          (statText(num1(st.yearsOfService)), s.t('yearsOfService')),
        ]),
      ]),
      children: [
        SectionTitle(s.t('myInfo')),
        padded(CardBox(
          padding: EdgeInsets.zero,
          child: Column(children: [
            _InfoRow(Icons.phone_iphone_rounded, s.t('phone'), p.phone, ltr: true),
            _InfoRow(Icons.qr_code_2_rounded, s.t('appId'), p.appId, ltr: true),
            _InfoRow(Icons.supervisor_account_rounded, s.t('supervisor'), p.supervisor),
            _InfoRow(Icons.location_on_rounded, s.t('workLocation'), p.workLocation),
            _InfoRow(
              Icons.account_balance_rounded,
              s.t('salaryAccount'),
              p.bankAccountLast4 == null ? null : '${p.bankName ?? ''} •••• ${p.bankAccountLast4}'.trim(),
              last: true,
            ),
          ]),
        )),
        if (me.custody.isNotEmpty) ...[
          SectionTitle(s.t('custody')),
          padded(CardBox(
            padding: EdgeInsets.zero,
            child: Column(children: [
              for (var i = 0; i < me.custody.length; i++) ...[
                if (i > 0) const Divider(height: 1, color: K.line),
                _CustodyRow(me.custody[i]),
              ],
            ]),
          )),
        ],
        const SizedBox(height: 18),
        padded(CardBox(
          padding: EdgeInsets.zero,
          child: Column(children: [
            _SettingRow(
              icon: Icons.translate_rounded,
              label: s.t('language'),
              trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                Text(s.t('otherLanguage'), style: const TextStyle(fontSize: 12.5, color: K.muted)),
                chevron,
              ]),
              onTap: () => context.read<LocaleController>().toggle(),
            ),
            _SettingRow(
              icon: Icons.notifications_none_rounded,
              label: s.t('notifications'),
              trailing: chevron,
              onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const NotificationsScreen())),
            ),
            _SettingRow(
              icon: Icons.help_outline_rounded,
              label: s.t('help'),
              trailing: chevron,
              onTap: () => showInfoSheet(context, icon: Icons.support_agent_rounded, title: s.t('help'), body: s.t('helpBody')),
            ),
            _SettingRow(
              icon: Icons.logout_rounded,
              label: s.t('logout'),
              danger: true,
              last: true,
              onTap: () => _logout(context),
            ),
          ]),
        )),
        const SizedBox(height: 16),
        Center(
          child: Text('${s.t('appName')} · ${s.t('version')} 1.0.0', style: const TextStyle(fontSize: 11.5, color: K.muted)),
        ),
      ],
    );
  }

  Future<void> _logout(BuildContext context) async {
    final s = S.read(context);
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        backgroundColor: Colors.white,
        title: Text(s.t('logoutConfirm'), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(s.t('cancel'), style: const TextStyle(color: K.muted))),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(s.t('logout'), style: const TextStyle(color: K.danger, fontWeight: FontWeight.w700)),
          ),
        ],
      ),
    );
    if (ok == true && context.mounted) context.read<Session>().signOut();
  }
}

class _InfoRow extends StatelessWidget {
  const _InfoRow(this.icon, this.label, this.value, {this.ltr = false, this.last = false});

  final IconData icon;
  final String label;
  final String? value;
  final bool ltr;
  final bool last;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return Container(
      decoration: BoxDecoration(border: last ? null : const Border(bottom: BorderSide(color: K.line))),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 13),
      child: Row(children: [
        IconTile(icon, size: 36),
        const SizedBox(width: 12),
        Text(label, style: const TextStyle(fontSize: 13, color: K.muted)),
        const SizedBox(width: 12),
        Expanded(
          child: Text(
            value == null || value!.isEmpty ? s.t('notSet') : value!,
            textAlign: TextAlign.end,
            textDirection: ltr ? TextDirection.ltr : null,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: TextStyle(fontSize: 14, fontWeight: FontWeight.w700, color: value == null ? K.muted : K.text),
          ),
        ),
      ]),
    );
  }
}

class _CustodyRow extends StatelessWidget {
  const _CustodyRow(this.c);

  final CustodyItem c;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final icon = switch (c.assetType?.toUpperCase()) {
      'VEHICLE' || 'CAR' || 'MOTORCYCLE' || 'BIKE' => Icons.two_wheeler_rounded,
      'PHONE' || 'MOBILE' || 'DEVICE' => Icons.smartphone_rounded,
      'BAG' => Icons.shopping_bag_rounded,
      _ => Icons.inventory_2_rounded,
    };
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Row(children: [
        Container(
          width: 42,
          height: 42,
          decoration: const BoxDecoration(color: K.ink, shape: BoxShape.circle),
          child: Icon(icon, color: K.brand, size: 20),
        ),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(c.assetLabel ?? c.assetType ?? '—', style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700)),
            Text(
              [if (c.notes != null && c.notes!.isNotEmpty) c.notes!, if (c.assignedAt != null) s.f('since', {'d': s.numericDate(S.riyadh(c.assignedAt!))})]
                  .join(' · '),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 12, color: K.muted),
            ),
          ]),
        ),
        Chip2(s.t('inYourCustody'), tone: Tone.warning),
      ]),
    );
  }
}

class _SettingRow extends StatelessWidget {
  const _SettingRow({required this.icon, required this.label, required this.onTap, this.trailing, this.danger = false, this.last = false});

  final IconData icon;
  final String label;
  final VoidCallback onTap;
  final Widget? trailing;
  final bool danger;
  final bool last;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      child: Container(
        decoration: BoxDecoration(border: last ? null : const Border(bottom: BorderSide(color: K.line))),
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        child: Row(children: [
          IconTile(icon, size: 36, tone: danger ? Tone.danger : Tone.neutral),
          const SizedBox(width: 12),
          Expanded(
            child: Text(label,
                style: TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700, color: danger ? K.danger : K.text)),
          ),
          ?trailing,
        ]),
      ),
    );
  }
}
