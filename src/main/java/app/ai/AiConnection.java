package app.ai;

import java.io.IOException;

/**
 * 프로그램이 쓸 AI 설정을 고르고 연결 상태를 알려 준다.
 * 순서: config/ai.properties의 키(기존 방식) → 메뉴에서 저장한 AI 서버 주소(중계 서버) → AI 꺼짐.
 * 중계 서버는 연결을 확인한 뒤에만 켠다. 연결되지 않으면 AI 기능만 꺼지고 PC-01과 규칙 점검으로 동작한다.
 */
public final class AiConnection {

    /** 상단 배지 문구 */
    public static final String STATUS_KEY = "AI: 키 사용";
    public static final String STATUS_RELAY_OK = "AI 서버 연결됨";
    public static final String STATUS_RELAY_FAIL = "AI 서버 연결 안 됨";
    public static final String STATUS_OFF = "AI 꺼짐";
    public static final String STATUS_CHECKING = "AI 서버 확인 중…";

    private final AiClient client;
    private final AiConfig keyConfig;
    private final AiServerSettings settings;
    private final AiTransport transport;
    private volatile String status = STATUS_OFF;
    private volatile String detail = "";

    public AiConnection(AiClient client, AiConfig keyConfig, AiServerSettings settings, AiTransport transport) {
        this.client = client;
        this.keyConfig = keyConfig == null ? AiConfig.disabled() : keyConfig;
        this.settings = settings;
        this.transport = transport;
    }

    /** 키 파일이 있어 키 방식을 쓰는지 (이때는 AI 서버 주소를 쓰지 않는다) */
    public boolean usesKeyFile() {
        return keyConfig.isEnabled() && !keyConfig.isRelay();
    }

    /** 저장된 AI 서버 주소 */
    public String serverUrl() {
        return settings.url();
    }

    /** 상단 배지 문구 */
    public String status() {
        return status;
    }

    /** 상태 설명 (설정 창 안내용) */
    public String detail() {
        return detail;
    }

    /**
     * 설정을 고르고 연결을 확인한다. 중계 서버는 네트워크를 쓰므로 화면 스레드가 아닌 곳에서 부른다.
     * 결과 상태 문구를 돌려준다.
     */
    public String connect() {
        if (usesKeyFile()) {
            client.setConfig(keyConfig);
            status = STATUS_KEY;
            detail = "config/ai.properties의 키를 사용합니다. (AI 서버 주소는 쓰지 않습니다)";
            return status;
        }
        String url = settings.url();
        if (url.isEmpty()) {
            client.setConfig(AiConfig.disabled());
            status = STATUS_OFF;
            detail = "AI 서버 주소가 없습니다. 기본 케이스와 규칙 점검만 동작합니다.";
            return status;
        }
        try {
            int remaining = AiClient.checkRelay(transport, url);
            client.setConfig(AiConfig.relay(url));
            status = STATUS_RELAY_OK;
            detail = "AI 서버에 연결했습니다." + (remaining >= 0 ? " 오늘 남은 사용량 " + remaining + "회." : "");
        } catch (IOException e) {
            client.setConfig(AiConfig.disabled());
            status = STATUS_RELAY_FAIL;
            detail = "AI 서버에 연결하지 못했습니다 (" + e.getMessage() + "). 기본 케이스와 규칙 점검만 동작합니다.";
        }
        return status;
    }

    /** 주소만 저장한다 (연결 확인은 connect). 형식이 틀리면 저장하지 않고 IllegalArgumentException. */
    public void saveUrl(String url) throws IOException {
        String problem = AiServerSettings.problem(url);
        if (problem != null) throw new IllegalArgumentException(problem);
        settings.save(url);
    }

    /** 주소를 저장하고 다시 연결한다. */
    public String saveAndConnect(String url) throws IOException {
        saveUrl(url);
        return connect();
    }
}
