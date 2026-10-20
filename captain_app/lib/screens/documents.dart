import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:provider/provider.dart';

import '../api/api_client.dart';
import '../api/models.dart';
import '../app/session.dart';
import '../app/strings.dart';
import '../app/theme.dart';
import '../widgets/page.dart';
import '../widgets/ui.dart';

const documentKinds = ['IQAMA', 'WORK_PERMIT', 'INSURANCE', 'PASSPORT', 'DRIVING_LICENCE'];

IconData documentIcon(String kind) => switch (kind) {
      'IQAMA' => Icons.badge_rounded,
      'WORK_PERMIT' => Icons.work_rounded,
      'INSURANCE' => Icons.health_and_safety_rounded,
      'PASSPORT' => Icons.public_rounded,
      'DRIVING_LICENCE' => Icons.directions_car_rounded,
      _ => Icons.description_rounded,
    };

Tone documentTone(String status) => switch (status) {
      'EXPIRED' || 'MISSING' => Tone.danger,
      'EXPIRING' => Tone.warning,
      _ => Tone.success,
    };

String documentStatusLabel(S s, String status) => switch (status) {
      'EXPIRED' => s.t('expired'),
      'EXPIRING' => s.t('expiringSoon'),
      'MISSING' => s.t('missing'),
      _ => s.t('valid'),
    };

class DocumentsScreen extends StatelessWidget {
  const DocumentsScreen({super.key, this.pushed = false});

  /// Opened on top of the shell (from a notification) rather than as a tab.
  final bool pushed;

  @override
  Widget build(BuildContext context) {
    final api = context.read<Session>().api;
    final body = Loader<DocumentsResponse>(load: api.documents, builder: (context, d, reload) => _Body(d, reload, pushed));
    return pushed ? Scaffold(body: body) : body;
  }
}

class _Body extends StatelessWidget {
  const _Body(this.d, this.reload, this.pushed);

  final DocumentsResponse d;
  final Future<void> Function() reload;
  final bool pushed;

  Future<void> _upload(BuildContext context, String kind) async {
    if (await showUploadSheet(context, kind) == true) reload();
  }

  Future<void> _pickKind(BuildContext context) async {
    final s = S.read(context);
    final kind = await showSheet<String>(
      context,
      Builder(
        builder: (context) => Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Text(s.t('pickDocument'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
          const SizedBox(height: 12),
          for (final k in documentKinds)
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: IconTile(documentIcon(k)),
              title: Text(s.docKind(k), style: const TextStyle(fontWeight: FontWeight.w700)),
              onTap: () => Navigator.pop(context, k),
            ),
        ]),
      ),
    );
    if (kind != null && context.mounted) _upload(context, kind);
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    int count(String st) => d.documents.where((x) => x.status == st).length;
    final action = d.documents.where((x) => x.needsAction).toList()
      ..sort((a, b) => (a.daysLeft ?? -9999).compareTo(b.daysLeft ?? -9999));
    final valid = d.documents.where((x) => !x.needsAction).toList();

    return HeroPage(
      bottom: pushed ? 32 : dockClearance,
      hero: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        HeroTitle(
          leading: pushed ? const BackHeroButton() : null,
          title: s.t('myDocuments'),
          subtitle: s.t('documentsSub'),
          subtitleFirst: true,
          trailing: [HeroButton(icon: Icons.add_rounded, onTap: () => _pickKind(context))],
        ),
        const SizedBox(height: 18),
        StatStrip([
          (Text('${count('EXPIRED') + count('MISSING')}', style: const TextStyle(color: Color(0xFFFF8A80))), s.t('expired')),
          (Text('${count('EXPIRING')}', style: const TextStyle(color: K.brand)), s.t('expiringSoon')),
          (statText('${count('VALID')}'), s.t('valid')),
        ]),
      ]),
      children: [
        if (action.isNotEmpty) ...[
          SectionTitle(s.t('needsAction')),
          for (final doc in action)
            Padding(padding: const EdgeInsets.only(bottom: 14), child: padded(_ActionCard(doc, onUpload: () => _upload(context, doc.kind)))),
        ],
        if (valid.isNotEmpty) ...[
          SectionTitle(s.t('valid')),
          padded(CardBox(
            padding: EdgeInsets.zero,
            child: Column(children: [
              for (var i = 0; i < valid.length; i++) ...[
                if (i > 0) const Divider(height: 1, color: K.line),
                _ValidRow(valid[i], onTap: () => _upload(context, valid[i].kind)),
              ],
            ]),
          )),
        ],
        if (d.requests.isNotEmpty) ...[
          SectionTitle(s.t('myRequests')),
          padded(CardBox(
            padding: EdgeInsets.zero,
            child: Column(children: [
              for (var i = 0; i < d.requests.length; i++) ...[
                if (i > 0) const Divider(height: 1, color: K.line),
                _RequestRow(d.requests[i]),
              ],
            ]),
          )),
        ],
      ],
    );
  }
}

