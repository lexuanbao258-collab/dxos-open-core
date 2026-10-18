package com.tricore.dxos.infrastructure.workflow;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * TEST-ONLY, deliberately restricted proof-framing prototype for disposable PostgreSQL 17.
 *
 * This class does NOT authenticate JWTs or implement ADR-0024's complete proof envelope,
 * replay protection, key custody, transition/write proposal signing or constant-time SQL
 * verification. It is NOT to be wired into production or treated as deployment approval.
 */
public final class WorkflowSqlProofPrototype {
    public static final String DOMAIN = "DXOS-WF-SQL-CAP-DEMO";
    private static final int FIELDS = 7;
    private static final int MAX_BYTES_PER_FIELD = 4096;

    private WorkflowSqlProofPrototype() {}

    /** unsigned big-endian field byte lengths (0..4096), strict UTF-8, fixed field count/order. */
    public static byte[] canonical(String actor, String operation, String target,
                                   String backendPid, String xid8, String nonce) {
        String[] values = {DOMAIN, actor, operation, target, backendPid, xid8, nonce};
        if (values.length != FIELDS) throw new AssertionError("Field count");
        try {
            var buffer = new ByteArrayOutputStream();
            var stream = new DataOutputStream(buffer);
            stream.writeInt(FIELDS);
            for (String value : values) {
                if (value == null || value.indexOf('\u0000') >= 0) {
                    throw new IllegalArgumentException("Missing/invalid proof field");
                }
                byte[] encoded = utf8Strict(value);
                if (encoded.length > MAX_BYTES_PER_FIELD) {
                    throw new IllegalArgumentException("Oversized proof field");
                }
                stream.writeInt(encoded.length);
                stream.write(encoded);
            }
            stream.flush();
            return buffer.toByteArray();
        } catch (java.io.IOException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static byte[] utf8Strict(String input) {
        try {
            var encoder = StandardCharsets.UTF_8.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            ByteBuffer bytes = encoder.encode(java.nio.CharBuffer.wrap(input));
            byte[] out = new byte[bytes.remaining()];
            bytes.get(out);
            return out;
        } catch (CharacterCodingException invalidUtf16) {
            throw new IllegalArgumentException("Noncanonical UTF-16 input", invalidUtf16);
        }
    }

    public static byte[] hmacSha256(byte[] key, byte[] canonicalInput) {
        if (key == null || key.length < 32 || canonicalInput == null) {
            throw new IllegalArgumentException("Prototype requires >=256-bit key");
        }
        return rawHmac(key, canonicalInput);
    }

    private static byte[] rawHmac(byte[] key, byte[] input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(input);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("HMAC unavailable", failure);
        }
    }

    public static boolean valid(byte[] expected, byte[] supplied) {
        return expected != null && supplied != null && expected.length == 32 && supplied.length == 32
                && MessageDigest.isEqual(expected, supplied);
    }

    /** JDK-only smoke test runnable without JUnit, Postgres or a network connection. */
    public static void main(String[] args) {
        byte[] rfc4231Key = new byte[20];
        Arrays.fill(rfc4231Key, (byte) 0x0b);
        String rfc4231Expected = "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7";
        if (!HexFormat.of().formatHex(rawHmac(rfc4231Key,
                "Hi There".getBytes(StandardCharsets.US_ASCII))).equals(rfc4231Expected)) {
            throw new AssertionError("RFC4231 HMAC-SHA-256 case 1 mismatch");
        }
        byte[] secret = new byte[32];
        Arrays.fill(secret, (byte) 0x31);
        byte[] allowed = canonical("oidc:é-🚀", "READ", "workflow:A", "4321", "988", "nonce-1");
        byte[] signed = hmacSha256(secret, allowed);
        if (!valid(signed, hmacSha256(secret, allowed))
                || valid(signed, hmacSha256(secret, canonical("oidc:é-🚀",
                "READ", "workflow:B", "4321", "988", "nonce-1")))
                || valid(signed, hmacSha256(secret, canonical("oidc:é-🚀",
                "CREATE", "workflow:A", "4321", "988", "nonce-1")))
                || valid(signed, hmacSha256(secret, canonical("oidc:é-🚀",
                "READ", "workflow:A", "4321", "989", "nonce-1")))) {
            throw new AssertionError("Target/operation/transaction binding failure");
        }
        try {
            canonical("broken\uD800", "READ", "A", "4321", "988", "nonce");
            throw new AssertionError("Unpaired surrogate was accepted");
        } catch (IllegalArgumentException expected) {
            // Correct fail-closed behavior for UTF-16 input outside strict UTF-8.
        }
        try {
            canonical("oidc:valid\u0000", "READ", "A", "4321", "988", "nonce");
            throw new AssertionError("NUL field was accepted");
        } catch (IllegalArgumentException expected) {
            // Correct.
        }
        if (!Arrays.equals(Arrays.copyOfRange(allowed, 0, 4), new byte[]{0, 0, 0, 7})) {
            throw new AssertionError("Canonical framing field-count prefix mismatch");
        }
        System.out.println("JAVA_HMAC_PROTOTYPE_PASS: RFC4231, strict UTF-8, target/action/xid binding");
        Arrays.fill(secret, (byte) 0);
    }
}
