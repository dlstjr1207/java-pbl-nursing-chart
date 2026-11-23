package app.ui;

import app.cases.CaseGenerator;
import app.cases.CaseLoadException;
import app.cases.CaseLoader;
import app.domain.PatientCase;

/**
 * 개인 메인 화면의 로직(Swing 비의존). 실습에 쓸 케이스를 고르고 실습을 시작한다.
 * 케이스 선택 순서: PC-01이 있으면 PC-01, 없으면 DEMO (D24·D25).
 * AI 생성은 T-31 전까지 비활성이며 이유를 문구로 돌려준다.
 */
public class HomeController {
    public static final String FIXED_ID = "PC-01";
    public static final String DEMO_ID = "demo";
    /** AI 생성 버튼이 꺼져 있을 때 보여 주는 이유 (T-31에서 키·허용 약물 상태로 교체). */
    public static final String AI_DISABLED_REASON = "AI 케이스 생성은 아직 준비 중입니다. 지금은 기본 케이스로 실습합니다.";

    private final AppState state;
    private final CaseLoader loader;
    /** AI 케이스 생성기 (없으면 생성 기능 없음). */
    private final CaseGenerator generator;

    public HomeController(AppState state, CaseLoader loader) {
        this(state, loader, null);
    }

    public HomeController(AppState state, CaseLoader loader, CaseGenerator generator) {
        this.state = state;
        this.loader = loader;
        this.generator = generator;
    }

    /** AI 생성 버튼을 켤 수 있는지 (키와 허용 약물 목록이 모두 있을 때). */
    public boolean aiGenerationAvailable() { return generator != null && generator.available(); }

    /** AI 생성을 쓸 수 없는 이유 (사용 가능하면 빈 문자열). */
    public String aiGenerationReason() {
        if (aiGenerationAvailable()) return "";
        return generator == null ? AI_DISABLED_REASON : generator.unavailableReason();
    }

    /**
     * AI로 새 케이스를 만든다(백그라운드 스레드에서 실행, 화면 상태는 바꾸지 않음).
     * 생성에 실패하면 기본 케이스(PC-01, 없으면 DEMO)가 결과에 들어 있다.
     *
     * @throws CaseLoadException 기본 케이스 파일도 읽지 못할 때
     */
    public CaseGenerator.Result generateCase() {
        if (generator == null) throw new IllegalStateException(AI_DISABLED_REASON);
        return generator.generate();
    }

    /** 생성 결과로 실습을 시작한다(화면 스레드). 돌려주는 문구는 화면 안내(성공이면 null). */
    public String applyGenerated(CaseGenerator.Result r) {
        state.startPractice(r.patientCase());
        return r.generated() ? null : r.message();
    }

    /**
     * AI로 새 케이스를 만들어 바로 실습을 시작한다(테스트·동기 실행용).
     * 생성에 실패하면 기본 케이스(PC-01, 없으면 DEMO)로 시작한다. 케이스를 못 읽으면 오류 문구를 돌려준다.
     */
    public String startGeneratedPractice() {
        if (generator == null) return AI_DISABLED_REASON;
        try {
            return applyGenerated(generateCase());
        } catch (CaseLoadException e) {
            return "케이스를 불러오지 못했습니다: " + e.getMessage();
        }
    }

    /** 기본 실습 케이스 ID. PC-01이 있으면 PC-01, 없으면 DEMO. */
    public String defaultCaseId() { return loader.exists(FIXED_ID) ? FIXED_ID : DEMO_ID; }

    /** 기본 케이스 안내 문구 (화면 표시용). */
    public String defaultCaseDescription() {
        return FIXED_ID.equals(defaultCaseId())
                ? "기본 케이스 PC-01로 실습합니다."
                : "PC-01이 아직 없어 DEMO 케이스(화면 확인용, 실제 케이스 아님)로 실습합니다.";
    }

    /**
     * 기본 케이스로 실습을 시작한다. 성공하면 null, 케이스를 읽지 못하면 사용자에게 보일 오류 문구.
     * 실패하면 화면 상태는 바뀌지 않는다.
     */
    public String startDefaultPractice() {
        try {
            state.startPractice(loader.load(defaultCaseId()));
            return null;
        } catch (CaseLoadException e) {
            return "케이스를 불러오지 못했습니다: " + e.getMessage();
        }
    }

    /** 이미 읽은 케이스로 실습을 시작한다 (테스트·후속 작업용). */
    public void startPractice(PatientCase patientCase) { state.startPractice(patientCase); }
}
