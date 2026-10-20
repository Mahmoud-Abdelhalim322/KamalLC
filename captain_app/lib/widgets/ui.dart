import 'package:flutter/material.dart';

import '../api/api_client.dart';
import '../app/strings.dart';
import '../app/theme.dart';
import 'money.dart';

enum Tone { success, danger, warning, purple, info, brand, ink, neutral }

extension ToneColors on Tone {
  Color get fg => switch (this) {
        Tone.success => K.success,
        Tone.danger => K.danger,
        Tone.warning => K.warning,
        Tone.purple => K.purple,
        Tone.info => K.info,
        Tone.brand => K.brandDark,
        Tone.ink => K.brand,
        Tone.neutral => K.muted,
      };

  Color get bg => switch (this) {
        Tone.success => K.successBg,
        Tone.danger => K.dangerBg,
        Tone.warning => K.warningBg,
        Tone.purple => K.purpleBg,
        Tone.info => K.infoBg,
        Tone.brand => K.brandTint,
        Tone.ink => K.ink,
        Tone.neutral => const Color(0xFFF1EFEA),
      };
}

/// A small rounded status label, optionally with a leading dot.
class Chip2 extends StatelessWidget {
  const Chip2(this.label, {super.key, this.tone = Tone.neutral, this.dot = false});

  final String label;
  final Tone tone;
  final bool dot;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3),
      decoration: BoxDecoration(color: tone.bg, borderRadius: BorderRadius.circular(20)),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        if (dot) ...[
          Container(
            width: 6,
            height: 6,
            decoration: BoxDecoration(color: tone == Tone.ink ? K.success : tone.fg, shape: BoxShape.circle),
          ),
          const SizedBox(width: 6),
        ],
        Text(label, style: TextStyle(color: tone == Tone.ink ? Colors.white : tone.fg, fontSize: 11.5, fontWeight: FontWeight.w700)),
      ]),
    );
  }
}

/// A white rounded card.
class CardBox extends StatelessWidget {
  const CardBox({super.key, required this.child, this.padding = const EdgeInsets.all(16), this.onTap, this.border, this.color});

  final Widget child;
  final EdgeInsets padding;
  final VoidCallback? onTap;
  final Color? border;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final radius = BorderRadius.circular(K.radius);
    return Container(
      decoration: BoxDecoration(
        color: color ?? K.card,
        borderRadius: radius,
        border: Border.all(color: border ?? K.line, width: border == null ? 1 : 1.4),
        boxShadow: K.softShadow,
      ),
      child: Material(
        type: MaterialType.transparency,
        child: InkWell(borderRadius: radius, onTap: onTap, child: Padding(padding: padding, child: child)),
      ),
    );
  }
}

class SectionTitle extends StatelessWidget {
  const SectionTitle(this.title, {super.key, this.action, this.onAction});

  final String title;
  final String? action;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 26, 20, 12),
      child: Row(children: [
        Expanded(child: Text(title, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800))),
        if (action != null)
          GestureDetector(
            onTap: onAction,
            child: Text(action!, style: const TextStyle(color: K.brandDark, fontWeight: FontWeight.w700, fontSize: 13)),
          ),
      ]),
    );
  }
}

/// The ink pill of headline numbers at the bottom of a hero.
class StatStrip extends StatelessWidget {
  const StatStrip(this.items, {super.key});

  final List<(Widget value, String label)> items;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 14),
      decoration: BoxDecoration(
        color: K.ink,
        borderRadius: BorderRadius.circular(20),
        boxShadow: [BoxShadow(color: Colors.black.withValues(alpha: 0.18), blurRadius: 16, offset: const Offset(0, 8))],
      ),
      child: IntrinsicHeight(
        child: Row(children: [
          for (var i = 0; i < items.length; i++) ...[
            if (i > 0) const VerticalDivider(color: Color(0xFF3A3C44), width: 1, indent: 6, endIndent: 6),
            Expanded(
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                DefaultTextStyle.merge(
                  style: const TextStyle(color: Colors.white, fontSize: 19, fontWeight: FontWeight.w800),
                  child: items[i].$1,
                ),
                const SizedBox(height: 2),
                Text(items[i].$2, style: const TextStyle(color: Color(0xFFB4B7BF), fontSize: 11.5)),
              ]),
            ),
          ],
        ]),
      ),
    );
  }
}

