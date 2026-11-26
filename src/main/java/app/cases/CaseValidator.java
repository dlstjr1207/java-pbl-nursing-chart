package app.cases;

import app.domain.Medication;
import app.domain.OrderType;
import app.domain.PatientCase;
import app.domain.VitalSign;
import java.util.ArrayList;
import java.util.List;

/**
 * 생성된 케이스를 사용하기 전에 검사한다 (F-14, T-30).
 * 필수 항목·형식(시각 HH:mm, 용량 숫자)·허용 약물·용량 범위·정답 키를 보고, 위반을 원인과 함께 모두 모아 돌려준다.
 * 생성 케이스도 PC-01과 같은 규칙으로 채점하므로 정답 키가 있어야 하고, 정답 키의 약·용량은 처방과 같아야 한다.
 * 허용 약물 목록이 비어 있으면 AI 생성을 쓸 수 없다({@link #generationAvailable()}).
 */
public class CaseValidator {
    /** 기존 관찰 로그 최소 건수 */
    public static final int MIN_LOGS = 2;
    /** 활력징후 최소 측정 횟수 */
    public static final int MIN_VITALS = 2;

    private static final String TIME = "([01]\\d|2[0-3]):[0-5]\\d";

    private final AllowedDrugs allowed;

    public CaseValidator(AllowedDrugs allowed) {
        this.allowed = allowed;
    }

    /** 검사에 쓰는 허용 약물 목록 (생성 요청에 함께 보낸다). */
    public AllowedDrugs allowedDrugs() {
        return allowed;
    }

    /** 허용 약물 목록이 채워져 있어 AI 생성을 쓸 수 있는지 (D26). */
    public boolean generationAvailable() {
        return !allowed.isEmpty();
    }

    /** 문제 목록을 돌려준다. 비어 있으면 통과. */
    public List<String> check(PatientCase c) {
        if (c == null) {
            return List.of("케이스 내용이 비어 있음");
        }
        List<String> p = new ArrayList<>(CaseLoader.validate(c));
        if (blank(c.briefing())) {
            p.add("briefing(상황 브리핑) 누락");
        }
        // 학생이 읽고 기록할 재료가 충분해야 한다 (polish 12): 기존 관찰 로그·활력징후 2건 이상
        if (c.observationLog().size() < MIN_LOGS) {
            p.add("observationLog(기존 관찰 로그) " + MIN_LOGS + "건 이상 필요 (현재 " + c.observationLog().size() + "건)");
        }
        if (c.vitals().size() < MIN_VITALS) {
            p.add("vitals(활력징후) " + MIN_VITALS + "회 이상 필요 (현재 " + c.vitals().size() + "회)");
        }
        for (int i = 0; i < c.orders().size(); i++) {
            Medication m = c.orders().get(i);
            if (m != null && !blank(m.drugName())) {
                checkOrder(p, i, m);
            }
        }
        checkAnswerKey(p, c);
        checkKeyLanguages(p, c);
        checkModelTimes(p, c);
        checkBriefingMatchesKey(p, c);
        checkRawNotes(p, c);
        checkLithium(p, c);
        checkDates(p, c);
        checkSbarStrength(p, c);
        for (int i = 0; i < c.vitals().size(); i++) {
            VitalSign v = c.vitals().get(i);
            if (v == null || v.time() == null || !v.time().matches(TIME)) {
                p.add("vitals[" + i + "].time 형식 오류 (HH:mm)");
            }
            if (v != null) checkVitalRanges(p, i, v);
        }
        return p;
    }

    /**
     * 활력징후가 생리적으로 가능한 범위인지 (생성 케이스 거부 기준, 정상 범위가 아니라 '있을 수 있는 값'의 바깥 경계).
     * 사용자 지정 범위: 수축기 70~200, 이완기 40~130, 맥박 40~160, 호흡 8~40, 체온 34~41, SpO2 80~100.
     */
    static final double[][] VITAL_LIMITS = {{70, 200}, {40, 130}, {40, 160}, {8, 40}, {34, 41}, {80, 100}};
    static final String[] VITAL_NAMES = {"systolic(수축기)", "diastolic(이완기)", "pulse(맥박)", "respiration(호흡)",
            "temperature(체온)", "spo2(SpO2)"};

    private static void checkVitalRanges(List<String> p, int i, VitalSign v) {
        Number[] values = {v.systolic(), v.diastolic(), v.pulse(), v.respiration(), v.temperature(), v.spo2()};
        for (int k = 0; k < values.length; k++) {
            if (values[k] == null) continue; // 값이 비어 있는 것은 형식 검사(CaseLoader)가 본다
            double x = values[k].doubleValue();
            if (x < VITAL_LIMITS[k][0] || x > VITAL_LIMITS[k][1]) {
                p.add("vitals[" + i + "]." + VITAL_NAMES[k] + " " + values[k] + "이(가) 가능한 범위("
                        + fmt(VITAL_LIMITS[k][0]) + "~" + fmt(VITAL_LIMITS[k][1]) + ") 밖");
            }
        }
    }

    /** 원자료 메모 최소 개수 */
    public static final int MIN_RAW_NOTES = 8;
    /** 관련 없는 메모 최소 개수 */
    public static final int MIN_IRRELEVANT = 2;

