package app.drugs;

import app.cases.AllowedDrug;
import app.cases.AllowedDrugs;
import app.domain.DomainJson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * 저장된 약 정보 파일({@code drugs/drug-info.json})을 읽어 처방 약명으로 찾아 준다 (단계 7).
 * 프로그램 실행 중에는 이 파일만 읽고 API를 부르지 않는다. 파일 갱신은 {@link DrugInfoUpdater}로 따로 한다.
 */
public class DrugInfoCatalog {

    /** 파일 전체 형식. */
    public record FileFormat(String source, String fetchedAt, List<DrugInfo> drugs) {
    }

    public static final String RESOURCE = "drugs/drug-info.json";

    private final FileFormat file;
    private final AllowedDrugs allowed;

    public DrugInfoCatalog(FileFormat file, AllowedDrugs allowed) {
        this.file = file == null ? new FileFormat("", "", List.of()) : file;
        this.allowed = allowed;
    }

    /** 클래스패스의 기본 파일을 읽는다. 없거나 형식이 틀리면 빈 목록(화면에 "약 정보 없음"). */
    public static DrugInfoCatalog load() {
        try (InputStream in = DrugInfoCatalog.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) return new DrugInfoCatalog(null, AllowedDrugs.load());
            return new DrugInfoCatalog(parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)), AllowedDrugs.load());
        } catch (IOException | RuntimeException e) {
            return new DrugInfoCatalog(null, AllowedDrugs.load());
        }
    }

    static FileFormat parse(String json) {
        FileFormat f = DomainJson.fromJson(json, FileFormat.class);
        return f == null || f.drugs() == null ? new FileFormat("", "", List.of()) : f;
    }

    public List<DrugInfo> all() {
        return file.drugs();
    }

    /** 자료를 받은 날짜 (yyyy-MM-dd). */
    public String fetchedAt() {
        return file.fetchedAt() == null ? "" : file.fetchedAt();
    }

    /** 처방 약명(예: "Lithium carbonate", "로라제팜")으로 찾는다. 허용 약물의 이름·별칭 규칙을 쓴다. */
    public Optional<DrugInfo> find(String orderDrugName) {
        Optional<String> name = allowed.find(orderDrugName).map(AllowedDrug::name);
        return name.flatMap(n -> file.drugs().stream().filter(d -> n.equalsIgnoreCase(d.drug())).findFirst());
    }

    /** 투약 화면에 보일 문장. 정보가 없으면 그 사실을 알린다. */
    public String describe(String orderDrugName) {
        return find(orderDrugName).map(this::text)
                .orElse("이 약의 허가정보가 저장되어 있지 않습니다.");
    }

    String text(DrugInfo d) {
        return "제품명: " + d.itemName() + " (" + d.company() + ")\n"
                + "※ 같은 성분의 대표 제품 1개 기준, 참고용입니다. 채점 기준은 근거표 확인 값입니다."
                + (fetchedAt().isEmpty() ? "" : " 자료 받은 날 " + fetchedAt() + ".") + "\n\n"
                + "[효능효과]\n" + d.efficacy() + "\n\n[용법용량]\n" + d.dosage();
    }
}
