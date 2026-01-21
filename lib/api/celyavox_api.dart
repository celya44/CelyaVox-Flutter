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
  }) async {
    final query = <String, String>{
      ...params,
      'api_key': apiKey,
    };

    final scheme = useHttps ? 'https' : 'http';
    final uri = Uri(
      scheme: scheme,
      host: domain,
      path: '/celyavox-api/$apiClass/$function',
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

    final decoded = jsonDecode(resp.body);
    if (decoded is Map<String, dynamic>) {
      return CelyaVoxApiResponse.fromJson(decoded);
    }
    return CelyaVoxApiResponse(
      status: 'ERROR',
      message: 'Invalid JSON response',
      data: resp.body,
    );
  }

  Future<CelyaVoxApiResponse> callProvisioned({
    required String apiClass,
    required String function,
    Map<String, String> params = const {},
    bool useHttps = true,
    bool includeExtension = true,
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
    );
  }
}