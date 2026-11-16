package com.unitel.laoads.infra.audience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.unitel.laoads.domain.audience.Audience;
import com.unitel.laoads.domain.audience.AudienceClient;
import com.unitel.laoads.domain.audience.AudienceException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Audience API giả trên localhost theo đúng ví dụ curl trong tài liệu (OCS: /internal, BCCS3: /internal/bbcs3). */
class HttpAudienceClientTest {

    HttpServer server;
    HttpAudienceClient client;
    final AtomicReference<String> lastKey = new AtomicReference<>();
    final AtomicReference<String> lastBody = new AtomicReference<>();

    static final Audience.Request REQUEST = new Audience.Request("subs", List.of(
            new Audience.SelectedColumn("phone_number", Audience.DataType.STRING, "PHONE")), List.of());

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/internal/", ex -> handle(ex, "/api/v1/internal"));
        server.start();
        String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/internal";
        client = new HttpAudienceClient(new AudienceProperties("http", "x-api-key", Duration.ofSeconds(2),
                Duration.ofSeconds(3), Duration.ofSeconds(1),
                new AudienceProperties.Endpoint(base, "api-key-test"),
                new AudienceProperties.Endpoint(base + "/bbcs3", "api-key-test")), new ObjectMapper());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex, String prefix) throws IOException {
        lastKey.set(ex.getRequestHeaders().getFirst("x-api-key"));
        lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        String path = ex.getRequestURI().getPath().substring(prefix.length()).replace("/bbcs3", "");
        if (!"api-key-test".equals(lastKey.get())) {
            send(ex, 401, "{\"success\":false,\"message\":\"Unauthorized\"}");
            return;
        }
        switch (path) {
            case "/catalog/tables" -> send(ex, 200, "{\"success\":true,\"data\":[\"table_1\",\"table_2\"]}");
            case "/catalog/tables/table_1/columns" -> send(ex, 200, "{\"success\":true,\"data\":["
                    + "{\"colName\":\"phone_number\",\"dataType\":\"string\"},{\"colName\":\"age\",\"dataType\":\"int\"}]}");
            case "/audiences/verify" -> {
                if (lastBody.get().contains("\"tableName\":\"bad\"")) {
                    send(ex, 400, "{\"success\":false,\"message\":\"table bad not found\"}");
                } else {
                    send(ex, 200, "{\"success\":true,\"data\":{\"valid\":true}}");
                }
            }
            case "/audiences/stream" -> {
                if (lastBody.get().contains("\"tableName\":\"broken\"")) {
                    send(ex, 200, "{\"phone_number\":\"020x\"}\nnot-json\n");
                } else if (lastBody.get().contains("\"tableName\":\"stall\"")) {
                    ex.getResponseHeaders().add("Content-Type", "application/x-ndjson");
                    ex.sendResponseHeaders(200, 0);
                    OutputStream out = ex.getResponseBody();
                    out.write("{\"phone_number\":\"02055510002\"}\n".getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    try {
                        Thread.sleep(4000);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                    ex.close();
                } else {
                    send(ex, 200, "{\"phone_number\":\"02055510002\",\"age\":25}\n\n{\"phone_number\":\"02055510003\",\"age\":31}\n");
                }
            }
            default -> send(ex, 404, "{\"success\":false,\"message\":\"not found\"}");
        }
    }

    private static void send(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }

    @Test
    @DisplayName("catalog: danh sách bảng / cột, gửi header x-api-key")
    void catalog() {
        assertThat(client.tables(Audience.Source.OCS)).containsExactly("table_1", "table_2");
        assertThat(lastKey.get()).isEqualTo("api-key-test");
        assertThat(client.columns(Audience.Source.BCCS3, "table_1")).containsExactly(
                new AudienceClient.RawColumn("phone_number", "string"), new AudienceClient.RawColumn("age", "int"));
    }

    @Test
    @DisplayName("verify: data.valid; service trả 400 → valid=false kèm message")
    void verify() {
        assertThat(client.verify(Audience.Source.OCS, REQUEST).valid()).isTrue();
        assertThat(lastBody.get()).contains("\"conditions\":[]").doesNotContain("offset");
        Audience.VerifyResult bad = client.verify(Audience.Source.OCS,
                new Audience.Request("bad", REQUEST.selectedColumns(), List.of()));
        assertThat(bad.valid()).isFalse();
        assertThat(bad.message()).contains("table bad not found");
    }

    @Test
    @DisplayName("stream NDJSON: bỏ dòng trống; OCS gửi offset, BCCS3 không")
    void stream() {
        List<Map<String, Object>> rows = new ArrayList<>();
        assertThat(client.stream(Audience.Source.OCS, REQUEST, 7, rows::add)).isEqualTo(2);
        assertThat(rows).extracting(r -> r.get("phone_number")).containsExactly("02055510002", "02055510003");
        assertThat(lastBody.get()).contains("\"offset\":7");
        client.stream(Audience.Source.BCCS3, REQUEST, 7, r -> { });
        assertThat(lastBody.get()).doesNotContain("offset");
    }

    @Test
    @DisplayName("lỗi: API key sai → UNAUTHORIZED; dòng không phải JSON → PROTOCOL; stream treo → UNAVAILABLE; chưa cấu hình")
    void errors() {
        HttpAudienceClient wrongKey = new HttpAudienceClient(new AudienceProperties("http", null, null, null, null,
                new AudienceProperties.Endpoint("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/internal", "invalid"),
                null), new ObjectMapper());
        assertThatThrownBy(() -> wrongKey.tables(Audience.Source.OCS))
                .isInstanceOfSatisfying(AudienceException.class, e -> assertThat(e.kind()).isEqualTo(AudienceException.Kind.UNAUTHORIZED));
        assertThatThrownBy(() -> wrongKey.tables(Audience.Source.BCCS3))
                .isInstanceOfSatisfying(AudienceException.class, e -> assertThat(e.kind()).isEqualTo(AudienceException.Kind.NOT_CONFIGURED));

        assertThatThrownBy(() -> client.stream(Audience.Source.OCS,
                new Audience.Request("broken", REQUEST.selectedColumns(), List.of()), 0, r -> { }))
                .isInstanceOfSatisfying(AudienceException.class, e -> assertThat(e.kind()).isEqualTo(AudienceException.Kind.PROTOCOL));

        List<Map<String, Object>> rows = new ArrayList<>();
        assertThatThrownBy(() -> client.stream(Audience.Source.OCS,
                new Audience.Request("stall", REQUEST.selectedColumns(), List.of()), 0, rows::add))
                .isInstanceOfSatisfying(AudienceException.class, e -> {
                    assertThat(e.kind()).isEqualTo(AudienceException.Kind.UNAVAILABLE);
                    assertThat(e.getMessage()).contains("không có dữ liệu");
                });
        assertThat(rows).hasSize(1);
    }
}
