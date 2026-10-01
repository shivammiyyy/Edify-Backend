package com.example.ragingestor.domain;

import com.example.ragingestor.security.Role;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

@Repository
public class ChunkVectorRepository {

    private final JdbcTemplate jdbcTemplate;

    public ChunkVectorRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void upsertEmbedding(UUID chunkId, float[] embedding) {
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "insert into chunk_embedding (chunk_id, embedding) values (?, cast(? as vector)) " +
                    "on conflict (chunk_id) do update set embedding = excluded.embedding");
            ps.setObject(1, chunkId);
            ps.setString(2, vectorLiteral(embedding));
            return ps;
        });
    }

    public void deleteEmbedding(UUID chunkId) {
        jdbcTemplate.update("delete from chunk_embedding where chunk_id = ?", chunkId);
    }

    public record ScoredChunk(UUID chunkId, UUID documentId, String content, Role minRoleRequired, double distance) {}

    /**
     * Cosine-distance nearest neighbour search (pgvector's `<=>` operator, lower = more similar),
     * pre-filtered so a chunk only comes back if the caller's role outranks (or equals) the
     * chunk's minRoleRequired. Non-ready documents are never exposed to retrieval.
     */
    public List<ScoredChunk> topK(float[] queryEmbedding, Role callerRole, int k) {
        if (k < 1) {
            return List.of();
        }
        String sql = """
                select c.id as chunk_id, c.document_id, c.content, c.min_role_required,
                       (e.embedding <=> cast(? as vector)) as distance
                from document_chunk c
                join chunk_embedding e on e.chunk_id = c.id
                join document_meta d on d.id = c.document_id
                where c.deleted = false
                  and d.status = 'READY'
                  and case c.min_role_required
                      when 'READ' then 0
                      when 'WRITE' then 1
                      when 'CUSTODIAN' then 2
                      when 'ADMIN' then 3
                      else 4
                  end <= ?
                order by e.embedding <=> cast(? as vector)
                limit ?
                """;
        return jdbcTemplate.query(sql, ps -> {
            String vector = vectorLiteral(queryEmbedding);
            ps.setString(1, vector);
            ps.setInt(2, callerRole.ordinal());
            ps.setString(3, vector);
            ps.setInt(4, k);
        }, (rs, rowNum) -> new ScoredChunk(
                (UUID) rs.getObject("chunk_id"),
                (UUID) rs.getObject("document_id"),
                rs.getString("content"),
                Role.valueOf(rs.getString("min_role_required")),
                rs.getDouble("distance")
        ));
    }

    private static String vectorLiteral(float[] embedding) {
        if (embedding.length == 0) {
            throw new IllegalArgumentException("embedding must not be empty");
        }
        StringBuilder literal = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (!Float.isFinite(embedding[i])) {
                throw new IllegalArgumentException("embedding values must be finite");
            }
            if (i > 0) {
                literal.append(',');
            }
            literal.append(embedding[i]);
        }
        return literal.append(']').toString();
    }
}
