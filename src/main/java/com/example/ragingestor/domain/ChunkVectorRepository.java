package com.example.ragingestor.domain;

import com.example.ragingestor.security.Role;
import com.example.ragingestor.rag.RetrievalFilters;
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

    public void deleteEmbeddingsForVersion(UUID versionId) {
        jdbcTemplate.update("""
                delete from chunk_embedding
                where chunk_id in (select id from document_chunk where version_id = ?)
                """, versionId);
    }

    public record ScoredChunk(UUID chunkId, UUID documentId, UUID documentVersionId, String documentName,
                              String content, Role minRoleRequired, double score, Integer page, String section) {}

    /**
     * Cosine-distance nearest neighbour search (pgvector's `<=>` operator, lower = more similar),
     * pre-filtered so a chunk only comes back if the caller's role outranks (or equals) the
     * chunk's minRoleRequired. Non-ready documents are never exposed to retrieval.
     */
    public List<ScoredChunk> vectorTopK(float[] queryEmbedding, Role callerRole, RetrievalFilters filters, int k) {
        if (k < 1) {
            return List.of();
        }
        String sql = """
                with recursive document_folders(document_id, folder_id, parent_id, min_role_required, deleted) as (
                    select d.id, f.id, f.parent_id, f.min_role_required, f.deleted
                    from document_meta d
                    join folder f on f.id = d.folder_id
                    union
                    select df.document_id, parent.id, parent.parent_id, parent.min_role_required, parent.deleted
                    from document_folders df
                    join folder parent on parent.id = df.parent_id
                )
                select c.id as chunk_id, c.document_id, c.version_id as document_version_id,
                       d.filename as document_name, c.content, c.min_role_required,
                       (1.0 - (e.embedding <=> cast(? as vector))) as score,
                       null::integer as page, null::text as section
                from document_chunk c
                join chunk_embedding e on e.chunk_id = c.id
                join document_meta d on d.id = c.document_id
                where c.deleted = false
                  and d.status = 'READY'
                  and ((d.active_version_id is null and c.version_id is null)
                       or c.version_id = d.active_version_id)
                  and case c.min_role_required
                      when 'READ' then 0
                      when 'WRITE' then 1
                      when 'CUSTODIAN' then 2
                      when 'ADMIN' then 3
                      else 4
                  end <= ?
                  and not exists (
                      select 1
                      from document_folders df
                      where df.document_id = d.id
                        and (df.deleted = true or case df.min_role_required
                            when 'READ' then 0
                            when 'WRITE' then 1
                            when 'CUSTODIAN' then 2
                            when 'ADMIN' then 3
                            else 4
                        end > ?)
                  )
                  and (cast(? as varchar) is null or lower(d.department) = lower(cast(? as varchar)))
                  and (cast(? as varchar) is null or lower(d.document_type) = lower(cast(? as varchar)))
                  and (cast(? as varchar) is null or
                       string_to_array(lower(d.tags), ',') @> string_to_array(cast(? as varchar), ','))
                  and (cast(? as uuid) is null or d.uploaded_by = cast(? as uuid))
                order by e.embedding <=> cast(? as vector)
                limit ?
                """;
        return jdbcTemplate.query(sql, ps -> {
            String vector = vectorLiteral(queryEmbedding);
            ps.setString(1, vector);
            ps.setInt(2, callerRole.ordinal());
            ps.setInt(3, callerRole.ordinal());
            int next = bindFilters(ps, 4, filters);
            ps.setString(next++, vector);
            ps.setInt(next, k);
        }, ChunkVectorRepository::mapChunk);
    }

    public List<ScoredChunk> keywordTopK(String query, Role callerRole, RetrievalFilters filters, int k) {
        if (k < 1 || query == null || query.isBlank()) {
            return List.of();
        }
        String sql = """
                with recursive document_folders(document_id, folder_id, parent_id, min_role_required, deleted) as (
                    select d.id, f.id, f.parent_id, f.min_role_required, f.deleted
                    from document_meta d
                    join folder f on f.id = d.folder_id
                    union
                    select df.document_id, parent.id, parent.parent_id, parent.min_role_required, parent.deleted
                    from document_folders df
                    join folder parent on parent.id = df.parent_id
                )
                select c.id as chunk_id, c.document_id, c.version_id as document_version_id,
                       d.filename as document_name, c.content, c.min_role_required,
                       ts_rank_cd(to_tsvector('english', c.content), plainto_tsquery('english', ?)) as score,
                       null::integer as page, null::text as section
                from document_chunk c
                join document_meta d on d.id = c.document_id
                where c.deleted = false
                  and d.status = 'READY'
                  and ((d.active_version_id is null and c.version_id is null)
                       or c.version_id = d.active_version_id)
                  and case c.min_role_required
                      when 'READ' then 0
                      when 'WRITE' then 1
                      when 'CUSTODIAN' then 2
                      when 'ADMIN' then 3
                      else 4
                  end <= ?
                  and not exists (
                      select 1
                      from document_folders df
                      where df.document_id = d.id
                        and (df.deleted = true or case df.min_role_required
                            when 'READ' then 0
                            when 'WRITE' then 1
                            when 'CUSTODIAN' then 2
                            when 'ADMIN' then 3
                            else 4
                        end > ?)
                  )
                  and (cast(? as varchar) is null or lower(d.department) = lower(cast(? as varchar)))
                  and (cast(? as varchar) is null or lower(d.document_type) = lower(cast(? as varchar)))
                  and (cast(? as varchar) is null or
                       string_to_array(lower(d.tags), ',') @> string_to_array(cast(? as varchar), ','))
                  and (cast(? as uuid) is null or d.uploaded_by = cast(? as uuid))
                  and to_tsvector('english', c.content) @@ plainto_tsquery('english', ?)
                order by score desc
                limit ?
                """;
        return jdbcTemplate.query(sql, ps -> {
            ps.setString(1, query);
            ps.setInt(2, callerRole.ordinal());
            ps.setInt(3, callerRole.ordinal());
            int next = bindFilters(ps, 4, filters);
            ps.setString(next++, query);
            ps.setInt(next, k);
        }, ChunkVectorRepository::mapChunk);
    }

    private static int bindFilters(PreparedStatement ps, int index, RetrievalFilters filters)
            throws java.sql.SQLException {
        ps.setString(index++, filters.department());
        ps.setString(index++, filters.department());
        ps.setString(index++, filters.documentType());
        ps.setString(index++, filters.documentType());
        String tags = filters.tags().isEmpty() ? null : String.join(",", filters.tags());
        ps.setString(index++, tags);
        ps.setString(index++, tags);
        String ownerId = filters.ownerId() == null ? null : filters.ownerId().toString();
        ps.setString(index++, ownerId);
        ps.setString(index++, ownerId);
        return index;
    }

    private static ScoredChunk mapChunk(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        return new ScoredChunk((UUID) rs.getObject("chunk_id"), (UUID) rs.getObject("document_id"),
                (UUID) rs.getObject("document_version_id"), rs.getString("document_name"),
                rs.getString("content"), Role.valueOf(rs.getString("min_role_required")),
                rs.getDouble("score"), (Integer) rs.getObject("page"), rs.getString("section"));
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
