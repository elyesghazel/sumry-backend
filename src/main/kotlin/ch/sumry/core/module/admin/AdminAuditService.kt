package ch.sumry.core.module.admin

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import kotlin.math.ceil

/** Writes and reads the admin_audit log. Rows are never updated or deleted. */
@Service
class AdminAuditService(
    private val jdbc: JdbcTemplate,
    private val objectMapper: ObjectMapper,
) {

    fun record(adminId: UUID, action: AdminAction, targetUserId: UUID? = null, detail: Map<String, Any?>? = null) {
        jdbc.update(
            "INSERT INTO admin_audit (id, admin_id, action, target_user_id, detail, created_at) VALUES (?, ?, ?, ?, ?::jsonb, ?)",
            UUID.randomUUID(), adminId, action.name, targetUserId,
            objectMapper.writeValueAsString(detail ?: emptyMap<String, Any?>()),
            Timestamp.from(Instant.now()),
        )
    }

    fun page(page: Int?, size: Int?): AdminAuditPageDto {
        val p = (page ?: 0).coerceAtLeast(0)
        val s = (size ?: DEFAULT_SIZE).coerceIn(1, MAX_SIZE)
        val total = jdbc.queryForObject("SELECT count(*) FROM admin_audit", Long::class.java) ?: 0L
        val items = jdbc.query(
            """
            SELECT a.id, a.action, a.admin_id, ad.username AS admin_username,
                   a.target_user_id, t.username AS target_username, a.detail::text AS detail, a.created_at
            FROM admin_audit a
            LEFT JOIN users ad ON ad.id = a.admin_id
            LEFT JOIN users t ON t.id = a.target_user_id
            ORDER BY a.seq DESC
            LIMIT ? OFFSET ?
            """,
            { rs, _ ->
                AdminAuditEntryDto(
                    id = rs.getObject("id", UUID::class.java),
                    action = AdminAction.valueOf(rs.getString("action")),
                    adminId = rs.getObject("admin_id", UUID::class.java),
                    adminUsername = rs.getString("admin_username"),
                    targetUserId = rs.getObject("target_user_id", UUID::class.java),
                    targetUsername = rs.getString("target_username"),
                    detail = objectMapper.readValue(rs.getString("detail"), DETAIL_TYPE),
                    createdAt = rs.getTimestamp("created_at").toInstant(),
                )
            },
            s, p.toLong() * s,
        )
        return AdminAuditPageDto(
            items = items,
            page = p,
            size = s,
            totalItems = total,
            totalPages = ceil(total.toDouble() / s).toInt(),
        )
    }

    private companion object {
        const val DEFAULT_SIZE = 50
        const val MAX_SIZE = 200
        val DETAIL_TYPE = object : TypeReference<Map<String, Any?>>() {}
    }
}