/// Plain stat text for [StatStrip].
Widget statText(String value) => Text(value, maxLines: 1, overflow: TextOverflow.ellipsis);

/// A round translucent button on the yellow hero (back, bell, help, upload…).
class HeroButton extends StatelessWidget {
  const HeroButton({super.key, required this.icon, this.onTap, this.badge = 0});

  final IconData icon;
  final VoidCallback? onTap;
  final int badge;

  @override
  Widget build(BuildContext context) {
    return Stack(clipBehavior: Clip.none, children: [
      Material(
        color: Colors.white.withValues(alpha: 0.45),
        shape: const CircleBorder(),
        child: InkWell(
          customBorder: const CircleBorder(),
          onTap: onTap,
          child: SizedBox(width: 44, height: 44, child: Icon(icon, size: 22, color: K.ink)),
        ),
      ),
      if (badge > 0)
        PositionedDirectional(
          top: 2,
          end: 2,
          child: Container(
            constraints: const BoxConstraints(minWidth: 16),
            height: 16,
            padding: const EdgeInsets.symmetric(horizontal: 4),
            decoration: BoxDecoration(color: K.danger, borderRadius: BorderRadius.circular(8), border: Border.all(color: K.brand, width: 1.5)),
            alignment: Alignment.center,
            child: Text('$badge', style: const TextStyle(color: Colors.white, fontSize: 9, fontWeight: FontWeight.w800, height: 1)),
          ),
        ),
    ]);
  }
}

/// The back button for pushed screens. The icon mirrors itself in RTL.
class BackHeroButton extends StatelessWidget {
  const BackHeroButton({super.key});

  @override
  Widget build(BuildContext context) => HeroButton(
        icon: Icons.arrow_back_rounded,
        onTap: () => Navigator.of(context).maybePop(),
      );
}

/// A rounded square icon on a tinted background.
class IconTile extends StatelessWidget {
  const IconTile(this.icon, {super.key, this.tone = Tone.brand, this.size = 40, this.filled = false});

  final IconData icon;
  final Tone tone;
  final double size;
  final bool filled;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: filled ? K.brand : tone.bg,
        borderRadius: BorderRadius.circular(size * 0.3),
      ),
      child: Icon(icon, size: size * 0.52, color: filled ? K.ink : (tone == Tone.brand ? K.brandDark : tone.fg)),
    );
  }
}

/// The main call to action: a yellow gradient pill.
class PrimaryButton extends StatelessWidget {
  const PrimaryButton({super.key, required this.label, this.icon, this.onTap, this.busy = false});

  final String label;
  final IconData? icon;
  final VoidCallback? onTap;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final enabled = onTap != null && !busy;
    return Opacity(
      opacity: enabled || busy ? 1 : 0.5,
      child: Container(
        height: 56,
        decoration: BoxDecoration(
          gradient: K.buttonGradient,
          borderRadius: BorderRadius.circular(18),
          boxShadow: [BoxShadow(color: K.brand.withValues(alpha: 0.35), blurRadius: 18, offset: const Offset(0, 8))],
        ),
        child: Material(
          type: MaterialType.transparency,
          child: InkWell(
            borderRadius: BorderRadius.circular(18),
            onTap: enabled ? onTap : null,
            child: Center(
              child: busy
                  ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.4, color: K.ink))
                  : Row(mainAxisSize: MainAxisSize.min, children: [
                      if (icon != null) ...[Icon(icon, color: K.ink, size: 20), const SizedBox(width: 10)],
                      Text(label, style: const TextStyle(color: K.ink, fontSize: 16, fontWeight: FontWeight.w800)),
                    ]),
            ),
          ),
        ),
      ),
    );
  }
}

