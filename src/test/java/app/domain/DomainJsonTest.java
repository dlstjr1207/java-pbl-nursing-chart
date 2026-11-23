package app.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T-01: 도메인 모델의 JSON 왕복 검증. 값은 모두 합성 테스트 값이며 임상 값이 아니다. */
class DomainJsonTest {

    /** 모든 항목을 채운 합성 케이스 */
    static PatientCase syntheticCase() {
        VitalSign vs = new VitalSign("VS-T1", "08:00", 111, 71, 71, 17, 36.1, 97);
        Medication regular = new Medication("ORD-T1", OrderType.REGULAR, "테스트약A", "1", "mg", "PO",
                "하루 2회", List.of("08:00", "20:00"), null, "합성 값");
        Medication prn = new Medication("ORD-T2", OrderType.PRN, "테스트약B", "2", "mg", "PO",
                "필요 시", List.of(), "테스트 상황", null);
        ObservationEntry log = new ObservationEntry("07:00", Shift.NIGHT, List.of("행동", "발언"),
                "합성 서술 \"인용\" <태그>", "합성 처치", vs);
        return new PatientCase(
                "TEST-01", "합성 케이스", "기본", List.of("목표1", "목표2"), "DRAFT", CaseSource.FIXED,
                new Patient("테스트환자", 30, "F", "TEST-P01", "000호", "테스트 진단", "[확인필요]", "2026-01-01", 3),
                new Adr(List.of("테스트 알레르기"), List.of("테스트 부작용")),
                "Tier-T",
                List.of(regular, prn),
                List.of(new RegimenChange("2026-01-02", "합성 변경", "합성 이유")),
                List.of(new LabResult("2026-01-03", "0.0", "단위T")),
                List.of(vs),
                List.of(log),
                List.of(new SafetyRound("06:00", "합성 라운딩")),
                "합성 브리핑",
                "합성 SBAR 참고",
                new AnswerKey(List.of("요소1"), List.of("요소2"),
                        new SbarElements(List.of("S1"), List.of("B1"), List.of("A1"), List.of("R1"))),
                new ModelAnswer("모범 관찰", "모범 투약", "모범 SBAR"));
    }

    /** 모든 항목을 채운 합성 실습 기록 */
    static Attempt syntheticAttempt() {
        return new Attempt(
                7L, 3L, "TEST-01", CaseSource.AI_GENERATED, "{\"id\":\"TEST-01\"}",
                LocalDateTime.of(2026, 11, 22, 9, 0), LocalDateTime.of(2026, 11, 22, 9, 15, 30),
                List.of(new ObservationEntry("09:00", Shift.DAY, List.of("행동"), "학생 서술", null,
                        new VitalSign(null, "09:00", 112, 72, 72, 18, 36.2, 98))),
                List.of(new MedicationRecord("ORD-T2", "테스트약B", "2", "mg", "PO", "09:05",
                                MedicationResult.GIVEN, "PRN 사유", "구두 처방", true, "30분 후 재평가"),
                        new MedicationRecord("ORD-T1", "테스트약A", "1", "mg", "PO", "08:00",
                                MedicationResult.REFUSED, "거부 사유", "정규 처방", null, null)),
                new SbarReport("상황", "배경", "사정",
                        List.of(new SbarRecommendation("10:00", "중재1"), new SbarRecommendation("11:00", "중재2"))),
                List.of(new CheckResult("R8", CheckTarget.SBAR, "sbar.situation", CheckStatus.PASS, "입력됨", null),
                        new CheckResult("R6", CheckTarget.MEDICATION, "medication[1].reason", CheckStatus.MISSING,
                                "사유 누락", 0.0)),
                null,
                new AiFeedback("SUCCESS", List.of("강점"), List.of("개선"), "예시 문장"));
    }

    @Test
    void patientCaseRoundTripKeepsAllData() {
        PatientCase original = syntheticCase();
        PatientCase restored = DomainJson.fromJson(DomainJson.toJson(original), PatientCase.class);
        assertEquals(original, restored);
        assertEquals(OrderType.PRN, restored.findOrder("ORD-T2").type());
        assertNull(restored.findOrder("NONE"));
    }

    @Test
    void attemptRoundTripKeepsInputsAndResults() {
        Attempt original = syntheticAttempt();
        Attempt restored = DomainJson.fromJson(DomainJson.toJson(original), Attempt.class);
        assertEquals(original, restored);
        assertEquals(LocalDateTime.of(2026, 11, 22, 9, 15, 30), restored.submittedAt());
        assertTrue(restored.isSubmitted());
        assertNull(restored.score(), "미산출 점수는 null로 유지");
    }

    @Test
    void koreanAndQuotesArePreservedAsIs() {
        String json = DomainJson.toJson(syntheticCase());
        assertTrue(json.contains("테스트환자"), "한글은 이스케이프하지 않고 그대로 저장");
        assertTrue(json.contains("<태그>"), "HTML 문자도 그대로 저장");
        assertTrue(json.contains("\"source\": \"FIXED\""));
    }

    @Test
    void missingListsBecomeEmptyNotNull() {
        PatientCase parsed = DomainJson.fromJson("""
                {"id":"TEST-02","source":"DEMO","orders":[{"orderId":"ORD-X","type":"REGULAR"}]}
                """, PatientCase.class);
        assertSame(CaseSource.DEMO, parsed.source());
        assertTrue(parsed.objectives().isEmpty());
        assertTrue(parsed.vitals().isEmpty());
        assertTrue(parsed.observationLog().isEmpty());
        assertTrue(parsed.orders().get(0).scheduleTimes().isEmpty());
        assertNull(parsed.answerKey());
    }

    @Test
    void unsubmittedAttemptHasNoSubmittedAt() {
        Attempt draft = new Attempt(null, 1L, "TEST-01", CaseSource.DEMO, null,
                LocalDateTime.of(2026, 11, 22, 8, 0), null, null, null, null, null, null, null);
        Attempt restored = DomainJson.fromJson(DomainJson.toJson(draft), Attempt.class);
        assertEquals(draft, restored);
        assertFalse(restored.isSubmitted());
        assertNotNull(restored.observationEntries());
    }

    @Test
    void listsAreDefensiveCopies() {
        List<String> tags = new ArrayList<>(List.of("행동"));
        ObservationEntry entry = new ObservationEntry("09:00", Shift.DAY, tags, "서술", null, null);
        tags.add("추가");
        assertEquals(List.of("행동"), entry.tags());
        assertThrows(UnsupportedOperationException.class, () -> entry.tags().add("x"));
    }
}
