package app.storage;

import app.domain.Attempt;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 단위 테스트용 메모리 실습 저장소. Attempt는 불변이라 그대로 보관한다. */
public class InMemoryAttemptRepository implements AttemptRepository {

    private final Map<Long, Attempt> attempts = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public synchronized Attempt save(Attempt attempt) {
        long id;
        if (attempt.attemptId() == null) {
            id = nextId++;
        } else {
            Attempt old = attempts.get(attempt.attemptId());
            if (old == null || old.userId() != attempt.userId()) {
                throw new IllegalArgumentException("덮어쓸 실습 기록이 없습니다: " + attempt.attemptId());
            }
            id = attempt.attemptId();
        }
        Attempt saved = withId(attempt, id);
        attempts.put(id, saved);
        return saved;
    }

    @Override
    public synchronized Optional<Attempt> findById(long userId, long attemptId) {
        return Optional.ofNullable(attempts.get(attemptId)).filter(a -> a.userId() == userId);
    }

    @Override
    public synchronized List<Attempt> findByUser(long userId) {
        List<Attempt> result = new ArrayList<>();
        for (Attempt a : attempts.values()) {
            if (a.userId() == userId) {
                result.add(a);
            }
        }
        Comparator<Attempt> oldestFirst = Comparator.comparing(Attempt::startedAt).thenComparing(Attempt::attemptId);
        result.sort(oldestFirst.reversed());
        return result;
    }

    /** 번호만 채운 복사본을 만든다. */
    static Attempt withId(Attempt a, long id) {
        return new Attempt(id, a.userId(), a.caseId(), a.caseSource(), a.generatedCaseJson(),
                a.startedAt(), a.submittedAt(), a.observationEntries(), a.medicationRecords(),
                a.sbar(), a.checks(), a.score(), a.aiFeedback(), a.mentalStatus());
    }
}
