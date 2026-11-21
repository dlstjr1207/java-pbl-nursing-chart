package app.storage;

import app.auth.User;
import java.util.Optional;

/** 계정 저장소 인터페이스. 단위 테스트는 메모리, 실제 실행은 MySQL 구현을 쓴다 (D21). */
public interface UserRepository {

    /**
     * 새 계정을 저장한다. 비밀번호는 이미 해시된 값만 받는다.
     *
     * @throws DuplicateLoginIdException 같은 아이디가 이미 있을 때
     */
    User create(String loginId, String name, String passwordHash, String salt);

    /** 아이디로 계정을 찾는다. */
    Optional<User> findByLoginId(String loginId);
}
