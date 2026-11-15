package com.unitel.laoads.domain.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    /** username đã chuẩn hoá chữ thường (Texts.normalizeUsername). */
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsBySystemRole(SystemRole systemRole);

    @Query("SELECT u FROM AppUser u WHERE LOWER(u.email) = :email ORDER BY u.id")
    List<AppUser> findByEmailLower(@Param("email") String emailLower);

    List<AppUser> findByPhoneOrderByIdAsc(String phone);

    @Query(value = "SELECT u FROM AppUser u WHERE "
            + "(:q IS NULL OR LOWER(u.username) LIKE :q ESCAPE '!' OR LOWER(u.fullName) LIKE :q ESCAPE '!' "
            + "  OR LOWER(u.email) LIKE :q ESCAPE '!' OR u.phone LIKE :q ESCAPE '!') "
            + "AND (:status IS NULL OR u.status = :status) "
            + "AND (:systemRole IS NULL OR u.systemRole = :systemRole) "
            + "ORDER BY u.createdAt DESC, u.id DESC",
            countQuery = "SELECT COUNT(u) FROM AppUser u WHERE "
            + "(:q IS NULL OR LOWER(u.username) LIKE :q ESCAPE '!' OR LOWER(u.fullName) LIKE :q ESCAPE '!' "
            + "  OR LOWER(u.email) LIKE :q ESCAPE '!' OR u.phone LIKE :q ESCAPE '!') "
            + "AND (:status IS NULL OR u.status = :status) "
            + "AND (:systemRole IS NULL OR u.systemRole = :systemRole)")
    Page<AppUser> search(@Param("q") String likePattern,
                         @Param("status") UserStatus status,
                         @Param("systemRole") SystemRole systemRole,
                         Pageable pageable);
}
