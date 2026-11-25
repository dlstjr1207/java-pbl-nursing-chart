package app.domain;

import java.util.List;

/**
 * 환자 케이스 1개 (PC-01, DEMO, AI 생성 케이스가 모두 같은 형식, D20).
 * 항목은 환자케이스 '데이터 변환' 표를 따른다. answerKey·modelAnswer는 제출 전에는 화면에 보이지 않는다.
 *
 * @param id             케이스 ID (예: PC-01)
 * @param title          제목
 * @param difficulty     난이도
 * @param objectives     학습 목표
 * @param reviewStatus   검토 상태 (DRAFT, DEMO 등)
 * @param source         케이스 출처
 * @param patient        환자 정보
 * @param adr            알레르기·부작용
 * @param riskTier       위험 지표 (참고 정보)
 * @param orders         처방 목록
 * @param regimenHistory 처방 변경 이력
 * @param lithiumLevel   리튬 농도 검사 결과
 * @param vitals         측정 시각별 활력징후
 * @param observationLog 기존 관찰 로그
 * @param safetyRounds   안전 라운딩
 * @param briefing       상황 브리핑. 학생에게 보이지 않고 모범 기록·AI 해설의 사실로만 쓴다 (원자료 메모가 없는 예전 케이스는 화면에 보인다)
 * @param sbarReference  SBAR 참고 자료 (제출 전 제공)
 * @param answerKey      정답 키 (제출 후 공개)
 * @param modelAnswer    모범 기록 (제출 후 공개)
 * @param rawNotes       원자료 메모 (학생 화면에 보이는 근무 중 메모, 시간 순서 일부 섞임)
 * @param task           과제 안내 (원자료 메모 위에 보인다)
 */
public record PatientCase(
        String id,
        String title,
        String difficulty,
        List<String> objectives,
        String reviewStatus,
        CaseSource source,
        Patient patient,
        Adr adr,
        String riskTier,
        List<Medication> orders,
        List<RegimenChange> regimenHistory,
        List<LabResult> lithiumLevel,
        List<VitalSign> vitals,
        List<ObservationEntry> observationLog,
        List<SafetyRound> safetyRounds,
        String briefing,
        String sbarReference,
        AnswerKey answerKey,
        ModelAnswer modelAnswer,
        List<RawNote> rawNotes,
        String task) {

    public PatientCase {
        objectives = Lists.copy(objectives);
        orders = Lists.copy(orders);
        regimenHistory = Lists.copy(regimenHistory);
        lithiumLevel = Lists.copy(lithiumLevel);
        vitals = Lists.copy(vitals);
        observationLog = Lists.copy(observationLog);
        safetyRounds = Lists.copy(safetyRounds);
        rawNotes = Lists.copy(rawNotes);
    }

    /** 원자료 메모가 있는지 (없으면 예전 방식대로 브리핑을 보여 준다). */
    public boolean hasRawNotes() {
        return !rawNotes.isEmpty();
    }

    /** 처방 식별자로 처방을 찾는다. 없으면 null. */
    public Medication findOrder(String orderId) {
        for (Medication order : orders) {
            if (order.orderId() != null && order.orderId().equals(orderId)) {
                return order;
            }
        }
        return null;
    }
}
