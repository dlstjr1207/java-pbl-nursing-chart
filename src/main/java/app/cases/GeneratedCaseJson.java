package app.cases;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI가 만든 케이스 JSON의 흔한 형식 차이를 읽기 전에 바로잡는다 (단계 6).
 * 내용(약·용량·정답 키)은 바꾸지 않고 형태만 맞춘다: 앞뒤 설명문·코드 펜스 제거, 문자열 칸에 온 객체·배열을
 * 문장으로 펼치기, 소문자 구분값(day, prn, given)을 대문자로. 허용 약물·정답 키 검사는 CaseValidator가 그대로 한다.
 */
final class GeneratedCaseJson {

    /** 문자열이어야 하는 칸 (최상위). */
    private static final List<String> TEXT_FIELDS = List.of("id", "title", "difficulty", "briefing", "sbarReference", "riskTier");

    private GeneratedCaseJson() {
    }

    /** 응답 문자열에서 JSON 객체 부분만 꺼내 형태를 맞춘다. 객체가 없으면 IllegalArgumentException. */
    static JsonObject normalize(String content) {
        String text = content == null ? "" : content;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) throw new IllegalArgumentException("응답에 JSON 객체가 없음");
        JsonObject root = JsonParser.parseString(text.substring(start, end + 1)).getAsJsonObject();
        for (String f : TEXT_FIELDS) flattenField(root, f);
        if (root.has("objectives") && root.get("objectives").isJsonPrimitive()) {
            JsonArray a = new JsonArray();
            a.add(root.get("objectives"));
            root.add("objectives", a);
        }
        upper(root, "orders", "type");
        upper(root, "observationLog", "shift");
        if (root.get("modelAnswer") instanceof JsonObject m) {
            for (String f : List.of("observation", "medication", "sbar")) flattenField(m, f);
            if (m.get("observationRecord") instanceof JsonObject o) upperValue(o, "shift");
            if (m.get("medicationRecord") instanceof JsonObject r) upperValue(r, "result");
        }
        return root;
    }

    /** 문자열 칸에 객체·배열이 오면 "키: 값" 줄로 펼친다. */
    private static void flattenField(JsonObject obj, String field) {
        JsonElement e = obj.get(field);
        if (e == null || e.isJsonNull() || e.isJsonPrimitive()) return;
        obj.add(field, new JsonPrimitive(flatten(e)));
    }

    static String flatten(JsonElement e) {
        if (e == null || e.isJsonNull()) return "";
        if (e.isJsonPrimitive()) return e.getAsString();
        List<String> parts = new ArrayList<>();
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) parts.add(flatten(x));
        } else {
            for (Map.Entry<String, JsonElement> x : e.getAsJsonObject().entrySet()) {
                parts.add(x.getKey() + ": " + flatten(x.getValue()));
            }
        }
        return String.join("\n", parts);
    }

    /** 배열 안 객체들의 구분값을 대문자로. */
    private static void upper(JsonObject root, String arrayField, String valueField) {
        if (!(root.get(arrayField) instanceof JsonArray arr)) return;
        for (JsonElement x : arr) {
            if (x instanceof JsonObject o) upperValue(o, valueField);
        }
    }

    private static void upperValue(JsonObject o, String field) {
        if (o.get(field) instanceof JsonPrimitive p && p.isString()) {
            o.addProperty(field, p.getAsString().trim().toUpperCase(Locale.ROOT));
        }
    }
}
