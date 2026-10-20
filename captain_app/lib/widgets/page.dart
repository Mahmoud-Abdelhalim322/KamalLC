import 'package:flutter/material.dart';

import '../app/theme.dart';
import 'hero.dart';
import 'ui.dart';

/// Room under the content for the floating dock.
const dockClearance = 112.0;

/// A scrolling page that opens with the yellow hero.
class HeroPage extends StatelessWidget {
  const HeroPage({super.key, required this.hero, required this.children, this.bottom = 32});

  final Widget hero;
  final List<Widget> children;
  final double bottom;

  @override
  Widget build(BuildContext context) {
    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: EdgeInsets.only(bottom: bottom + MediaQuery.paddingOf(context).bottom),
      children: [HeroHeader(child: hero), ...children],
    );
  }
}

/// The title line of a hero: optional leading button, title over subtitle, and
/// trailing buttons.
class HeroTitle extends StatelessWidget {
  const HeroTitle({super.key, required this.title, this.subtitle, this.leading, this.trailing = const [], this.subtitleFirst = false});

  final String title;
  final String? subtitle;
  final Widget? leading;
  final List<Widget> trailing;
  /// Small eyebrow above the title instead of a caption below it.
  final bool subtitleFirst;

  @override
  Widget build(BuildContext context) {
    final sub = subtitle == null
        ? null
        : Text(subtitle!, maxLines: 1, overflow: TextOverflow.ellipsis,
            style: TextStyle(fontSize: 12.5, color: K.ink.withValues(alpha: 0.65)));
    return Row(children: [
      if (leading != null) ...[leading!, const SizedBox(width: 12)],
      Expanded(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
          if (subtitleFirst && sub != null) sub,
          Text(title, maxLines: 1, overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w900, color: K.ink, height: 1.3)),
          if (!subtitleFirst && sub != null) sub,
        ]),
      ),
      for (final t in trailing) ...[const SizedBox(width: 8), t],
    ]);
  }
}

/// Horizontal page padding for content below the hero.
Widget padded(Widget child) => Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: child);

/// An empty state inside a card.
class EmptyCard extends StatelessWidget {
  const EmptyCard({super.key, required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) {
    return CardBox(
      padding: const EdgeInsets.symmetric(vertical: 28, horizontal: 20),
      child: Column(children: [
        IconTile(icon, size: 52),
        const SizedBox(height: 12),
        Text(text, textAlign: TextAlign.center, style: const TextStyle(color: K.muted, fontSize: 13.5)),
      ]),
    );
  }
}

/// A small metric tile: icon + label on top, big value, optional caption.
class MetricTile extends StatelessWidget {
  const MetricTile({super.key, required this.icon, required this.label, required this.value, this.tone = Tone.brand, this.trailing});

  final IconData icon;
  final String label;
  final String value;
  final Tone tone;
  final Widget? trailing;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(14),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(children: [
          Icon(icon, size: 16, color: tone == Tone.brand ? K.brandDark : tone.fg),
          const SizedBox(width: 6),
          Expanded(child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 12, color: K.muted))),
        ]),
        const SizedBox(height: 8),
        Row(children: [
          Text(value, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, height: 1.1)),
          if (trailing != null) ...[const Spacer(), trailing!],
        ]),
      ]),
    );
  }
}

/// Four [MetricTile]s in a 2×2 card with hairline dividers.
class MetricGrid extends StatelessWidget {
  const MetricGrid(this.tiles, {super.key});

  final List<Widget> tiles;

  @override
  Widget build(BuildContext context) {
    Widget row(List<Widget> items) => IntrinsicHeight(
          child: Row(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            Expanded(child: items[0]),
            const VerticalDivider(width: 1, color: K.line),
            Expanded(child: items.length > 1 ? items[1] : const SizedBox()),
          ]),
        );
    return CardBox(
      padding: EdgeInsets.zero,
      child: Column(children: [
        row(tiles.sublist(0, tiles.length < 2 ? tiles.length : 2)),
        if (tiles.length > 2) ...[
          const Divider(height: 1, color: K.line),
          row(tiles.sublist(2)),
        ],
      ]),
    );
  }
}
