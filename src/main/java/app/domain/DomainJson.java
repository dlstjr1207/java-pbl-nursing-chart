package app.domain;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * 도메인 모델과 JSON 사이의 변환 (Gson).
 * 케이스 파일 읽기, AI 생성 케이스 원문 저장, 실습 기록 재현에서 같은 설정을 쓴다.
 * 날짜·시각(LocalDateTime)은 ISO-8601 문자열(예: 2026-11-22T09:30)로 저장한다.
 */
public final class DomainJson {

    private static final Gson GSON = new GsonBuilder()
            .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter().nullSafe())
            .disableHtmlEscaping()
            .setPrettyPrinting()
            .create();

    private DomainJson() {
    }

    /** 공통 설정의 Gson을 돌려준다. */
    public static Gson gson() {
        return GSON;
    }

    /** 객체를 JSON 문자열로 바꾼다. */
    public static String toJson(Object value) {
        return GSON.toJson(value);
    }

    /** JSON 문자열을 지정한 형식의 객체로 바꾼다. */
    public static <T> T fromJson(String json, Class<T> type) {
        return GSON.fromJson(json, type);
    }

    /** LocalDateTime을 ISO-8601 문자열로 읽고 쓴다. */
    private static final class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            out.value(value.toString());
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            return LocalDateTime.parse(in.nextString());
        }
    }
}
