package com.unitel.laoads.web.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.unitel.laoads.api.common.ApiResponse;
import com.unitel.laoads.api.common.ClientInfos;
import com.unitel.laoads.api.common.CurrentUser;
import com.unitel.laoads.api.common.WorkspaceContext;
import com.unitel.laoads.domain.audit.AuditWriter;
import com.unitel.laoads.domain.audit.Auditable;
import com.unitel.laoads.domain.auth.AuthUser;
import com.unitel.laoads.web.filter.RequestIdFilter;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.MDC;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.io.InputStreamSource;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * AOP {@code @Around @Auditable} (adapt từ BOC AuditAspect). Sau khi method trả thành công:
 * resolve actor / entityId / workspaceId / summary bằng SpEL, dựng detailJson (tham số + kết quả, đã che
 * password/token/secret/ticket), rồi {@link AuditWriter#writeAsync} — không block request.
 * Method ném exception → KHÔNG ghi. Lỗi trong aspect chỉ log warn, không ảnh hưởng response.
 */
@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    static final String MASK = "***";
    static final int MAX_DETAIL = 8000;
    private static final Pattern SENSITIVE = Pattern.compile("(?i).*(password|token|secret|ticket).*");
    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAMES = new DefaultParameterNameDiscoverer();

    private final AuditWriter auditWriter;
    private final ObjectMapper objectMapper;
    private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>();

    @Around("@annotation(auditable)")
    public Object intercept(ProceedingJoinPoint jp, Auditable auditable) throws Throwable {
        Object result = jp.proceed();
        try {
            Method method = ((MethodSignature) jp.getSignature()).getMethod();
            HttpServletRequest req = currentRequest();
            AuditWriter.AuditEvent ev = buildEvent(method, jp.getArgs(), auditable, result, CurrentUser.orNull(),
                    req == null ? null : parseLong(req.getHeader(WorkspaceContext.HEADER)),
                    req == null ? null : ClientInfos.clientIp(req),
                    req == null ? null : req.getHeader("User-Agent"),
                    MDC.get(RequestIdFilter.MDC_KEY));
            auditWriter.writeAsync(ev);
        } catch (Exception e) {
            log.warn("[AUDIT] aspect post-process failed: {}", e.getMessage());
        }
        return result;
    }

    AuditWriter.AuditEvent buildEvent(Method method, Object[] args, Auditable ann, Object result,
                                      AuthUser currentUser, Long workspaceHeader,
                                      String ip, String userAgent, String requestId) {
        Object data = unwrap(result);
        MethodBasedEvaluationContext ctx = new MethodBasedEvaluationContext(null, method, args, NAMES);
        ctx.setVariable("result", data);
        ctx.setVariable("currentUser", currentUser);
        ctx.setVariable("workspaceHeader", workspaceHeader);

        String actionType = firstNonBlank(asString(eval(ann.actionTypeExpr(), ctx)), ann.actionType());
        String actor = firstNonBlank(asString(eval(ann.actor(), ctx)),
                currentUser == null ? "anonymous" : currentUser.username());
        Long actorId = asLong(eval(ann.actorId(), ctx));
        if (actorId == null && currentUser != null && ann.actor().isBlank()) {
            actorId = currentUser.id();
        }
        String entityId = ann.entityId().isBlank() ? reflectId(data) : asString(eval(ann.entityId(), ctx));
        Long workspaceId = asLong(eval(ann.workspaceId(), ctx));
        String summary = asString(eval(ann.summary(), ctx));

        return new AuditWriter.AuditEvent(actor, actorId, actionType, ann.entityType(), entityId, workspaceId,
                summary, detailJson(method, args, data, result), ip, userAgent, requestId, Instant.now());
    }

    private Object eval(String expr, EvaluationContext ctx) {
        if (expr == null || expr.isBlank()) {
            return null;
        }
        try {
            return expressionCache.computeIfAbsent(expr, PARSER::parseExpression).getValue(ctx);
        } catch (Exception e) {
            log.warn("[AUDIT] SpEL lỗi '{}': {}", expr, e.getMessage());
            return null;
        }
    }

    /** {"params": {...}, "result": ...} — đã che field nhạy cảm, cắt ≤ {@value #MAX_DETAIL} ký tự. */
    String detailJson(Method method, Object[] args, Object data, Object rawResult) {
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            String[] names = NAMES.getParameterNames(method);
            for (int i = 0; i < args.length; i++) {
                Object a = args[i];
                if (a == null || a instanceof ServletRequest || a instanceof ServletResponse
                        || a instanceof BindingResult || a instanceof InputStreamSource || a instanceof byte[]) {
                    continue;
                }
                params.put(names != null && i < names.length ? names[i] : "arg" + i, a);
            }
            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("params", params);
            if (data != null && !(rawResult instanceof ResponseEntity<?>) && !(data instanceof byte[])) {
                detail.put("result", data);
            }
            JsonNode tree = objectMapper.valueToTree(detail);
            mask(tree);
            String json = objectMapper.writeValueAsString(tree);
            return json.length() > MAX_DETAIL ? json.substring(0, MAX_DETAIL) : json;
        } catch (Exception e) {
            return "{\"error\":\"serialization-failed\"}";
        }
    }

    static void mask(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            Iterator<Map.Entry<String, JsonNode>> it = obj.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (SENSITIVE.matcher(e.getKey()).matches() && !e.getValue().isNull()) {
                    e.setValue(TextNode.valueOf(MASK));
                } else {
                    mask(e.getValue());
                }
            }
        } else if (node instanceof ArrayNode arr) {
            arr.forEach(AuditAspect::mask);
        }
    }

    private static Object unwrap(Object result) {
        if (result instanceof ApiResponse<?> r) {
            return r.data();
        }
        if (result instanceof ResponseEntity<?> re) {
            Object body = re.getBody();
            return body instanceof ApiResponse<?> r ? r.data() : null;
        }
        return result;
    }

    private static String reflectId(Object data) {
        if (data == null) {
            return null;
        }
        for (String name : new String[]{"id", "getId"}) {
            try {
                Object v = data.getClass().getMethod(name).invoke(data);
                if (v != null) {
                    return v.toString();
                }
            } catch (ReflectiveOperationException ignored) {
                // không có accessor id — bỏ qua
            }
        }
        return null;
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String asString(Object o) {
        return o == null ? null : o.toString();
    }

    private static Long asLong(Object o) {
        if (o instanceof Number n) {
            return n.longValue();
        }
        return o == null ? null : parseLong(o.toString());
    }

    private static Long parseLong(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
