import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../api/models.dart';
import '../app/locale_controller.dart';
import '../app/session.dart';
import '../app/strings.dart';
import '../app/theme.dart';
import '../widgets/hero.dart';
import '../widgets/ui.dart';

/// Phone number, then the one-time code sent to it.
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _phone = TextEditingController();
  final _code = TextEditingController();
  final _codeFocus = FocusNode();

  OtpRequestResult? _otp;
  bool _busy = false;
  int _resendLeft = 0;
  Timer? _timer;

  @override
  void dispose() {
    _timer?.cancel();
    _phone.dispose();
    _code.dispose();
    _codeFocus.dispose();
    super.dispose();
  }

  bool get _phoneValid => RegExp(r'^0?5\d{8}$').hasMatch(_phone.text.trim());
  int get _codeLength => _otp?.codeLength ?? 4;

  Future<void> _requestCode() async {
    setState(() => _busy = true);
    try {
      final otp = await context.read<Session>().api.requestOtp(_phone.text.trim());
      if (!mounted) return;
      _code.clear();
      setState(() => _otp = otp);
      _startCountdown(otp.resendInSeconds);
      _codeFocus.requestFocus();
    } catch (e) {
      if (mounted) showError(context, e);
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _startCountdown(int seconds) {
    _timer?.cancel();
    _resendLeft = seconds;
    _timer = Timer.periodic(const Duration(seconds: 1), (t) {
      if (!mounted) return t.cancel();
      setState(() => _resendLeft--);
      if (_resendLeft <= 0) t.cancel();
    });
  }

  Future<void> _verify() async {
    if (_code.text.length != _codeLength) return;
    setState(() => _busy = true);
    try {
      await context.read<Session>().signIn(_phone.text.trim(), _code.text);
    } catch (e) {
      if (!mounted) return;
      _code.clear();
      showError(context, e);
      setState(() => _busy = false);
    }
  }

  void _editNumber() {
    _timer?.cancel();
    setState(() {
      _otp = null;
      _code.clear();
    });
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final expired = context.select<Session, bool>((x) => x.expired);
    final top = MediaQuery.paddingOf(context).top;

    return Scaffold(
      body: ListView(padding: EdgeInsets.zero, children: [
        ClipRRect(
          borderRadius: const BorderRadius.vertical(bottom: Radius.circular(36)),
          child: DecoratedBox(
            decoration: const BoxDecoration(gradient: K.heroGradient),
            child: Stack(children: [
              const Positioned.fill(child: CustomPaint(painter: RouteBackdrop())),
              Padding(
                padding: EdgeInsets.fromLTRB(20, top + 12, 20, 64),
                child: Column(children: [
                  Align(
                    alignment: AlignmentDirectional.centerEnd,
                    child: TextButton.icon(
                      onPressed: () => context.read<LocaleController>().toggle(),
                      style: TextButton.styleFrom(
                        foregroundColor: K.ink,
                        backgroundColor: Colors.white.withValues(alpha: 0.45),
                        shape: const StadiumBorder(),
                      ),
                      icon: const Icon(Icons.translate_rounded, size: 18),
                      label: Text(s.t('otherLanguage'), style: const TextStyle(fontWeight: FontWeight.w700)),
                    ),
                  ),
                  const SizedBox(height: 8),
                  const LogoTile(size: 88),
                  const SizedBox(height: 16),
                  Text(s.t('kamal'), style: const TextStyle(fontSize: 28, fontWeight: FontWeight.w900, color: K.ink, height: 1.2)),
                  Text(s.t('captainApp'), style: TextStyle(fontSize: 14, color: K.ink.withValues(alpha: 0.7))),
                ]),
              ),
            ]),
          ),
        ),
        Transform.translate(
          offset: const Offset(0, -40),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              if (expired) ...[
                NoteBox(s.t('sessionEnded'), icon: Icons.lock_clock_rounded, tone: Tone.warning),
                const SizedBox(height: 12),
              ],
              CardBox(
                padding: const EdgeInsets.all(20),
                child: AnimatedSize(
                  duration: const Duration(milliseconds: 220),
                  alignment: Alignment.topCenter,
                  child: _otp == null ? _phoneStep(s) : _codeStep(s),
                ),
              ),
              const SizedBox(height: 18),
              Center(
                child: TextButton(
                  onPressed: () => showInfoSheet(context,
                      icon: Icons.support_agent_rounded, title: s.t('contactSupervisor'), body: s.t('troubleBody')),
                  child: Text.rich(TextSpan(children: [
                    TextSpan(text: '${s.t('trouble')} ', style: const TextStyle(color: K.muted)),
                    TextSpan(text: s.t('contactSupervisor'), style: const TextStyle(color: K.brandDark, fontWeight: FontWeight.w800)),
                  ])),
                ),
              ),
            ]),
          ),
        ),
      ]),
    );
  }

  Widget _phoneStep(S s) {
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Text(s.t('welcome'), style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
      const SizedBox(height: 4),
      Text(s.t('welcomeSub'), style: const TextStyle(fontSize: 13, color: K.muted, height: 1.5)),
      const SizedBox(height: 20),
      Text(s.t('phone'), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700)),
      const SizedBox(height: 8),
      Directionality(
        textDirection: TextDirection.ltr,
        child: TextField(
          controller: _phone,
          keyboardType: TextInputType.phone,
          autofillHints: const [AutofillHints.telephoneNumber],
          inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(10)],
          style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700, letterSpacing: 1.2),
          onChanged: (_) => setState(() {}),
          onSubmitted: (_) => _phoneValid ? _requestCode() : null,
          decoration: InputDecoration(
            hintText: s.t('phoneHint'),
            hintStyle: const TextStyle(color: Color(0xFFBDBAB2), fontWeight: FontWeight.w500),
            prefixIcon: Container(
              margin: const EdgeInsets.fromLTRB(8, 8, 10, 8),
              padding: const EdgeInsets.symmetric(horizontal: 10),
              decoration: BoxDecoration(color: K.brandTint, borderRadius: BorderRadius.circular(10)),
              alignment: Alignment.center,
              width: 64,
              child: const Text('+966', style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700)),
            ),
          ),
        ),
      ),
      const SizedBox(height: 20),
      PrimaryButton(
        label: s.t('sendCode'),
        icon: Icons.sms_rounded,
        busy: _busy,
        onTap: _phoneValid ? _requestCode : null,
      ),
    ]);
  }

  Widget _codeStep(S s) {
    final otp = _otp!;
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
      Text(s.t('otp'), style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w900)),
      const SizedBox(height: 4),
      Row(children: [
        Expanded(
          child: Text.rich(
            TextSpan(children: [
              TextSpan(text: s.f('otpSentTo', {'phone': ''})),
              TextSpan(text: otp.maskedPhone, style: const TextStyle(fontWeight: FontWeight.w800, color: K.text)),
            ]),
            style: const TextStyle(fontSize: 13, color: K.muted),
          ),
        ),
        TextButton(
          onPressed: _busy ? null : _editNumber,
          style: TextButton.styleFrom(foregroundColor: K.brandDark, padding: const EdgeInsets.symmetric(horizontal: 8)),
          child: Text(s.t('editNumber'), style: const TextStyle(fontWeight: FontWeight.w800)),
        ),
      ]),
      const SizedBox(height: 18),
      _CodeBoxes(
        controller: _code,
        focusNode: _codeFocus,
        length: otp.codeLength,
        onChanged: () {
          setState(() {});
          if (_code.text.length == otp.codeLength) _verify();
        },
      ),
      const SizedBox(height: 14),
      Center(
        child: _resendLeft > 0
            ? Text.rich(TextSpan(children: [
                TextSpan(text: '${s.t('resendIn')} '),
                TextSpan(
                  text: '0:${_resendLeft.toString().padLeft(2, '0')}',
                  style: const TextStyle(fontWeight: FontWeight.w800, color: K.text),
                ),
              ]), style: const TextStyle(fontSize: 13, color: K.muted))
            : TextButton(
                onPressed: _busy ? null : _requestCode,
                style: TextButton.styleFrom(foregroundColor: K.brandDark),
                child: Text(s.t('resend'), style: const TextStyle(fontWeight: FontWeight.w800)),
              ),
      ),
      if (otp.devCode != null) ...[
        const SizedBox(height: 10),
        NoteBox('${s.t('devCode')}: ${otp.devCode}', icon: Icons.code_rounded, tone: Tone.info),
      ],
      const SizedBox(height: 18),
      PrimaryButton(
        label: s.t('verify'),
        icon: Icons.login_rounded,
        busy: _busy,
        onTap: _code.text.length == otp.codeLength ? _verify : null,
      ),
    ]);
  }
}