/// A dark action button inside cards ("Upload the renewed document").
class InkButton extends StatelessWidget {
  const InkButton({super.key, required this.label, this.icon, this.onTap});

  final String label;
  final IconData? icon;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: K.ink,
      borderRadius: BorderRadius.circular(16),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap,
        child: SizedBox(
          height: 48,
          child: Center(
            child: Row(mainAxisSize: MainAxisSize.min, children: [
              if (icon != null) ...[Icon(icon, color: K.brand, size: 18), const SizedBox(width: 8)],
              Flexible(
                child: Text(label,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: K.brand, fontSize: 14, fontWeight: FontWeight.w800)),
              ),
            ]),
          ),
        ),
      ),
    );
  }
}

/// A route-style progress bar: dashed track behind, solid fill, and a scooter
/// marker riding at the current position.
class RouteProgress extends StatelessWidget {
  const RouteProgress({super.key, required this.value, this.dark = false, this.marker = Icons.delivery_dining_rounded});

  final double value;
  /// Ink fill on the yellow hero instead of yellow on white.
  final bool dark;
  final IconData marker;

  @override
  Widget build(BuildContext context) {
    final v = value.clamp(0.0, 1.0);
    final rtl = Directionality.of(context) == TextDirection.rtl;
    return SizedBox(
      height: 34,
      child: LayoutBuilder(builder: (context, c) {
        final w = c.maxWidth;
        final x = 15 + (w - 30) * v;
        return Stack(alignment: Alignment.centerLeft, children: [
          Positioned.fill(child: CustomPaint(painter: _DashedTrack(dark: dark))),
          PositionedDirectional(
            start: 0,
            child: Container(
              width: x,
              height: 6,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(3),
                color: dark ? K.ink : null,
                gradient: dark ? null : LinearGradient(colors: rtl ? [K.brand, K.brandDark] : [K.brandDark, K.brand]),
              ),
            ),
          ),
          PositionedDirectional(
            start: 4,
            child: Icon(Icons.flag_rounded, size: 14, color: dark ? K.ink.withValues(alpha: 0.5) : K.muted),
          ),
          PositionedDirectional(
            start: x - 15,
            child: Container(
              width: 30,
              height: 30,
              decoration: BoxDecoration(
                color: dark ? K.ink : K.brand,
                shape: BoxShape.circle,
                border: Border.all(color: dark ? K.brand : K.ink, width: 2),
              ),
              child: Transform.flip(flipX: rtl, child: Icon(marker, size: 17, color: dark ? K.brand : K.ink)),
            ),
          ),
        ]);
      }),
    );
  }
}

class _DashedTrack extends CustomPainter {
  _DashedTrack({required this.dark});

  final bool dark;

  @override
  void paint(Canvas canvas, Size size) {
    final p = Paint()
      ..color = dark ? K.ink.withValues(alpha: 0.35) : const Color(0xFFD9D5CC)
      ..strokeWidth = 2
      ..strokeCap = StrokeCap.round;
    final y = size.height / 2;
    for (double x = 0; x < size.width; x += 8) {
      canvas.drawLine(Offset(x, y), Offset(x + 3, y), p);
    }
  }

  @override
  bool shouldRepaint(covariant _DashedTrack old) => old.dark != dark;
}

/// A thin progress bar, used for income shares and month progress.
class Bar extends StatelessWidget {
  const Bar({super.key, required this.value, this.color = K.brand, this.track = const Color(0xFFF1EEE6), this.height = 6});

  final double value;
  final Color color;
  final Color track;
  final double height;

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(height),
      child: SizedBox(
        width: double.infinity,
        height: height,
        child: Stack(children: [
          Positioned.fill(child: ColoredBox(color: track)),
          FractionallySizedBox(
            alignment: AlignmentDirectional.centerStart,
            widthFactor: value.clamp(0.0, 1.0),
            heightFactor: 1,
            child: ColoredBox(color: color),
          ),
        ]),
      ),
    );
  }
}

