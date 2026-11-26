package app.ui;

import app.domain.CaseSource;
import app.domain.MedicationRecord;
import app.domain.ObservationEntry;
import app.domain.PatientCase;
import java.util.ArrayList;
import java.util.List;

/**
 * 화면 전체가 공유하는 상태(로그인 사용자, 실습 중인 케이스, 현재 화면).
 * 화면 이동 가능 여부와 상단 표시 문구를 결정한다. Swing에 의존하지 않아 단위 테스트가 가능하다.
 */
public class AppState {
    /** 모든 화면에 표시하는 고정 문구 (화면명세 '공통 구성'). */
    public static final String EDU_NOTICE = "교육용 예시 · 임상 검수 전";
    public static final String DEMO_NOTICE = "DEMO · 실제 케이스 아님";
    public static final String AI_NOTICE = "AI 생성 · 임상 검수 전";
    /** DB 없이 파일에 저장할 때 상단에 표시하는 문구 (D27). */
    public static final String FILE_MODE_NOTICE = "DB 없이 파일 저장 모드";

    /** 저장 방식 안내 문구 (파일 저장 모드일 때만 값이 있다). */
    private String storageNotice;
    /** AI 연결 상태 배지 문구 (null이면 표시 안 함) */
    private volatile String aiNotice;

    private String userName;
    private PatientCase currentCase;
    private Screen current = Screen.LOGIN;
    /** 이번 실습에서 학생이 작성한 관찰 기록 (제출 전 임시 보관). */
    private final List<ObservationEntry> studentObservations = new ArrayList<>();

    /** 이번 실습에서 학생이 작성한 투약 기록 (제출 전 임시 보관). */
    private final List<MedicationRecord> studentMedications = new ArrayList<>();

    /** 결과 화면에 보일 실습 기록 (방금 제출했거나 과거 기록에서 고른 것). */
    private app.domain.Attempt resultAttempt;

    /** 이번 실습의 시작 시각 (제출 시 Attempt에 저장). */
    private java.time.LocalDateTime practiceStartedAt;

    /**
     * 제출한 기록 보기 중인지. 결과 화면을 열면 그 기록의 환자(AI 환자면 그때 저장한 환자)와 제출한 관찰·투약을
     * currentCase·학생 기록 목록에 채워 환자 정보·관찰·투약·SBAR 화면을 읽기 전용으로 다시 볼 수 있게 한다.
     * 이 동안에는 기록을 추가·제출할 수 없다. 새 실습을 시작하거나 개인 메인으로 가면 끝난다.
     */
    private boolean reviewing;
    /** 이번 실습의 정신상태 평가 (D38, 적지 않았으면 null) */
    private app.domain.MentalStatus mentalStatus;

    /** 읽기 전용 화면에서 저장·제출을 막을 때 보이는 문구 */
    public static final String REVIEW_LOCKED = "제출한 기록 보기 중에는 기록을 고치거나 다시 제출할 수 없습니다.";
    /** 읽기 전용 화면 위 안내 */
    public static final String REVIEW_BANNER = "제출한 기록 보기 중 (읽기 전용)";

    public Screen current() { return current; }

    /** 저장 방식 안내 문구를 정한다 (파일 저장 모드면 FILE_MODE_NOTICE, DB면 null). */
    public void setStorageNotice(String notice) { this.storageNotice = notice; }

    /** AI 연결 상태 문구를 정한다 (예: "AI 서버 연결됨"). */
    public void setAiNotice(String notice) { this.aiNotice = notice; }

    /** 실습 중에 학생이 작성해 둔 관찰·투약 기록이 있는지 (제출 전이라 이동하면 사라진다). */
    public boolean hasStudentRecords() {
        return !reviewing && currentCase != null && (!studentObservations.isEmpty() || !studentMedications.isEmpty());
    }

    /** 제출한 기록 보기 중인지 (환자 정보·관찰·투약·SBAR가 읽기 전용) */
    public boolean isReviewing() { return reviewing; }

    /** 이번 실습의 시작 시각. 실습 중이 아니면 null. */
    public java.time.LocalDateTime practiceStartedAt() { return practiceStartedAt; }

    public String userName() { return userName; }

    public PatientCase currentCase() { return currentCase; }

    /** 결과 화면에 보일 기록 (없으면 null). */
    public app.domain.Attempt resultAttempt() { return resultAttempt; }

    /**
     * 실습 기록의 결과 화면으로 간다. 진행 중이던 실습은 끝낸다(제출 직후이거나 개인 메인에서 연 경우).
     * 그 기록의 환자와 제출한 관찰·투약을 채워 읽기 전용으로 다시 볼 수 있게 한다(케이스를 못 읽으면 결과만 본다).
     * 로그인 전이면 false.
     */
    public boolean showResult(app.domain.Attempt attempt) {
        if (userName == null || attempt == null) return false;
        endPractice();
        resultAttempt = attempt;
        PatientCase c = ResultView.caseOf(attempt);
        if (c != null) {
            currentCase = c;
            studentObservations.addAll(attempt.observationEntries());
            studentMedications.addAll(attempt.medicationRecords());
            mentalStatus = attempt.mentalStatus();
            reviewing = true;
        }
        current = Screen.RESULT;
        return true;
    }