    /**
     * 원자료 메모 검사. 학생은 메모만 보고 기록하므로, 정답 키의 모든 요소(관찰·투약·SBAR)를
     * 관련 있는 메모 안에서 찾을 수 있어야 한다("필요한 내용은 전부 있다"). 또 메모 8개 이상, 관련 없는 메모 2개 이상,
     * 시각이 완전히 정렬돼 있지 않아야 한다(두서없는 메모). 관련 없는 메모의 낱말(cues)은 그 메모에만 있고
     * 모범 기록에는 없어야 한다(모범 기록이 '관련 없는 정보' 피드백을 받지 않게). 모범 기록이 원자료를 그대로 옮긴 글이어도 안 된다.
     */
    private static void checkRawNotes(List<String> p, PatientCase c) {
        List<app.domain.RawNote> notes = c.rawNotes();
        if (notes.size() < MIN_RAW_NOTES) {
            p.add("rawNotes(원자료 메모) " + MIN_RAW_NOTES + "개 이상 필요 (현재 " + notes.size() + "개)");
        }
        if (notes.isEmpty()) return;
        if (blank(c.task())) p.add("task(과제 안내) 누락");
        List<String> times = new ArrayList<>();
        StringBuilder relevant = new StringBuilder();
        int irrelevantCount = 0;
        for (int i = 0; i < notes.size(); i++) {
            app.domain.RawNote n = notes.get(i);
            if (n == null || blank(n.text())) {
                p.add("rawNotes[" + i + "].text 누락");
                continue;
            }
            if (n.time() == null || !n.time().matches(TIME)) {
                p.add("rawNotes[" + i + "].time 형식 오류 (HH:mm): " + n.time());
            } else {
                times.add(n.time());
            }
            if (n.irrelevant()) {
                irrelevantCount++;
                if (n.cues().isEmpty()) p.add("rawNotes[" + i + "] 관련 없는 메모에 cues(알아볼 낱말)가 없음");
                for (String cue : n.cues()) {
                    if (blank(cue) || !squash(n.text()).contains(squash(cue))) {
                        p.add("rawNotes[" + i + "].cues '" + cue + "'이(가) 그 메모 글에 없음");
                    }
                }
            } else {
                relevant.append(n.time()).append(' ').append(n.text()).append('\n');
            }
        }
        if (irrelevantCount < MIN_IRRELEVANT) {
            p.add("rawNotes 관련 없는 메모(irrelevant) " + MIN_IRRELEVANT + "개 이상 필요 (현재 " + irrelevantCount + "개)");
        }
        boolean sorted = true;
        for (int i = 1; i < times.size(); i++) {
            if (times.get(i).compareTo(times.get(i - 1)) < 0) sorted = false;
        }
        if (sorted && times.size() > 1) p.add("rawNotes 시각이 모두 순서대로 정렬돼 있음 (일부 순서를 섞어야 함)");
        String rel = relevant.toString();
        String relSquashed = squash(rel);
        for (app.domain.RawNote n : notes) {
            if (n == null || !n.irrelevant()) continue;
            for (String cue : n.cues()) {
                if (!blank(cue) && relSquashed.contains(squash(cue))) {
                    p.add("rawNotes cues '" + cue + "'이(가) 관련 있는 메모에도 있음 (관련 없는 메모에만 있는 낱말이어야 함)");
                }
            }
        }
        checkKeyInNotes(p, c, rel);
        checkUnlabeledNotes(p, c);
        checkRawSpeech(p, rel);
        checkIrrelevantInShift(p, c);
        checkMealTimes(p, c);
        checkFlow(p, c);
        checkModelAgainstNotes(p, c);
    }

    /**
     * 날짜 (D36): 근무 날짜(task의 yyyy-MM-dd) = 입원일 + (입원 일수 − 1)이어야 한다(입원한 날이 1일째).
     * 메모에 "입원 N일째/차"가 있으면 N도 입원 일수와 같아야 한다.
     */
    private static void checkDates(List<String> p, PatientCase c) {
        var pt = c.patient();
        java.time.LocalDate shift = CaseGenerator.taskDate(c.task());
        if (pt == null) return;
        if (shift == null) {
            if (c.hasRawNotes()) p.add("task(과제 안내)에 근무 날짜(yyyy-MM-dd)가 없음");
            return;
        }
        java.time.LocalDate admission;
        try {
            admission = java.time.LocalDate.parse(pt.admissionDate() == null ? "" : pt.admissionDate().trim());
        } catch (java.time.format.DateTimeParseException e) {
            p.add("patient.admissionDate(입원일)가 yyyy-MM-dd가 아님: " + pt.admissionDate());
            return;
        }
        if (pt.hospitalDay() == null || pt.hospitalDay() < 1) {
            p.add("patient.hospitalDay(입원 일수) 누락");
            return;
        }
        java.time.LocalDate expected = admission.plusDays(pt.hospitalDay() - 1L);
        if (!expected.equals(shift)) {
            p.add("날짜가 맞지 않음: 입원일 " + admission + " + (입원 " + pt.hospitalDay() + "일째 − 1) = " + expected
                    + "인데 근무 날짜는 " + shift);
        }
        var m = java.util.regex.Pattern.compile("입원\\s*(\\d+)\\s*일\\s*(째|차)").matcher("");
        for (var n : c.rawNotes()) {
            if (n == null || n.text() == null) continue;
            m.reset(n.text());
            while (m.find()) {
                if (Integer.parseInt(m.group(1)) != pt.hospitalDay()) {
                    p.add("메모 '" + m.group() + "'이(가) 입원 일수 " + pt.hospitalDay() + "일째와 다름");
                }
            }
        }
    }

    /** 사건 흐름에 꼭 있어야 하는 메모 종류 (시각 순서대로) */
    static final List<String> FLOW = List.of(app.domain.RawNote.EVENT, app.domain.RawNote.SPEECH,
            app.domain.RawNote.INTERVENTION, app.domain.RawNote.PRN, app.domain.RawNote.REASSESS);
    /** 재평가는 PRN 투약 몇 분 뒤인지 (포함 범위) */
    static final int REASSESS_MIN = 20;
    static final int REASSESS_MAX = 60;

