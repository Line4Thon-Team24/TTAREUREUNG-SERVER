package com.ttareureung.api.domain.push;

import com.ttareureung.api.domain.push.client.WebPushCrypto;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

// 브라우저(수신자) 입장에서 RFC 8291 순서대로 복호화해 암호화 결과 검증
class WebPushCryptoTests {

    @Test
    void receiverCanDecryptAes128gcmPayload() throws Exception {
        KeyPair receiver = WebPushCrypto.newKeyPair();
        byte[] uaPublic = WebPushCrypto.toUncompressed((ECPublicKey) receiver.getPublic());
        byte[] auth = new byte[16];
        new SecureRandom().nextBytes(auth);
        String message = "{\"type\":\"DOSE_CALL\",\"body\":\"점심 약 드실 시간이에요\"}";

        byte[] body = WebPushCrypto.encrypt(message.getBytes(StandardCharsets.UTF_8),
                WebPushCrypto.encode(uaPublic), WebPushCrypto.encode(auth));

        ByteBuffer buf = ByteBuffer.wrap(body);
        byte[] salt = new byte[16];
        buf.get(salt);
        assertThat(buf.getInt()).isEqualTo(4096);
        byte[] asPublic = new byte[buf.get()];
        buf.get(asPublic);
        byte[] ciphertext = new byte[buf.remaining()];
        buf.get(ciphertext);

        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(receiver.getPrivate());
        agreement.doPhase(WebPushCrypto.publicKey(asPublic), true);
        byte[] prkKey = hmac(auth, agreement.generateSecret());
        byte[] ikm = hmac(prkKey, concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[]{1}));
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 12);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        byte[] padded = cipher.doFinal(ciphertext);

        assertThat(padded[padded.length - 1]).isEqualTo((byte) 2);
        assertThat(new String(padded, 0, padded.length - 1, StandardCharsets.UTF_8)).isEqualTo(message);
    }

    @Test
    void keysRoundTripThroughBase64Url() throws Exception {
        KeyPair pair = WebPushCrypto.newKeyPair();
        byte[] uncompressed = WebPushCrypto.toUncompressed((ECPublicKey) pair.getPublic());

        assertThat(uncompressed).hasSize(65);
        assertThat(WebPushCrypto.publicKey(WebPushCrypto.decode(WebPushCrypto.encode(uncompressed))))
                .isEqualTo(pair.getPublic());
        // 표준 base64(+/=)로 와도 읽힘
        String standard = java.util.Base64.getEncoder().encodeToString(uncompressed);
        assertThat(WebPushCrypto.decode(standard)).isEqualTo(uncompressed);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
