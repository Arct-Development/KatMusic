import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:pointycastle/export.dart';

/// The desktop side of the short-lived Android → desktop YouTube session
/// handoff. The server is LAN-only, the QR contains a one-use secret, and the
/// actual cookie header is still encrypted with AES-GCM before it leaves the
/// phone.
enum YtPhonePairingPhase {
  waiting,
  awaitingApproval,
  approved,
  received,
  rejected,
  expired,
}

class YtPhonePairingSnapshot {
  final YtPhonePairingPhase phase;
  final String? deviceName;
  final DateTime expiresAt;

  const YtPhonePairingSnapshot({
    required this.phase,
    required this.expiresAt,
    this.deviceName,
  });
}

class YtPhoneTransfer {
  final String cookies;
  final String accountName;
  final String channelHandle;
  final String photoUrl;
  final String pageId;

  const YtPhoneTransfer({
    required this.cookies,
    this.accountName = '',
    this.channelHandle = '',
    this.photoUrl = '',
    this.pageId = '',
  });
}

class YtPhonePairing {
  static const _version = 1;
  static const _ttl = Duration(minutes: 5);
  static const _maxBodyBytes = 256 * 1024;

  final ValueNotifier<YtPhonePairingSnapshot> snapshot;
  final Completer<YtPhoneTransfer> _result = Completer<YtPhoneTransfer>();
  final Uint8List _key;
  final String _code;
  final DateTime _expiresAt;
  final String _clientPath;
  final List<String> _hosts;

  HttpServer? _server;
  StreamSubscription<HttpRequest>? _subscription;
  Timer? _expiryTimer;
  _PendingRequest? _pending;
  bool _closed = false;

  YtPhonePairing._(
    List<String> hosts,
    Uint8List key,
    String code,
    DateTime expiresAt,
  ) : _hosts = hosts,
      _key = key,
      _code = code,
      _expiresAt = expiresAt,
      _clientPath = '/v1/yt-pair',
      snapshot = ValueNotifier(
        YtPhonePairingSnapshot(
          phase: YtPhonePairingPhase.waiting,
          expiresAt: expiresAt,
        ),
      );

  static Future<YtPhonePairing> create() async {
    final hosts = await _lanHosts();
    if (hosts.isEmpty) {
      throw const SocketException(
        'No private IPv4 network found. Connect the computer to the same Wi-Fi as the phone.',
      );
    }
    final now = DateTime.now();
    final pairing = YtPhonePairing._(
      hosts,
      _randomBytes(32),
      _token(9),
      now.add(_ttl),
    );
    await pairing._start();
    return pairing;
  }

  Future<YtPhoneTransfer> get result => _result.future;

  /// JSON is used rather than a custom URI so QR scanners on both platforms
  /// can validate the protocol version before opening a socket.
  String get qrData => jsonEncode({
    'type': 'katmusic-youtube-pair',
    'v': _version,
    'hosts': _hosts,
    'port': _server?.port ?? 0,
    'path': _clientPath,
    'code': _code,
    'key': _base64Url(_key),
    'expiresAt': _expiresAt.millisecondsSinceEpoch,
  });

  String get hostLabel => _hosts.join(', ');

  bool get isExpired => DateTime.now().isAfter(_expiresAt);

  Future<void> _start() async {
    _server = await HttpServer.bind(InternetAddress.anyIPv4, 0);
    _subscription = _server!.listen(_handleRequest);
    _expiryTimer = Timer(_expiresAt.difference(DateTime.now()), () {
      if (_closed) return;
      snapshot.value = YtPhonePairingSnapshot(
        phase: YtPhonePairingPhase.expired,
        deviceName: _pending?.deviceName,
        expiresAt: _expiresAt,
      );
    });
  }

  /// Called by the desktop user after seeing the requesting phone.
  void approve() {
    final pending = _pending;
    if (_closed || pending == null || isExpired) return;
    pending.approved = true;
    snapshot.value = YtPhonePairingSnapshot(
      phase: YtPhonePairingPhase.approved,
      deviceName: pending.deviceName,
      expiresAt: _expiresAt,
    );
  }

  /// Rejecting is terminal for this QR. A fresh QR is required for another
  /// attempt, which prevents an old phone request from being reused silently.
  void reject() {
    final pending = _pending;
    if (_closed || pending == null) return;
    pending.rejected = true;
    snapshot.value = YtPhonePairingSnapshot(
      phase: YtPhonePairingPhase.rejected,
      deviceName: pending.deviceName,
      expiresAt: _expiresAt,
    );
  }

