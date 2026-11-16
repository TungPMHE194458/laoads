package com.unitel.laoads.infra.gateway;

import com.unitel.laoads.domain.settings.ConnectionProbe;
import com.unitel.laoads.domain.settings.ConnectionTestCode;
import com.unitel.laoads.domain.settings.GatewayProtocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.UnresolvedAddressException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * Kiểm tra kết nối gateway thật (chỉ Super Admin gọi được, qua API kiểm tra kết nối của đầu số):
 * <ul>
 *   <li>SMPP 3.4: mở TCP, gửi {@code bind_transceiver}, đọc {@code bind_transceiver_resp} → OK / sai tài khoản / bị từ chối,
 *       rồi {@code unbind} và đóng. Không gửi tin nào.</li>
 *   <li>HTTP: {@code GET} URL (Basic auth nếu có user, Bearer nếu chỉ có mật khẩu/token), không theo redirect.</li>
 * </ul>
 */
@Component
@Slf4j
public class GatewayConnectionProbe implements ConnectionProbe {

    static final int BIND_TRANSCEIVER = 0x00000009;
    static final int BIND_TRANSCEIVER_RESP = 0x80000009;
    static final int UNBIND = 0x00000006;
    static final int GENERIC_NACK = 0x80000000;
    static final int ESME_RINVPASWD = 0x0000000E;
    static final int ESME_RINVSYSID = 0x0000000F;
    private static final byte INTERFACE_VERSION_34 = 0x34;

    private final Duration timeout;

    public GatewayConnectionProbe(@Value("${laoads.engine.gateway-probe-timeout:PT5S}") Duration timeout) {
        this.timeout = timeout;
    }

    @Override
    public Result probe(Target t) {
        long start = System.nanoTime();
        try {
            Result r = t.protocol() == GatewayProtocol.HTTP ? http(t, start) : smpp(t, start);
            log.info("[GW-PROBE] {} → {} {}", t, r.code(), r.detail());
            return r;
        } catch (UnknownHostException | UnresolvedAddressException e) {
            return fail(ConnectionTestCode.UNKNOWN_HOST, "Không phân giải được host", start);
        } catch (SocketTimeoutException | HttpTimeoutException e) {
            return fail(ConnectionTestCode.TIMEOUT, "Quá " + timeout.toSeconds() + " giây không phản hồi", start);
        } catch (ConnectException e) {
            // HttpClient bọc lỗi DNS trong ConnectException (cause UnresolvedAddressException)
            if (unresolved(e)) {
                return fail(ConnectionTestCode.UNKNOWN_HOST, "Không phân giải được host", start);
            }
            return fail(ConnectionTestCode.REFUSED, e.getMessage() == null ? "Connection refused" : e.getMessage(), start);
        } catch (EOFException e) {
            return fail(ConnectionTestCode.PROTOCOL_ERROR, "Gateway đóng kết nối trước khi phản hồi", start);
        } catch (IOException e) {
            return fail(ConnectionTestCode.ERROR, e.getClass().getSimpleName() + ": " + e.getMessage(), start);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return fail(ConnectionTestCode.ERROR, "Bị ngắt", start);
        } catch (IllegalArgumentException e) {
            return fail(ConnectionTestCode.ERROR, "Cấu hình không hợp lệ: " + e.getMessage(), start);
        }
    }

    // ───────────── SMPP 3.4 ─────────────

