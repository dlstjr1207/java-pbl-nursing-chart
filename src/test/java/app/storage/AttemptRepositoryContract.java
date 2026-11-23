package app.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.domain.AiFeedback;
import app.domain.Attempt;
import app.domain.CaseSource;
import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.CheckTarget;
import app.domain.MedicationRecord;
import app.domain.MedicationResult;
import app.domain.ObservationEntry;
import app.domain.SbarRecommendation;
import app.domain.SbarReport;
import app.domain.Shift;
import app.domain.VitalSign;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AttemptRepository 구현이 공통으로 지켜야 할 동작 (메모리·MySQL 테스트가 상속). 값은 모두 합성 값이다. */
abstract class AttemptRepositoryContract {

    abstract AttemptRepository repository();

    /** 사용자를 만들고 번호를 돌려준다 (MySQL은 외래키 때문에 실제 행이 필요). */
    abstract long newUser(String loginId);

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 22, 9, 0, 0);

    private Attempt full(long userId, LocalDateTime startedAt) {
        ObservationEntry obs = new ObservationEntry("09:30", Shift.DAY, List.of("수면", "기분"), "합성 서술 한글 😀",
                "합성 처치", new VitalSign(null, "09:30", 120, 80, 72, 18, 36.5, 98));
        MedicationRecord med = new MedicationRecord("ORD-1", "테스트약A", "1", "정", "PO", "10:00",
                MedicationResult.HELD, "합성 사유", "합성 근거", true, "합성 재평가");
        SbarReport sbar = new SbarReport("S 내용", "B 내용", "A 내용", List.of(
                new SbarRecommendation("11:00", "첫째 중재"), new SbarRecommendation("11:30", "둘째 중재")));
        List<CheckResult> checks = List.of(
                new CheckResult("R8", CheckTarget.OBSERVATION, "obs[0].narrative", CheckStatus.PASS, "통과", null),
                new CheckResult("R6", CheckTarget.MEDICATION, "med[0].reason", CheckStatus.MISSING, "사유 누락", 1.5));
        return new Attempt(null, userId, "DEMO", CaseSource.DEMO, "{\"caseId\": \"X\"}", startedAt,
                startedAt.plusMinutes(30), List.of(obs), List.of(med), sbar, checks, 85.5,
                new AiFeedback("SUCCESS", List.of("잘한 점"), List.of("고칠 점1", "고칠 점2"), "예시"));
    }

    @Test
    void savesAndReadsBackAllData() {
        AttemptRepository repo = repository();
        long user = newUser("student01");
        Attempt saved = repo.save(full(user, T0));
        assertTrue(saved.attemptId() != null);

        Attempt found = repo.findById(user, saved.attemptId()).orElseThrow();
        assertEquals(saved, found);
        assertEquals("합성 서술 한글 😀", found.observationEntries().get(0).narrative());
        assertEquals(List.of("첫째 중재", "둘째 중재"),
                found.sbar().recommendations().stream().map(SbarRecommendation::intervention).toList());
    }

    @Test
    void keepsNullsForUnsubmittedAttempt() {
        AttemptRepository repo = repository();
        long user = newUser("student01");
        Attempt draft = new Attempt(null, user, "PC-01", CaseSource.FIXED, null, T0, null,
                List.of(), List.of(), null, List.of(), null, null);
        Attempt saved = repo.save(draft);

        Attempt found = repo.findById(user, saved.attemptId()).orElseThrow();
        assertEquals(draft.caseId(), found.caseId());
        assertNull(found.submittedAt());
        assertNull(found.sbar());
        assertNull(found.score());
        assertNull(found.aiFeedback());
        assertTrue(found.observationEntries().isEmpty());
    }

    @Test
    void otherUsersCannotSeeAttempt() {
        AttemptRepository repo = repository();
        long a = newUser("student01");
        long b = newUser("student02");
        Attempt saved = repo.save(full(a, T0));
        repo.save(full(b, T0.plusHours(1)));

        assertTrue(repo.findById(b, saved.attemptId()).isEmpty());
        assertEquals(1, repo.findByUser(a).size());
        assertEquals(1, repo.findByUser(b).size());
        assertTrue(repo.findByUser(a).stream().allMatch(x -> x.userId() == a));
    }

    @Test
    void listsNewestFirst() {
        AttemptRepository repo = repository();
        long user = newUser("student01");
        Attempt older = repo.save(full(user, T0));
        Attempt newer = repo.save(full(user, T0.plusHours(2)));
        List<Attempt> list = repo.findByUser(user);
        assertEquals(List.of(newer.attemptId(), older.attemptId()), list.stream().map(Attempt::attemptId).toList());
    }

    @Test
    void overwriteReplacesSubmittedResultsAndKeepsId() {
        AttemptRepository repo = repository();
        long user = newUser("student01");
        Attempt saved = repo.save(full(user, T0));
        Attempt changed = new Attempt(saved.attemptId(), user, saved.caseId(), saved.caseSource(),
                saved.generatedCaseJson(), T0, saved.submittedAt(), List.of(), saved.medicationRecords(), null,
                List.of(new CheckResult("R9", CheckTarget.SBAR, "sbar", CheckStatus.NOT_SCORED, "미산출", null)),
                null, new AiFeedback("FAILED", null, null, null));
        repo.save(changed);

        Attempt found = repo.findById(user, saved.attemptId()).orElseThrow();
        assertEquals(changed, found);
        assertEquals(1, repo.findByUser(user).size());
    }

    @Test
    void overwriteOfOtherUsersAttemptIsRejected() {
        AttemptRepository repo = repository();
        long a = newUser("student01");
        long b = newUser("student02");
        Attempt saved = repo.save(full(a, T0));
        Attempt hijack = new Attempt(saved.attemptId(), b, "DEMO", CaseSource.DEMO, null, T0, null,
                List.of(), List.of(), null, List.of(), null, null);
        assertThrows(IllegalArgumentException.class, () -> repo.save(hijack));
        assertEquals(saved, repo.findById(a, saved.attemptId()).orElseThrow());
    }
}
