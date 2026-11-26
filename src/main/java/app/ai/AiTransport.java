package app.ai;

import java.io.IOException;

/** HTTP 전송 부분. 테스트에서는 가짜 구현으로 바꾼다. */
public interface AiTransport {

    /** 프로그램이 보내는 앱 식별 헤더. AI 중계 서버는 이 헤더가 없는 요청을 거절한다. */
    String APP_HEADER = "X-Nursing-Chart-App";
    /** 앱 식별 헤더 값 */
    String APP_ID = "nursing-chart-trainer";

    /**
     * JSON 본문을 POST하고 응답 본문을 돌려준다. apiKey가 비어 있으면(중계 서버 사용) Authorization을 보내지 않는다.
     *
     * @throws IOException 연결 실패·시간 초과·HTTP 오류 상태
     */
    String post(String url, String apiKey, String jsonBody) throws IOException;

    /**
     * GET 요청(중계 서버 연결 확인용). 응답 본문을 돌려준다.
     *
     * @throws IOException 연결 실패·HTTP 오류 상태
     */
    default String get(String url) throws IOException {
        throw new IOException("GET을 지원하지 않는 전송입니다.");
    }
}
