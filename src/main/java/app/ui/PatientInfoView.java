package app.ui;

import app.domain.LabResult;
import app.domain.Medication;
import app.domain.OrderType;
import app.domain.Patient;
import app.domain.PatientCase;
import app.domain.RegimenChange;
import java.util.ArrayList;
import java.util.List;

/**
 * 환자 정보 화면에 보여 줄 내용을 케이스에서 뽑아 만든다 (Swing 비의존, 읽기 전용).
 * 정답 키·모범 기록은 제출 전 비공개이므로 어떤 구역에도 넣지 않는다.
 * 값이 없으면 "-"로 표시한다. 근거 번호·[검증필요]·[확인필요] 같은 개발용 표기는 학생 화면에서 숨기고
 * (StudentText), 원문은 "파일 → 근거·출처 보기" 창에서 본다.
 */
public final class PatientInfoView {
    /** 값이 없을 때 쓰는 표시. */
    public static final String EMPTY = "-";

    /** 화면의 한 구역: 제목과 줄 목록. */
    public record Section(String title, List<String> lines) {
        public Section {
            lines = List.copyOf(lines);
        }
    }

    private PatientInfoView() {
    }

    /** 케이스의 환자 정보를 구역별로 만든다. */
    public static List<Section> sections(PatientCase c) {
        List<Section> list = new ArrayList<>();
        list.add(basic(c.patient()));
        list.add(new Section("진단", List.of(text(c.patient().diagnosis()))));
        list.add(orders(c));
        list.add(allergies(c));
        // 값이 없으면 빈 카드를 만들지 않는다 (polish 9)
        if (!text(c.riskTier()).equals(EMPTY)) list.add(new Section("위험 지표 (참고 정보)", List.of(text(c.riskTier()))));
        list.add(history(c));
        return list;
    }

    private static Section basic(Patient p) {
        List<String> l = new ArrayList<>();
        l.add("이름: " + text(p.name()));
        l.add("나이/성별: " + (p.age() == null ? EMPTY : p.age() + "세") + " / " + text(p.sex()));
        l.add("등록번호: " + text(p.patientNo()));
        l.add("병실: " + text(p.room()));
        l.add("입원 유형: " + text(p.admissionType()));
        l.add("입원일: " + text(p.admissionDate()));
        l.add("입원 일수: " + (p.hospitalDay() == null ? EMPTY : p.hospitalDay() + "일"));
        return new Section("환자 기본 정보", l);
    }

    private static Section orders(PatientCase c) {
        List<String> l = new ArrayList<>();
        for (Medication m : c.orders()) {
            l.add(orderLine(m));
        }
        if (l.isEmpty()) l.add("등록된 처방이 없습니다.");
        return new Section("처방", l);
    }

    /** 처방 1건을 한 줄로: [구분] 약명 용량단위 / 경로 / 횟수 (시각, PRN 적응, 비고). */
    static String orderLine(Medication m) {
        // 실제 EMR 형식 (D38): "[정규] Aripiprazole 10mg 1T PO QD 08:30"
        return "[" + typeLabel(m.type()) + "] " + OrderNotation.format(m);
    }

    private static String typeLabel(OrderType t) {
        if (t == null) return "구분 없음";
        return switch (t) {
            case REGULAR -> "정규";
            case PRN -> "PRN";
            case PAST -> "과거";
        };
    }

    /** 알레르기와 약물 부작용은 구분해서 보여 준다 (C-08). */
    private static Section allergies(PatientCase c) {
        List<String> l = new ArrayList<>();
        l.add("알레르기: " + joinOrNone(c.adr() == null ? List.of() : c.adr().allergies()));
        l.add("약물 부작용 이력: " + joinOrNone(c.adr() == null ? List.of() : c.adr().adverseReactions()));
        return new Section("알레르기·부작용", l);
    }

    private static Section history(PatientCase c) {
        List<String> l = new ArrayList<>();
        for (RegimenChange r : c.regimenHistory()) {
            String reason = text(r.reason());
            l.add("처방 변경 " + text(r.date()) + ": " + StudentText.note(r.description())
                    + (reason.equals(EMPTY) ? "" : " (이유: " + reason + ")"));
        }
        for (LabResult r : c.lithiumLevel()) {
            String time = text(r.time());
            l.add("리튬 농도" + (time.equals(EMPTY) ? "" : " " + time) + ": " + text(r.value()) + (r.unit() == null ? "" : " " + r.unit()));
        }
        if (l.isEmpty()) l.add("기록된 변경 이력·검사 결과가 없습니다.");
        return new Section("처방 변경 이력·검사", l);
    }

    private static String joinOrNone(List<String> items) {
        List<String> clean = items.stream().map(StudentText::message).filter(x -> !x.isBlank()).toList();
        return clean.isEmpty() ? "없음" : String.join(", ", clean);
    }

    /** 값 표시: 비었거나 개발용 표기([확인필요] 등)뿐이면 "-". */
    static String text(String s) {
        return StudentText.value(s);
    }
}
