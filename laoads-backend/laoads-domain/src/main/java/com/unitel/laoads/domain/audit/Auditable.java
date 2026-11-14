package com.unitel.laoads.domain.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Đánh dấu method controller cần ghi LADS_AUDIT_LOG khi thành công (exception → không ghi).
 * AOP ở laoads-web ({@code AuditAspect}).
 *
 * <p>Các thuộc tính {@code *Expr}/{@code entityId}/{@code workspaceId}/{@code summary}/{@code actor} là
 * biểu thức SpEL (rỗng = không dùng). Biến có sẵn:
 * <ul>
 *   <li>{@code #<tên tham số>} — tham số method (compile với {@code -parameters});</li>
 *   <li>{@code #result} — {@code data} của ApiResponse trả về (hoặc giá trị trả về nếu không phải envelope);</li>
 *   <li>{@code #currentUser} — AuthUser hiện tại (null nếu chưa đăng nhập);</li>
 *   <li>{@code #workspaceHeader} — giá trị số của header X-Workspace-Id (null nếu thiếu/sai).</li>
 * </ul>
 * Mặc định: actor = username đang đăng nhập; entityId = {@code #result.id} (nếu có); workspaceId = null.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {

    /** LOGIN | CREATE | UPDATE | DELETE | LOCK | UNLOCK | RESET_PASSWORD | EXPORT | CHANGE_ROLE | ... */
    String actionType();

    /** USER | WORKSPACE | MEMBER | ROLE_PERMISSION | PRICE_QUOTE | AUDIT_LOG | ... */
    String entityType();

    /** SpEL ghi đè actionType, vd {@code #req.status().name() == 'LOCKED' ? 'LOCK' : 'UNLOCK'}. */
    String actionTypeExpr() default "";

    /** SpEL id đối tượng. */
    String entityId() default "";

    /** SpEL workspace liên quan — để OWNER/ADMIN xem log của workspace mình (contract §8). */
    String workspaceId() default "";

    /** SpEL tóm tắt hiển thị (tiếng Việt). */
    String summary() default "";

    /** SpEL username người thực hiện (dùng cho LOGIN khi SecurityContext còn trống). */
    String actor() default "";

    /** SpEL id người thực hiện. */
    String actorId() default "";
}
