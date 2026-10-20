import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:provider/provider.dart';

import 'app/locale_controller.dart';
import 'app/session.dart';
import 'app/theme.dart';
import 'screens/login.dart';
import 'screens/shell.dart';
import 'screens/splash.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final locale = LocaleController();
  await locale.load();
  runApp(MultiProvider(
    providers: [
      ChangeNotifierProvider.value(value: locale),
      ChangeNotifierProvider(create: (_) => Session()),
    ],
    child: const CaptainApp(),
  ));
}

class CaptainApp extends StatefulWidget {
  const CaptainApp({super.key});

  @override
  State<CaptainApp> createState() => _CaptainAppState();
}

class _CaptainAppState extends State<CaptainApp> {
  /// The splash stays up at least this long so it does not just flash by.
  bool _splashDone = false;

  final _navigator = GlobalKey<NavigatorState>();
  SessionStatus? _lastStatus;

  @override
  void initState() {
    super.initState();
    final session = context.read<Session>();
    Future.wait([
      session.restore(),
      Future<void>.delayed(const Duration(milliseconds: 1400)),
    ]).whenComplete(() {
      if (mounted) setState(() => _splashDone = true);
    });
  }

  @override
  Widget build(BuildContext context) {
    final locale = context.watch<LocaleController>().locale;
    final status = context.select<Session, SessionStatus>((s) => s.status);
    // Signing out (or an expired token) closes any screen pushed over the shell.
    if (_lastStatus == SessionStatus.signedIn && status != SessionStatus.signedIn) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _navigator.currentState?.popUntil((r) => r.isFirst));
    }
    _lastStatus = status;
    final Widget home = !_splashDone || status == SessionStatus.restoring
        ? const SplashScreen()
        : status == SessionStatus.signedIn
            ? const Shell()
            : const LoginScreen();
    return MaterialApp(
      title: locale.languageCode == 'ar' ? 'كمال كابتن' : 'Kamal Captain',
      navigatorKey: _navigator,
      debugShowCheckedModeBanner: false,
      locale: locale,
      supportedLocales: const [Locale('ar'), Locale('en')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme: K.theme(locale),
      home: AnimatedSwitcher(
        duration: const Duration(milliseconds: 350),
        child: KeyedSubtree(key: ValueKey(home.runtimeType), child: home),
      ),
    );
  }
}
