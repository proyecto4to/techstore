package com.techstore.notificaciones.outbox;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OutboxStore {
    private final JdbcTemplate jdbcTemplate;

    public record ClaimedJob(Long id, Long tenantId, int attempts) { }

    /** Reclama trabajo sin bloquear otros nodos y recupera reclamos abandonados. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<ClaimedJob> claim(int batchSize, long staleAfterSeconds) {
        return jdbcTemplate.query("""
                WITH candidates AS (
                    SELECT id
                    FROM notification_outbox
                    WHERE (status = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                       OR (status = 'PROCESSING'
                           AND claimed_at < CURRENT_TIMESTAMP - (? * INTERVAL '1 second'))
                    ORDER BY next_attempt_at, created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE notification_outbox outbox
                SET status = 'PROCESSING', attempts = outbox.attempts + 1,
                    claimed_at = CURRENT_TIMESTAMP, updated_at = CURRENT_TIMESTAMP
                FROM candidates
                WHERE outbox.id = candidates.id
                RETURNING outbox.id, outbox.tenant_id, outbox.attempts
                """, (rs, row) -> new ClaimedJob(
                        rs.getLong("id"), rs.getLong("tenant_id"), rs.getInt("attempts")),
                staleAfterSeconds, batchSize);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessed(Long id, Long tenantId) {
        jdbcTemplate.update("""
                UPDATE notification_outbox
                SET status = 'PROCESSED', processed_at = CURRENT_TIMESTAMP,
                    claimed_at = NULL, last_error_sanitized = NULL, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND tenant_id = ? AND status = 'PROCESSING'
                """, id, tenantId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, Long tenantId, boolean exhausted,
                           long retryAfterSeconds, String sanitizedError) {
        jdbcTemplate.update("""
                UPDATE notification_outbox
                SET status = ?, next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second'),
                    claimed_at = NULL, last_error_sanitized = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND tenant_id = ? AND status = 'PROCESSING'
                """, exhausted ? "EXHAUSTED" : "PENDING", retryAfterSeconds,
                sanitizedError, id, tenantId);
    }
}