/// A tinted note box with an icon ("Your supervisor reviews it within 24 hours").
class NoteBox extends StatelessWidget {
  const NoteBox(this.text, {super.key, this.icon = Icons.info_rounded, this.tone = Tone.brand});

  final String text;
  final IconData icon;
  final Tone tone;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(color: tone.bg, borderRadius: BorderRadius.circular(16)),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Icon(icon, size: 20, color: tone == Tone.brand ? K.brandDark : tone.fg),
        const SizedBox(width: 10),
        Expanded(child: Text(text, style: TextStyle(fontSize: 12.5, color: tone == Tone.brand ? K.text : tone.fg, height: 1.5))),
      ]),
    );
  }
}

/// The gradient total bar at the bottom of a pay breakdown.
class TotalBar extends StatelessWidget {
  const TotalBar({super.key, required this.label, required this.value});

  final String label;
  final double value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 14),
      decoration: BoxDecoration(gradient: K.buttonGradient, borderRadius: BorderRadius.circular(16)),
      child: Row(children: [
        Expanded(child: Text(label, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w800, color: K.ink))),
        Money(value, size: 18, weight: FontWeight.w900, color: K.ink),
      ]),
    );
  }
}

/// A label · amount line in a breakdown.
class AmountRow extends StatelessWidget {
  const AmountRow({super.key, required this.label, required this.value, this.color, this.signed = false, this.bold = false});

  final String label;
  final double value;
  final Color? color;
  final bool signed;
  final bool bold;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 9),
      child: Row(children: [
        Expanded(
          child: Text(label,
              style: TextStyle(fontSize: 13.5, color: bold ? K.text : const Color(0xFF555A64), fontWeight: bold ? FontWeight.w700 : FontWeight.w500)),
        ),
        Money(value, size: 14, color: color ?? K.text, signed: signed, weight: bold ? FontWeight.w800 : FontWeight.w600),
      ]),
    );
  }
}

/// Loads [load] and shows [builder] with pull-to-refresh, a skeleton while
/// waiting and a retry card on failure.
class Loader<T> extends StatefulWidget {
  const Loader({super.key, required this.load, required this.builder});

  final Future<T> Function() load;
  final Widget Function(BuildContext context, T data, Future<void> Function() reload) builder;

  @override
  State<Loader<T>> createState() => LoaderState<T>();
}

class LoaderState<T> extends State<Loader<T>> {
  T? _data;
  Object? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    reload();
  }

  Future<void> reload() async {
    setState(() => _loading = true);
    try {
      final data = await widget.load();
      if (!mounted) return;
      setState(() {
        _data = data;
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

  @override
  Widget build(BuildContext context) {
    final data = _data;
    if (data != null) {
      return RefreshIndicator(
        color: K.ink,
        backgroundColor: K.brand,
        onRefresh: reload,
        child: widget.builder(context, data, reload),
      );
    }
    if (_loading) return const Center(child: CircularProgressIndicator(color: K.brand));
    return ErrorView(error: _error, onRetry: reload);
  }
}

class ErrorView extends StatelessWidget {
  const ErrorView({super.key, this.error, required this.onRetry});

  final Object? error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const IconTile(Icons.wifi_off_rounded, size: 64, tone: Tone.brand),
          const SizedBox(height: 16),
          Text(s.t('loadFailed'), style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          Text(errorText(s, error), textAlign: TextAlign.center, style: const TextStyle(color: K.muted)),
          const SizedBox(height: 20),
          SizedBox(width: 200, child: InkButton(label: s.t('retry'), icon: Icons.refresh_rounded, onTap: onRetry)),
        ]),
      ),
    );
  }
}

