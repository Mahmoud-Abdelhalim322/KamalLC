import 'package:flutter/material.dart';

import '../app/strings.dart';
import '../app/theme.dart';
import '../widgets/hero.dart';

/// Shown while the stored session is checked.
class SplashScreen extends StatelessWidget {
  const SplashScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return Scaffold(
      body: DecoratedBox(
        decoration: const BoxDecoration(gradient: K.heroGradient),
        child: Stack(children: [
          const Positioned.fill(child: CustomPaint(painter: RouteBackdrop(strong: true))),
          SafeArea(
            child: Column(children: [
              const Spacer(flex: 3),
              const Center(child: LogoTile(size: 112)),
              const SizedBox(height: 24),
              Text(s.t('appName'), style: const TextStyle(fontSize: 30, fontWeight: FontWeight.w900, color: K.ink)),
              const SizedBox(height: 4),
              Text(s.t('appTagline'), style: TextStyle(fontSize: 14, color: K.ink.withValues(alpha: 0.7))),
              const Spacer(flex: 3),
              SizedBox(
                width: 120,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(3),
                  child: LinearProgressIndicator(
                    minHeight: 5,
                    color: K.ink,
                    backgroundColor: Colors.white.withValues(alpha: 0.45),
                  ),
                ),
              ),
              const SizedBox(height: 18),
              Text(s.t('company'),
                  textAlign: TextAlign.center, style: TextStyle(fontSize: 11.5, color: K.ink.withValues(alpha: 0.6))),
              const SizedBox(height: 20),
            ]),
          ),
        ]),
      ),
    );
  }
}
