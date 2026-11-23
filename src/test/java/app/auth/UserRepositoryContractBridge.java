package app.auth;

import app.storage.UserRepository;

/** 다른 패키지(app.storage)의 테스트가 계약 테스트를 상속할 수 있게 공개한 다리 클래스. */
public abstract class UserRepositoryContractBridge extends UserRepositoryContract {

    @Override
    public abstract UserRepository repository();
}
