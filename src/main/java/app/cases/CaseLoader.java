package app.cases;

import app.domain.DomainJson;
import app.domain.Medication;
import app.domain.PatientCase;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 클래스패스의 {@code cases/<id>.json}에서 환자 케이스를 읽는다.
 * 파일이 없거나, JSON 형식이 틀리거나, 필수 항목이 비어 있으면 원인과 함께 {@link CaseLoadException}으로 거부한다.
 * 임상 값(용량 등)은 검사하지 않는다. 비어 있거나 [확인필요]여도 그대로 읽는다.
 */
public class CaseLoader {

    private static final String BASE = "cases/";

    private final ClassLoader classLoader;

    public CaseLoader() {
        this(CaseLoader.class.getClassLoader());
    }

    /** 테스트에서 다른 클래스패스를 쓰기 위한 생성자. */
    public CaseLoader(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    /** 케이스 ID(파일 이름, 확장자 제외)로 읽는다. 예: load("PC-01"). */
    public PatientCase load(String caseId) {
        if (!validId(caseId)) {
            throw new CaseLoadException("케이스 ID 형식이 올바르지 않습니다: " + caseId, List.of());
        }
        String path = BASE + caseId + ".json";
        try (InputStream in = classLoader.getResourceAsStream(path)) {
            if (in == null) {
                throw new CaseLoadException("케이스 파일이 없습니다: " + path, List.of());
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), path);
        } catch (IOException e) {
            throw new CaseLoadException("케이스 파일을 읽을 수 없습니다: " + path, e);
        }
    }

    /** 케이스가 있는지 확인한다 (예: PC-01이 없으면 DEMO로 대체할 때). */
    public boolean exists(String caseId) {
        return validId(caseId) && classLoader.getResource(BASE + caseId + ".json") != null;
    }

    /** JSON 문자열을 케이스로 바꾸고 필수 항목을 검사한다. origin은 오류 문구에 쓸 이름. */
    public PatientCase parse(String json, String origin) {
        PatientCase c;
        try {
            c = DomainJson.fromJson(json, PatientCase.class);
        } catch (RuntimeException e) {
            throw new CaseLoadException("JSON 형식이 올바르지 않습니다: " + origin, e);
        }
        if (c == null) {
            throw new CaseLoadException("케이스 내용이 비어 있습니다: " + origin, List.of());
        }
        List<String> problems = validate(c);
        if (!problems.isEmpty()) {
            throw new CaseLoadException("케이스 필수 항목 오류: " + origin, problems);
        }
        return c;
    }

    /** 필수 항목 점검. 문제를 모두 모아 돌려준다. */
    static List<String> validate(PatientCase c) {
        List<String> p = new ArrayList<>();
        if (blank(c.id())) {
            p.add("id 누락");
        }
        if (blank(c.title())) {
            p.add("title 누락");
        }
        if (c.source() == null) {
            p.add("source 누락 또는 알 수 없는 값 (FIXED, AI_GENERATED, DEMO 중 하나)");
        }
        if (c.patient() == null) {
            p.add("patient 누락");
        } else if (blank(c.patient().name())) {
            p.add("patient.name 누락");
        }
        if (c.orders().isEmpty()) {
            p.add("orders(처방)가 비어 있음");
        }
        for (int i = 0; i < c.orders().size(); i++) {
            Medication m = c.orders().get(i);
            if (m == null) {
                p.add("orders[" + i + "] 비어 있음");
                continue;
            }
            if (blank(m.orderId())) {
                p.add("orders[" + i + "].orderId 누락");
            }
            if (m.type() == null) {
                p.add("orders[" + i + "].type 누락 또는 알 수 없는 값 (REGULAR, PRN, PAST 중 하나)");
            }
            if (blank(m.drugName())) {
                p.add("orders[" + i + "].drugName 누락");
            }
        }
        return p;
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_-]+");
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
