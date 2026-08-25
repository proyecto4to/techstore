final class RealtimeEvent {
  const RealtimeEvent({
    required this.id,
    required this.type,
    required this.aggregate,
    this.aggregateId,
  });

  factory RealtimeEvent.fromJson(Map<String, Object?> json) => RealtimeEvent(
    id: (json['id'] as num).toInt(),
    type: json['eventType'] as String? ?? '',
    aggregate: json['aggregateType'] as String? ?? '',
    aggregateId: (json['aggregateId'] as num?)?.toInt(),
  );

  final int id;
  final String type;
  final String aggregate;
  final int? aggregateId;
}