    /**
     * 메모 종류와 사건 흐름 (D36). 모든 메모에 알려진 종류가 있어야 하고, 관련 없음 종류와 irrelevant 표시가 맞아야 한다.
     * 각 종류의 가장 이른 시각이 사건 관찰 ≤ 환자 말 ≤ 간호 중재 ≤ PRN 투약 &lt; 재평가 순서이고,
     * 재평가는 PRN 투약 20~60분 뒤, PRN 투약 메모 시각은 정답 키의 투약 시각과 같아야 한다.
     * 메모 목록의 배열 순서는 섞여 있어도 된다(학생 화면 순서). 실제 시각만 본다.
     */
    private static void checkFlow(List<String> p, PatientCase c) {
        java.util.Map<String, Integer> first = new java.util.HashMap<>();
        for (int i = 0; i < c.rawNotes().size(); i++) {
            app.domain.RawNote n = c.rawNotes().get(i);
            if (n == null) continue;
            String k = n.kind();
            if (k == null || !app.domain.RawNote.KIND_NAMES.containsKey(k)) {
                p.add("rawNotes[" + i + "](" + n.time() + ") 메모 종류(kind)가 없거나 알 수 없음: " + k);
                continue;
            }
            if (k.equals(app.domain.RawNote.IRRELEVANT) != n.irrelevant()) {
                p.add("rawNotes[" + i + "](" + n.time() + ") 종류 " + k + "와 irrelevant 표시가 맞지 않음");
            }
            if (n.time() == null || !n.time().matches(TIME)) continue;
            int t = minutes(n.time());
            first.merge(k, t, Math::min);
        }
        for (String k : FLOW) {
            if (!first.containsKey(k)) p.add("rawNotes에 '" + app.domain.RawNote.KIND_NAMES.get(k) + "'(" + k + ") 메모가 없음");
        }
        for (int i = 1; i < FLOW.size(); i++) {
            Integer a = first.get(FLOW.get(i - 1));
            Integer b = first.get(FLOW.get(i));
            if (a == null || b == null) continue;
            boolean strict = FLOW.get(i).equals(app.domain.RawNote.REASSESS);
            if (strict ? a >= b : a > b) {
                // 고치는 법: 앞 종류 메모를 뒤 종류보다 앞 시각으로 옮긴다 (AI가 같은 실수를 되풀이하지 않게 구체적인 시각을 준다)
                Integer before = i >= 2 ? first.get(FLOW.get(i - 2)) : null;
                int target = strict ? b - 1 : b;
                int suggest = before == null ? target - 3 : Math.max(before, Math.min(target, (before + target + 1) / 2));
                p.add("사건 흐름이 어색함: " + app.domain.RawNote.KIND_NAMES.get(FLOW.get(i - 1)) + "(" + hhmm(a) + ") "
                        + (strict ? "<" : "≤") + " " + app.domain.RawNote.KIND_NAMES.get(FLOW.get(i)) + "(" + hhmm(b) + ")이어야 함"
                        + " (고치는 법: " + FLOW.get(i - 1) + " 메모 time을 " + hhmm(suggest) + "로 바꾸고, 그 메모 글은 "
                        + app.domain.RawNote.KIND_NAMES.get(FLOW.get(i)) + " 전에 한 일로 쓴다)");
            }
        }
        Integer prn = first.get(app.domain.RawNote.PRN);
        Integer re = first.get(app.domain.RawNote.REASSESS);
        if (prn != null && re != null && (re - prn < REASSESS_MIN || re - prn > REASSESS_MAX)) {
            p.add("재평가(" + hhmm(re) + ")는 PRN 투약(" + hhmm(prn) + ") " + REASSESS_MIN + "~" + REASSESS_MAX + "분 뒤여야 함 (현재 "
                    + (re - prn) + "분)");
        }
        if (prn != null && c.answerKey() != null) {
            var time = app.scoring.ElementPattern.parseAll(c.answerKey().medicationElements()).stream()
                    .filter(e -> e.label().equals("시각")).findFirst().orElse(null);
            if (time != null && !time.equalsValue(hhmm(prn))) {
                p.add("PRN 투약 메모 시각(" + hhmm(prn) + ")이 정답 키 투약 시각(" + time.expected() + ")과 다름");
            }
        }
    }

    private static int minutes(String hhmm) {
        return Integer.parseInt(hhmm.substring(0, 2)) * 60 + Integer.parseInt(hhmm.substring(3, 5));
    }

    private static String hhmm(int m) {
        return String.format("%02d:%02d", m / 60, m % 60);
    }

    /**
     * 식사 시간대 (D34, 교육용 병동 일과 가정이며 임상 값이 아니다). 분 단위 [시작, 끝].
     * 아침 06:30~09:00, 점심 11:00~13:30, 저녁 17:00~19:30.
     */
    static final java.util.Map<String, int[]> MEAL_HOURS = mealHours();

    private static java.util.Map<String, int[]> mealHours() {
        java.util.Map<String, int[]> m = new java.util.LinkedHashMap<>();
        m.put("아침", new int[] {6 * 60 + 30, 9 * 60});
        m.put("점심", new int[] {11 * 60, 13 * 60 + 30});
        m.put("저녁", new int[] {17 * 60, 19 * 60 + 30});
        return java.util.Collections.unmodifiableMap(m);
    }

    /** "점심 배식", "아침식사", "저녁 식사 도움"처럼 식사 이름 바로 뒤(2글자 안)에 배식·식사가 오는 표현. 1번 묶음이 식사 이름. */
    static final java.util.regex.Pattern MEAL = java.util.regex.Pattern.compile("(아침|조식|점심|중식|저녁|석식)\\s{0,2}(배식|식사)");

    /** 식사 표현이 든 메모 시각이 그 식사 시간대 안인지. 식사 표현이 없으면 null(검사 안 함), 맞으면 "", 틀리면 식사 이름. */
    static String mealMismatch(String time, String text) {
        if (time == null || !time.matches(TIME) || text == null) return null;
        var m = MEAL.matcher(text);
        if (!m.find()) return null;
        String meal = switch (m.group(1)) {
            case "조식" -> "아침";
            case "중식" -> "점심";
            case "석식" -> "저녁";
            default -> m.group(1);
        };
        int[] w = MEAL_HOURS.get(meal);
        int t = Integer.parseInt(time.substring(0, 2)) * 60 + Integer.parseInt(time.substring(3, 5));
        return t >= w[0] && t <= w[1] ? "" : meal;
    }

    /** 그 시각이 들어가는 식사 이름 (없으면 null) */
    static String mealAt(String time) {
        if (time == null || !time.matches(TIME)) return null;
        int t = Integer.parseInt(time.substring(0, 2)) * 60 + Integer.parseInt(time.substring(3, 5));
        for (var e : MEAL_HOURS.entrySet()) if (t >= e.getValue()[0] && t <= e.getValue()[1]) return e.getKey();
        return null;
    }

    /** 원자료 메모의 식사(배식·식사) 시각이 식사 시간대와 맞아야 한다 (D34, 예: 08:10 점심 배식 거부). */
    private static void checkMealTimes(List<String> p, PatientCase c) {
        for (int i = 0; i < c.rawNotes().size(); i++) {
            app.domain.RawNote n = c.rawNotes().get(i);
            if (n == null) continue;
            String meal = mealMismatch(n.time(), n.text());
            if (meal != null && !meal.isEmpty()) {
                int[] w = MEAL_HOURS.get(meal);
                String fitting = mealAt(n.time());
                p.add("rawNotes[" + i + "](" + n.time() + ") " + meal + " 배식·식사 메모의 시각이 " + meal + " 시간대("
                        + String.format("%02d:%02d~%02d:%02d", w[0] / 60, w[0] % 60, w[1] / 60, w[1] % 60) + ") 밖임"
                        + (fitting != null ? " (고치는 법: 이 시각이면 '" + fitting + " 배식'으로 쓴다)"
                                : " (고치는 법: 이 시각에는 식사 시간대가 없으니 배식·식사가 아닌 다른 관련 없는 일, 예: 비품 정리·다른 환자 채혈 지원으로 바꾼다)"));
            }
        }
    }

