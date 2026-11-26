package app.domain;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 실습 1회차 (F-01, F-09, F-11). 학생 입력과 제출 당시 점검 결과를 함께 보관한다.
 * AI 생성 케이스는 generatedCaseJson에 원문을 남겨 과거 기록에서 같은 환자로 재현한다.
 *
 * @param attemptId          실습 번호 (저장 전이면 null)
 * @param userId             사용자 번호
 * @param caseId             케이스 ID
 * @param caseSource         케이스 출처
 * @param generatedCaseJson  AI 생성 케이스 원문 (고정 케이스면 null)
 * @param startedAt          시작 시각
 * @param submittedAt        제출 시각 (제출 전이면 null)
 * @param observationEntries 학생 관찰 기록
 * @param medicationRecords  학생 투약 기록
 * @param sbar               학생 SBAR (작성 전이면 null)
 * @param checks             점검 결과
 * @param score              점수 (미산출이면 null)
 * @param aiFeedback         AI 해설 (없으면 null)
 * @param mentalStatus       정신상태 평가 (D38. 적지 않았거나 이 기능 전 기록이면 null)
 */
public record Attempt(
        Long attemptId,
        long userId,
        String caseId,
        CaseSource caseSource,
        String generatedCaseJson,
        LocalDateTime startedAt,
        LocalDateTime submittedAt,
        List<ObservationEntry> observationEntries,
        List<MedicationRecord> medicationRecords,
        SbarReport sbar,
        List<CheckResult> checks,
        Double score,
        AiFeedback aiFeedback,
        MentalStatus mentalStatus) {

    public Attempt {
        observationEntries = Lists.copy(observationEntries);
        medicationRecords = Lists.copy(medicationRecords);
        checks = Lists.copy(checks);
    }

    /** 정신상태 평가가 없는 기록 (D38 전 기록·테스트용) */
    public Attempt(Long attemptId, long userId, String caseId, CaseSource caseSource, String generatedCaseJson,
                   LocalDateTime startedAt, LocalDateTime submittedAt, List<ObservationEntry> observationEntries,
                   List<MedicationRecord> medicationRecords, SbarReport sbar, List<CheckResult> checks, Double score,
                   AiFeedback aiFeedback) {
        this(attemptId, userId, caseId, caseSource, generatedCaseJson, startedAt, submittedAt, observationEntries,
                medicationRecords, sbar, checks, score, aiFeedback, null);
    }

    /** 제출되었는지 여부 */
    public boolean isSubmitted() {
        return submittedAt != null;
    }
}
