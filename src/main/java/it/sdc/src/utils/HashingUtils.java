package it.sdc.src.utils;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class HashingUtils {

    private static byte[] innerDigest(byte[] data, String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm).digest(data);
        } catch (NoSuchAlgorithmException e) {
            // Impossible on compliant JDK
            throw new IllegalStateException(algorithm + " hash algorithm is not available", e);
        }
    }

    public static byte[] sha1(byte[] data) {
        return innerDigest(data, "SHA-1");
    }

    public static byte[] sha512(byte[] data) {
        return innerDigest(data, "SHA-512");
    }
}
