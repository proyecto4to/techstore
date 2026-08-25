final class ProblemDetail {
  const ProblemDetail({
    required this.title,
    required this.status,
    this.detail,
    this.code,
    this.errors = const {},
  });

  factory ProblemDetail.fromJson(Map<String, Object?> json) {
    final rawErrors = json['errors'];
    final errors = <String, String>{};
    if (rawErrors is Map) {
      for (final entry in rawErrors.entries) {
        errors[entry.key.toString()] = entry.value.toString();
      }
    }
    return ProblemDetail(
      title: json['title']?.toString() ?? 'Error',
      status: (json['status'] as num?)?.toInt() ?? 0,
      detail: json['detail']?.toString(),
      code: json['code']?.toString(),
      errors: errors,
    );
  }

  final String title;
  final int status;
  final String? detail;
  final String? code;
  final Map<String, String> errors;

  String get userMessage => detail?.trim().isNotEmpty == true ? detail! : title;
}
