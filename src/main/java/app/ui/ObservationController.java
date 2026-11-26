package app.ui;

import app.domain.ObservationEntry;
import app.domain.PatientCase;
import app.domain.Shift;
import app.domain.VitalSign;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 활력징후·관찰 화면의 로직(Swing 없음). 입력 검증, 학생 기록 저장, 기존 로그와 학생 기록의
 * 시각순 병합(출처 구분), 활력징후 추이 차트 데이터를 맡는다 (S3, F-03·F-04).
 */
public class ObservationController {
    /** 관찰 분류 태그. 화면 시안의 태그 예시다. */
    public static final List<String> TAGS = List.of("안정 회복", "불안 급증", "PRN 투여", "기타");

    private static final Pattern TIME = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private final AppState state;

    public ObservationController(AppState state) {
        this.state = state;
    }

    /** 입력 검증 오류. 입력 칸 이름(field)별 안내 문구를 가진다. */
    public record Errors(Map<String, String> byField) {
        public boolean isEmpty() { return byField.isEmpty(); }
    }

    /** 저장 결과. 성공이면 errors가 비어 있다. */
    public record SaveResult(boolean saved, Errors errors) {}

    /** 케이스에 원래 있던 기록의 서명 (D38) */
    public static final String PREVIOUS_STAFF = "이전 근무자";

    /** 기록 서명: 케이스 기록은 "이전 근무자", 학생 기록은 로그인한 사용자 이름 (D38, 3교대 기록자 표시) */
    public String signature(TimelineRow row) {
        return row.fromCase() ? PREVIOUS_STAFF : state.signature();
    }

    /** 표의 날짜·시간 칸: 케이스 근무 날짜(과제 안내의 날짜) + 시각. 날짜가 없으면 시각만 */
    public String dateTime(TimelineRow row) {
        return dateTime(state.currentCase(), row.time());
    }

    /** 케이스 근무 날짜 + 시각 (결과 화면에서도 쓴다) */
    public static String dateTime(PatientCase c, String time) {
        java.time.LocalDate d = c == null ? null : app.cases.CaseGenerator.taskDate(c.task());
        String t = time == null ? "" : time;
        return d == null ? t : d + " " + t;
    }

    /**
     * 정신상태 평가를 저장한다 (D38). 모두 비우면 지운다. 의식 수준은 5단계 중 하나여야 한다.
     */
    public SaveResult saveMentalStatus(String consciousness, String appearance, String mood, String thought,
                                       String perception, String orientation) {
        if (state.isReviewing()) return new SaveResult(false, new Errors(Map.of("readonly", AppState.REVIEW_LOCKED)));
        if (state.currentCase() == null) return new SaveResult(false, new Errors(Map.of("case", "실습 중인 케이스가 없습니다.")));
        app.domain.MentalStatus m = new app.domain.MentalStatus(consciousness, appearance, mood, thought, perception, orientation);
        if (m.consciousness() != null && !app.domain.MentalStatus.CONSCIOUSNESS.contains(m.consciousness())) {
            return new SaveResult(false, new Errors(Map.of("consciousness", "의식 수준은 5단계 중에서 고르세요.")));
        }
        state.setMentalStatus(m);
        return new SaveResult(true, new Errors(Map.of()));
    }

    /** 타임라인 한 줄. fromCase가 true면 기존 기록, false면 학생 기록. */
    public record TimelineRow(String time, boolean fromCase, ObservationEntry entry) {
        /** 화면에 보일 한 줄 문구. 출처를 앞에 붙인다. */
        public String text() {
            String source = fromCase ? "[기존]" : "[학생]";
            return source + " " + time + " " + entry.shift() + " " + entry.tags()
                    + " " + entry.narrative();
        }
    }

    /** 차트 한 점. */
    public record ChartPoint(String time, double value, boolean fromCase) {}

    /** 차트에 그릴 수 있는 활력징후 항목. */
    public enum Metric {
        // 세로축 최소 범위와 눈금 간격 (polish 7). 값이 범위를 벗어나면 차트가 범위를 넓힌다. 화면 표시용이며 임상 기준값이 아니다.
        SYSTOLIC("수축기 혈압", VitalSign::systolic, 60, 180, 20),
        DIASTOLIC("이완기 혈압", VitalSign::diastolic, 40, 120, 20),
        PULSE("맥박", VitalSign::pulse, 40, 140, 20),
        RESPIRATION("호흡수", VitalSign::respiration, 8, 30, 5),
        TEMPERATURE("체온", VitalSign::temperature, 35, 40, 1),
        SPO2("SpO2", VitalSign::spo2, 85, 100, 5);

        private final String label;
        private final Function<VitalSign, Number> getter;
        private final double axisMin;
        private final double axisMax;
        private final double tick;

        Metric(String label, Function<VitalSign, Number> getter, double axisMin, double axisMax, double tick) {
            this.label = label;
            this.getter = getter;
            this.axisMin = axisMin;
            this.axisMax = axisMax;
            this.tick = tick;
        }

        public String label() { return label; }