    private void endPractice() {
        currentCase = null;
        practiceStartedAt = null;
        reviewing = false;
        mentalStatus = null;
        studentObservations.clear();
        studentMedications.clear();
    }

    /** 로그인 처리. 로그인 직후 개인 메인으로 이동한다. */
    public void login(String name) {
        this.userName = name;
        endPractice();
        this.current = Screen.HOME;
    }

    /** 로그아웃 처리. 이전 사용자의 상태(케이스 포함)를 모두 지우고 로그인 화면으로 돌아간다. */
    public void logout() {
        this.userName = null;
        this.resultAttempt = null;
        endPractice();
        this.current = Screen.LOGIN;
    }

    /** 실습 케이스를 정하고 환자 정보 화면으로 이동한다. */
    public void startPractice(PatientCase patientCase) {
        if (userName == null) throw new IllegalStateException("로그인이 필요합니다.");
        this.reviewing = false; // 새 실습은 다시 입력할 수 있다
        this.currentCase = patientCase;
        this.practiceStartedAt = java.time.LocalDateTime.now();
        this.studentObservations.clear(); // 새 실습은 빈 기록으로 시작
        this.studentMedications.clear();
        this.mentalStatus = null;
        this.current = Screen.PATIENT;
    }

    /** 학생이 작성한 관찰 기록(읽기 전용 목록). */
    public List<ObservationEntry> studentObservations() {
        return java.util.Collections.unmodifiableList(studentObservations);
    }

    /** 학생 관찰 기록을 추가한다. */
    public void addObservation(ObservationEntry entry) {
        if (reviewing) throw new IllegalStateException(REVIEW_LOCKED);
        studentObservations.add(entry);
    }

    /** 학생이 작성한 투약 기록(읽기 전용 목록). */
    public List<MedicationRecord> studentMedications() {
        return java.util.Collections.unmodifiableList(studentMedications);
    }

    /** 학생 투약 기록을 추가한다. */
    public void addMedication(MedicationRecord record) {
        if (reviewing) throw new IllegalStateException(REVIEW_LOCKED);
        studentMedications.add(record);
    }

    /** 이번 실습의 정신상태 평가 (적지 않았으면 null) */
    public app.domain.MentalStatus mentalStatus() { return mentalStatus; }

    /** 정신상태 평가를 저장한다. 모두 비었으면 지운다. 제출한 기록 보기 중에는 고칠 수 없다. */
    public void setMentalStatus(app.domain.MentalStatus m) {
        if (reviewing) throw new IllegalStateException(REVIEW_LOCKED);
        mentalStatus = app.domain.MentalStatus.blank(m) ? null : m;
    }

    /** 기록 서명: 로그인한 사용자 이름 (D38, 3교대 기록자 표시) */
    public String signature() { return userName == null ? "학생" : userName; }

    /** 왼쪽 메뉴(로그아웃 포함)를 보일지. 로그인 전에는 숨긴다 (polish C-10). */
    public boolean showSideMenu() { return userName != null; }

    /** 이동 가능 여부. 로그인·케이스 조건을 확인한다. */
    public boolean canGo(Screen target) {
        if (target.needsLogin() != (userName != null)) return false;
        if (target == Screen.RESULT && resultAttempt == null) return false;
        return !(target.needsCase() && currentCase == null);
    }

    /** 이동한다. 불가능하면 현재 화면을 유지하고 false를 돌려준다. */
    public boolean go(Screen target) {
        if (!canGo(target)) return false;
        if (target == Screen.HOME) endPractice(); // 개인 메인으로 돌아가면 실습 종료
        current = target;
        return true;
    }

    /** 상단 상태줄의 '현재 환자' 문구. */
    public String patientLabel() {
        if (current == Screen.RESULT && resultAttempt != null) {
            // 결과를 보는 동안에는 그 실습의 환자를 보여 준다 (실습은 끝났으므로 currentCase는 비어 있다).
            PatientCase c = ResultView.caseOf(resultAttempt);
            String name = c == null || c.patient() == null ? "-" : c.patient().name();
            return "환자: " + name + " (" + resultAttempt.caseId() + ") · 결과 보기";
        }
        if (currentCase == null) return "환자: 선택 안 함";
        return "환자: " + currentCase.patient().name() + " (" + currentCase.id() + ")" + (reviewing ? " · 제출한 기록 보기" : "");
    }

    /** 상단 상태줄의 '로그인 사용자' 문구. */
    public String userLabel() {
        return userName == null ? "로그인 안 됨" : "사용자: " + userName;
    }

    /** 현재 화면 문구. */
    public String screenLabel() { return "현재 화면: " + current.title(); }

    /** 화면에 항상 표시할 안내 문구 목록. DEMO·AI 케이스면 해당 문구를 덧붙인다. */
    public List<String> notices() {
        List<String> list = new ArrayList<>();
        list.add(EDU_NOTICE);
        if (storageNotice != null) list.add(storageNotice);
        if (aiNotice != null) list.add(aiNotice);
        if (currentCase != null) {
            if (currentCase.source() == CaseSource.DEMO) list.add(DEMO_NOTICE);
            else if (currentCase.source() == CaseSource.AI_GENERATED) list.add(AI_NOTICE);
        }
        return list;
    }
}