  Future<void> close() async {
    if (_closed) return;
    _closed = true;
    _expiryTimer?.cancel();
    await _subscription?.cancel();
    await _server?.close(force: true);
    _subscription = null;
    _server = null;
    snapshot.dispose();
  }

  Future<void> _handleRequest(HttpRequest request) async {
    if (_closed) return;
    try {
      if (request.method == 'OPTIONS') {
        request.response.statusCode = HttpStatus.noContent;
        await request.response.close();
        return;
      }
      if (request.uri.path == '$_clientPath/request' &&
          request.method == 'GET') {
        await _requestPairing(request);
        return;
      }
      if (request.uri.path == '$_clientPath/status' &&
          request.method == 'GET') {
        await _pairingStatus(request);
        return;
      }
      if (request.uri.path == '$_clientPath/transfer' &&
          request.method == 'POST') {
        await _receiveTransfer(request);
        return;
      }
      await _reply(request, HttpStatus.notFound, {'error': 'not_found'});
    } catch (_) {
      try {
        await _reply(request, HttpStatus.badRequest, {
          'error': 'invalid_request',
        });
      } catch (_) {
        try {
          await request.response.close();
        } catch (_) {}
      }
    }
  }

  Future<void> _requestPairing(HttpRequest request) async {
    if (!_validCode(request.uri.queryParameters['code']) || isExpired) {
      await _reply(request, HttpStatus.gone, {'error': 'expired'});
      return;
    }
    final device = _safeLabel(request.uri.queryParameters['device'], 'Android');
    final clientId = _safeLabel(request.uri.queryParameters['client'], 'phone');
    final existing = _pending;
    if (existing != null &&
        !existing.rejected &&
        existing.clientId != clientId) {
      await _reply(request, HttpStatus.conflict, {'error': 'busy'});
      return;
    }
    final pending =
        existing ??
        _PendingRequest(
          requestId: _token(12),
          clientId: clientId,
          deviceName: device,
        );
    _pending = pending;
    snapshot.value = YtPhonePairingSnapshot(
      phase: pending.approved
          ? YtPhonePairingPhase.approved
          : YtPhonePairingPhase.awaitingApproval,
      deviceName: pending.deviceName,
      expiresAt: _expiresAt,
    );
    await _reply(request, HttpStatus.ok, {
      'v': _version,
      'status': 'awaiting_approval',
      'requestId': pending.requestId,
      'expiresAt': _expiresAt.millisecondsSinceEpoch,
    });
  }

  Future<void> _pairingStatus(HttpRequest request) async {
    final pending = _pending;
    if (!_validCode(request.uri.queryParameters['code']) || pending == null) {
      await _reply(request, HttpStatus.notFound, {'error': 'not_found'});
      return;
    }
    if (isExpired) {
      await _reply(request, HttpStatus.gone, {'error': 'expired'});
      return;
    }
    if (request.uri.queryParameters['requestId'] != pending.requestId) {
      await _reply(request, HttpStatus.notFound, {'error': 'not_found'});
      return;
    }
    if (pending.rejected) {
      await _reply(request, HttpStatus.forbidden, {'error': 'rejected'});
      return;
    }
    await _reply(request, HttpStatus.ok, {
      'status': pending.approved ? 'approved' : 'awaiting_approval',
      'desktop': 'KatMusic Desktop',
    });
  }