class _ActionCard extends StatelessWidget {
  const _ActionCard(this.doc, {required this.onUpload});

  final DocumentItem doc;
  final VoidCallback onUpload;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final tone = documentTone(doc.status);
    final name = s.docKind(doc.kind);
    final pending = doc.pendingRequest;
    final days = doc.daysLeft;
    return CardBox(
      border: tone == Tone.danger ? const Color(0xFFF5B5B5) : const Color(0xFFF7D68A),
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Row(children: [
          IconTile(documentIcon(doc.kind), tone: tone == Tone.danger ? Tone.danger : Tone.brand, size: 44),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(name, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)),
              if (doc.numberLast4 != null)
                Text(s.f('numberEnding', {'n': doc.numberLast4}), style: const TextStyle(fontSize: 12, color: K.muted)),
            ]),
          ),
          Chip2(documentStatusLabel(s, doc.status), tone: tone),
        ]),
        if (doc.expiry != null) ...[
          const SizedBox(height: 12),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
            decoration: BoxDecoration(color: const Color(0xFFF7F5F0), borderRadius: BorderRadius.circular(12)),
            child: Row(children: [
              const Icon(Icons.event_rounded, size: 16, color: K.muted),
              const SizedBox(width: 6),
              Expanded(
                child: Text(
                  s.f(doc.status == 'EXPIRED' ? 'expiredOn' : 'expiresOn', {'d': s.numericDate(doc.expiry!)}),
                  style: const TextStyle(fontSize: 12.5, color: K.muted),
                ),
              ),
              if (days != null)
                Text(
                  days < 0 ? s.f('daysAgo', {'n': -days}) : s.f('daysLeft', {'n': days}),
                  style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: tone.fg),
                ),
            ]),
          ),
        ],
        const SizedBox(height: 12),
        if (pending != null)
          NoteBox(
            s.f('underReviewSub', {'doc': name, 'd': s.numericDate(pending.newExpiry)}),
            icon: Icons.hourglass_top_rounded,
            tone: Tone.info,
          )
        else
          InkButton(label: s.f('uploadRenewed', {'doc': name}), icon: Icons.upload_rounded, onTap: onUpload),
      ]),
    );
  }
}

class _ValidRow extends StatelessWidget {
  const _ValidRow(this.doc, {required this.onTap});

  final DocumentItem doc;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    return InkWell(
      onTap: doc.pendingRequest == null ? onTap : null,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
        child: Row(children: [
          IconTile(documentIcon(doc.kind), size: 42),
          const SizedBox(width: 12),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(s.docKind(doc.kind), style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700)),
              if (doc.expiry != null)
                Text(s.f('expiresOn', {'d': s.numericDate(doc.expiry!)}), style: const TextStyle(fontSize: 12, color: K.muted)),
            ]),
          ),
          doc.pendingRequest != null ? Chip2(s.t('underReview'), tone: Tone.info) : Chip2(s.t('valid'), tone: Tone.success),
          const SizedBox(width: 4),
          Icon(Icons.chevron_right_rounded,
              color: K.muted),
        ]),
      ),
    );
  }
}

class _RequestRow extends StatelessWidget {
  const _RequestRow(this.r);

