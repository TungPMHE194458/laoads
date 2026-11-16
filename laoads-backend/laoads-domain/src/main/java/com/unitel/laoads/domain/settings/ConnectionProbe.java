package com.unitel.laoads.domain.settings;

/**
 * Port kiểm tra kết nối gateway của một đầu số (cài đặt thật ở laoads-infra: SMPP bind_transceiver / HTTP GET).
 * Gọi ra mạng, có thể mất vài giây — không gọi trong transaction.
 */
public interface ConnectionProbe {

    record Target(GatewayProtocol protocol, String host, Integer port, String systemType, String url,
                  String username, String password) {

        @Override
        public String toString() {
            // không để lộ mật khẩu vào log
            return "Target[" + protocol + " " + (protocol == GatewayProtocol.HTTP ? url : host + ":" + port)
                    + ", user=" + username + "]";
        }
    }

    record Result(ConnectionTestCode code, String detail, Long latencyMs) {

        public boolean ok() {
            return code == ConnectionTestCode.OK;
        }
    }

    Result probe(Target target);
}
