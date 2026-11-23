package app.storage;

import app.domain.Attempt;
import com.google.gson.reflect.TypeToken;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 파일 저장 모드(D27)의 실습 기록 저장소. DB가 없을 때 사용자 홈 아래 attempts.json에 저장한다.
 * 조회는 사용자 번호로 거르므로 다른 사용자의 기록은 보이지 않는다 (S8).
 */
public class FileAttemptRepository implements AttemptRepository {

    /** 파일 내용: 다음 번호와 실습 기록 목록. */
    private static final class Data {
        long nextId = 1;
        List<Attempt> attempts = new ArrayList<>();
    }

    private final Path file;
    private final Data data;

    /** @param file 저장 파일(예: ~/.nursing-chart-trainer/attempts.json). 없으면 처음 저장할 때 만든다. */
    public FileAttemptRepository(Path file) {
        this.file = file;
        Data loaded = JsonFileStore.read(file, new TypeToken<Data>() {}.getType());
        this.data = loaded == null ? new Data() : loaded;
        if (data.attempts == null) data.attempts = new ArrayList<>();
    }

    @Override
    public synchronized Attempt save(Attempt attempt) {
        Attempt saved;
        if (attempt.attemptId() == null) {
            saved = InMemoryAttemptRepository.withId(attempt, data.nextId++);
            data.attempts.add(saved);
        } else {
            int index = indexOf(attempt.attemptId());
            if (index < 0 || data.attempts.get(index).userId() != attempt.userId()) {
                throw new IllegalArgumentException("덮어쓸 실습 기록이 없습니다: " + attempt.attemptId());
            }
            saved = attempt;
            data.attempts.set(index, saved);
        }
        JsonFileStore.write(file, data);
        return saved;
    }

    @Override
    public synchronized Optional<Attempt> findById(long userId, long attemptId) {
        int index = indexOf(attemptId);
        return index < 0 ? Optional.empty()
                : Optional.of(data.attempts.get(index)).filter(a -> a.userId() == userId);
    }

    @Override
    public synchronized List<Attempt> findByUser(long userId) {
        List<Attempt> result = new ArrayList<>();
        for (Attempt a : data.attempts) {
            if (a.userId() == userId) result.add(a);
        }
        Comparator<Attempt> oldestFirst = Comparator.comparing(Attempt::startedAt).thenComparing(Attempt::attemptId);
        result.sort(oldestFirst.reversed());
        return result;
    }

    private int indexOf(long attemptId) {
        for (int i = 0; i < data.attempts.size(); i++) {
            Long id = data.attempts.get(i).attemptId();
            if (id != null && id == attemptId) return i;
        }
        return -1;
    }
}