    private Result smpp(Target t, long start) throws IOException {
        int ms = (int) timeout.toMillis();
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(t.host(), t.port()), ms);
            s.setSoTimeout(ms);
            OutputStream out = s.getOutputStream();
            DataInputStream in = new DataInputStream(s.getInputStream());
            out.write(bindTransceiver(t.username(), t.password(), t.systemType(), 1));
            out.flush();
            int[] header = readPdu(in);
            long latency = elapsed(start);
            Result r = interpretBindResponse(header[0], header[1], latency);
            if (r.ok()) {
                try {
                    out.write(header(16, UNBIND, 0, 2));
                    out.flush();
                    readPdu(in);
                } catch (IOException ignored) {
                    // unbind chỉ là dọn dẹp
                }
            }
            return r;
        }
    }

    /** {@code bind_transceiver}: header + system_id, password, system_type, interface_version, addr_ton, addr_npi, address_range. */
    static byte[] bindTransceiver(String systemId, String password, String systemType, int sequence) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        cString(body, systemId);
        cString(body, password);
        cString(body, systemType);
        body.write(INTERFACE_VERSION_34);
        body.write(0); // addr_ton
        body.write(0); // addr_npi
        cString(body, null); // address_range
        byte[] b = body.toByteArray();
        return ByteBuffer.allocate(16 + b.length).put(header(16 + b.length, BIND_TRANSCEIVER, 0, sequence)).put(b).array();
    }

    static Result interpretBindResponse(int commandId, int status, long latencyMs) {
        String hex = String.format("0x%08X", status);
        if (commandId == GENERIC_NACK) {
            return new Result(ConnectionTestCode.PROTOCOL_ERROR, "generic_nack status=" + hex, latencyMs);
        }
        if (commandId != BIND_TRANSCEIVER_RESP) {
            return new Result(ConnectionTestCode.PROTOCOL_ERROR, String.format("PDU lạ command_id=0x%08X", commandId), latencyMs);
        }
        if (status == 0) {
            return new Result(ConnectionTestCode.OK, "bind_transceiver thành công", latencyMs);
        }
        if (status == ESME_RINVPASWD || status == ESME_RINVSYSID) {
            return new Result(ConnectionTestCode.AUTH_FAILED,
                    (status == ESME_RINVPASWD ? "Sai mật khẩu" : "Sai system_id") + " (status=" + hex + ")", latencyMs);
        }
        return new Result(ConnectionTestCode.REJECTED, "bind_transceiver_resp status=" + hex, latencyMs);
    }

    /** Đọc 1 PDU, trả [command_id, command_status]; bỏ qua phần thân. */
    private static int[] readPdu(DataInputStream in) throws IOException {
        int length = in.readInt();
        int commandId = in.readInt();
        int status = in.readInt();
        in.readInt(); // sequence_number
        if (length < 16 || length > 64 * 1024) {
            throw new EOFException("command_length bất thường: " + length);
        }
        in.skipNBytes(length - 16L);
        return new int[]{commandId, status};
    }

    private static byte[] header(int length, int commandId, int status, int sequence) {
        return ByteBuffer.allocate(16).putInt(length).putInt(commandId).putInt(status).putInt(sequence).array();
    }

    private static void cString(ByteArrayOutputStream out, String s) {
        if (s != null) {
            out.writeBytes(s.getBytes(StandardCharsets.US_ASCII));
        }
        out.write(0);
    }

    // ───────────── HTTP ─────────────

    private Result http(Target t, long start) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(t.url())).timeout(timeout).GET();
        if (t.username() != null && !t.username().isBlank()) {
            String token = t.username() + ":" + (t.password() == null ? "" : t.password());
            req.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)));
        } else if (t.password() != null && !t.password().isBlank()) {
            req.header("Authorization", "Bearer " + t.password());
        }
        try {
            HttpResponse<Void> res = client.send(req.build(), HttpResponse.BodyHandlers.discarding());
            return interpretHttpStatus(res.statusCode(), elapsed(start));
        } catch (HttpConnectTimeoutException e) {
            return fail(ConnectionTestCode.TIMEOUT, "Quá " + timeout.toSeconds() + " giây không kết nối được", start);
        }
    }

    static Result interpretHttpStatus(int status, long latencyMs) {
        String detail = "HTTP " + status;
        if (status == 401 || status == 403) {
            return new Result(ConnectionTestCode.AUTH_FAILED, detail, latencyMs);
        }
        // 405: endpoint có thật nhưng không nhận GET (thường chỉ nhận POST gửi tin) — vẫn coi là kết nối được
        if (status < 400 || status == 405) {
            return new Result(ConnectionTestCode.OK, detail, latencyMs);
        }
        return new Result(ConnectionTestCode.REJECTED, detail, latencyMs);
    }

    // ───────────── chung ─────────────

    private static boolean unresolved(Throwable e) {
        for (Throwable c = e; c != null; c = c.getCause()) {
            if (c instanceof UnresolvedAddressException || c instanceof UnknownHostException) {
                return true;
            }
        }
        return false;
    }

    private static Result fail(ConnectionTestCode code, String detail, long start) {
        return new Result(code, detail, elapsed(start));
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }
}
