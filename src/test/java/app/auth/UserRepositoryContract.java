package app.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.storage.DuplicateLoginIdException;
import app.storage.UserRepository;
import org.junit.jupiter.api.Test;

/** UserRepository 구현이 공통으로 지켜야 할 동작 (메모리·MySQL 테스트가 상속). */
abstract class UserRepositoryContract {

    abstract UserRepository repository();

    private User signUp(UserRepository repo, String loginId, String password) {
        String salt = PasswordHasher.newSalt();
        return repo.create(loginId, "테스트학생", PasswordHasher.hash(password, salt), salt);
    }

    @Test
    void storesHashOnlyAndVerifiesPassword() {
        UserRepository repo = repository();
        signUp(repo, "student01", "pass-1234");

        User found = repo.findByLoginId("student01").orElseThrow();
        assertEquals("테스트학생", found.name());
        assertNotEquals("pass-1234", found.passwordHash());
        assertFalse(found.passwordHash().contains("pass-1234"));
        assertTrue(PasswordHasher.verify("pass-1234", found.salt(), found.passwordHash()));
        assertFalse(PasswordHasher.verify("wrong-pass", found.salt(), found.passwordHash()));
    }

    @Test
    void rejectsDuplicateLoginId() {
        UserRepository repo = repository();
        signUp(repo, "student01", "pass-1234");
        assertThrows(DuplicateLoginIdException.class, () -> signUp(repo, "student01", "other-5678"));
    }

    @Test
    void unknownLoginIdIsEmpty() {
        assertTrue(repository().findByLoginId("nobody01").isEmpty());
    }
}