    /** 리튬 처방 고정값 (D34): PC-01과 같은 300mg PO 1일 3회 08:30·12:30·18:30. 새 임상 값을 만들지 않기 위해서다. */
    static final String LITHIUM_DOSE = "300";
    static final List<String> LITHIUM_TIMES = List.of("08:30", "12:30", "18:30");
    private static final java.util.regex.Pattern LITHIUM = java.util.regex.Pattern.compile("(?i)리튬|lithium");
    /** 글 속 리튬 용량: "리튬 600mg", "Lithium carbonate (탄산리튬) 300 mg" (숫자를 건너뛰지 않는다) */
    private static final java.util.regex.Pattern LITHIUM_MG =
            java.util.regex.Pattern.compile("(?i)(리튬|lithium)[^\\d\\n]{0,15}(\\d+(?:\\.\\d+)?)\\s*(mg|밀리그램)");

    /** 리튬은 처방도, 메모·브리핑·관찰 로그·모범 기록 글의 용량도 PC-01과 같아야 한다 (D34). */
    private static void checkLithium(List<String> p, PatientCase c) {
        for (int i = 0; i < c.orders().size(); i++) {
            Medication m = c.orders().get(i);
            if (m == null || m.drugName() == null || !LITHIUM.matcher(m.drugName()).find()) continue;
            String dose = app.scoring.ElementPattern.trimZeroDecimal(m.dose());
            List<String> times = m.scheduleTimes() == null ? List.of()
                    : m.scheduleTimes().stream().map(t -> t == null ? "" : t.trim()).sorted().toList();
            boolean ok = m.type() == app.domain.OrderType.REGULAR && LITHIUM_DOSE.equals(dose)
                    && "mg".equalsIgnoreCase(m.unit() == null ? "" : m.unit().trim())
                    && "PO".equalsIgnoreCase(m.route() == null ? "" : m.route().trim())
                    && m.frequency() != null && m.frequency().replaceAll("\\s+", "").contains("1일3회")
                    && LITHIUM_TIMES.equals(times);
            if (!ok) {
                p.add("orders[" + i + "] 리튬은 PC-01과 같은 정규 300mg PO 1일 3회(08:30·12:30·18:30)로만 처방 (현재 "
                        + m.type() + " " + m.dose() + m.unit() + " " + m.route() + " " + m.frequency() + " " + m.scheduleTimes() + ")");
            }
        }
        List<String> texts = new ArrayList<>();
        texts.add(c.briefing());
        for (var n : c.rawNotes()) if (n != null) texts.add(n.text());
        for (var o : c.observationLog()) if (o != null) texts.add(o.narrative());
        var ma = c.modelAnswer();
        if (ma != null) {
            texts.add(ma.observation());
            texts.add(ma.medication());
            texts.add(ma.sbar());
            if (ma.sbarReport() != null) {
                texts.add(ma.sbarReport().background());
                for (var r : ma.sbarReport().recommendations()) if (r != null) texts.add(r.intervention());
            }
        }
        for (String t : texts) {
            if (t == null) continue;
            var mm = LITHIUM_MG.matcher(t);
            while (mm.find()) {
                if (!LITHIUM_DOSE.equals(app.scoring.ElementPattern.trimZeroDecimal(mm.group(2)))) {
                    p.add("리튬 용량 '" + mm.group() + "'이(가) 300mg이 아님 (리튬은 300mg 1일 3회로 고정)");
                }
            }
        }
    }

    /** 근무 시간 (D33): Day 07:00~15:30, Evening 15:00~23:30, Night 23:00~07:30(다음 날). 분 단위 [시작, 끝]. */
    static final java.util.Map<app.domain.Shift, int[]> SHIFT_HOURS = java.util.Map.of(
            app.domain.Shift.DAY, new int[] {7 * 60, 15 * 60 + 30},
            app.domain.Shift.EVENING, new int[] {15 * 60, 23 * 60 + 30},
            app.domain.Shift.NIGHT, new int[] {23 * 60, 7 * 60 + 30});

    /** HH:mm이 그 근무 시간 안인지 (Night는 자정을 넘는다). */
    static boolean inShift(app.domain.Shift shift, String time) {
        if (shift == null || time == null || !time.matches(TIME)) return true;
        int m = Integer.parseInt(time.substring(0, 2)) * 60 + Integer.parseInt(time.substring(3, 5));
        int[] w = SHIFT_HOURS.get(shift);
        return w[0] <= w[1] ? m >= w[0] && m <= w[1] : m >= w[0] || m <= w[1];
    }

    /**
     * 관련 없는 메모의 시각은 케이스 근무(관찰 로그·모범 기록의 근무) 시간 안이어야 한다 (D33).
     * 관찰 로그에 근무가 여럿이면 그중 하나에만 들어가도 된다. 근무를 알 수 없으면 검사하지 않는다.
     */
    private static void checkIrrelevantInShift(List<String> p, PatientCase c) {
        java.util.Set<app.domain.Shift> shifts = caseShifts(c);
        if (shifts.isEmpty()) return;
        for (int i = 0; i < c.rawNotes().size(); i++) {
            app.domain.RawNote n = c.rawNotes().get(i);
            if (n == null || !n.irrelevant() || n.time() == null || !n.time().matches(TIME)) continue;
            boolean ok = shifts.stream().anyMatch(s -> inShift(s, n.time()));
            if (!ok) {
                p.add("rawNotes[" + i + "](" + n.time() + ") 관련 없는 메모의 시각이 근무(" + shiftText(shifts)
                        + ") 시간 밖임");
            }
        }
    }

    /** 케이스 근무: 관찰 로그의 근무들, 없으면 모범 기록 관찰의 근무. */
    static java.util.Set<app.domain.Shift> caseShifts(PatientCase c) {
        java.util.Set<app.domain.Shift> out = java.util.EnumSet.noneOf(app.domain.Shift.class);
        for (var o : c.observationLog()) {
            if (o != null && o.shift() != null) out.add(o.shift());
        }
        if (out.isEmpty() && c.modelAnswer() != null && c.modelAnswer().observationRecord() != null
                && c.modelAnswer().observationRecord().shift() != null) {
            out.add(c.modelAnswer().observationRecord().shift());
        }
        return out;
    }

    private static String shiftText(java.util.Set<app.domain.Shift> shifts) {
        List<String> parts = new ArrayList<>();
        for (app.domain.Shift s : shifts) {
            int[] w = SHIFT_HOURS.get(s);
            parts.add(s + " " + String.format("%02d:%02d~%02d:%02d", w[0] / 60, w[0] % 60, w[1] / 60, w[1] % 60));
        }
        return String.join(", ", parts);
    }

