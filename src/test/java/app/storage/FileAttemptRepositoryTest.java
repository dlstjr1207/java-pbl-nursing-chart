package app.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import app.domain.Attempt;
import app.domain.CaseSource;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 파일 저장 모드 실습 저장소가 계약을 지키고, 다시 열어도 기록이 남는지 확인한다 (D27). */
class FileAttemptRepositoryTest extends AttemptRepositoryContract {

    @TempDir
    Path dir;
    private AttemptRepository repository;
    private long nextUser = 1;

    @Override
    AttemptRepository repository() {
        if (repository == null) repository = new FileAttemptRepository(dir.resolve("attempts.json"));
        return repository;
    }

    @Override
    long newUser(String loginId) {
        return nextUser++;
    }

    @Test
    void recordsSurviveReopen() {
        Path file = dir.resolve("reopen.json");
        Attempt a = new Attempt(null, 7L, "SYN-1", CaseSource.DEMO, null,
                LocalDateTime.of(2026, 11, 23, 9, 0), LocalDateTime.of(2026, 11, 23, 9, 30),
                List.of(), List.of(), null, List.of(), null, null);
        long id = new FileAttemptRepository(file).save(a).attemptId();
        FileAttemptRepository reopened = new FileAttemptRepository(file);
        assertEquals("SYN-1", reopened.findById(7L, id).orElseThrow().caseId());
        assertEquals(0, reopened.findByUser(8L).size());
        // 다시 열어도 번호가 겹치지 않는다
        assertEquals(id + 1, reopened.save(a).attemptId());
    }
}