/// The code as separate boxes over one hidden text field, so paste and
/// SMS autofill still work.
class _CodeBoxes extends StatelessWidget {
  const _CodeBoxes({required this.controller, required this.focusNode, required this.length, required this.onChanged});

  final TextEditingController controller;
  final FocusNode focusNode;
  final int length;
  final VoidCallback onChanged;

  @override
  Widget build(BuildContext context) {
    final text = controller.text;
    return GestureDetector(
      onTap: focusNode.requestFocus,
      child: Stack(children: [
        Opacity(
          opacity: 0,
          child: SizedBox(
            height: 64,
            child: TextField(
              controller: controller,
              focusNode: focusNode,
              autofocus: true,
              keyboardType: TextInputType.number,
              autofillHints: const [AutofillHints.oneTimeCode],
              inputFormatters: [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(length)],
              onChanged: (_) => onChanged(),
              showCursor: false,
              enableInteractiveSelection: false,
            ),
          ),
        ),
        Positioned.fill(
          child: IgnorePointer(
            child: ListenableBuilder(
              listenable: focusNode,
              builder: (context, _) => Directionality(
                textDirection: TextDirection.ltr,
                child: Row(children: [
                  for (var i = 0; i < length; i++) ...[
                    if (i > 0) const SizedBox(width: 12),
                    Expanded(
                      child: AnimatedContainer(
                        duration: const Duration(milliseconds: 150),
                        decoration: BoxDecoration(
                          color: i < text.length ? K.brandTint : const Color(0xFFFAF9F6),
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(
                            color: focusNode.hasFocus && i == text.length.clamp(0, length - 1) ? K.brand : K.line,
                            width: focusNode.hasFocus && i == text.length.clamp(0, length - 1) ? 2 : 1,
                          ),
                        ),
                        alignment: Alignment.center,
                        child: Text(i < text.length ? text[i] : '',
                            style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w900, color: K.ink)),
                      ),
                    ),
                  ],
                ]),
              ),
            ),
          ),
        ),
      ]),
    );
  }
}
