package app.cases;

import app.domain.DomainJson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * 허용 약물 목록. {@code cases/allowed-drugs.json}의 {@code {"drugs":[...]}}를 읽는다 (D26).
 * 목록이 비어 있으면 AI 케이스 생성을 끈다({@link #isEmpty()}). 값은 코드가 채우지 않는다.
 */
public class AllowedDrugs {

    /** JSON 파일 전체 형식. */
    private record FileFormat(List<AllowedDrug> drugs) {
    }

    private static final String DEFAULT_PATH = "cases/allowed-drugs.json";

    private final List<AllowedDrug> drugs;

    public AllowedDrugs(List<AllowedDrug> drugs) {
        this.drugs = drugs == null ? List.of() : List.copyOf(drugs);
    }

    /** 기본 위치(cases/allowed-drugs.json)에서 읽는다. */
    public static AllowedDrugs load() {
        return load(AllowedDrugs.class.getClassLoader(), DEFAULT_PATH);
    }

    /** 클래스패스의 지정 경로에서 읽는다. 파일이 없으면 빈 목록으로 본다. 형식이 틀리면 거부한다. */
    public static AllowedDrugs load(ClassLoader classLoader, String path) {
        try (InputStream in = classLoader.getResourceAsStream(path)) {
            if (in == null) {
                return new AllowedDrugs(List.of());
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), path);
        } catch (IOException e) {
            throw new CaseLoadException("허용 약물 파일을 읽을 수 없습니다: " + path, e);
        }
    }

    /** JSON 문자열을 목록으로 바꾼다. */
    public static AllowedDrugs parse(String json, String origin) {
        FileFormat f;
        try {
            f = DomainJson.fromJson(json, FileFormat.class);
        } catch (RuntimeException e) {
            throw new CaseLoadException("허용 약물 JSON 형식이 올바르지 않습니다: " + origin, e);
        }
        if (f == null || f.drugs() == null) {
            return new AllowedDrugs(List.of());
        }
        for (int i = 0; i < f.drugs().size(); i++) {
            AllowedDrug d = f.drugs().get(i);
            if (d == null || d.name() == null || d.name().isBlank()) {
                throw new CaseLoadException("허용 약물 항목 오류: " + origin, List.of("drugs[" + i + "].name 누락"));
            }
        }
        return new AllowedDrugs(f.drugs());
    }

    /** 목록이 비어 있는지. 비어 있으면 AI 생성만 끈다. */
    public boolean isEmpty() {
        return drugs.isEmpty();
    }

    public List<AllowedDrug> all() {
        return drugs;
    }

    /** 약명(대소문자·앞뒤 공백 무시)으로 찾는다. */
    public Optional<AllowedDrug> find(String drugName) {
        if (drugName == null) {
            return Optional.empty();
        }
        String key = drugName.trim();
        return drugs.stream().filter(d -> d.name().trim().equalsIgnoreCase(key)).findFirst();
    }
}
