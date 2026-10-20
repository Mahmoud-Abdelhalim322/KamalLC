import 'package:flutter/material.dart';

import '../app/theme.dart';

/// The yellow header every screen opens with: brand gradient, two faint road bands
/// and a dotted delivery route ending at a pin.
class HeroHeader extends StatelessWidget {
  const HeroHeader({super.key, required this.child, this.padding = const EdgeInsets.fromLTRB(20, 12, 20, 24)});

  final Widget child;
  final EdgeInsets padding;

  @override
  Widget build(BuildContext context) {
    final top = MediaQuery.paddingOf(context).top;
    return ClipRRect(
      borderRadius: const BorderRadius.vertical(bottom: Radius.circular(36)),
      child: DecoratedBox(
        decoration: const BoxDecoration(gradient: K.heroGradient),
        child: Stack(
          children: [
            const Positioned.fill(child: CustomPaint(painter: RouteBackdrop())),
            Padding(padding: padding.copyWith(top: padding.top + top), child: child),
          ],
        ),
      ),
    );
  }
}

class RouteBackdrop extends CustomPainter {
  const RouteBackdrop({this.strong = false});

  /// Brighter lines for the full-screen splash.
  final bool strong;

  @override
  void paint(Canvas canvas, Size size) {
    final w = size.width, h = size.height;
    final band = Paint()
      ..color = Colors.white.withValues(alpha: strong ? 0.22 : 0.18)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 22;
    canvas.drawLine(Offset(-20, h * 0.30), Offset(w + 20, h * 0.62), band);
    canvas.drawLine(Offset(-20, h * 0.86), Offset(w + 20, h * 0.48), band..strokeWidth = 16);

    final route = Path()
      ..moveTo(w * 0.86, h * 0.18)
      ..cubicTo(w * 0.80, h * 0.42, w * 0.55, h * 0.30, w * 0.48, h * 0.55)
      ..cubicTo(w * 0.42, h * 0.75, w * 0.20, h * 0.70, w * 0.08, h * 0.95);
    final dot = Paint()..color = const Color(0xFF8A5A00).withValues(alpha: strong ? 0.35 : 0.28);
    for (final metric in route.computeMetrics()) {
      for (double d = 0; d < metric.length; d += 9) {
        final p = metric.getTangentForOffset(d)!.position;
        canvas.drawCircle(p, 1.6, dot);
      }
    }

    // Pin at the route's end.
    final pin = Offset(w * 0.86, h * 0.18 - 10);
    final pinPaint = Paint()..color = const Color(0xFF8A5A00).withValues(alpha: strong ? 0.4 : 0.3);
    final pinPath = Path()
      ..addOval(Rect.fromCircle(center: pin, radius: 9))
      ..moveTo(pin.dx - 7, pin.dy + 5)
      ..lineTo(pin.dx, pin.dy + 17)
      ..lineTo(pin.dx + 7, pin.dy + 5)
      ..close();
    canvas.drawPath(pinPath, pinPaint);
    canvas.drawCircle(pin, 3.5, Paint()..color = K.brand);
  }

  @override
  bool shouldRepaint(covariant RouteBackdrop old) => old.strong != strong;
}

/// The dark rounded tile with the yellow Kamal mark.
class LogoTile extends StatelessWidget {
  const LogoTile({super.key, this.size = 88});

  final double size;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      padding: EdgeInsets.all(size * 0.2),
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(size * 0.26),
        gradient: const LinearGradient(
          begin: Alignment.topCenter,
          end: Alignment.bottomCenter,
          colors: [Color(0xFF3A3326), K.ink],
        ),
        boxShadow: [
          BoxShadow(color: const Color(0xFF6B4A00).withValues(alpha: 0.35), blurRadius: 30, offset: const Offset(0, 14)),
        ],
      ),
      child: Image.asset('assets/images/mark.png', fit: BoxFit.contain),
    );
  }
}
