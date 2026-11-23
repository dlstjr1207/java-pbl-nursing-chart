package app.storage;

import app.auth.User;
import com.google.gson.reflect.TypeToken;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 파일 저장 모드(D27)의 계정 저장소. DB가 없을 때 사용자 홈 아래 users.json에 저장한다.
 * 비밀번호는 MySQL 구현과 같이 해시·솔트만 저장한다.
 */
public class FileUserRepository implements UserRepository {

    /** 파일 내용: 다음 번호와 계정 목록. */
    private static final class Data {
        long nextId = 1;
        List<User> users = new ArrayList<>();
    }

    private final Path file;
    private final Data data;

    /** @param file 저장 파일(예: ~/.nursing-chart-trainer/users.json). 없으면 처음 저장할 때 만든다. */
    public FileUserRepository(Path file) {
        this.file = file;
        Data loaded = JsonFileStore.read(file, new TypeToken<Data>() {}.getType());
        this.data = loaded == null ? new Data() : loaded;
        if (data.users == null) data.users = new ArrayList<>();
    }

    @Override
    public synchronized User create(String loginId, String name, String passwordHash, String salt) {
        if (findByLoginId(loginId).isPresent()) throw new DuplicateLoginIdException(loginId);
        User user = new User(data.nextId++, loginId, name, passwordHash, salt);
        data.users.add(user);
        JsonFileStore.write(file, data);
        return user;
    }

    @Override
    public synchronized Optional<User> findByLoginId(String loginId) {
        return data.users.stream().filter(u -> u.loginId().equals(loginId)).findFirst();
    }
}
