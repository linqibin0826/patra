package com.patra.starter.core.util;

import com.patra.starter.core.json.JsonNormalizer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * General hashing helpers for consistent SHA-256 usage across modules.
 */
public final class HashUtils {

    private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();

    private HashUtils() {
    }

    public static byte[] sha256(JsonNormalizer.Result normalized) {
        byte[] source = normalized == null ? new byte[0] : normalized.getHashMaterial();
        return sha256(source);
    }

    public static byte[] sha256(String source) {
        return sha256(source == null ? new byte[0] : source.getBytes(StandardCharsets.UTF_8));
    }

    public static byte[] sha256(byte[] source) {
        return digest("SHA-256", source == null ? new byte[0] : source);
    }

    public static String sha256Hex(JsonNormalizer.Result normalized) {
        return sha256Hex(normalized == null ? new byte[0] : normalized.getHashMaterial());
    }

    public static String sha256Hex(String source) {
        return sha256Hex(source == null ? new byte[0] : source.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] source) {
        return toHex(sha256(source));
    }

    public static String toHex(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        char[] chars = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xFF;
            chars[i * 2] = HEX_DIGITS[v >>> 4];
            chars[i * 2 + 1] = HEX_DIGITS[v & 0x0F];
        }
        return new String(chars);
    }

    private static byte[] digest(String algorithm, byte[] input) {
        Objects.requireNonNull(algorithm, "algorithm");
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return digest.digest(input == null ? new byte[0] : input);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unsupported digest algorithm: " + algorithm, e);
        }
    }
}
