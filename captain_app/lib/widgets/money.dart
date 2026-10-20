import 'package:flutter/material.dart';
import 'package:intl/intl.dart' show NumberFormat;

/// U+20C1 SAUDI RIYAL SIGN, drawn by the bundled SaudiRiyal font.
const riyalSign = '⃁';

final _amount = NumberFormat('#,##0.00', 'en_US');
final _whole = NumberFormat('#,##0', 'en_US');

String formatAmount(num value, {bool whole = false}) => (whole ? _whole : _amount).format(value.abs());

/// An amount with the riyal sign on its left, as SAMA's usage guide asks, in both
/// languages. [signed] prefixes + or − (a negative amount always shows −).
class Money extends StatelessWidget {
  const Money(this.value,
      {super.key, this.size = 15, this.weight = FontWeight.w700, this.color, this.signed = false, this.whole = false});

  final num value;
  final double size;
  final FontWeight weight;
  final Color? color;
  final bool signed;
  final bool whole;

  @override
  Widget build(BuildContext context) {
    final style = DefaultTextStyle.of(context).style.copyWith(
          fontSize: size,
          fontWeight: weight,
          color: color ?? DefaultTextStyle.of(context).style.color,
          height: 1.1,
          fontFeatures: const [FontFeature.tabularFigures()],
        );
    final prefix = value < 0 ? '−' : (signed && value > 0 ? '+' : '');
    return Directionality(
      textDirection: TextDirection.ltr,
      child: Text.rich(
        TextSpan(children: [
          TextSpan(
            text: riyalSign,
            style: style.copyWith(fontFamily: 'SaudiRiyal', fontSize: size * 0.92),
          ),
          TextSpan(text: ' $prefix${formatAmount(value, whole: whole)}', style: style),
        ]),
        maxLines: 1,
      ),
    );
  }
}
