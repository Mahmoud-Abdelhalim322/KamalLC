import 'package:flutter/material.dart';

import '../app/strings.dart';
import '../app/theme.dart';
import 'documents.dart';
import 'earnings.dart';
import 'home.dart';
import 'profile.dart';
import 'shifts.dart';

/// The signed-in app: five tabs under the floating dark dock.
class Shell extends StatefulWidget {
  const Shell({super.key});

  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  int _tab = 0;

  /// Tabs are built on first visit and kept alive afterwards.
  final _visited = <int>{0};

  void _go(int i) => setState(() {
        _tab = i;
        _visited.add(i);
      });

  @override
  Widget build(BuildContext context) {
    final pages = <Widget Function()>[
      () => HomeScreen(onTab: _go),
      () => const ShiftsScreen(),
      () => const EarningsScreen(),
      () => const DocumentsScreen(),
      () => const ProfileScreen(),
    ];
    return Scaffold(
      extendBody: true,
      body: IndexedStack(
        index: _tab,
        children: [
          for (var i = 0; i < pages.length; i++) _visited.contains(i) ? pages[i]() : const SizedBox.shrink(),
        ],
      ),
      bottomNavigationBar: _Dock(index: _tab, onTap: _go),
    );
  }
}

class _Dock extends StatelessWidget {
  const _Dock({required this.index, required this.onTap});

  final int index;
  final ValueChanged<int> onTap;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final items = [
      (Icons.home_rounded, s.t('navHome')),
      (Icons.calendar_month_rounded, s.t('navShifts')),
      (Icons.account_balance_wallet_rounded, s.t('navEarnings')),
      (Icons.description_rounded, s.t('navDocuments')),
      (Icons.person_rounded, s.t('navProfile')),
    ];
    return SafeArea(
      top: false,
      minimum: const EdgeInsets.only(bottom: 12),
      child: Container(
        margin: const EdgeInsets.fromLTRB(14, 0, 14, 4),
        height: 68,
        padding: const EdgeInsets.all(8),
        decoration: BoxDecoration(
          color: K.ink,
          borderRadius: BorderRadius.circular(26),
          boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.25), blurRadius: 24, offset: const Offset(0, 10))],
        ),
        child: Row(children: [
          for (var i = 0; i < items.length; i++)
            Expanded(
              flex: i == index ? 5 : 3,
              child: _DockItem(icon: items[i].$1, label: items[i].$2, active: i == index, onTap: () => onTap(i)),
            ),
        ]),
      ),
    );
  }
}

class _DockItem extends StatelessWidget {
  const _DockItem({required this.icon, required this.label, required this.active, required this.onTap});

  final IconData icon;
  final String label;
  final bool active;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      selected: active,
      button: true,
      label: label,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: onTap,
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 220),
          curve: Curves.easeOut,
          decoration: BoxDecoration(
            gradient: active ? K.buttonGradient : null,
            borderRadius: BorderRadius.circular(19),
          ),
          alignment: Alignment.center,
          child: active
              ? Row(mainAxisSize: MainAxisSize.min, children: [
                  Icon(icon, size: 20, color: K.ink),
                  const SizedBox(width: 6),
                  Flexible(
                    child: Text(label,
                        maxLines: 1,
                        overflow: TextOverflow.fade,
                        softWrap: false,
                        style: const TextStyle(color: K.ink, fontSize: 12.5, fontWeight: FontWeight.w800)),
                  ),
                ])
              : Icon(icon, size: 22, color: Colors.white.withValues(alpha: 0.6)),
        ),
      ),
    );
  }
}
