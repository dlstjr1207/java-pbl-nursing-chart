package app.ai;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * AI 설정 (config/ai.properties). 키는 사람이 직접 넣으며 toString·오류 메시지에 출력하지 않는다.
 * 파일이 없거나 키가 비어 있으면 {@link #isEnabled()}가 false라서 AI 기능만 꺼진다.
 */
public final class AiConfig {

    /** 기본 설정 파일 위치 (프로젝트 루트 기준) */
    public static final Path DEFAULT_PATH = Path.of("config", "ai.properties");

    private final String provider;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public AiConfig(String provider, String apiKey, String model, String baseUrl) {
        this.provider = clean(provider);
        this.apiKey = clean(apiKey);
        this.model = clean(model);
        this.baseUrl = clean(baseUrl);
    }

    /** 키·모델·접속 주소가 모두 있어야 AI를 쓸 수 있다. */
    public boolean isEnabled() {
        return !apiKey.isEmpty() && !model.isEmpty() && !baseUrl.isEmpty();
    }

    /** 키 없는 설정(AI 꺼짐) */
    public static AiConfig disabled() {
        return new AiConfig("", "", "", "");
    }

    /** 파일을 읽는다. 파일이 없거나 읽을 수 없으면 AI 꺼짐 설정을 돌려준다. */
    public static AiConfig load(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return disabled();
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            props.load(reader);
        } catch (IOException e) {
            return disabled();
        }
        return new AiConfig(props.getProperty("ai.provider"), props.getProperty("ai.apiKey"),
                props.getProperty("ai.model"), props.getProperty("ai.baseUrl"));
    }

    public String provider() {
        return provider;
    }

    public String model() {
        return model;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** 요청을 보낼 때만 쓴다. 화면·로그에 출력하지 않는다. */
    String apiKey() {
        return apiKey;
    }

    private static String clean(String s) {
        return s == null ? "" : s.trim();
    }

    @Override
    public String toString() {
        return "AiConfig[provider=" + provider + ", model=" + model + ", baseUrl=" + baseUrl + ", apiKey=****]";
    }
}
