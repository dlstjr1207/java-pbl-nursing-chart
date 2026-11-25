package app.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.domain.CheckResult;
import app.domain.CheckStatus;
import app.domain.MedicationRecord;
import app.domain.ModelAnswer;
import app.domain.ObservationEntry;
import app.domain.PatientCase;
import app.domain.RawNote;
import app.domain.SbarReport;
import app.scoring.RawNotesChecker;
import app.scoring.Scorer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 원자료 메모: PC-01 검사, 검사기의 메모 조건, R10 피드백(그대로 옮김·관련 없는 정보, 배점 없음). 실제 AI는 부르지 않는다. */
class RawNotesTest {

    private final PatientCase pc01 = new CaseLoader().load("PC-01");
    private final CaseValidator validator = new CaseValidator(AllowedDrugs.load());

    private static PatientCase withNotes(PatientCase c, List<RawNote> notes) {
        return new PatientCase(c.id(), c.title(), c.difficulty(), c.objectives(), c.reviewStatus(), c.source(), c.patient(),
                c.adr(), c.riskTier(), c.orders(), c.regimenHistory(), c.lithiumLevel(), c.vitals(), c.observationLog(),
                c.safetyRounds(), c.briefing(), c.sbarReference(), c.answerKey(), c.modelAnswer(), notes, c.task());
    }

    private static PatientCase withModel(PatientCase c, ModelAnswer m) {
        return new PatientCase(c.id(), c.title(), c.difficulty(), c.objectives(), c.reviewStatus(), c.source(), c.patient(),
                c.adr(), c.riskTier(), c.orders(), c.regimenHistory(), c.lithiumLevel(), c.vitals(), c.observationLog(),
                c.safetyRounds(), c.briefing(), c.sbarReference(), c.answerKey(), m, c.rawNotes(), c.task());
    }

    private static ObservationEntry obs(ObservationEntry base, String narrative) {
        return new ObservationEntry(base.time(), base.shift(), base.tags(), narrative, base.intervention(), base.vitals());
    }

    private Scorer.Result score(List<ObservationEntry> o) {
        ModelAnswer m = pc01.modelAnswer();
        return Scorer.evaluate(pc01, o, List.of(m.medicationRecord()), m.sbarReport());
    }

    private static List<CheckResult> r10(Scorer.Result r) {
        return r.checks().stream().filter(c -> c.ruleId().equals(RawNotesChecker.RULE)).toList();
    }

    // ---- PC-01 ----

    @Test
    void PC01은_원자료_메모_검사를_모두_통과한다() {
        assertEquals(List.of(), validator.check(pc01));
        assertTrue(pc01.rawNotes().size() >= CaseValidator.MIN_RAW_NOTES);
        assertTrue(pc01.rawNotes().stream().filter(RawNote::irrelevant).count() >= CaseValidator.MIN_IRRELEVANT);
        assertFalse(pc01.task() == null || pc01.task().isBlank());
    }

    @Test
    void PC01_확정_값은_바뀌지_않고_메모에_그대로_있다() {
        String all = RawNotesChecker.allNotes(pc01.rawNotes());
        for (String v : List.of("09:25", "데이룸", "누가 나를 감시하는 것 같아요", "로라제팜 1mg PO", "09:30", "VAS 8→2", "RASS 0",
                "118/76", "118/74", "SpO2 99", "18:30 리튬 300mg", "14일째")) {
            assertTrue(all.contains(v), v);
        }
        // 판단 표현이 메모에 있어, 학생이 그대로 옮기면 기존 R1 피드백에 걸린다
        assertTrue(all.contains("협조적"));
    }

    @Test
    void 모범_기록은_여전히_100점이고_R10_피드백이_없다() {
        ModelAnswer m = pc01.modelAnswer();
        Scorer.Result r = score(List.of(m.observationRecord()));
        assertEquals(100.0, r.score());
        assertEquals(List.of(), r10(r));
    }

    // ---- R10 피드백 ----

