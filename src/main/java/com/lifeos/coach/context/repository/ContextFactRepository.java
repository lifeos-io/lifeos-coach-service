package com.lifeos.coach.context.repository;

import com.lifeos.coach.context.entity.ContextFact;
import com.lifeos.coach.context.entity.FactKind;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** The user's facts in plain SQL: an upsert that keeps the newer version is all there is to writing them. */
@Repository
public class ContextFactRepository {

    private final JdbcClient jdbcClient;
    private final JsonMapper jsonMapper;

    public ContextFactRepository(JdbcClient jdbcClient, JsonMapper jsonMapper) {
        this.jdbcClient = jdbcClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Stores the fact, replacing the stored one unless both have a version and the stored one is newer (a late
     * redelivery). Facts without a version are replaced by the latest event (one user's events arrive in order).
     */
    public void upsert(UUID userId, FactKind kind, UUID subjectId, Integer version, Object data, Instant now) {
        jdbcClient.sql("""
                        INSERT INTO context_fact (user_id, kind, subject_id, version, data, updated_at)
                        VALUES (:userId, :kind, :subjectId, :version, :data::jsonb, :now)
                        ON CONFLICT (user_id, kind, subject_id) DO UPDATE
                        SET version = EXCLUDED.version, data = EXCLUDED.data, updated_at = EXCLUDED.updated_at
                        WHERE context_fact.version IS NULL OR EXCLUDED.version IS NULL
                           OR EXCLUDED.version >= context_fact.version
                        """)
                .param("userId", userId)
                .param("kind", kind.name())
                .param("subjectId", subjectId)
                .param("version", version)
                .param("data", jsonMapper.writeValueAsString(data))
                .param("now", Timestamp.from(now))
                .update();
    }

    public Optional<ContextFact> find(UUID userId, FactKind kind, UUID subjectId) {
        return jdbcClient.sql("""
                        SELECT kind, subject_id, version, data::text AS data, updated_at FROM context_fact
                        WHERE user_id = :userId AND kind = :kind AND subject_id = :subjectId
                        """)
                .param("userId", userId).param("kind", kind.name()).param("subjectId", subjectId)
                .query(this::fact)
                .optional();
    }

    /** All of the user's facts, oldest change first. */
    public List<ContextFact> findAll(UUID userId) {
        return jdbcClient.sql("""
                        SELECT kind, subject_id, version, data::text AS data, updated_at FROM context_fact
                        WHERE user_id = :userId ORDER BY updated_at, kind, subject_id
                        """)
                .param("userId", userId)
                .query(this::fact)
                .list();
    }

    public int deleteAll(UUID userId) {
        return jdbcClient.sql("DELETE FROM context_fact WHERE user_id = :userId").param("userId", userId).update();
    }

    private ContextFact fact(ResultSet row, int rowNum) throws SQLException {
        Integer version = row.getObject("version", Integer.class);
        return new ContextFact(FactKind.valueOf(row.getString("kind")), row.getObject("subject_id", UUID.class),
                version, jsonMapper.readTree(row.getString("data")), row.getTimestamp("updated_at").toInstant());
    }
}
