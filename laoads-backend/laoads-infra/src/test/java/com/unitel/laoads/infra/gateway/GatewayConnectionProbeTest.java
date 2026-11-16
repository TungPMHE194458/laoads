package com.unitel.laoads.infra.gateway;

import com.sun.net.httpserver.HttpServer;
import com.unitel.laoads.domain.settings.ConnectionProbe.Result;
import com.unitel.laoads.domain.settings.ConnectionProbe.Target;
import com.unitel.laoads.domain.settings.ConnectionTestCode;
import com.unitel.laoads.domain.settings.GatewayProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Gateway giả chạy trên localhost (SMPP bằng ServerSocket, HTTP bằng HttpServer của JDK) — không cần mạng ngoài. */
class GatewayConnectionProbeTest {

    private final GatewayConnectionProbe probe = new GatewayConnectionProbe(Duration.ofSeconds(3));

    @Test
    @DisplayName("PDU bind_transceiver đúng định dạng SMPP 3.4")
    void bindPduLayout() {
        byte[] pdu = GatewayConnectionProbe.bindTransceiver("sys", "pw", null, 7);
        ByteBuffer b = ByteBuffer.wrap(pdu);
        assertThat(b.getInt()).isEqualTo(pdu.length);
        assertThat(b.getInt()).isEqualTo(0x00000009);
        assertThat(b.getInt()).isZero();
        assertThat(b.getInt()).isEqualTo(7);
        // "sys\0" "pw\0" "\0"(system_type) 0x34 ton npi "\0"(address_range)
        byte[] body = new byte[pdu.length - 16];
        b.get(body);
        assertThat(body).containsExactly('s', 'y', 's', 0, 'p', 'w', 0, 0, 0x34, 0, 0, 0);
    }

    @Test
    @DisplayName("SMPP: bind thành công → OK; sai mật khẩu → AUTH_FAILED; gateway nhận đúng system_id/password")
    void smppBind() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        try (FakeSmsc ok = new FakeSmsc(0, seen)) {
            Result r = probe.probe(new Target(GatewayProtocol.SMPP, "127.0.0.1", ok.port(), null, null, "laoads", "pw"));
            assertThat(r.code()).isEqualTo(ConnectionTestCode.OK);
            assertThat(r.latencyMs()).isNotNull();
            assertThat(seen.get()).isEqualTo("laoads/pw");
        }
        try (FakeSmsc bad = new FakeSmsc(0x0000000E, seen)) {
            Result r = probe.probe(new Target(GatewayProtocol.SMPP, "127.0.0.1", bad.port(), null, null, "laoads", "x"));
            assertThat(r.code()).isEqualTo(ConnectionTestCode.AUTH_FAILED);
            assertThat(r.detail()).contains("0x0000000E");
        }
    }

    @Test
    @DisplayName("SMPP: port đóng → REFUSED")
    void smppRefused() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            port = s.getLocalPort();
        }
        Result r = probe.probe(new Target(GatewayProtocol.SMPP, "127.0.0.1", port, null, null, "laoads", "pw"));
        assertThat(r.code()).isEqualTo(ConnectionTestCode.REFUSED);
    }

    @Test
    @DisplayName("HTTP: 200 → OK (gửi Basic auth); 401 → AUTH_FAILED; 500 → REJECTED")
    void http() throws Exception {
        AtomicReference<String> auth = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/ok", ex -> {
            auth.set(ex.getRequestHeaders().getFirst("Authorization"));
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.createContext("/denied", ex -> {
            ex.sendResponseHeaders(401, -1);
            ex.close();
        });
        server.createContext("/boom", ex -> {
            ex.sendResponseHeaders(500, -1);
            ex.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            Result ok = probe.probe(new Target(GatewayProtocol.HTTP, null, null, null, base + "/ok", "u", "p"));
            assertThat(ok.code()).isEqualTo(ConnectionTestCode.OK);
            assertThat(auth.get()).isEqualTo("Basic dTpw");
            assertThat(probe.probe(new Target(GatewayProtocol.HTTP, null, null, null, base + "/denied", null, "tok")).code())
                    .isEqualTo(ConnectionTestCode.AUTH_FAILED);
            assertThat(probe.probe(new Target(GatewayProtocol.HTTP, null, null, null, base + "/boom", null, null)).code())
                    .isEqualTo(ConnectionTestCode.REJECTED);
        } finally {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("diễn giải phản hồi: generic_nack / PDU lạ → PROTOCOL_ERROR; status khác → REJECTED; HTTP 405 vẫn OK")
    void interpret() {
        assertThat(GatewayConnectionProbe.interpretBindResponse(0x80000000, 3, 1).code()).isEqualTo(ConnectionTestCode.PROTOCOL_ERROR);
        assertThat(GatewayConnectionProbe.interpretBindResponse(0x80000004, 0, 1).code()).isEqualTo(ConnectionTestCode.PROTOCOL_ERROR);
        assertThat(GatewayConnectionProbe.interpretBindResponse(0x80000009, 0x0F, 1).code()).isEqualTo(ConnectionTestCode.AUTH_FAILED);
        assertThat(GatewayConnectionProbe.interpretBindResponse(0x80000009, 0x0D, 1).code()).isEqualTo(ConnectionTestCode.REJECTED);
        assertThat(GatewayConnectionProbe.interpretHttpStatus(405, 1).code()).isEqualTo(ConnectionTestCode.OK);
        assertThat(GatewayConnectionProbe.interpretHttpStatus(404, 1).code()).isEqualTo(ConnectionTestCode.REJECTED);
    }

    /** SMSC giả: nhận bind_transceiver, ghi lại system_id/password, trả bind_transceiver_resp với status cho trước. */
    private static final class FakeSmsc implements AutoCloseable {
        private final ServerSocket server;
        private final Thread thread;

        FakeSmsc(int status, AtomicReference<String> seen) throws Exception {
            server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
            thread = new Thread(() -> {
                try (Socket s = server.accept()) {
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    int len = in.readInt();
                    int cmd = in.readInt();
                    in.readInt();
                    int seq = in.readInt();
                    byte[] body = new byte[len - 16];
                    in.readFully(body);
                    if (cmd == 0x00000009) {
                        String[] parts = new String(body, StandardCharsets.US_ASCII).split("\0", -1);
                        seen.set(parts[0] + "/" + parts[1]);
                    }
                    byte[] sysId = "SMSC\0".getBytes(StandardCharsets.US_ASCII);
                    out.writeInt(16 + sysId.length);
                    out.writeInt(0x80000009);
                    out.writeInt(status);
                    out.writeInt(seq);
                    out.write(sysId);
                    out.flush();
                    if (status == 0 && in.readInt() > 0) { // unbind
                        in.readInt();
                        in.readInt();
                        int unbindSeq = in.readInt();
                        out.writeInt(16);
                        out.writeInt(0x80000006);
                        out.writeInt(0);
                        out.writeInt(unbindSeq);
                        out.flush();
                    }
                } catch (Exception ignored) {
                    // kết thúc test
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        int port() {
            return server.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            server.close();
            thread.join(2000);
        }
    }
}
