package app.ai;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * 메뉴에서 넣은 AI 서버 주소(중계 서버)를 사용자 폴더(~/.nursing-chart-trainer/ai-server.properties)에 저장한다.
 * 키가 아니라 주소만 저장하므로 비밀 정보가 없다. 다음 실행에도 기억한다.
 */
public final class AiServerSettings {

    public static final String FILE_NAME = "ai-server.properties";
    private static final String KEY = "ai.serverUrl";

    private final Path file;

    public AiServerSettings(Path file) {
        this.file = file;
    }

    /** 저장된 주소. 없으면 빈 문자열. */
    public String url() {
        if (file == null || !Files.isRegularFile(file)) return "";
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        } catch (IOException e) {
            return "";
        }
        return p.getProperty(KEY, "").trim();
    }

    /** 주소를 저장한다. 빈 문자열이면 지운다. */
    public void save(String url) throws IOException {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) {
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        Properties p = new Properties();
        p.setProperty(KEY, u);
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            p.store(w, "AI 서버 주소 (간호 차팅 연습 프로그램)");
        }
    }

    /**
     * 주소 형식 검사: http:// 또는 https://로 시작하고 공백이 없어야 한다. 맞으면 null, 아니면 안내 문구.
     * 빈 문자열은 '주소 지우기'라 통과.
     */
    public static String problem(String url) {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) return null;
        if (!(u.startsWith("https://") || u.startsWith("http://"))) return "주소는 https:// 로 시작해야 합니다.";
        if (u.chars().anyMatch(Character::isWhitespace)) return "주소에 공백이 있습니다.";
        try {
            java.net.URI uri = java.net.URI.create(u);
            if (uri.getHost() == null) return "주소 형식이 올바르지 않습니다.";
        } catch (IllegalArgumentException e) {
            return "주소 형식이 올바르지 않습니다.";
        }
        return null;
    }
}