/// What to tell the captain about a failed call. Server messages are Arabic, so
/// English falls back to a generic line for codes the app does not know.
String errorText(S s, Object? error) {
  if (error is ApiException) {
    if (error.isNetwork) return s.t('offline');
    if (error.isUnauthorized) return s.t('sessionEnded');
    if (s.isAr && error.message.isNotEmpty) return error.message;
    final known = _errorsEn[error.code];
    if (known != null) return known;
    return error.message.isNotEmpty && s.isAr ? error.message : 'Something went wrong (${error.code})';
  }
  return s.t('loadFailed');
}

const _errorsEn = {
  'INVALID_PHONE': 'That mobile number is not valid',
  'PHONE_NOT_REGISTERED': 'This number is not registered with the company. Contact your supervisor',
  'PHONE_AMBIGUOUS': 'This number is registered to more than one captain. Ask your supervisor to fix it',
  'OTP_TOO_SOON': 'Please wait a little before requesting another code',
  'RATE_LIMITED': 'Too many attempts. Try again later',
  'OTP_EXPIRED': 'The code has expired. Request a new one',
  'OTP_INVALID': 'The code is not correct',
  'CAPTAIN_INACTIVE': 'Your account is not active. Contact your supervisor',
  'INVALID_EXPIRY': 'The new expiry date must be in the future',
  'FRONT_REQUIRED': 'The front photo is required',
  'REQUEST_PENDING': 'An update for this document is already under review',
  'INVALID_DOCUMENT_KIND': 'Unknown document type',
  'FUTURE_MONTH': 'That month has not started yet',
  'SHIFT_NOT_FOUND': 'Shift not found',
  'PAYSLIP_NOT_FOUND': 'Payslip not found',
};

void showError(BuildContext context, Object error) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(errorText(S.read(context), error))));
}

/// Initials from a name ("فيصل الشهري" → "ف ش").
String initials(String name) {
  final parts = name.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
  if (parts.isEmpty) return '';
  if (parts.length == 1) return parts.first.characters.first;
  return '${parts.first.characters.first} ${parts.last.characters.first}';
}

class Avatar extends StatelessWidget {
  const Avatar({super.key, required this.name, this.size = 48});

  final String name;
  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        color: K.ink,
        shape: BoxShape.circle,
        border: Border.all(color: Colors.white.withValues(alpha: 0.8), width: 2),
      ),
      alignment: Alignment.center,
      child: Text(initials(name),
          style: TextStyle(color: K.brand, fontWeight: FontWeight.w800, fontSize: size * 0.3, height: 1.1)),
    );
  }
}

/// Bottom sheet chrome: grab handle, rounded top, safe padding.
Future<T?> showSheet<T>(BuildContext context, Widget child) {
  return showModalBottomSheet<T>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
    builder: (context) => Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 10, 20, 24),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                margin: const EdgeInsets.only(bottom: 18),
                decoration: BoxDecoration(color: K.line, borderRadius: BorderRadius.circular(2)),
              ),
            ),
            child,
          ]),
        ),
      ),
    ),
  );
}

/// A simple info sheet with a title, body and close button.
Future<void> showInfoSheet(BuildContext context, {required IconData icon, required String title, required String body}) {
  final s = S.read(context);
  return showSheet(
    context,
    Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Row(children: [
        IconTile(icon, size: 46),
        const SizedBox(width: 12),
        Expanded(child: Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800))),
      ]),
      const SizedBox(height: 16),
      Text(body, style: const TextStyle(fontSize: 14, height: 1.7, color: Color(0xFF555A64))),
      const SizedBox(height: 24),
      Builder(builder: (context) => InkButton(label: s.t('ok'), onTap: () => Navigator.pop(context))),
    ]),
  );
}

String percent(double? fraction) => fraction == null ? '—' : '${(fraction * 100).round()}%';

String num1(double? v) => v == null ? '—' : (v == v.roundToDouble() ? v.toInt().toString() : v.toStringAsFixed(1));
