package app.ai;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * AI 설정. 두 가지 방식이 있다.
 * <ul>
 *   <li>키 방식: config/ai.properties에 키·모델·접속 주소를 사람이 직접 넣는다. 키는 toString·오류 메시지에 출력하지 않는다.</li>
 *   <li>중계 방식: 키 없이 AI 중계 서버 주소만 쓴다({@link #relay}). 키와 모델은 중계 서버(맥미니)에만 있다.</li>
 * </ul>
 * 둘 다 없으면 {@link #isEnabled()}가 false라서 AI 기능만 꺼진다.
 */
public final class AiConfig {

    /** 기본 설정 파일 위치 (프로젝트 루트 기준) */
    public static final Path DEFAULT_PATH = Path.of("config", "ai.properties");

    private final String provider;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    /** 중계 서버 주소를 쓰는지 (키 없이 보낸다) */
    private final boolean relay;

    /** 중계 방식의 provider 이름 */
    public static final String RELAY = "relay";

    public AiConfig(String provider, String apiKey, String model, String baseUrl) {
        this.provider = clean(provider);
        this.apiKey = clean(apiKey);
        this.model = clean(model);
        this.baseUrl = clean(baseUrl);
        this.relay = RELAY.equals(this.provider) && this.apiKey.isEmpty();
    }

    /** 키 없이 AI 중계 서버 주소로 보내는 설정. 모델은 중계 서버가 정한다. */
    public static AiConfig relay(String serverUrl) {
        return new AiConfig(RELAY, "", RELAY, serverUrl);
    }

    /** 키 방식은 키·모델·접속 주소가 모두, 중계 방식은 주소가 있어야 AI를 쓸 수 있다. */
    public boolean isEnabled() {
        if (relay) return !baseUrl.isEmpty();
        return !apiKey.isEmpty() && !model.isEmpty() && !baseUrl.isEmpty();
    }

    /** 중계 서버 방식인지 */
    public boolean isRelay() {
        return relay;
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
