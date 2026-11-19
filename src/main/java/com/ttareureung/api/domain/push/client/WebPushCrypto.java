package com.ttareureung.api.domain.push.client;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * 웹 푸시 본문 암호화(RFC 8291, aes128gcm)와 P-256 키 변환.
 * 외부 라이브러리 없이 JDK 암호 API만 사용
 */
public final class WebPushCrypto {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RECORD_SIZE = 4096;
    static final ECParameterSpec P256 = p256();

    private WebPushCrypto() {
    }

    // 브라우저 구독의 p256dh(수신자 공개키)·auth로 본문 암호화. 결과는 헤더(salt·rs·발신 공개키) + 암호문
    public static byte[] encrypt(byte[] plaintext, String p256dhBase64, String authBase64) throws GeneralSecurityException {
        return encrypt(plaintext, decode(p256dhBase64), decode(authBase64), newKeyPair(), randomSalt());
    }

    static byte[] encrypt(byte[] plaintext, byte[] uaPublic, byte[] authSecret, KeyPair senderKeys, byte[] salt)
            throws GeneralSecurityException {
        byte[] asPublic = toUncompressed((ECPublicKey) senderKeys.getPublic());
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(senderKeys.getPrivate());
        agreement.doPhase(publicKey(uaPublic), true);
        byte[] ecdhSecret = agreement.generateSecret();

        byte[] prkKey = hmac(authSecret, ecdhSecret);
        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), uaPublic, asPublic, new byte[]{1});
        byte[] ikm = hmac(prkKey, keyInfo);
        byte[] prk = hmac(salt, ikm);
        byte[] cek = Arrays.copyOf(hmac(prk, concat("Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 16);
        byte[] nonce = Arrays.copyOf(hmac(prk, concat("Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), new byte[]{1})), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(cek, "AES"), new GCMParameterSpec(128, nonce));
        // 0x02: 마지막 레코드 구분자
        byte[] ciphertext = cipher.doFinal(concat(plaintext, new byte[]{2}));

        ByteBuffer header = ByteBuffer.allocate(16 + 4 + 1 + asPublic.length);
        header.put(salt).putInt(RECORD_SIZE).put((byte) asPublic.length).put(asPublic);
        return concat(header.array(), ciphertext);
    }

    public static KeyPair newKeyPair() throws GeneralSecurityException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    public static java.security.PublicKey publicKey(byte[] uncompressed) throws GeneralSecurityException {
        if (uncompressed.length != 65 || uncompressed[0] != 4) {
            throw new GeneralSecurityException("P-256 비압축 공개키(65바이트)가 아님");
        }
        BigInteger x = new BigInteger(1, Arrays.copyOfRange(uncompressed, 1, 33));
        BigInteger y = new BigInteger(1, Arrays.copyOfRange(uncompressed, 33, 65));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), P256));
    }

    public static java.security.PrivateKey privateKey(byte[] d) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, d), P256));
    }

    public static byte[] toUncompressed(ECPublicKey key) {
        return concat(new byte[]{4}, fixed32(key.getW().getAffineX()), fixed32(key.getW().getAffineY()));
    }

    public static byte[] decode(String base64) {
        String s = base64.trim().replace('+', '-').replace('/', '_').replace("=", "");
        return Base64.getUrlDecoder().decode(s);
    }

    public static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    private static byte[] randomSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return salt;
    }

    private static byte[] fixed32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static ECParameterSpec p256() {
        try {
            AlgorithmParameters params = AlgorithmParameters.getInstance("EC");
            params.init(new ECGenParameterSpec("secp256r1"));
            return params.getParameterSpec(ECParameterSpec.class);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
