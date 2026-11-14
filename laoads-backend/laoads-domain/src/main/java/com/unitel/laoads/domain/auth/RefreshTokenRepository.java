package com.unitel.laoads.domain.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /** Thu hồi mọi refresh token còn hiệu lực của user (khoá tài khoản / admin đặt lại mật khẩu). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.userId = :userId AND t.revokedAt IS NULL")
    int revokeAllOfUser(@Param("userId") Long userId, @Param("now") Instant now);

    /** Thu hồi mọi phiên khác của user, giữ lại phiên {@code keepId} (đổi mật khẩu). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revokedAt = :now WHERE t.userId = :userId AND t.revokedAt IS NULL "
            + "AND t.id <> :keepId")
    int revokeOthersOfUser(@Param("userId") Long userId, @Param("keepId") Long keepId, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
