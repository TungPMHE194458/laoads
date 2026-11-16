package com.unitel.laoads.domain.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecretCipherTest {

    private final SecretCipher cipher = new SecretCipher("key-A-0123456789abcdef0123456789", "");

    @Test
    @DisplayName("mã hoá rồi giải mã ra đúng bản rõ; mỗi lần mã hoá khác nhau (IV ngẫu nhiên)")
    void roundTrip() {
        String a = cipher.encrypt("mật-khẩu ລາວ");
        String b = cipher.encrypt("mật-khẩu ລາວ");
        assertThat(a).startsWith("v1:").isNotEqualTo(b);
        assertThat(cipher.decrypt(a)).isEqualTo("mật-khẩu ລາວ");
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    @DisplayName("khoá khác hoặc dữ liệu bị sửa → không giải mã được")
    void wrongKeyOrTampered() {
        String enc = cipher.encrypt("secret");
        SecretCipher other = new SecretCipher("key-B-0123456789abcdef0123456789", "");
        assertThatThrownBy(() -> other.decrypt(enc)).isInstanceOf(IllegalStateException.class);

        byte[] raw = Base64.getDecoder().decode(enc.substring(3));
        raw[raw.length - 1] ^= 1;
        String tampered = "v1:" + Base64.getEncoder().encodeToString(raw);
        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cipher.decrypt("plain")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("chưa đặt LAOADS_SECRETS_KEY → suy ra từ JWT secret; thiếu cả hai → lỗi khởi tạo")
    void fallbackToJwtSecret() {
        SecretCipher a = new SecretCipher("", "jwt-secret-0123456789abcdef0123456789");
        SecretCipher b = new SecretCipher(null, "jwt-secret-0123456789abcdef0123456789");
        assertThat(b.decrypt(a.encrypt("x"))).isEqualTo("x");
        assertThatThrownBy(() -> new SecretCipher("", "")).isInstanceOf(IllegalStateException.class);
    }
}
