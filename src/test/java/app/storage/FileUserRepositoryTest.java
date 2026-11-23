package app.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import app.auth.UserRepositoryContractBridge;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 파일 저장 모드 계정 저장소가 계약을 지키고, 다시 열어도 계정이 남는지 확인한다 (D27). */
class FileUserRepositoryTest extends UserRepositoryContractBridge {

    @TempDir
    Path dir;
    private int n;

    @Override
    public UserRepository repository() {
        return new FileUserRepository(dir.resolve("users-" + (n++) + ".json"));
    }

    @Test
    void accountsSurviveReopen() {
        Path file = dir.resolve("users.json");
        new FileUserRepository(file).create("student01", "합성 이름", "hash", "salt");
        FileUserRepository reopened = new FileUserRepository(file);
        assertEquals("합성 이름", reopened.findByLoginId("student01").orElseThrow().name());
        assertThrows(DuplicateLoginIdException.class, () -> reopened.create("student01", "x", "h", "s"));
        assertEquals(2, reopened.create("student02", "y", "h", "s").id());
    }
}
