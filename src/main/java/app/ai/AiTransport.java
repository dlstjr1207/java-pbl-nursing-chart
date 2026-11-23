package app.ai;

import java.io.IOException;

/** HTTP 전송 부분. 테스트에서는 가짜 구현으로 바꾼다. */
public interface AiTransport {

    /**
     * JSON 본문을 POST하고 응답 본문을 돌려준다.
     *
     * @throws IOException 연결 실패·시간 초과·HTTP 오류 상태
     */
    String post(String url, String apiKey, String jsonBody) throws IOException;
}
