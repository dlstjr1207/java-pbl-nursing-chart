package app.auth;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** PBKDF2(HMAC-SHA256)와 사용자별 솔트로 비밀번호를 해시하고 검증한다 (D23). */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /** 무작위 솔트를 만들어 Base64 문자열로 돌려준다. */
    public static String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /** 비밀번호와 솔트로 해시(Base64)를 계산한다. */
    public static String hash(String password, String saltBase64) {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_BITS);
        try {
            byte[] bytes = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(bytes);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("비밀번호 해시를 계산할 수 없습니다", e);
        } finally {
            spec.clearPassword();
        }
    }

    /** 입력한 비밀번호가 저장된 해시와 맞는지 시간 일정 비교로 확인한다. */
    public static boolean verify(String password, String saltBase64, String expectedHash) {
        byte[] actual = hash(password, saltBase64).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(actual, expectedHash.getBytes(StandardCharsets.UTF_8));
    }
}