  final DocumentRequestItem r;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final tone = switch (r.status) { 'APPROVED' => Tone.success, 'REJECTED' => Tone.danger, _ => Tone.info };
    final note = r.reviewNote;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
        IconTile(documentIcon(r.kind), size: 40, tone: tone),
        const SizedBox(width: 12),
        Expanded(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(s.docKind(r.kind), style: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w700)),
            Text(
              [s.f('newExpiry', {'d': s.numericDate(r.newExpiry)}), if (r.createdAt != null) s.dayMonth(S.riyadh(r.createdAt!))].join(' · '),
              style: const TextStyle(fontSize: 12, color: K.muted),
            ),
            if (note != null && note.isNotEmpty)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text(s.f(r.status == 'REJECTED' ? 'rejectNote' : 'reviewNote', {'n': note}),
                    style: TextStyle(fontSize: 12, color: tone.fg, fontWeight: FontWeight.w600)),
              ),
          ]),
        ),
        Chip2(s.requestStatus(r.status), tone: tone),
      ]),
    );
  }
}

// ---- Upload sheet ----

/// Asks for photos, number and new expiry of a renewed document. Completes with
/// true once the request was sent.
Future<bool?> showUploadSheet(BuildContext context, String kind) {
  return showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    backgroundColor: Colors.white,
    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
    builder: (_) => _UploadSheet(kind: kind),
  );
}

class _Picked {
  _Picked(this.name, this.bytes);

  final String name;
  final Uint8List bytes;

  String get size => bytes.length >= 1024 * 1024
      ? '${(bytes.length / 1024 / 1024).toStringAsFixed(1)}MB'
      : '${(bytes.length / 1024).round()}KB';
}

class _UploadSheet extends StatefulWidget {
  const _UploadSheet({required this.kind});

  final String kind;

  @override
  State<_UploadSheet> createState() => _UploadSheetState();
}

class _UploadSheetState extends State<_UploadSheet> {
  final _number = TextEditingController();
  _Picked? _front;
  _Picked? _back;
  DateTime? _expiry;
  bool _busy = false;

  @override
  void dispose() {
    _number.dispose();
    super.dispose();
  }

  Future<void> _pick(bool front) async {
    final s = S.read(context);
    final source = await showSheet<ImageSource>(
      context,
      Builder(
        builder: (context) => Column(children: [
          ListTile(
            leading: const IconTile(Icons.photo_camera_rounded),
            title: Text(s.t('camera'), style: const TextStyle(fontWeight: FontWeight.w700)),
            onTap: () => Navigator.pop(context, ImageSource.camera),
          ),
          ListTile(
            leading: const IconTile(Icons.photo_library_rounded),
            title: Text(s.t('gallery'), style: const TextStyle(fontWeight: FontWeight.w700)),
            onTap: () => Navigator.pop(context, ImageSource.gallery),
          ),
        ]),
      ),
    );
    if (source == null) return;
    final file = await ImagePicker().pickImage(source: source, maxWidth: 2200, imageQuality: 85);
    if (file == null) return;
    final picked = _Picked(file.name, await file.readAsBytes());
    setState(() => front ? _front = picked : _back = picked);
  }

  Future<void> _date() async {
    final today = DateTime.now();
    final picked = await showDatePicker(
      context: context,
      initialDate: _expiry ?? DateTime(today.year + 1, today.month, today.day),
      firstDate: today.add(const Duration(days: 1)),
      lastDate: DateTime(today.year + 15),
    );
    if (picked != null) setState(() => _expiry = picked);
  }

