package app.storage;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * DB 접속 설정 (D21, T-19).
 * Git에서 제외된 config/db.properties 파일을 읽는다. 항목 이름은 config/db.properties.example 참고.
 * 비밀번호는 toString()이나 오류 메시지에 절대 넣지 않는다.
 */
public final class DbConfig {

    /** 기본 설정 파일 위치 (프로젝트 루트 기준) */
    public static final Path DEFAULT_PATH = Path.of("config", "db.properties");

    static final String KEY_URL = "db.url";
    static final String KEY_TEST_URL = "db.test.url";
    static final String KEY_USER = "db.user";
    static final String KEY_PASSWORD = "db.password";

    private final String url;
    private final String testUrl;
    private final String user;
    private final String password;

    private DbConfig(String url, String testUrl, String user, String password) {
        this.url = url;
        this.testUrl = testUrl;
        this.user = user;
        this.password = password;
    }

    /** 설정 파일이 있는지 확인한다. 없으면 DB 기능(과 DB 테스트)을 건너뛸 수 있다. */
    public static boolean exists(Path path) {
        return Files.isRegularFile(path);
    }

    /**
     * 설정 파일을 읽는다.
     * 필수 항목(db.url, db.test.url, db.user, db.password)이 없거나 비어 있으면 항목 이름을 알려 주며 실패한다.
     */
    public static DbConfig load(Path path) {
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            throw new IllegalStateException("DB 설정 파일을 읽을 수 없습니다: " + path, e);
        }

        List<String> missing = new ArrayList<>();
        String url = required(props, KEY_URL, missing);
        String testUrl = required(props, KEY_TEST_URL, missing);
        String user = required(props, KEY_USER, missing);
        String password = required(props, KEY_PASSWORD, missing);
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "DB 설정 파일(" + path + ")에 값이 비어 있는 항목: " + String.join(", ", missing));
        }
        return new DbConfig(url, testUrl, user, password);
    }

    private static String required(Properties props, String key, List<String> missing) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            missing.add(key);
            return null;
        }
        return value.trim();
    }

    /** 실제 사용 DB 주소 */
    public String url() {
        return url;
    }

    /** 테스트 전용 DB 주소 */
    public String testUrl() {
        return testUrl;
    }

    public String user() {
        return user;
    }

    /** 비밀번호는 연결할 때만 쓰고 화면이나 로그에 출력하지 않는다. */
    String password() {
        return password;
    }

    @Override
    public String toString() {
        return "DbConfig[url=" + url + ", testUrl=" + testUrl + ", user=" + user + ", password=****]";
    }
}