    /**
     * 환자 말이 정리되지 않은 원문인지 (PC-01 수준): 관련 있는 메모의 따옴표 안 환자 말 전체에 끊김(… 또는 ...)이 2번 이상,
     * 한 메모의 환자 말 안에서 같은 낱말 되풀이가 1번 이상 있어야 한다.
     * (환자 말이 든 메모를 2~3개 두라는 것은 생성 지시로만 요구한다. 실제 AI가 한 메모에 몰아 쓰는 일이 잦아 검사로 막으면 생성이 자주 실패한다.)
     */
    private static void checkRawSpeech(List<String> p, String rel) {
        boolean repeated = false;
        for (String line : rel.split("\n")) {
            String said = quoted(line);
            if (!said.isBlank() && hasRepeatedWord(said)) repeated = true;
        }
        String speech = quoted(rel);
        int pauses = 0;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("…|\\.\\.\\.").matcher(speech);
        while (m.find()) pauses++;
        if (pauses < 2) p.add("rawNotes 환자 말이 정리돼 있음: 말 끊김(…)이 2번 이상 필요 (현재 " + pauses + "번)");
        if (!repeated) p.add("rawNotes 환자 말이 정리돼 있음: 같은 낱말을 되풀이하는 말이 필요 (예: \"계속, 계속 봐요\")");
    }

    /** 메모 하나의 환자 말 안에서 같은 낱말(2글자 이상)이 두 번 이상 나오는지. */
    static boolean hasRepeatedWord(String said) {
        java.util.Map<String, Integer> count = new java.util.HashMap<>();
        for (String w : said.split("[^\\p{L}\\p{N}]+")) {
            if (w.length() >= 2 && count.merge(w, 1, Integer::sum) >= 2) return true;
        }
        return false;
    }

    /**
     * irrelevant 표시가 없는 메모는 이번 사건과 이어져야 한다: 정답 키 요소(관찰·투약·SBAR 아무 영역)의 동의어,
     * 케이스 활력징후 혈압 값(예: 118/76), 따옴표 안 환자 말 중 하나라도 있어야 한다. 없으면 표시 없이 섞인 관련 없는 메모로 본다.
     */
    private static void checkUnlabeledNotes(List<String> p, PatientCase c) {
        var key = c.answerKey();
        if (key == null) return;
        List<app.scoring.ElementPattern> all = new ArrayList<>();
        all.addAll(app.scoring.ElementPattern.parseAll(key.observationElements()));
        all.addAll(app.scoring.ElementPattern.parseAll(key.medicationElements()));
        var s = key.sbarElements();
        if (s != null) {
            for (List<String> l : List.of(s.situation(), s.background(), s.assessment(), s.recommendation())) {
                all.addAll(app.scoring.ElementPattern.parseAll(l));
            }
        }
        List<String> bp = new ArrayList<>();
        for (var v : c.vitals()) {
            if (v != null && v.systolic() != null && v.diastolic() != null) bp.add(v.systolic() + "/" + v.diastolic());
        }
        for (int i = 0; i < c.rawNotes().size(); i++) {
            app.domain.RawNote n = c.rawNotes().get(i);
            if (n == null || n.irrelevant() || blank(n.text())) continue;
            String text = n.text();
            // 환자 말(따옴표)이 든 메모는 이번 환자의 사건 메모로 본다
            boolean linked = !quoted(text).isBlank()
                    || all.stream().anyMatch(e -> e.foundIn(text)) || bp.stream().anyMatch(text::contains);
            if (!linked) {
                p.add("rawNotes[" + i + "](" + n.time() + ") 관련 표시인데 정답 키·활력징후와 이어지는 내용이 없음"
                        + " (이번 사건과 관계없는 메모면 \"irrelevant\":true와 cues를 붙임)");
            }
        }
    }

    /** 정답 키의 모든 요소가 관련 있는 메모에 있는지. 발언 인용은 메모의 따옴표 안 환자 말에 있어야 한다. */
    private static void checkKeyInNotes(List<String> p, PatientCase c, String rel) {
        var key = c.answerKey();
        if (key == null) return;
        for (var e : app.scoring.ElementPattern.parseAll(key.observationElements())) {
            String where = e.label().equals("발언 인용") ? quoted(rel) : rel;
            if (!e.foundIn(where)) {
                p.add("answerKey 관찰 요소 '" + e.label() + "'(" + e.example() + ")을(를) 원자료 메모에서 찾을 수 없음"
                        + (e.label().equals("발언 인용") ? " (따옴표 안 환자 말)" : ""));
            }
        }
        for (var e : app.scoring.ElementPattern.parseAll(key.medicationElements())) {
            if (!e.foundIn(rel)) {
                p.add("answerKey 투약 요소 '" + e.label() + "'(" + e.example() + ")을(를) 원자료 메모에서 찾을 수 없음");
            }
        }
        var s = key.sbarElements();
        if (s == null) return;
        String[] areas = {"S", "B", "A", "R"};
        List<List<String>> lists = List.of(s.situation(), s.background(), s.assessment(), s.recommendation());
        for (int k = 0; k < 4; k++) {
            for (var e : app.scoring.ElementPattern.parseAll(lists.get(k))) {
                if (!e.foundIn(rel)) {
                    p.add("answerKey SBAR " + areas[k] + " 요소 '" + e.label() + "'(" + e.example() + ")을(를) 원자료 메모에서 찾을 수 없음"
                            + " (고치는 법: 동의어를 메모에 쓴 글자 그대로 바꾸거나 더한다. 수치는 표기가 달라지기 쉬움, 예: 메모 'VAS 8에서 2로'면 '8에서 2로')");
                }
            }
        }
    }

    /** 모범 기록은 원자료를 그대로 옮기지 않고, 관련 없는 정보도 넣지 않아야 한다 (R10 점검을 모두 통과해야 한다). */
    private static void checkModelAgainstNotes(List<String> p, PatientCase c) {
        var m = c.modelAnswer();
        if (m == null || m.observationRecord() == null) return;
        var fb = app.scoring.RawNotesChecker.check(c, List.of(m.observationRecord()),
                m.medicationRecord() == null ? List.of() : List.of(m.medicationRecord()), m.sbarReport());
        for (var f : fb) {
            if (f.status() == app.domain.CheckStatus.PASS) continue;
            p.add("모범 기록이 원자료 정리 점검에 걸림: " + f.message());
        }
    }

