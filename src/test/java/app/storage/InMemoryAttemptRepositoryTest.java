package app.storage;

/** 메모리 실습 저장소가 계약을 지키는지 확인한다. */
class InMemoryAttemptRepositoryTest extends AttemptRepositoryContract {

    private final AttemptRepository repository = new InMemoryAttemptRepository();
    private long nextUser = 1;

    @Override
    AttemptRepository repository() {
        return repository;
    }

    @Override
    long newUser(String loginId) {
        return nextUser++;
    }
}
