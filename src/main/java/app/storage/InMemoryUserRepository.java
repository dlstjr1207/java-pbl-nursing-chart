package app.storage;

import app.auth.User;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 단위 테스트용 메모리 계정 저장소. */
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> users = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public synchronized User create(String loginId, String name, String passwordHash, String salt) {
        if (users.containsKey(loginId)) {
            throw new DuplicateLoginIdException(loginId);
        }
        User user = new User(nextId++, loginId, name, passwordHash, salt);
        users.put(loginId, user);
        return user;
    }

    @Override
    public synchronized Optional<User> findByLoginId(String loginId) {
        return Optional.ofNullable(users.get(loginId));
    }
}