        /** 세로축 최소 범위의 아래쪽 */
        public double axisMin() { return axisMin; }

        /** 세로축 최소 범위의 위쪽 */
        public double axisMax() { return axisMax; }

        /** 눈금 간격 */
        public double tick() { return tick; }

        Number valueOf(VitalSign v) { return getter.apply(v); }
    }

    /**
     * 관찰 입력을 검증하고, 통과하면 학생 기록에 저장한다. 실패하면 저장하지 않으며 입력은 화면에 남긴다.
     * 활력징후는 모두 비어 있으면 없는 것으로 보고, 하나라도 쓰면 시각이 필요하다.
     */
    public SaveResult save(String time, Shift shift, List<String> tags, String narrative,
                           String intervention, Map<Metric, String> vitalTexts) {
        if (state.isReviewing()) return new SaveResult(false, new Errors(Map.of("readonly", AppState.REVIEW_LOCKED)));
        Map<String, String> errors = new LinkedHashMap<>();
        String t = time == null ? "" : time.trim();
        if (t.isEmpty()) errors.put("time", "시각을 입력하세요. (예: 14:30)");
        else if (!TIME.matcher(t).matches()) errors.put("time", "시각은 HH:mm 형식으로 입력하세요. (예: 14:30)");
        if (shift == null) errors.put("shift", "근무를 선택하세요.");
        if (tags == null || tags.isEmpty()) errors.put("tags", "태그를 1개 이상 선택하세요.");
        if (narrative == null || narrative.isBlank()) errors.put("narrative", "서술을 입력하세요.");

        Double[] parsed = new Double[Metric.values().length];
        boolean anyVital = false;
        for (Metric m : Metric.values()) {
            String raw = vitalTexts == null ? null : vitalTexts.get(m);
            if (raw == null || raw.isBlank()) continue;
            anyVital = true;
            try {
                double d = Double.parseDouble(raw.trim());
                if (d <= 0 || (m != Metric.TEMPERATURE && d != Math.rint(d))) throw new NumberFormatException();
                parsed[m.ordinal()] = d;
            } catch (NumberFormatException e) {
                errors.put("vital." + m.name(), m.label() + "은(는) "
                        + (m == Metric.TEMPERATURE ? "양수" : "양의 정수") + "로 입력하세요.");
            }
        }
        if (!errors.isEmpty()) return new SaveResult(false, new Errors(errors));

        VitalSign vitals = null;
        if (anyVital) {
            vitals = new VitalSign(null, t,
                    asInt(parsed[Metric.SYSTOLIC.ordinal()]), asInt(parsed[Metric.DIASTOLIC.ordinal()]),
                    asInt(parsed[Metric.PULSE.ordinal()]), asInt(parsed[Metric.RESPIRATION.ordinal()]),
                    parsed[Metric.TEMPERATURE.ordinal()], asInt(parsed[Metric.SPO2.ordinal()]));
        }
        String care = intervention == null || intervention.isBlank() ? null : intervention;
        // 서술·처치는 입력 그대로 보존한다(앞뒤 공백 포함).
        state.addObservation(new ObservationEntry(t, shift, tags, narrative, care, vitals));
        return new SaveResult(true, new Errors(Map.of()));
    }

    private static Integer asInt(Double d) {
        return d == null ? null : (int) Math.round(d);
    }

    /**
     * 화면에 보일 관찰 기록 목록: 학생 기록만 시각순으로 둔다.
     * 케이스의 기존 관찰 로그(observationLog)는 정리된 문장이라 원자료 메모를 대신 정리해 주는 셈이므로 학생 화면에 보이지 않는다.
     * 데이터는 지우지 않고 AI 해설의 사실 자료로 계속 쓴다(SbarController.explainPrompt).
     */
    public List<TimelineRow> timeline() {
        List<TimelineRow> rows = new ArrayList<>();
        for (ObservationEntry e : state.studentObservations()) rows.add(new TimelineRow(e.time(), false, e));
        rows.sort((a, b) -> a.time().compareTo(b.time())); // 안정 정렬: 같은 시각이면 입력 순서 유지
        return rows;
    }

    /** 해당 항목의 차트 점. 케이스 활력징후와 학생 관찰의 활력징후를 시각순으로 모은다. */
    public List<ChartPoint> chartPoints(Metric metric) {
        List<ChartPoint> points = new ArrayList<>();
        PatientCase c = state.currentCase();
        if (c != null) {
            for (VitalSign v : c.vitals()) addPoint(points, metric, v, true);
        }
        for (ObservationEntry e : state.studentObservations()) {
            if (e.vitals() != null) addPoint(points, metric, e.vitals(), false);
        }
        points.sort((a, b) -> a.time().compareTo(b.time()));
        return points;
    }

    private static void addPoint(List<ChartPoint> points, Metric metric, VitalSign v, boolean fromCase) {
        Number n = metric.valueOf(v);
        if (n != null && v.time() != null) points.add(new ChartPoint(v.time(), n.doubleValue(), fromCase));
    }
}
