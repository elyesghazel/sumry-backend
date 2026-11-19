package ch.sumry.core.module.registration

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant
import java.util.*

interface InviteCodeRepository : JpaRepository<InviteCode, UUID> {

    fun findAllByOrderByCreatedAtDesc(): List<InviteCode>

    @Query(
        """
        select count(i) > 0 from InviteCode i
        where i.codeHash = :hash and i.usedAt is null and i.revokedAt is null
          and (i.expiresAt is null or i.expiresAt > :now)
        """
    )
    fun isRedeemable(@Param("hash") hash: String, @Param("now") now: Instant): Boolean

    /**
     * Marks the code used in a single conditional update, so two signups racing on the same
     * code cannot both succeed. Returns the number of rows changed: 1 on success, else 0.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        """
        update InviteCode i set i.usedAt = :now, i.usedBy = :userId
        where i.codeHash = :hash and i.usedAt is null and i.revokedAt is null
          and (i.expiresAt is null or i.expiresAt > :now)
        """
    )
    fun redeem(@Param("hash") hash: String, @Param("userId") userId: UUID, @Param("now") now: Instant): Int
}
