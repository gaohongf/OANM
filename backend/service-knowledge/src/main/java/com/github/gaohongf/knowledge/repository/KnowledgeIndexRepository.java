package com.github.gaohongf.knowledge.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;

import lombok.RequiredArgsConstructor;

/**
 * 文章分块与向量的读写。
 *
 * <h2>为什么不用 MyBatis-Plus</h2>
 * {@code embedding} 是 pgvector 的 {@code vector} 类型，没有现成的类型处理器；
 * 而且重建索引时要按文章物理删除（逻辑删除会在 HNSW 索引里留下墓碑行）。
 *
 * <h2>为什么向量参数必须用 PGobject</h2>
 * PgJDBC 默认把 Java String 当成 varchar 发送，而 pgvector 没有
 * varchar → vector 的隐式转换；{@code ?::vector} 同样救不了。
 * JdbcTemplate 的可变参数最终也走 {@code setString}，所以这里一律用
 * {@link PreparedStatementSetter} + {@code PGobject(type = "vector")}。
 */
@Repository
@RequiredArgsConstructor
public class KnowledgeIndexRepository {

    private static final RowMapper<ChunkHitRow> HIT_ROW_MAPPER = (rs, rowNum) -> new ChunkHitRow(
            rs.getLong("article_id"),
            rs.getInt("chunk_index"),
            rs.getString("content"),
            rs.getDouble("score"));

    private final JdbcTemplate jdbcTemplate;

    /**
     * 把文章标记为索引成功。
     *
     * <p>
     * 条件更新同时承担两件事：校验 version 围栏（嵌入期间文章被改过就返回 0，
     * 本次结果直接丢弃），以及锁住文章行，让同一篇文章的并发重建串行化。
     *
     * @return 1 表示围栏通过、可以替换 chunks；0 表示文章已被修改
     */
    public int markReady(long articleId, int version) {
        return jdbcTemplate.update("""
                UPDATE knowledge_articles
                SET index_status = 'READY', indexed_at = NOW(), index_error = NULL
                WHERE id = ? AND version = ? AND status = 'PUBLISHED' AND deleted = 0
                """, articleId, version);
    }

    /** 索引失败：同样带 version 围栏，避免覆盖并发修改后的新状态 */
    public int markFailed(long articleId, int version, String error) {
        return jdbcTemplate.update("""
                UPDATE knowledge_articles
                SET index_status = 'FAILED', index_error = ?
                WHERE id = ? AND version = ? AND status = 'PUBLISHED' AND deleted = 0
                """, error, articleId, version);
    }

    /** 线程池拒绝：此时拿不到围栏值，只按 id 标记，且不覆盖已就绪的状态 */
    public int markFailedWithoutFence(long articleId, String error) {
        return jdbcTemplate.update("""
                UPDATE knowledge_articles
                SET index_status = 'FAILED', index_error = ?
                WHERE id = ? AND status = 'PUBLISHED' AND index_status = 'INDEXING' AND deleted = 0
                """, error, articleId);
    }

    /**
     * 文章已不是发布态：把状态改回 NONE。
     *
     * @return 1 表示围栏通过，调用方应删除 chunks；0 表示文章又被改过，不要动
     */
    public int resetToNone(long articleId, int version) {
        return jdbcTemplate.update("""
                UPDATE knowledge_articles
                SET index_status = 'NONE', indexed_at = NULL, index_error = NULL
                WHERE id = ? AND version = ? AND status <> 'PUBLISHED' AND deleted = 0
                """, articleId, version);
    }

    /** 物理删除一篇文章的全部分块。派生数据，不需要保留墓碑。 */
    public void deleteChunks(long articleId) {
        jdbcTemplate.update("DELETE FROM knowledge_chunks WHERE article_id = ?", articleId);
    }

    /** 一次性写入一篇文章的全部分块 */
    public void insertChunks(long articleId, List<ChunkRow> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO knowledge_chunks
                    (id, article_id, chunk_index, content, embedding,
                     create_time, update_time, deleted, version)
                VALUES (?, ?, ?, ?, ?, NOW(), NOW(), 0, 0)
                """, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ChunkRow chunk = chunks.get(i);
                ps.setLong(1, IdWorker.getId());
                ps.setLong(2, articleId);
                ps.setInt(3, chunk.chunkIndex());
                ps.setString(4, chunk.content());
                ps.setObject(5, vectorOf(chunk.embedding()));
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
    }

    /**
     * 按余弦距离取近邻。
     *
     * <p>
     * 只按 {@code status = 'PUBLISHED'} 过滤，不要求 {@code index_status = 'READY'}：
     * 索引失败时旧 chunks 仍然保留，文章继续可搜，比让它从结果里消失更合理。
     * 过滤发生在 HNSW 取完近邻之后，所以调用方要过采样。
     */
    public List<ChunkHitRow> search(float[] queryVector, Long categoryId, int limit) {
        String vector = toVectorLiteral(queryVector);
        StringBuilder sql = new StringBuilder("""
                SELECT c.article_id, c.chunk_index, c.content,
                       1 - (c.embedding <=> ?) AS score
                FROM knowledge_chunks c
                JOIN knowledge_articles a ON a.id = c.article_id
                WHERE c.deleted = 0 AND a.deleted = 0 AND a.status = 'PUBLISHED'
                """);
        if (categoryId != null) {
            sql.append(" AND a.category_id = ? ");
        }
        // 排序表达式和 SELECT 里的距离各用一个参数，必须绑两次
        sql.append(" ORDER BY c.embedding <=> ? LIMIT ?");

        return jdbcTemplate.query(sql.toString(), ps -> {
            int index = 1;
            ps.setObject(index++, vectorOf(vector));
            if (categoryId != null) {
                ps.setLong(index++, categoryId);
            }
            ps.setObject(index++, vectorOf(vector));
            ps.setInt(index, limit);
        }, HIT_ROW_MAPPER);
    }

    /** 进程被杀会留下 INDEXING 中间态，启动时据此重新入队 */
    public List<Long> findIndexingArticleIds() {
        return jdbcTemplate.queryForList("""
                SELECT id FROM knowledge_articles
                WHERE status = 'PUBLISHED' AND index_status = 'INDEXING' AND deleted = 0
                """, Long.class);
    }

    private static PGobject vectorOf(float[] vector) throws SQLException {
        return vectorOf(toVectorLiteral(vector));
    }

    private static PGobject vectorOf(String literal) throws SQLException {
        PGobject object = new PGobject();
        object.setType("vector");
        object.setValue(literal);
        return object;
    }

    /** pgvector 的文本表示：[1.0,2.0,...] */
    static String toVectorLiteral(float[] vector) {
        StringBuilder builder = new StringBuilder(vector.length * 10 + 2);
        builder.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(vector[i]);
        }
        return builder.append(']').toString();
    }
}