  Future<void> _receiveTransfer(HttpRequest request) async {
    final pending = _pending;
    if (!_validCode(request.uri.queryParameters['code']) ||
        pending == null ||
        request.uri.queryParameters['requestId'] != pending.requestId) {
      await _reply(request, HttpStatus.notFound, {'error': 'not_found'});
      return;
    }
    if (isExpired) {
      await _reply(request, HttpStatus.gone, {'error': 'expired'});
      return;
    }
    if (pending.rejected || !pending.approved || pending.consumed) {
      await _reply(request, HttpStatus.forbidden, {'error': 'not_approved'});
      return;
    }
    final rawBody = await _readBody(request);
    final body = jsonDecode(rawBody);
    if (body is! Map) throw const FormatException('Invalid transfer body');
    final nonce = _decodeBase64(body['nonce']?.toString() ?? '');
    final encrypted = _decodeBase64(body['ciphertext']?.toString() ?? '');
    if (nonce.length != 12 || encrypted.length < 17) {
      throw const FormatException('Invalid encrypted transfer');
    }
    final cipher = GCMBlockCipher(AESEngine())
      ..init(
        false,
        AEADParameters(KeyParameter(_key), 128, nonce, Uint8List(0)),
      );
    final plain = cipher.process(encrypted);
    final decoded = jsonDecode(utf8.decode(plain));
    if (decoded is! Map) throw const FormatException('Invalid session payload');
    final cookies = decoded['cookies']?.toString().trim() ?? '';
    if (cookies.isEmpty ||
        (!cookies.contains('SAPISID=') &&
            !cookies.contains('__Secure-3PAPISID=') &&
            !cookies.contains('APISID='))) {
      throw const FormatException('The Android session has no SAPISID cookie');
    }
    pending.consumed = true;
    final transfer = YtPhoneTransfer(
      cookies: cookies,
      accountName: decoded['accountName']?.toString() ?? '',
      channelHandle: decoded['channelHandle']?.toString() ?? '',
      photoUrl: decoded['photoUrl']?.toString() ?? '',
      pageId: decoded['pageId']?.toString() ?? '',
    );
    snapshot.value = YtPhonePairingSnapshot(
      phase: YtPhonePairingPhase.received,
      deviceName: pending.deviceName,
      expiresAt: _expiresAt,
    );
    await _reply(request, HttpStatus.ok, {'status': 'received'});
    if (!_result.isCompleted) _result.complete(transfer);
  }

  bool _validCode(String? candidate) =>
      candidate != null && _constantTime(candidate, _code);

  Future<String> _readBody(HttpRequest request) async {
    final chunks = <List<int>>[];
    var length = 0;
    await for (final chunk in request) {
      length += chunk.length;
      if (length > _maxBodyBytes) throw const FormatException('Body too large');
      chunks.add(chunk);
    }
    final bytes = Uint8List(length);
    var offset = 0;
    for (final chunk in chunks) {
      bytes.setRange(offset, offset + chunk.length, chunk);
      offset += chunk.length;
    }
    return utf8.decode(bytes);
  }

  Future<void> _reply(
    HttpRequest request,
    int status,
    Map<String, Object?> body,
  ) async {
    final response = request.response;
    response.statusCode = status;
    response.headers.contentType = ContentType.json;
    response.headers.set('Cache-Control', 'no-store');
    response.headers.set('Pragma', 'no-cache');
    response.write(jsonEncode(body));
    await response.close();
  }

  static Future<List<String>> _lanHosts() async {
    final hosts = <String>{};
    final interfaces = await NetworkInterface.list(
      type: InternetAddressType.IPv4,
      includeLoopback: false,
    );
    for (final interface in interfaces) {
      for (final address in interface.addresses) {
        if (_isPrivateIpv4(address.address)) hosts.add(address.address);
      }
    }
    return hosts.toList()..sort();
  }

  static bool _isPrivateIpv4(String value) {
    final octets = value.split('.').map(int.tryParse).toList();
    if (octets.length != 4 ||
        octets.any((part) => part == null || part < 0 || part > 255)) {
      return false;
    }
    final first = octets[0]!;
    final second = octets[1]!;
    return first == 10 ||
        (first == 172 && second >= 16 && second <= 31) ||
        (first == 192 && second == 168) ||
        (first == 169 && second == 254) ||
        (first == 100 && second >= 64 && second <= 127);
  }

  static Uint8List _randomBytes(int length) => Uint8List.fromList(
    List<int>.generate(length, (_) => Random.secure().nextInt(256)),
  );

  static String _token(int bytes) => _base64Url(_randomBytes(bytes));

  static String _base64Url(List<int> value) =>
      base64Url.encode(value).replaceAll('=', '');

  static Uint8List _decodeBase64(String value) {
    final padded = value.padRight(
      value.length + ((4 - value.length % 4) % 4),
      '=',
    );
    return Uint8List.fromList(base64Url.decode(padded));
  }

  static bool _constantTime(String a, String b) {
    if (a.length != b.length) return false;
    var difference = 0;
    for (var i = 0; i < a.length; i++) {
      difference |= a.codeUnitAt(i) ^ b.codeUnitAt(i);
    }
    return difference == 0;
  }

  static String _safeLabel(String? raw, String fallback) {
    final value = (raw ?? '').replaceAll(RegExp(r'[^\w .()\-]'), '').trim();
    return value.isEmpty ? fallback : value.substring(0, min(value.length, 48));
  }
}

class _PendingRequest {
  final String requestId;
  final String clientId;
  final String deviceName;
  bool approved = false;
  bool rejected = false;
  bool consumed = false;

  _PendingRequest({
    required this.requestId,
    required this.clientId,
    required this.deviceName,
  });
}