    @Test
    void 원자료를_통째로_붙여_넣으면_그대로_옮겼다는_피드백만_준다() {
        ObservationEntry model = pc01.modelAnswer().observationRecord();
        String pasted = RawNotesChecker.allNotes(pc01.rawNotes().stream().filter(n -> !n.irrelevant()).toList());
        Scorer.Result r = score(List.of(obs(model, pasted)));
        List<CheckResult> fb = r10(r);
        assertTrue(fb.stream().anyMatch(c -> c.message().startsWith("원자료를 그대로 옮겼습니다. 필요한 사실만 골라 정리하세요")), fb.toString());
        assertTrue(fb.stream().allMatch(c -> c.status() == CheckStatus.FEEDBACK));
        // 판단 표현('협조적')도 함께 옮겨 와 기존 R1 피드백이 나온다
        assertTrue(r.checks().stream().anyMatch(c -> c.ruleId().equals("R1") && c.message().contains("협조적")));
        // R10은 점수에 들어가지 않는다: R10을 빼고 계산해도 점수가 같다
        List<CheckResult> without = new ArrayList<>(r.checks());
        without.removeIf(c -> c.ruleId().equals("R10"));
        assertEquals(Scorer.score(without).score(), r.score());
        assertFalse(r.ruleScores().containsKey("R10"));
    }

    @Test
    void 메모_한_줄을_길게_그대로_옮겨도_피드백을_준다() {
        ObservationEntry model = pc01.modelAnswer().observationRecord();
        String line = pc01.rawNotes().stream().filter(n -> n.time().equals("07:20")).findFirst().orElseThrow().text();
        Scorer.Result r = score(List.of(obs(model, model.narrative() + " " + line)));
        assertTrue(r10(r).stream().anyMatch(c -> c.message().startsWith("원자료를 그대로")), r10(r).toString());
    }

    @Test
    void 관련_없는_메모_내용을_넣으면_피드백만_주고_점수는_그대로다() {
        ObservationEntry model = pc01.modelAnswer().observationRecord();
        Scorer.Result r = score(List.of(obs(model, model.narrative() + " 13:20 어머니가 전화로 면회 시간을 문의함.")));
        List<CheckResult> fb = r10(r);
        assertEquals(1, fb.size(), fb.toString());
        assertTrue(fb.get(0).message().startsWith("관련 없는 정보가 들어갔습니다"));
        assertTrue(fb.get(0).message().contains("어머니") && fb.get(0).message().contains("면회"));
        assertEquals(100.0, r.score()); // 배점 없음
    }

    @Test
    void 관련_없는_정보는_투약_기록과_SBAR에서도_찾는다() {
        ModelAnswer m = pc01.modelAnswer();
        MedicationRecord med = m.medicationRecord();
        MedicationRecord med2 = new MedicationRecord(med.orderId(), med.drugName(), med.dose(), med.unit(), med.route(),
                med.administeredTime(), med.result(), med.reason() + ", 점심 배식 전", med.orderBasis(), med.oralCheck(),
                med.reassessmentPlan());
        SbarReport s = m.sbarReport();
        SbarReport s2 = new SbarReport(s.situation(), s.background(), s.assessment() + " 원무과 안내함.", s.recommendations());
        List<CheckResult> fb = RawNotesChecker.check(pc01, List.of(m.observationRecord()), List.of(med2), s2);
        assertEquals(List.of("medication[1].reason", "assessment"), fb.stream().map(CheckResult::targetRef).toList());
    }

    @Test
    void 필요한_사실을_골라_자기_문장으로_쓰면_피드백이_없다() {
        ObservationEntry model = pc01.modelAnswer().observationRecord();
        String own = "09:25 데이룸 창가에서 계속 서성이고 손톱을 물어뜯음. \"누가 나를 감시하는 것 같아요… 계속 봐요\"라고 말함. "
                + "조용한 곳으로 함께 이동해 1:1로 이야기를 들음. 09:30 PRN 로라제팜 1mg PO 투여. 10:00 재평가 예정.";
        assertEquals(List.of(), r10(score(List.of(obs(model, own)))));
    }

    @Test
    void 원자료가_없는_예전_케이스는_R10을_내지_않는다() {
        PatientCase old = withNotes(pc01, List.of());
        ModelAnswer m = pc01.modelAnswer();
        String pasted = RawNotesChecker.allNotes(pc01.rawNotes());
        assertEquals(List.of(), RawNotesChecker.check(old, List.of(obs(m.observationRecord(), pasted)),
                List.of(m.medicationRecord()), m.sbarReport()));
    }

    // ---- 검사기: 메모 조건 ----

    private List<String> problems(List<RawNote> notes) {
        return validator.check(withNotes(pc01, notes));
    }

    private static boolean has(List<String> p, String text) {
        return p.stream().anyMatch(x -> x.contains(text));
    }

    @Test
    void 메모가_8개_미만이면_불합격() {
        List<String> p = problems(pc01.rawNotes().subList(0, 7));
        assertTrue(has(p, "8개 이상 필요"), p.toString());
    }

