package app.drugs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 허가정보 상세 API(getDrugPrdtPrmsnDtlInq08)의 JSON 응답에서 지정한 품목의 정보를 꺼낸다.
 * 응답 형식: {@code {"header":{"resultCode":"00"},"body":{"items":[{ITEM_SEQ, ITEM_NAME, EE_DOC_DATA, UD_DOC_DATA, ...}]}}}
 */
public final class DrugInfoParser {

    private DrugInfoParser() {
    }

    /**
     * 응답에서 품목기준코드가 같은 항목을 찾아 {@link DrugInfo}로 바꾼다.
     *
     * @throws IllegalArgumentException 오류 응답이거나 품목이 없을 때
     */
    public static DrugInfo parse(String json, String drug, String itemSeq) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("응답을 JSON으로 읽을 수 없음");
        }
        if (root.has("OpenAPI_ServiceResponse")) {
            JsonObject h = root.getAsJsonObject("OpenAPI_ServiceResponse").getAsJsonObject("cmmMsgHeader");
            throw new IllegalArgumentException("API 오류: " + str(h, "returnAuthMsg") + " (코드 " + str(h, "returnReasonCode") + ")");
        }
        JsonObject header = root.getAsJsonObject("header");
        if (header != null && !"00".equals(str(header, "resultCode"))) {
            throw new IllegalArgumentException("API 오류: " + str(header, "resultMsg"));
        }
        JsonObject body = root.getAsJsonObject("body");
        JsonElement items = body == null ? null : body.get("items");
        JsonArray list = new JsonArray();
        if (items instanceof JsonArray a) list = a;
        else if (items instanceof JsonObject o) list.add(o);
        for (JsonElement e : list) {
            JsonObject it = e.getAsJsonObject();
            if (itemSeq.equals(str(it, "ITEM_SEQ"))) {
                return new DrugInfo(drug, itemSeq, str(it, "ITEM_NAME").strip(), str(it, "ENTP_NAME"),
                        str(it, "ITEM_PERMIT_DATE"), str(it, "CHANGE_DATE"),
                        DrugDocText.toText(str(it, "EE_DOC_DATA")), DrugDocText.toText(str(it, "UD_DOC_DATA")));
            }
        }
        throw new IllegalArgumentException("응답에 품목 " + itemSeq + "이 없음");
    }

    private static String str(JsonObject o, String key) {
        JsonElement e = o == null ? null : o.get(key);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }
}
