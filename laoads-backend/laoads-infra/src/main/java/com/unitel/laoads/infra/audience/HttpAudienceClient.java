package com.unitel.laoads.infra.audience;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unitel.laoads.domain.audience.Audience;
import com.unitel.laoads.domain.audience.AudienceClient;
import com.unitel.laoads.domain.audience.AudienceException;
import com.unitel.laoads.domain.audience.AudienceException.Kind;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gọi Audience API thật (OCS / BCCS3) theo tài liệu tích hợp: header {@code x-api-key}, envelope
 * {@code {success, data}}, stream {@code application/x-ndjson}. OCS gửi thêm {@code offset} để nạp tiếp.
 * Stream không có read-timeout của HttpClient nên dùng watchdog: quá {@code streamIdleTimeout} không có dòng mới
 * thì đóng kết nối → lỗi UNAVAILABLE (tệp FAILED, chạy lại được).
 */
@Slf4j
public class HttpAudienceClient implements AudienceClient {

    private static final TypeReference<Map<String, Object>> ROW = new TypeReference<>() {
    };

    private final AudienceProperties props;
    private final ObjectMapper mapper;
    private final HttpClient http;
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "audience-stream-watchdog");
        t.setDaemon(true);
        return t;
    });

    public HttpAudienceClient(AudienceProperties props, ObjectMapper mapper) {
        this.props = props;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.connectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public String mode() {
        return "http";
    }

    @Override
    public boolean configured(Audience.Source source) {
        return props.endpoint(source).configured();
    }

    @Override
    public List<String> tables(Audience.Source source) {
        JsonNode data = getJson(source, "/catalog/tables");
        List<String> out = new ArrayList<>();
        if (data.isArray()) {
            data.forEach(n -> out.add(n.isTextual() ? n.asText() : n.path("tableName").asText(n.path("name").asText())));
        }
        return out;
    }

    @Override
    public List<RawColumn> columns(Audience.Source source, String table) {
        JsonNode data = getJson(source, "/catalog/tables/" + URLEncoder.encode(table, StandardCharsets.UTF_8).replace("+", "%20")
                + "/columns");
        List<RawColumn> out = new ArrayList<>();
        if (data.isArray()) {
            for (JsonNode n : data) {
                String name = text(n, "colName", "columnName", "name");
                if (name != null) {
                    out.add(new RawColumn(name, text(n, "dataType", "type")));
                }
            }
        }
        return out;
    }

    @Override
    public Audience.VerifyResult verify(Audience.Source source, Audience.Request request) {
        HttpResponse<String> res = send(source, post(source, "/audiences/verify", body(source, request, null), "application/json"),
                HttpResponse.BodyHandlers.ofString());
        JsonNode root = parse(res.body());
        if (res.statusCode() == 400 || res.statusCode() == 422) {
            // service trả lỗi validation → coi là request không hợp lệ (không phải mất kết nối)
            return new Audience.VerifyResult(false, message(root, "HTTP " + res.statusCode()));
        }
        check(res.statusCode(), root);
        JsonNode data = root.path("data");
        return new Audience.VerifyResult(data.path("valid").asBoolean(false), text(data, "message", "reason"));
    }

    @Override
    public long stream(Audience.Source source, Audience.Request request, long offset, RowHandler handler) {
        HttpRequest req = post(source, "/audiences/stream", body(source, request, source.supportsOffset() ? offset : null),
                "application/x-ndjson");
        HttpResponse<InputStream> res = send(source, req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() >= 300) {
            String text;
            try (InputStream in = res.body()) {
                text = new String(in.readNBytes(4096), StandardCharsets.UTF_8);
            } catch (IOException e) {
                text = "";
            }
            check(res.statusCode(), parse(text));
        }
        AtomicLong lastLine = new AtomicLong(System.nanoTime());
        AtomicBoolean idleClosed = new AtomicBoolean(false);
        long idleNanos = props.streamIdleTimeout().toNanos();
        InputStream body = res.body();
        ScheduledFuture<?> guard = watchdog.scheduleAtFixedRate(() -> {
            if (System.nanoTime() - lastLine.get() > idleNanos && idleClosed.compareAndSet(false, true)) {
                try {
                    body.close();
                } catch (IOException ignored) {
                    // đóng để readLine() thoát
                }
            }
        }, 1, 1, TimeUnit.SECONDS);
        long count = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lastLine.set(System.nanoTime());
                if (line.isBlank()) {
                    continue;
                }
                Map<String, Object> row;
                try {
                    row = mapper.readValue(line, ROW);
                } catch (IOException e) {
                    throw new AudienceException(Kind.PROTOCOL, "Dòng NDJSON " + (offset + count + 1) + " không phải JSON", e);
                }
                handler.row(row);
                count++;
            }
        } catch (IOException e) {
            if (idleClosed.get()) {
                throw new AudienceException(Kind.UNAVAILABLE, "Stream dừng quá " + props.streamIdleTimeout().toSeconds()
                        + " giây không có dữ liệu (đã nạp " + count + " dòng trong lần này)", e);
            }
            throw new AudienceException(Kind.UNAVAILABLE, "Stream bị ngắt sau " + count + " dòng: " + e.getMessage(), e);
        } finally {
            guard.cancel(false);
        }
        return count;
    }

    // ───────────── HTTP ─────────────

    private JsonNode getJson(Audience.Source source, String path) {
        HttpRequest req = base(source, path).header("Accept", "application/json").GET().build();
        HttpResponse<String> res = send(source, req, HttpResponse.BodyHandlers.ofString());
        JsonNode root = parse(res.body());
        check(res.statusCode(), root);
        if (root.has("success") && !root.path("success").asBoolean(true)) {
            throw new AudienceException(Kind.BAD_REQUEST, message(root, "success=false"));
        }
        return root.has("data") ? root.path("data") : root;
    }

    private HttpRequest post(Audience.Source source, String path, String json, String accept) {
        return base(source, path)
                .header("Content-Type", "application/json")
                .header("Accept", accept)
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
    }

    private HttpRequest.Builder base(Audience.Source source, String path) {
        AudienceProperties.Endpoint ep = props.endpoint(source);
        if (!ep.configured()) {
            throw new AudienceException(Kind.NOT_CONFIGURED, "Thiếu URL hoặc API key của " + source);
        }
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(ep.base() + path))
                .header(props.apiKeyHeader(), ep.apiKey());
        // stream có thể kéo dài: chỉ giới hạn thời gian chờ header với request thường
        if (!path.endsWith("/stream")) {
            b.timeout(props.requestTimeout());
        }
        return b;
    }

    private <T> HttpResponse<T> send(Audience.Source source, HttpRequest req, HttpResponse.BodyHandler<T> handler) {
        try {
            return http.send(req, handler);
        } catch (HttpConnectTimeoutException e) {
            throw new AudienceException(Kind.UNAVAILABLE, "Quá thời gian kết nối " + source, e);
        } catch (HttpTimeoutException e) {
            throw new AudienceException(Kind.UNAVAILABLE, "Quá thời gian chờ phản hồi " + source, e);
        } catch (ConnectException e) {
            throw new AudienceException(Kind.UNAVAILABLE, "Không kết nối được " + req.uri().getHost() + ":" + req.uri().getPort(), e);
        } catch (IOException e) {
            throw new AudienceException(Kind.UNAVAILABLE, e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AudienceException(Kind.UNAVAILABLE, "Bị ngắt", e);
        } catch (IllegalArgumentException e) {
            throw new AudienceException(Kind.NOT_CONFIGURED, "URL không hợp lệ: " + e.getMessage(), e);
        }
    }

    private static void check(int status, JsonNode root) {
        if (status == 401 || status == 403) {
            throw new AudienceException(Kind.UNAUTHORIZED, "HTTP " + status);
        }
        if (status == 400 || status == 404 || status == 422) {
            throw new AudienceException(Kind.BAD_REQUEST, message(root, "HTTP " + status));
        }
        if (status >= 300) {
            throw new AudienceException(Kind.UNAVAILABLE, "HTTP " + status + " " + message(root, ""));
        }
    }

    // ───────────── JSON ─────────────

    String body(Audience.Source source, Audience.Request request, Long offset) {
        ObjectNode n = mapper.valueToTree(request);
        n.putIfAbsent("conditions", mapper.createArrayNode()); // conditions luôn là mảng, không null
        if (offset != null) {
            n.put("offset", Math.max(0, offset));
        }
        try {
            return mapper.writeValueAsString(n);
        } catch (IOException e) {
            throw new AudienceException(Kind.PROTOCOL, "Không tạo được JSON request", e);
        }
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return mapper.createObjectNode();
        }
        try {
            return mapper.readTree(body);
        } catch (IOException e) {
            return mapper.createObjectNode().put("message", body.length() > 300 ? body.substring(0, 300) : body);
        }
    }

    private static String message(JsonNode root, String fallback) {
        String m = text(root, "message", "error", "detail");
        return m == null || m.isBlank() ? fallback : m;
    }

    private static String text(JsonNode n, String... keys) {
        for (String k : keys) {
            JsonNode v = n.get(k);
            if (v != null && !v.isNull()) {
                return v.asText();
            }
        }
        return null;
    }
}