    @Test
    void 관련_없는_메모가_2개_미만이면_불합격() {
        List<RawNote> notes = new ArrayList<>(pc01.rawNotes());
        notes.removeIf(n -> n.irrelevant() && n.cues().contains("원무과"));
        notes.removeIf(n -> n.irrelevant() && n.cues().contains("채혈"));
        notes.add(RawNote.of("15:20", "합성 메모"));
        List<String> p = problems(notes);
        assertTrue(has(p, "관련 없는 메모(irrelevant) 2개 이상"), p.toString());
    }

    @Test
    void 시각이_모두_정렬돼_있으면_불합격() {
        List<RawNote> sorted = pc01.rawNotes().stream()
                .sorted(java.util.Comparator.comparing(RawNote::time)).toList();
        assertTrue(has(problems(sorted), "모두 순서대로 정렬"));
    }

    @Test
    void 정답_키_요소를_메모에서_찾을_수_없으면_불합격() {
        // 장소(데이룸)를 지운다
        List<RawNote> notes = pc01.rawNotes().stream()
                .map(n -> new RawNote(n.time(), n.text().replace("데이룸", "그쪽"), n.irrelevant(), n.cues())).toList();
        List<String> p = problems(notes);
        assertTrue(has(p, "관찰 요소 '장소'"), p.toString());
        // 투약 시각·SBAR R 요소(18:30 리튬)를 지운다
        notes = pc01.rawNotes().stream().filter(n -> !n.time().equals("09:30") && !n.time().equals("15:10")).toList();
        p = problems(notes);
        assertTrue(has(p, "투약 요소 '시각'"), p.toString());
        assertTrue(has(p, "SBAR R 요소"), p.toString());
    }

    @Test
    void 환자_발언은_메모의_따옴표_안에_있어야_한다() {
        List<RawNote> notes = pc01.rawNotes().stream()
                .map(n -> new RawNote(n.time(), n.text().replace("\"", ""), n.irrelevant(), n.cues())).toList();
        assertTrue(has(problems(notes), "'발언 인용'"));
    }

    @Test
    void 관련_없는_메모의_낱말이_관련_메모나_모범_기록에_있으면_불합격() {
        List<RawNote> notes = new ArrayList<>(pc01.rawNotes());
        notes.add(RawNote.of("15:25", "점심 이후 특이 없음"));
        assertTrue(has(problems(notes), "cues '점심'이(가) 관련 있는 메모에도"));
        // 모범 기록에 관련 없는 정보가 들어가면 모범 기록이 R10에 걸리므로 불합격
        ModelAnswer m = pc01.modelAnswer();
        ObservationEntry o = obs(m.observationRecord(), m.observationRecord().narrative() + " 원무과 번호 안내함.");
        ModelAnswer bad = new ModelAnswer(m.observation(), m.medication(), m.sbar(), o, m.medicationRecord(), m.sbarReport());
        List<String> p = validator.check(withModel(pc01, bad));
        assertTrue(has(p, "원자료 정리 점검에 걸림"), p.toString());
    }

    @Test
    void 관련_없는_메모에는_알아볼_낱말이_있어야_한다() {
        List<RawNote> notes = pc01.rawNotes().stream()
                .map(n -> n.irrelevant() ? new RawNote(n.time(), n.text(), true, List.of("없는낱말")) : n).toList();
        assertTrue(has(problems(notes), "'없는낱말'이(가) 그 메모 글에 없음"));
    }

    @Test
    void 생성_케이스의_흔한_알아볼_낱말은_정리한다() {
        List<RawNote> notes = List.of(RawNote.of("17:50", "재평가함. 침상에서 쉬는 중."),
                new RawNote("15:30", "다른 환자 혈당 측정 지원, 침상 정리", true, List.of("혈당", "침상", "없는말")),
                new RawNote("16:00", "침상 정리", true, List.of("침상")));
        List<RawNote> t = CaseGenerator.tidyCues(notes);
        assertEquals(notes.get(0), t.get(0));
        assertEquals(List.of("혈당"), t.get(1).cues()); // 관련 메모에도 있는 '침상', 글에 없는 '없는말'은 뺀다
        assertEquals(List.of("침상"), t.get(2).cues()); // 남는 낱말이 없으면 그대로 두어 검사기가 알린다
        assertTrue(new CaseValidator(AllowedDrugs.load()).check(withNotes(pc01, t)).stream()
                .anyMatch(x -> x.contains("cues '침상'이(가) 관련 있는 메모에도")));
    }
}