    private static String squash(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * 정답 키와 브리핑이 서로 맞는지: 학생은 브리핑을 보고 쓰므로, 브리핑에 없는 사실을 정답으로 요구하면 안 된다.
     * 투약 정답 시각이 브리핑에 있는지, '장소' 동의어 중 하나가 브리핑에 있는지, '발언 인용' 동의어가 브리핑의 따옴표 안에 있는지 본다.
     */
    private static void checkBriefingMatchesKey(List<String> p, PatientCase c) {
        var key = c.answerKey();
        if (key == null || blank(c.briefing())) return;
        String briefing = c.briefing();
        app.scoring.ElementPattern.parseAll(key.medicationElements()).stream()
                .filter(e -> e.label().equals("시각")).findFirst()
                .ifPresent(t -> {
                    if (!t.foundIn(briefing)) {
                        p.add("answerKey 투약 시각(" + t.expected() + ")이 briefing에 없음");
                    }
                });
        for (var e : app.scoring.ElementPattern.parseAll(key.observationElements())) {
            if (e.label().equals("장소") && !e.foundIn(briefing)) {
                p.add("answerKey '장소'(" + e.example() + ") 동의어가 briefing에 없음");
            }
            if (e.label().equals("발언 인용") && !e.foundIn(quoted(briefing))) {
                p.add("answerKey '발언 인용'(" + e.example() + ") 동의어가 briefing의 따옴표 안 환자 발언에 없음");
            }
        }
    }

    /**
     * 구조화된 모범 기록의 시각이 HH:mm인지. 학생 입력 화면은 HH:mm만 받으므로, 모범 기록도 같은 형식이어야
     * 학생이 그대로 입력해 제출할 수 있다(예: SBAR R 행 시각에 "EVENING" 같은 값 금지).
     */
    private static void checkModelTimes(List<String> p, PatientCase c) {
        var m = c.modelAnswer();
        if (m == null) return;
        checkModelPlainText(p, m);
        if (m.observationRecord() != null && !nz(m.observationRecord().time()).matches(TIME)) {
            p.add("modelAnswer.observationRecord.time 형식 오류 (HH:mm): " + m.observationRecord().time());
        }
        if (m.medicationRecord() != null && !nz(m.medicationRecord().administeredTime()).matches(TIME)) {
            p.add("modelAnswer.medicationRecord.administeredTime 형식 오류 (HH:mm): " + m.medicationRecord().administeredTime());
        }
        if (m.sbarReport() != null) {
            var recs = m.sbarReport().recommendations();
            for (int i = 0; i < recs.size(); i++) {
                String t = recs.get(i) == null ? null : recs.get(i).time();
                if (!nz(t).matches(TIME)) {
                    p.add("modelAnswer.sbarReport.recommendations[" + i + "].time 형식 오류 (HH:mm): " + t);
                }
            }
        }
    }

    /**
     * 모범 기록은 학생에게 보이는 자연스러운 문장이어야 한다. 정답 키 기호(| &)를 그대로 옮겨 적어
     * 채점만 통과시키는 것을 막는다 (예: "로라제팜|Lorazepam&1mg|1 mg&PO|경구 투약").
     */
    private static void checkModelPlainText(List<String> p, app.domain.ModelAnswer m) {
        java.util.Map<String, String> texts = new java.util.LinkedHashMap<>();
        texts.put("modelAnswer.observation", m.observation());
        texts.put("modelAnswer.medication", m.medication());
        texts.put("modelAnswer.sbar", m.sbar());
        if (m.observationRecord() != null) {
            texts.put("modelAnswer.observationRecord.narrative", m.observationRecord().narrative());
            texts.put("modelAnswer.observationRecord.intervention", m.observationRecord().intervention());
        }
        if (m.medicationRecord() != null) {
            var r = m.medicationRecord();
            texts.put("modelAnswer.medicationRecord", String.join(" ", nz(r.drugName()), nz(r.dose()), nz(r.unit()),
                    nz(r.route()), nz(r.reason()), nz(r.orderBasis()), nz(r.reassessmentPlan())));
        }
        if (m.sbarReport() != null) {
            var b = m.sbarReport();
            StringBuilder all = new StringBuilder(nz(b.situation()) + " " + nz(b.background()) + " " + nz(b.assessment()));
            b.recommendations().forEach(r -> all.append(' ').append(r == null ? "" : nz(r.intervention())));
            texts.put("modelAnswer.sbarReport", all.toString());
        }
        texts.forEach((where, t) -> {
            if (t != null && (t.contains("|") || t.contains("&"))) {
                p.add(where + "에 정답 키 기호(| 또는 &)가 있음. 모범 기록은 자연스러운 문장으로 쓴다");
            }
        });
    }

    /** 글에서 따옴표(" " “ ” ' ' ‘ ’) 안의 말만 모은다. */
    static String quoted(String text) {
        StringBuilder sb = new StringBuilder();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[\"“”]([^\"“”]+)[\"“”]|['‘’]([^'‘’]+)['‘’]").matcher(text);
        while (m.find()) sb.append(m.group(1) != null ? m.group(1) : m.group(2)).append(' ');
        return sb.toString();
    }

    /**
     * 학생이 한글로 써도 맞게 채점되도록, 정답 키의 약명은 한글·영어를 모두, 경로는 PO와 경구를 모두 갖게 한다.
     * 투약 정답(medicationElements)의 약명·경로와 관찰 핵심 사실의 '투약' 요소를 본다.
     */
    private static void checkKeyLanguages(List<String> p, PatientCase c) {
        var key = c.answerKey();
        if (key == null) return;
        boolean hasRoute = false;
        for (var e : app.scoring.ElementPattern.parseAll(key.medicationElements())) {
            if (e.groups().isEmpty()) continue;
            if (e.label().equals("약명")) requireBoth(p, "answerKey.medicationElements 약명", e.groups().get(0));
            if (e.label().equals("경로")) {
                hasRoute = true;
                requireRoute(p, "answerKey.medicationElements 경로", e.groups().get(0));
            }
        }
        if (!key.medicationElements().isEmpty() && !hasRoute) {
            p.add("answerKey.medicationElements에 경로(PO|경구)가 없음");
        }
        for (var e : app.scoring.ElementPattern.parseAll(key.observationElements())) {
            if (!e.label().equals("투약") || e.groups().isEmpty()) continue;
            // 견본 형식: 투약=한글약명|영문약명&용량&PO|경구 — 첫 묶음이 약명이다
            requireBoth(p, "answerKey.observationElements 투약 약명", e.groups().get(0));
            for (List<String> group : e.groups()) {
                if (group.stream().anyMatch(a -> a.equalsIgnoreCase("po") || a.equals("경구"))) {
                    requireRoute(p, "answerKey.observationElements 투약 경로", group);
                }
            }
        }
    }

    private static void requireBoth(List<String> p, String where, List<String> alts) {
        boolean ko = alts.stream().anyMatch(CaseValidator::hasHangul);
        boolean en = alts.stream().anyMatch(a -> a.matches("(?i).*[a-z]{3,}.*"));
        if (!ko || !en) p.add(where + "에 한글·영어 약명이 모두 있어야 함 (현재: " + String.join("|", alts) + ")");
    }

    private static void requireRoute(List<String> p, String where, List<String> alts) {
        boolean po = alts.stream().anyMatch(a -> a.equalsIgnoreCase("po"));
        boolean ko = alts.stream().anyMatch(a -> a.equals("경구"));
        if (!po || !ko) p.add(where + "에 PO와 경구가 모두 있어야 함 (예: PO|경구, 현재: " + String.join("|", alts) + ")");
    }

    private static boolean hasHangul(String s) {
        return s != null && s.codePoints().anyMatch(ch -> ch >= 0xAC00 && ch <= 0xD7A3);
    }

    private static String fmt(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    /** 정답 키 필수, 투약 정답(약명·용량·경로)이 현재 처방(정규·PRN) 중 하나와 같은지 확인한다. */
    private static void checkAnswerKey(List<String> p, PatientCase c) {
        var key = c.answerKey();
        if (key == null) {
            p.add("answerKey(정답 키) 누락");
            return;
        }
        if (key.observationElements().isEmpty()) p.add("answerKey.observationElements(관찰 핵심 사실) 누락");
        if (key.medicationElements().isEmpty()) {
            p.add("answerKey.medicationElements(투약 정답 값) 누락");
            return;
        }
        var elements = app.scoring.ElementPattern.parseAll(key.medicationElements());
        var drug = elements.stream().filter(e -> e.label().equals("약명")).findFirst();
        var dose = elements.stream().filter(e -> e.label().equals("용량")).findFirst();
        var route = elements.stream().filter(e -> e.label().equals("경로")).findFirst();
        if (drug.isEmpty() || dose.isEmpty()) {
            p.add("answerKey.medicationElements에 약명·용량이 없음");
            return;
        }
        boolean matched = c.orders().stream().anyMatch(m -> m != null && m.type() != OrderType.PAST
                && drug.get().foundIn(m.drugName()) && dose.get().equalsValue(m.dose())
                && (route.isEmpty() || route.get().equalsValue(m.route())));
        if (!matched) {
            p.add("answerKey 투약 정답(" + drug.get().expected() + " " + dose.get().expected()
                    + ")이 현재 처방(정규·PRN)의 약·용량과 다름");
        }
    }

    /** SBAR 영역별 최소 요소 수 (PC-01 정답 키 구성: S 2, B 3, A 2, R 2) */
    static final int MIN_S = 2, MIN_B = 3, MIN_A = 2, MIN_R = 2;
    private static final java.util.regex.Pattern HHMM = java.util.regex.Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    /**
     * 정답 키의 SBAR 요소가 PC-01만큼 구체적인지 (Windows 시연 검토). 요소가 적으면 "B: 조현병 환자임"처럼 짧은 SBAR로도 만점이 나온다.
     * PC-01 기준: S = 사건 증상 + PRN 투약(약명), B = 진단 + 입원 일수(N일) + 현재 정규 처방(약명),
     * A = 재평가 수치(숫자가 든 요소) 2개 이상, R = 다음 시각 정규 투약(HH:mm과 약명) + 관찰 계획.
     * 모범 기록이 이 요소를 모두 담아야 하는 것은 '모범 기록이 자기 정답 키로 100점' 검사가 함께 막는다.
     */
    void checkSbarStrength(List<String> p, PatientCase c) {
        var key = c.answerKey();
        if (key == null) return;
        var s = key.sbarElements();
        if (s == null) {
            p.add("answerKey.sbarElements 누락 (S·B·A·R 요소를 PC-01처럼 채운다)");
            return;
        }
        count(p, "situation(S)", s.situation(), MIN_S, "사건 증상, PRN 투약 약명");
        count(p, "background(B)", s.background(), MIN_B, "진단, 입원 일수(N일), 현재 정규 처방 약명");
        count(p, "assessment(A)", s.assessment(), MIN_A, "재평가 수치(예: VAS 2, RASS 0), 재평가 결과");
        count(p, "recommendation(R)", s.recommendation(), MIN_R, "다음 시각 정규 투약(HH:mm&약명), 관찰 계획");
        // 약 이름: 처방 약명과 허용 약물 목록의 이름·별칭 (소문자)
        List<String> regular = new ArrayList<>();
        List<String> prn = new ArrayList<>();
        for (Medication m : c.orders()) {
            if (m == null || blank(m.drugName()) || m.type() == OrderType.PAST) continue;
            (m.type() == OrderType.PRN ? prn : regular).addAll(drugNames(m.drugName()));
        }
        if (!prn.isEmpty() && none(s.situation(), prn)) {
            p.add("answerKey SBAR S 요소에 PRN 투약 약명이 없음 (예: \"PRN 투여=prn|로라제팜|lorazepam\")");
        }
        var pt = c.patient();
        if (pt != null && !blank(pt.diagnosis()) && s.background().stream().noneMatch(e -> diagnosisIn(e, pt.diagnosis()))) {
            p.add("answerKey SBAR B 요소에 진단(" + pt.diagnosis() + ")이 없음 (예: \"진단=" + pt.diagnosis().replaceAll("\\(.*\\)", "").strip() + "\")");
        }
        if (pt != null && pt.hospitalDay() != null && none(s.background(), List.of(pt.hospitalDay() + "일"))) {
            p.add("answerKey SBAR B 요소에 입원 일수(" + pt.hospitalDay() + "일)가 없음 (예: \"입원 " + pt.hospitalDay() + "일=" + pt.hospitalDay() + "일\")");
        }
        if (!regular.isEmpty() && none(s.background(), regular)) {
            p.add("answerKey SBAR B 요소에 현재 정규 처방 약명이 없음 (예: \"현재 처방=아리피프라졸|aripiprazole\")");
        }
        if (s.assessment().stream().noneMatch(e -> synonyms(e).matches(".*\\d.*"))) {
            p.add("answerKey SBAR A 요소에 재평가 수치(숫자)가 없음 (예: \"재평가 결과=vas 2|vas 8→2\", \"RASS 0=rass 0\")");
        }
        if (!regular.isEmpty() && s.recommendation().stream().noneMatch(e -> HHMM.matcher(synonyms(e)).find() && hasAny(synonyms(e), regular))) {
            p.add("answerKey SBAR R 요소에 다음 시각 정규 투약(HH:mm과 약명)이 없음 (예: \"18:30 리튬 투약=18:30&리튬|lithium\")");
        }
    }

    private static void count(List<String> p, String area, List<String> list, int min, String hint) {
        if (list.size() < min) {
            p.add("answerKey SBAR " + area + " 요소가 " + list.size() + "개뿐임 (" + min + "개 이상: " + hint + ")");
        }
    }

    /** 요소 문자열의 동의어 부분 (소문자, '=' 뒤) */
    private static String synonyms(String element) {
        if (element == null) return "";
        int i = element.indexOf('=');
        return (i < 0 ? element : element.substring(i + 1)).toLowerCase();
    }

    /** 동의어 글에 약 이름이 있거나, 동의어 낱말(2글자 이상, 시각 제외)이 약 이름 안에 있으면 그 약으로 본다 (예: 리튬 ⊂ Lithium carbonate (탄산리튬)) */
    private static boolean hasAny(String text, List<String> names) {
        if (names.stream().anyMatch(n -> !n.isBlank() && text.contains(n))) return true;
        for (String syn : text.split("[|&]")) {
            String x = syn.strip();
            if (x.length() < 2 || HHMM.matcher(x).matches()) continue;
            if (names.stream().anyMatch(n -> n.contains(x))) return true;
        }
        return false;
    }

    private static boolean none(List<String> elements, List<String> names) {
        return elements.stream().noneMatch(e -> hasAny(synonyms(e), names));
    }

    /** 요소 동의어(2글자 이상) 중 하나가 진단명에 들어 있으면 진단 요소로 본다 (예: '조현정동장애' ⊂ '조현정동장애(양극형)') */
    private static boolean diagnosisIn(String element, String diagnosis) {
        String d = diagnosis.toLowerCase().replaceAll("\\s+", "");
        for (String syn : synonyms(element).split("[|&]")) {
            String x = syn.strip().replaceAll("\\s+", "");
            if (x.length() >= 2 && (d.contains(x) || x.contains(d))) return true;
        }
        return false;
    }

    /** 처방 약명과 허용 약물 목록의 이름·별칭 (소문자, 괄호 앞 이름 포함) */
    private List<String> drugNames(String drugName) {
        List<String> out = new ArrayList<>();
        out.add(drugName.toLowerCase().strip());
        out.add(drugName.replaceAll("\\(.*\\)", "").toLowerCase().strip());
        allowedDrugs().find(drugName).ifPresent(d -> {
            out.add(d.name().toLowerCase());
            d.aliases().forEach(a -> out.add(a.toLowerCase()));
        });
        out.removeIf(String::isBlank);
        return out;
    }

    /** 통과하지 못하면 원인과 함께 {@link CaseLoadException}으로 거부한다. */
    public void validate(PatientCase c) {
        List<String> problems = check(c);
        if (!problems.isEmpty()) {
            throw new CaseLoadException("생성 케이스 검사 실패", problems);
        }
    }

    /** 처방 1건의 허용 약물·용량·형식 검사. */
    private void checkOrder(List<String> p, int i, Medication m) {
        String at = "orders[" + i + "]";
        for (String t : m.scheduleTimes()) {
            if (t == null || !t.matches(TIME)) {
                p.add(at + ".scheduleTimes 형식 오류 (HH:mm): " + t);
            }
        }
        var found = allowed.find(m.drugName());
        if (found.isEmpty()) {
            p.add(at + ".drugName 허용 약물 목록에 없음: " + m.drugName());
            return;
        }
        AllowedDrug d = found.get();
        if (m.type() == OrderType.PAST) {
            return; // 과거 처방은 약 이름만 검사한다 (경로·용량은 현재 처방이 아님)
        }
        if (!blank(d.route()) && !d.route().trim().equalsIgnoreCase(nz(m.route()).trim())) {
            p.add(at + ".route 허용 경로(" + d.route() + ")와 다름: " + nz(m.route()));
        }
        // 용량이 없는 PRN 처방은 용량·단위 검사를 건너뛴다. 과거 처방도 같은 목록으로 본다.
        if (m.type() == OrderType.PRN && blank(m.dose())) {
            return;
        }
        if (!blank(d.unit()) && !d.unit().trim().equalsIgnoreCase(nz(m.unit()).trim())) {
            p.add(at + ".unit 허용 단위(" + d.unit() + ")와 다름: " + nz(m.unit()));
        }
        Double dose = parseDose(m.dose());
        if (dose == null) {
            p.add(at + ".dose 숫자가 아님 또는 누락: " + nz(m.dose()));
            return;
        }
        if (d.minDose() != null && dose < d.minDose()) {
            p.add(at + ".dose " + m.dose() + "이(가) 허용 하한 " + d.minDose() + " 미만");
        }
        if (d.maxDose() != null && dose > d.maxDose()) {
            p.add(at + ".dose " + m.dose() + "이(가) 허용 상한 " + d.maxDose() + " 초과");
        }
        if (m.type() == OrderType.PRN) {
            if (!d.prnDoses().isEmpty() && d.prnDoses().stream().noneMatch(v -> Math.abs(v - dose) < 1e-9)) {
                p.add(at + ".dose PRN 1회 용량 " + m.dose() + "이(가) 허용 값 " + d.prnDoses() + "에 없음");
            }
            if (d.maxDailyDose() != null && dose > d.maxDailyDose()) {
                p.add(at + ".dose PRN 1회 용량 " + m.dose() + "이(가) 1일 상한 " + d.maxDailyDose() + " 초과");
            }
            return;
        }
        Integer times = timesPerDay(m);
        if (times == null) {
            if (d.minDailyDose() != null || d.maxDailyDose() != null || d.timesPerDay() != null) {
                p.add(at + " 1일 투여 횟수를 알 수 없음 (scheduleTimes 또는 frequency '1일 N회' 필요)");
            }
            return;
        }
        if (d.timesPerDay() != null && !d.timesPerDay().equals(times)) {
            p.add(at + " 1일 " + times + "회가 허용 분할 횟수(" + d.timesPerDay() + "회)와 다름");
        }
        double daily = dose * times;
        if (d.minDailyDose() != null && daily < d.minDailyDose()) {
            p.add(at + " 1일 총량 " + daily + "이(가) 허용 하한 " + d.minDailyDose() + " 미만");
        }
        if (d.maxDailyDose() != null && daily > d.maxDailyDose()) {
            p.add(at + " 1일 총량 " + daily + "이(가) 허용 상한 " + d.maxDailyDose() + " 초과");
        }
    }

    /** 정규 처방의 1일 횟수: 투여 시각 수, 없으면 frequency의 '1일 N회'. 알 수 없으면 null. */
    static Integer timesPerDay(Medication m) {
        if (!m.scheduleTimes().isEmpty()) return m.scheduleTimes().size();
        if (m.frequency() == null) return null;
        java.util.regex.Matcher x = java.util.regex.Pattern.compile("1\\s*일\\s*(\\d+)\\s*회").matcher(m.frequency());
        return x.find() ? Integer.valueOf(x.group(1)) : null;
    }

    private static Double parseDose(String s) {
        if (blank(s)) {
            return null;
        }
        try {
            return Double.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
