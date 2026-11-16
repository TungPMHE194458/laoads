package com.unitel.laoads.domain.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Mã hoá bí mật lưu trong DB (mật khẩu gateway của đầu số…) bằng AES-256-GCM.
 * Khoá = SHA-256({@code laoads.secrets.key}); nếu chưa đặt thì suy ra từ {@code laoads.jwt.secret} (kèm cảnh báo —
 * đổi JWT secret khi đó sẽ làm mất các bí mật đã lưu, phải nhập lại).
 * Định dạng lưu: {@code v1:} + base64(IV 12 byte ‖ ciphertext+tag).
 */
@Component
@Slf4j
public class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(@Value("${laoads.secrets.key:}") String secretsKey,
                        @Value("${laoads.jwt.secret:}") String jwtSecret) {
        String material;
        if (secretsKey != null && !secretsKey.isBlank()) {
            material = "laoads-secrets:" + secretsKey;
        } else {
            if (jwtSecret == null || jwtSecret.isBlank()) {
                throw new IllegalStateException("Cần LAOADS_SECRETS_KEY hoặc LAOADS_JWT_SECRET để mã hoá bí mật");
            }
            log.warn("[SECRETS] LAOADS_SECRETS_KEY chưa đặt — khoá mã hoá bí mật suy ra từ JWT secret; "
                    + "đổi JWT secret sẽ phải nhập lại mật khẩu gateway của các đầu số.");
            material = "laoads-secrets-from-jwt:" + jwtSecret;
        }
        this.key = new SecretKeySpec(sha256(material), "AES");
    }

    public String encrypt(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ct = c.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không mã hoá được bí mật", e);
        }
    }

    /** @throws IllegalStateException khi dữ liệu hỏng hoặc khoá đã đổi */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Định dạng bí mật không hỗ trợ");
        }
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            if (all.length <= IV_BYTES) {
                throw new IllegalStateException("Bí mật hỏng");
            }
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(c.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (AEADBadTagException e) {
            throw new IllegalStateException("Không giải mã được bí mật (khoá đã đổi hoặc dữ liệu bị sửa)", e);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Bí mật hỏng", e);
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
