package app.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PasswordAndSignupTest {

    @Test
    void sameStringGetsDifferentHashPerSalt() {
        String s1 = PasswordHasher.newSalt();
        String s2 = PasswordHasher.newSalt();
        assertNotEquals(s1, s2);
        assertNotEquals(PasswordHasher.hash("password1", s1), PasswordHasher.hash("password1", s2));
    }

    @Test
    void validInputPasses() {
        assertTrue(SignupValidator.validate("abcd", "12345678", "홍길동").isEmpty());
        assertTrue(SignupValidator.validate("A1234567890123456789", "12345678", "홍길동").isEmpty());
    }

    @Test
    void invalidInputIsReportedPerField() {
        Map<String, String> errors = SignupValidator.validate("ab", "short", " ");
        assertEquals(3, errors.size());
        assertTrue(errors.containsKey("loginId") && errors.containsKey("password") && errors.containsKey("name"));
        assertTrue(SignupValidator.validate("abc_d", "12345678", "x").containsKey("loginId"));
        assertTrue(SignupValidator.validate("A12345678901234567890", "12345678", "x").containsKey("loginId"));
        assertTrue(SignupValidator.validate("한글아이디1", "12345678", "x").containsKey("loginId"));
        assertTrue(SignupValidator.validate("abcd", "1234567", "x").containsKey("password"));
    }
}
