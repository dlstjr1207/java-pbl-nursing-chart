package app.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /** R10에서 걸린 점검(불일치)만 */
    private static List<CheckResult> r10(Scorer.Result r) {
        return r.checks().stream().filter(c -> c.ruleId().equals(RawNotesChecker.RULE) && c.status() != CheckStatus.PASS).toList();
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
    void 모범_기록은_여전히_100점이고_R10_두_점검을_통과한다() {
        ModelAnswer m = pc01.modelAnswer();
        Scorer.Result r = score(List.of(m.observationRecord()));
        assertEquals(100.0, r.score());
        assertEquals(List.of(), r10(r));
        // R10은 점검 2개(그대로 옮김·관련 없는 정보)를 하나씩 센다 (D33)
        assertEquals(List.of(RawNotesChecker.REF_COPIED, RawNotesChecker.REF_IRRELEVANT), r.checks().stream()
                .filter(c -> c.ruleId().equals("R10")).map(CheckResult::targetRef).toList());
        assertEquals(10.0, r.ruleScores().get("R10"));
    }

    @Test
    void 판단_표현_불안해_보임은_R1_피드백만_주고_점수는_그대로다() {
        ObservationEntry model = pc01.modelAnswer().observationRecord();
        Scorer.Result base = score(List.of(model));
        Scorer.Result r = score(List.of(obs(model, model.narrative() + " 불안해 보임.")));
        assertTrue(r.checks().stream().anyMatch(c -> c.ruleId().equals("R1") && c.status() == CheckStatus.FEEDBACK
                && c.message().contains("불안해 보임")), r.checks().toString());
        assertEquals(base.score(), r.score());
        // 모범 기록에는 판단 표현 피드백이 없다
        assertTrue(base.checks().stream().noneMatch(c -> c.status() == CheckStatus.FEEDBACK), base.checks().toString());
    }

    @Test
    void 불안해하며_같은_행동_서술은_판단_표현으로_잡지_않는다() {
        ObservationEntry model = pc01.modelAnswer().observationRecord();
        Scorer.Result r = score(List.of(obs(model, model.narrative() + " 불안해하며 서성임.")));
        assertTrue(r.checks().stream().noneMatch(c -> c.ruleId().equals("R1") && c.status() == CheckStatus.FEEDBACK));
    }

    // ---- R10 원자료 정리 (배점 10, 점검 2개) ----

    @Test
    void 원자료를_통째로_붙여_넣으면_그대로_옮김_점검이_불일치이고_5점이_빠진다() {
        ObservationEntry model = pc01.modelAnswer().observationRecord();
        String pasted = RawNotesChecker.allNotes(pc01.rawNotes().stream().filter(n -> !n.irrelevant()).toList());
        Scorer.Result r = score(List.of(obs(model, pasted)));
        List<CheckResult> fb = r10(r);
        assertEquals(1, fb.size(), fb.toString());
        assertEquals(RawNotesChecker.REF_COPIED, fb.get(0).targetRef());
        assertEquals(CheckStatus.MISMATCH, fb.get(0).status());
        assertTrue(fb.get(0).message().startsWith("원자료를 그대로 옮겼습니다 (관찰 기록 1). 필요한 사실만 골라 정리하세요"), fb.toString());
        // 판단 표현('협조적')도 함께 옮겨 와 기존 R1 피드백이 나온다
        assertTrue(r.checks().stream().anyMatch(c -> c.ruleId().equals("R1") && c.message().contains("협조적")));
        // R10 10점 중 점검 1개(5점)가 빠진다
        assertEquals(5.0, r.ruleScores().get("R10"));
        assertEquals(95.0, r.score());
    }

    @Test
    void 메모_12개를_전부_붙여_넣으면_R10_두_점검에_모두_걸려_10점이_빠진다() {
        ObservationEntry model = pc01.modelAnswer().observationRecord();
        Scorer.Result r = score(List.of(obs(model, RawNotesChecker.allNotes(pc01.rawNotes()))));
        assertEquals(List.of(RawNotesChecker.REF_COPIED, RawNotesChecker.REF_IRRELEVANT),
                r10(r).stream().map(CheckResult::targetRef).toList());
        assertEquals(0.0, r.ruleScores().get("R10"));
        assertEquals(90.0, r.score());
    }

    @Test
    void 메모_한_줄을_길게_그대로_옮겨도_피드백을_준다() {
        ObservationEntry model = pc01.modelAnswer().observationRecord();
        String line = pc01.rawNotes().stream().filter(n -> n.time().equals("07:20")).findFirst().orElseThrow().text();
        Scorer.Result r = score(List.of(obs(model, model.narrative() + " " + line)));
        assertTrue(r10(r).stream().anyMatch(c -> c.message().startsWith("원자료를 그대로")), r10(r).toString());
    }

    @Test
    void 관련_없는_메모_내용을_넣으면_관련_없는_정보_점검이_불일치이고_5점이_빠진다() {
        ObservationEntry model = pc01.modelAnswer().observationRecord();
        Scorer.Result r = score(List.of(obs(model, model.narrative() + " 13:20 어머니가 전화로 면회 시간을 문의함.")));
        List<CheckResult> fb = r10(r);
        assertEquals(1, fb.size(), fb.toString());
        assertEquals(RawNotesChecker.REF_IRRELEVANT, fb.get(0).targetRef());
        assertTrue(fb.get(0).message().startsWith("관련 없는 정보가 들어갔습니다 (관찰 기록 1 '어머니', '면회')"), fb.toString());
        assertEquals(95.0, r.score());
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
        List<CheckResult> fb = RawNotesChecker.check(pc01, List.of(m.observationRecord()), List.of(med2), s2).stream()
                .filter(c -> c.status() != CheckStatus.PASS).toList();
        // 여러 곳에서 걸려도 점검은 하나이고, 어디서 걸렸는지 메시지에 모두 적는다
        assertEquals(1, fb.size(), fb.toString());
        assertTrue(fb.get(0).message().contains("투약 기록 1 '점심'") && fb.get(0).message().contains("SBAR A '원무과'"),
                fb.get(0).message());
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

    @Test
    void 관련_없는_메모의_시각이_근무_시간_밖이면_거부한다() {
        assertFalse(has(problems(pc01.rawNotes()), "근무"), problems(pc01.rawNotes()).toString()); // PC-01은 Day 안
        List<RawNote> notes = new ArrayList<>(pc01.rawNotes());
        int i = indexOfIrrelevant(notes);
        RawNote n = notes.get(i);
        notes.set(i, new RawNote("18:10", n.text(), true, n.cues()));
        assertTrue(has(problems(notes), "(18:10) 관련 없는 메모의 시각이 근무(DAY 07:00~15:30) 시간 밖임"), problems(notes).toString());
        // 경계값(15:30)은 안
        notes.set(i, new RawNote("15:30", n.text(), true, n.cues()));
        assertFalse(has(problems(notes), "시간 밖"));
    }

    @Test
    void 근무_시간은_Night가_자정을_넘는다() {
        assertTrue(CaseValidator.inShift(app.domain.Shift.NIGHT, "23:40"));
        assertTrue(CaseValidator.inShift(app.domain.Shift.NIGHT, "03:00"));
        assertTrue(CaseValidator.inShift(app.domain.Shift.NIGHT, "07:30"));
        assertFalse(CaseValidator.inShift(app.domain.Shift.NIGHT, "12:00"));
        assertTrue(CaseValidator.inShift(app.domain.Shift.EVENING, "15:00"));
        assertFalse(CaseValidator.inShift(app.domain.Shift.EVENING, "14:59"));
        assertFalse(CaseValidator.inShift(app.domain.Shift.DAY, "06:59"));
        assertEquals(java.util.Set.of(app.domain.Shift.DAY), CaseValidator.caseShifts(pc01));
    }

    @Test
    void 배식_식사_메모의_시각이_식사_시간대_밖이면_거부한다() {
        List<RawNote> notes = new ArrayList<>(pc01.rawNotes());
        int i = indexOfIrrelevant(notes);
        RawNote n = notes.get(i);
        notes.set(i, new RawNote("08:10", "점심 배식 준비.", true, List.of("점심", "배식")));
        assertTrue(has(problems(notes), "(08:10) 점심 배식·식사 메모의 시각이 점심 시간대(11:00~13:30) 밖임"), problems(notes).toString());
        notes.set(i, new RawNote("12:10", "점심 배식 준비.", true, List.of("점심", "배식")));
        assertFalse(has(problems(notes), "시간대"));
        // 다른 식사·표기
        assertEquals("저녁", CaseValidator.mealMismatch("15:20", "저녁배식 카트 정리"));
        assertEquals("아침", CaseValidator.mealMismatch("10:00", "조식 식사 보조"));
        assertEquals("", CaseValidator.mealMismatch("07:40", "아침 식사 도움"));
        assertEquals("", CaseValidator.mealMismatch("19:30", "석식 배식"));
        // 식사 이름 + 배식·식사가 아니면 검사하지 않는다 (투약 시각을 말하는 인계 등)
        assertNull(CaseValidator.mealMismatch("15:10", "18:30 리튬 300mg 저녁 투약"));
        assertNull(CaseValidator.mealMismatch("07:20", "Aripiprazole 10mg 아침 1번"));
        assertEquals(List.of("아침", "점심", "저녁"), List.copyOf(CaseValidator.MEAL_HOURS.keySet()));
    }

    @Test
    void 리튬은_PC01과_같은_300mg_1일_3회로만_처방한다() {
        assertTrue(problems(pc01.rawNotes()).stream().noneMatch(x -> x.contains("리튬")), problems(pc01.rawNotes()).toString());
        var li = pc01.orders().stream().filter(o -> o.drugName().contains("Lithium")).findFirst().orElseThrow();
        java.util.function.Function<app.domain.Medication, List<String>> withLi = m -> {
            List<app.domain.Medication> orders = new ArrayList<>(pc01.orders());
            orders.replaceAll(o -> o == li ? m : o);
            return validator.check(new PatientCase(pc01.id(), pc01.title(), pc01.difficulty(), pc01.objectives(),
                    pc01.reviewStatus(), pc01.source(), pc01.patient(), pc01.adr(), pc01.riskTier(), orders,
                    pc01.regimenHistory(), pc01.lithiumLevel(), pc01.vitals(), pc01.observationLog(), pc01.safetyRounds(),
                    pc01.briefing(), pc01.sbarReference(), pc01.answerKey(), pc01.modelAnswer(), pc01.rawNotes(), pc01.task()));
        };
        var p600 = withLi.apply(new app.domain.Medication(li.orderId(), li.type(), "Lithium", "600", "mg", "PO", "1일 3회",
                li.scheduleTimes(), null, null));
        assertTrue(has(p600, "리튬은 PC-01과 같은 정규 300mg PO 1일 3회"), p600.toString());
        var wrongTime = withLi.apply(new app.domain.Medication(li.orderId(), li.type(), "탄산리튬", "300.0", "mg", "PO", "1일 3회",
                List.of("08:00", "12:30", "18:30"), null, null));
        assertTrue(has(wrongTime, "리튬은 PC-01과 같은"), wrongTime.toString());
        var ok = withLi.apply(new app.domain.Medication(li.orderId(), li.type(), "탄산리튬", "300.0", "mg", "PO", "1일 3회",
                List.of("18:30", "08:30", "12:30"), null, null));
        assertFalse(has(ok, "리튬"), ok.toString());
        // 메모 글의 리튬 용량도 300mg이어야 한다
        List<RawNote> notes = new ArrayList<>(pc01.rawNotes());
        notes.add(new RawNote("15:20", "인계: 리튬 600mg 저녁 투약", false, List.of()));
        assertTrue(has(problems(notes), "리튬 용량 '리튬 600mg'이(가) 300mg이 아님"), problems(notes).toString());
    }

    private static int indexOfIrrelevant(List<RawNote> notes) {
        for (int i = 0; i < notes.size(); i++) if (notes.get(i).irrelevant()) return i;
        throw new IllegalStateException();
    }

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
    void PC01은_환자_말_원문과_관련_표시_검사를_통과한다() {
        List<String> p = problems(pc01.rawNotes());
        assertTrue(p.stream().noneMatch(x -> x.contains("환자 말") || x.contains("관련 표시인데")), p.toString());
    }

    @Test
    void 표시_없이_섞인_관련_없는_메모는_불합격() {
        List<RawNote> notes = new ArrayList<>(pc01.rawNotes());
        notes.add(RawNote.of("15:20", "502호 침구 교체 요청 받아 린넨실에 연락"));
        assertTrue(has(problems(notes), "(15:20) 관련 표시인데 정답 키·활력징후와 이어지는 내용이 없음"));
        // 정답 키 낱말이 없어도 환자 말(따옴표)이 든 메모는 사건 메모로 본다
        List<RawNote> speech = new ArrayList<>(pc01.rawNotes());
        speech.add(RawNote.of("09:28", "또 말함 \"자꾸… 자꾸 비웃는 것 같아요\""));
        assertFalse(has(problems(speech), "(09:28) 관련 표시인데"));
    }

    @Test
    void 환자_말을_정리해_쓰면_불합격() {
        // 끊김·되풀이 없이 정리된 말로 바꾼다
        List<RawNote> notes = pc01.rawNotes().stream().map(n -> n.irrelevant() || !n.text().contains("\"") ? n
                : new RawNote(n.time(), n.text().replaceAll("\"[^\"]*\"", "\"누가 나를 감시하는 것 같아요\""), false, List.of()))
                .toList();
        List<String> p = problems(notes);
        assertTrue(has(p, "말 끊김(…)이 2번 이상 필요"), p.toString());
        assertTrue(has(p, "같은 낱말을 되풀이하는 말이 필요"), p.toString());
        // 끊기고 되풀이되는 환자 말이 한 메모에만 있어도 통과 (메모 2~3개는 생성 지시로만 요구)
        List<RawNote> one = pc01.rawNotes().stream().filter(n -> n.irrelevant() || !n.text().contains("\"")
                || n.text().contains("감시")).toList();
        assertFalse(has(problems(one), "환자 말"), problems(one).toString());
    }

    @Test
    void 되풀이_낱말_판정() {
        assertTrue(CaseValidator.hasRepeatedWord("계속, 계속 봐요 계속"));
        assertTrue(CaseValidator.hasRepeatedWord("여기는… 여기는 아무도 없죠?"));
        assertFalse(CaseValidator.hasRepeatedWord("누가 나를 감시하는 것 같아요"));
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
        assertEquals(List.of("정리"), t.get(2).cues()); // 남는 낱말이 없으면 메모 글에서 관련 메모에 없는 낱말을 고른다
        // 그런 낱말도 없으면 그대로 두어 검사기가 알린다
        List<RawNote> none = List.of(RawNote.of("17:50", "재평가함. 침상에서 쉬는 중."),
                new RawNote("16:00", "침상", true, List.of("침상")));
        assertEquals(List.of("침상"), CaseGenerator.tidyCues(none).get(1).cues());
        assertTrue(new CaseValidator(AllowedDrugs.load()).check(withNotes(pc01, CaseGenerator.tidyCues(none))).stream()
                .anyMatch(x -> x.contains("cues '침상'이(가) 관련 있는 메모에도")));
    }

    @Test
    void 사건_장소와_겹치는_알아볼_낱말은_메모_글의_다른_낱말로_바꾼다() {
        // 실제 AI 생성에서 나온 경우: 사건 장소 '면회실'과 관련 없는 메모의 낱말 '면회'가 겹침
        List<RawNote> notes = List.of(RawNote.of("19:30", "면회실 앞. 계속 왔다 갔다 함."),
                new RawNote("16:00", "보호자가 면회 신청 전화함.", true, List.of("면회")));
        List<RawNote> t = CaseGenerator.tidyCues(notes);
        assertEquals(List.of("보호자", "신청"), t.get(1).cues());
    }

    @Test
    void 생성_케이스의_메모가_시각순으로_정렬돼_있으면_일부를_섞는다() {
        List<RawNote> sorted = pc01.rawNotes().stream().sorted(java.util.Comparator.comparing(RawNote::time)).toList();
        List<RawNote> t = CaseGenerator.tidyOrder(sorted);
        assertEquals(new java.util.HashSet<>(sorted), new java.util.HashSet<>(t)); // 메모는 그대로, 순서만
        assertEquals(sorted.size(), t.size());
        assertFalse(has(problems(t), "모두 순서대로 정렬"));
        // 이미 섞여 있으면 그대로
        assertEquals(pc01.rawNotes(), CaseGenerator.tidyOrder(pc01.rawNotes()));
    }
}
