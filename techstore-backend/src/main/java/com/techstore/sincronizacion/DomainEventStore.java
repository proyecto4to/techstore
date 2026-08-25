package com.techstore.sincronizacion;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DomainEventStore {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public record Claimed(Long id, Long tenantId, int attempts) { }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Long> tenantsActivos() {
        return jdbcTemplate.queryForList("SELECT id FROM tenants WHERE activo = TRUE ORDER BY id", Long.class);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Claimed> claim(int limit, long staleSeconds) {
        return jdbcTemplate.query("""
                WITH candidates AS (
                    SELECT id FROM domain_event_outbox
                    WHERE (status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                       OR (status = 'PROCESSING' AND claimed_at < CURRENT_TIMESTAMP - (? * INTERVAL '1 second'))
                    ORDER BY id FOR UPDATE SKIP LOCKED LIMIT ?
                )
                UPDATE domain_event_outbox event SET status='PROCESSING', attempts=event.attempts+1,
                    claimed_at=CURRENT_TIMESTAMP
                FROM candidates WHERE event.id=candidates.id
                RETURNING event.id,event.tenant_id,event.attempts
                """, (rs, row) -> new Claimed(rs.getLong(1), rs.getLong(2), rs.getInt(3)), staleSeconds, limit);
    }

    @Transactional(readOnly = true)
    public DomainEventResponse find(Long id) {
        return jdbcTemplate.queryForObject("""
                SELECT id,event_type,aggregate_type,aggregate_id,payload::text,occurred_at
                FROM domain_event_outbox WHERE id=?
                """, (rs, row) -> map(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getString(5), rs.getTimestamp(6)), id);
    }

    @Transactional(readOnly = true)
    public List<DomainEventResponse> after(long cursor, int limit) {
        return jdbcTemplate.query("""
                SELECT id,event_type,aggregate_type,aggregate_id,payload::text,occurred_at
                FROM domain_event_outbox WHERE id > ? ORDER BY id LIMIT ?
                """, (rs, row) -> map(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getString(5), rs.getTimestamp(6)), cursor, limit);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processed(Long id) {
        jdbcTemplate.update("UPDATE domain_event_outbox SET status='PROCESSED', processed_at=CURRENT_TIMESTAMP, claimed_at=NULL, last_error=NULL WHERE id=?", id);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retry(Long id, String error) {
        jdbcTemplate.update("UPDATE domain_event_outbox SET status='PENDING', next_attempt_at=CURRENT_TIMESTAMP + INTERVAL '2 seconds', claimed_at=NULL, last_error=? WHERE id=?", error, id);
    }

    private DomainEventResponse map(long id, String type, String aggregate, long aggregateId,
                                    String payload, Timestamp occurredAt) {
        try {
            return new DomainEventResponse(id, type, aggregate, aggregateId,
                    objectMapper.readValue(payload, new TypeReference<>() { }), occurredAt.toLocalDateTime());
        } catch (Exception exception) {
            throw new IllegalStateException("Payload de evento inválido", exception);
        }
    }
}