  Future<void> _submit() async {
    final s = S.read(context);
    final front = _front;
    final expiry = _expiry;
    if (front == null) return _toast(s.t('frontRequired'));
    if (expiry == null) return _toast(s.t('expiryRequired'));
    setState(() => _busy = true);
    try {
      await context.read<Session>().api.submitDocument(
            kind: widget.kind,
            number: _number.text,
            newExpiry: expiry,
            front: UploadFile(front.name, front.bytes),
            back: _back == null ? null : UploadFile(_back!.name, _back!.bytes),
          );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(s.t('sentForReview'))));
      Navigator.pop(context, true);
    } catch (e) {
      if (!mounted) return;
      setState(() => _busy = false);
      _toast(errorText(s, e));
    }
  }

  void _toast(String text) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final name = s.docKind(widget.kind);
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 10, 20, 24),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Center(
            child: Container(
              width: 40,
              height: 4,
              margin: const EdgeInsets.only(bottom: 18),
              decoration: BoxDecoration(color: K.line, borderRadius: BorderRadius.circular(2)),
            ),
          ),
          Row(children: [
            IconTile(documentIcon(widget.kind), size: 46),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(s.f('updateDoc', {'doc': name}), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                Text(s.f('updateDocSub', {'doc': name}), style: const TextStyle(fontSize: 12, color: K.muted)),
              ]),
            ),
            IconButton(
              style: IconButton.styleFrom(backgroundColor: const Color(0xFFF3F1EC)),
              onPressed: () => Navigator.pop(context),
              icon: const Icon(Icons.close_rounded, size: 20),
            ),
          ]),
          const SizedBox(height: 18),
          Row(children: [
            Expanded(child: _PhotoBox(label: s.t('front'), picked: _front, onTap: () => _pick(true))),
            const SizedBox(width: 12),
            Expanded(child: _PhotoBox(label: s.t('back'), picked: _back, optional: true, onTap: () => _pick(false))),
          ]),
          const SizedBox(height: 18),
          Text(s.f('docNumber', {'doc': name}), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          TextField(
            controller: _number,
            keyboardType: TextInputType.number,
            textDirection: TextDirection.ltr,
            decoration: const InputDecoration(prefixIcon: Icon(Icons.pin_outlined, color: K.muted)),
          ),
          const SizedBox(height: 16),
          Text(s.t('newExpiryLabel'), style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600)),
          const SizedBox(height: 8),
          InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: _date,
            child: InputDecorator(
              decoration: const InputDecoration(prefixIcon: Icon(Icons.calendar_month_rounded, color: K.muted)),
              child: Text(
                _expiry == null ? s.t('pickDate') : s.numericDate(_expiry!),
                style: TextStyle(fontSize: 15, color: _expiry == null ? K.muted : K.text, fontWeight: FontWeight.w600),
              ),
            ),
          ),
          const SizedBox(height: 16),
          NoteBox(s.t('reviewNotice')),
          const SizedBox(height: 18),
          PrimaryButton(label: s.t('sendForReview'), icon: Icons.send_rounded, busy: _busy, onTap: _submit),
        ]),
      ),
    );
  }
}

class _PhotoBox extends StatelessWidget {
  const _PhotoBox({required this.label, required this.picked, required this.onTap, this.optional = false});

  final String label;
  final _Picked? picked;
  final VoidCallback onTap;
  final bool optional;

  @override
  Widget build(BuildContext context) {
    final s = S.of(context);
    final p = picked;
    return InkWell(
      borderRadius: BorderRadius.circular(18),
      onTap: onTap,
      child: Container(
        height: 168,
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: p == null ? K.brandTint : const Color(0xFFF7F5F0),
          borderRadius: BorderRadius.circular(18),
          border: Border.all(color: p == null ? K.brand : K.line, width: p == null ? 1.4 : 1),
        ),
        child: p == null
            ? Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: const BoxDecoration(color: K.brand, shape: BoxShape.circle),
                  child: const Icon(Icons.photo_camera_rounded, color: K.ink),
                ),
                const SizedBox(height: 10),
                Text(label, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13.5)),
                Text(optional ? '${s.t('tapToPick')} · ${s.t('optional')}' : s.t('tapToPick'),
                    textAlign: TextAlign.center, style: const TextStyle(fontSize: 11, color: K.muted)),
              ])
            : Column(children: [
                Expanded(
                  child: Stack(fit: StackFit.expand, children: [
                    ClipRRect(borderRadius: BorderRadius.circular(12), child: Image.memory(p.bytes, fit: BoxFit.cover)),
                    PositionedDirectional(
                      top: 6,
                      start: 6,
                      child: Container(
                        decoration: const BoxDecoration(color: K.success, shape: BoxShape.circle),
                        padding: const EdgeInsets.all(3),
                        child: const Icon(Icons.check_rounded, size: 14, color: Colors.white),
                      ),
                    ),
                  ]),
                ),
                const SizedBox(height: 6),
                Text(label, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 13)),
                Text('${p.name} · ${p.size}', maxLines: 1, overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 10.5, color: K.muted)),
              ]),
      ),
    );
  }
}
