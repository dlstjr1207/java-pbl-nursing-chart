package app.storage;

import app.domain.Attempt;
import java.util.List;
import java.util.Optional;

/**
 * 실습 기록 저장소 인터페이스 (S8). 단위 테스트는 메모리, 실제 실행은 MySQL 구현을 쓴다 (D21).
 * 모든 조회는 사용자 번호를 함께 받아, 다른 사용자의 기록은 보이지 않는다.
 */
public interface AttemptRepository {

    /**
     * 실습 1회차를 저장한다. 학생 입력(관찰·투약·SBAR)과 제출 당시 점검 결과·AI 해설을 함께 보존한다.
     * attemptId가 null이면 새로 저장하고, 있으면 그 사용자의 기존 기록을 같은 번호로 덮어쓴다.
     *
     * @return 번호가 채워진 저장 결과
     * @throws IllegalArgumentException 덮어쓸 기록이 없거나 다른 사용자의 것일 때
     */
    Attempt save(Attempt attempt);

    /** 사용자 본인의 실습 1건을 찾는다. 다른 사용자의 기록이면 빈 값이다. */
    Optional<Attempt> findById(long userId, long attemptId);

    /** 사용자 본인의 실습을 시작 시각 최신순으로 돌려준다. */
    List<Attempt> findByUser(long userId);
}
