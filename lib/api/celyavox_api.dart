import 'dart:convert';

import 'package:http/http.dart' as http;

import '../provisioning/provisioning_channel.dart';
import '../log/app_logger.dart';

class CelyaVoxApiResponse {
  final String status;
  final String message;
  final dynamic data;

  const CelyaVoxApiResponse({
    required this.status,
    required this.message,
    required this.data,
  });

  bool get isOk => status.toUpperCase() == 'OK';

  dynamic get decodedData {
    final raw = data;
    if (raw is String) {
      final trimmed = raw.trim();
      final looksLikeJson =
          (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
          (trimmed.startsWith('[') && trimmed.endsWith(']'));
      if (looksLikeJson) {
        try {
          return jsonDecode(trimmed);
        } catch (_) {
          return raw;
        }
      }
    }
    return raw;
  }

  factory CelyaVoxApiResponse.fromJson(Map<String, dynamic> json) {
    return CelyaVoxApiResponse(
      status: json['status']?.toString() ?? 'ERROR',
      message: json['message']?.toString() ?? '',
      data: json['data'],
    );
  }
}

class CelyaVoxApiClient {
  final http.Client _client;

  CelyaVoxApiClient({http.Client? client}) : _client = client ?? http.Client();

  Future<CelyaVoxApiResponse> call({
    required String apiClass,
    required String function,
    required String domain,
    required String apiKey,
    Map<String, String> params = const {},
    bool useHttps = true,
    int? port,
    String apiRootPath = '/celyavox-api',
  }) async {
    final query = <String, String>{
      ...params,
      'api_key': apiKey,
    };

    final scheme = useHttps ? 'https' : 'http';
    final domainInfo = _parseDomain(domain);
    final effectiveHost = domainInfo.host;
    final effectivePort = port ?? domainInfo.port;
    final normalizedApiRoot = _normalizePathSegment(apiRootPath);
    final normalizedClass = _normalizePathSegment(apiClass);
    final normalizedFunction = _normalizePathSegment(function);
    final uri = Uri(
      scheme: scheme,
      host: effectiveHost,
      port: effectivePort,
      path: '$normalizedApiRoot/$normalizedClass/$normalizedFunction',
      queryParameters: query,
    );

    await AppLogger.instance.logApiRequest(uri.toString(), query);

    final resp = await _client.get(uri);
    await AppLogger.instance.logApiResponse(uri.toString(), resp.statusCode, resp.body);
    if (resp.statusCode < 200 || resp.statusCode >= 300) {
      return CelyaVoxApiResponse(
        status: 'ERROR',
        message: 'HTTP ${resp.statusCode}',
        data: resp.body,
      );
    }

    try {
      final decoded = jsonDecode(resp.body);
      if (decoded is Map<String, dynamic>) {
        return CelyaVoxApiResponse.fromJson(decoded);
      }
      return CelyaVoxApiResponse(
        status: 'ERROR',
        message: 'Invalid JSON response',
        data: resp.body,
      );
    } catch (_) {
      return CelyaVoxApiResponse(
        status: 'ERROR',
        message: 'Invalid JSON response',
        data: resp.body,
      );
    }
  }

  Future<CelyaVoxApiResponse> callProvisioned({
    required String apiClass,
    required String function,
    Map<String, String> params = const {},
    bool useHttps = true,
    bool includeExtension = true,
    int defaultHttpsPort = 445,
    int? overridePort,
    String apiRootPath = '/celyavox-api',
  }) async {
    final domain = await ProvisioningChannel.getSipDomain();
    final apiKey = await ProvisioningChannel.getApiKey();
    if (domain == null || domain.isEmpty) {
      return const CelyaVoxApiResponse(
        status: 'ERROR',
        message: 'Missing SIP domain in provisioning',
        data: null,
      );
    }
    if (apiKey == null || apiKey.isEmpty) {
      return const CelyaVoxApiResponse(
        status: 'ERROR',
        message: 'Missing api_key in provisioning',
        data: null,
      );
    }

    final query = <String, String>{...params};
    if (includeExtension && !query.containsKey('extension')) {
      final ext = await ProvisioningChannel.getSipUsername();
      if (ext != null && ext.isNotEmpty) {
        query['extension'] = ext;
      }
    }

    return call(
      apiClass: apiClass,
      function: function,
      domain: domain,
      apiKey: apiKey,
      params: query,
      useHttps: useHttps,
      port: overridePort ?? (useHttps ? defaultHttpsPort : null),
      apiRootPath: apiRootPath,
    );
  }

  Future<CelyaVoxApiResponse> callProvisionedEndpoint({
    required String endpoint,
    Map<String, String> params = const {},
    bool useHttps = true,
    bool includeExtension = true,
    int defaultHttpsPort = 445,
    int? overridePort,
    String apiRootPath = '/celyavox-api',
  }) async {
    final parts = endpoint
        .split('/')
        .map((e) => e.trim())
        .where((e) => e.isNotEmpty)
        .toList();
    if (parts.length != 2) {
      return const CelyaVoxApiResponse(
        status: 'ERROR',
        message: 'Endpoint must be "class/function"',
        data: null,
      );
    }

    return callProvisioned(
      apiClass: parts[0],
      function: parts[1],
      params: params,
      useHttps: useHttps,
      includeExtension: includeExtension,
      defaultHttpsPort: defaultHttpsPort,
      overridePort: overridePort,
      apiRootPath: apiRootPath,
    );
  }

  _DomainInfo _parseDomain(String value) {
    final raw = value.trim();
    if (raw.isEmpty) {
      return const _DomainInfo(host: '');
    }

    final withScheme = raw.contains('://') ? raw : 'https://$raw';
    final uri = Uri.tryParse(withScheme);
    if (uri == null || uri.host.isEmpty) {
      return _DomainInfo(host: raw);
    }
    return _DomainInfo(
      host: uri.host,
      port: uri.hasPort ? uri.port : null,
    );
  }

  String _normalizePathSegment(String value) {
    final trimmed = value.trim();
    if (trimmed.isEmpty) return '';
    final withoutPrefix = trimmed.startsWith('/') ? trimmed.substring(1) : trimmed;
    return withoutPrefix.endsWith('/')
        ? withoutPrefix.substring(0, withoutPrefix.length - 1)
        : withoutPrefix;
  }
}

class _DomainInfo {
  final String host;
  final int? port;

  const _DomainInfo({required this.host, this.port});
}