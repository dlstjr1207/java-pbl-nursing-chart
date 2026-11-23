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

    private String userName;
    private PatientCase currentCase;
    private Screen current = Screen.LOGIN;
    /** 이번 실습에서 학생이 작성한 관찰 기록 (제출 전 임시 보관). */
    private final List<ObservationEntry> studentObservations = new ArrayList<>();

    /** 이번 실습에서 학생이 작성한 투약 기록 (제출 전 임시 보관). */
    private final List<MedicationRecord> studentMedications = new ArrayList<>();

    /** 이번 실습의 시작 시각 (제출 시 Attempt에 저장). */
    private java.time.LocalDateTime practiceStartedAt;

    public Screen current() { return current; }

    /** 저장 방식 안내 문구를 정한다 (파일 저장 모드면 FILE_MODE_NOTICE, DB면 null). */
    public void setStorageNotice(String notice) { this.storageNotice = notice; }

    /** 실습 중에 학생이 작성해 둔 관찰·투약 기록이 있는지 (제출 전이라 이동하면 사라진다). */
    public boolean hasStudentRecords() {
        return currentCase != null && (!studentObservations.isEmpty() || !studentMedications.isEmpty());
    }

    /** 이번 실습의 시작 시각. 실습 중이 아니면 null. */
    public java.time.LocalDateTime practiceStartedAt() { return practiceStartedAt; }

    public String userName() { return userName; }

    public PatientCase currentCase() { return currentCase; }

    /** 로그인 처리. 로그인 직후 개인 메인으로 이동한다. */
    public void login(String name) {
        this.userName = name;
        this.currentCase = null;
        this.studentObservations.clear();
        this.studentMedications.clear();
        this.current = Screen.HOME;
    }

    /** 로그아웃 처리. 이전 사용자의 상태(케이스 포함)를 모두 지우고 로그인 화면으로 돌아간다. */
    public void logout() {
        this.userName = null;
        this.practiceStartedAt = null;
        this.currentCase = null;
        this.studentObservations.clear();
        this.studentMedications.clear();
        this.current = Screen.LOGIN;
    }

    /** 실습 케이스를 정하고 환자 정보 화면으로 이동한다. */
    public void startPractice(PatientCase patientCase) {
        if (userName == null) throw new IllegalStateException("로그인이 필요합니다.");
        this.currentCase = patientCase;
        this.practiceStartedAt = java.time.LocalDateTime.now();
        this.studentObservations.clear(); // 새 실습은 빈 기록으로 시작
        this.studentMedications.clear();
        this.current = Screen.PATIENT;
    }

    /** 학생이 작성한 관찰 기록(읽기 전용 목록). */
    public List<ObservationEntry> studentObservations() {
        return java.util.Collections.unmodifiableList(studentObservations);
    }

    /** 학생 관찰 기록을 추가한다. */
    public void addObservation(ObservationEntry entry) {
        studentObservations.add(entry);
    }

    /** 학생이 작성한 투약 기록(읽기 전용 목록). */
    public List<MedicationRecord> studentMedications() {
        return java.util.Collections.unmodifiableList(studentMedications);
    }

    /** 학생 투약 기록을 추가한다. */
    public void addMedication(MedicationRecord record) {
        studentMedications.add(record);
    }

    /** 이동 가능 여부. 로그인·케이스 조건을 확인한다. */
    public boolean canGo(Screen target) {
        if (target.needsLogin() != (userName != null)) return false;
        return !(target.needsCase() && currentCase == null);
    }

    /** 이동한다. 불가능하면 현재 화면을 유지하고 false를 돌려준다. */
    public boolean go(Screen target) {
        if (!canGo(target)) return false;
        if (target == Screen.HOME) {
            currentCase = null;
            practiceStartedAt = null; // 개인 메인으로 돌아가면 실습 종료
            studentObservations.clear();
            studentMedications.clear();
        }
        current = target;
        return true;
    }

    /** 상단 상태줄의 '현재 환자' 문구. */
    public String patientLabel() {
        if (currentCase == null) return "환자: 선택 안 함";
        return "환자: " + currentCase.patient().name() + " (" + currentCase.id() + ")";
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
        if (currentCase != null) {
            if (currentCase.source() == CaseSource.DEMO) list.add(DEMO_NOTICE);
            else if (currentCase.source() == CaseSource.AI_GENERATED) list.add(AI_NOTICE);
        }
        return list;
    }
}
