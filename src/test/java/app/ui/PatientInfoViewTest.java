package app.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.cases.CaseLoader;
import app.domain.PatientCase;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 환자 정보 화면 내용이 케이스 값과 일치하고 정답 정보를 노출하지 않는지 확인한다 (S2). */
class PatientInfoViewTest {
    private final PatientCase demo = new CaseLoader().load("demo");

    private String all(List<PatientInfoView.Section> sections) {
        StringBuilder sb = new StringBuilder();
        for (PatientInfoView.Section s : sections) {
            sb.append(s.title()).append('\n').append(String.join("\n", s.lines())).append('\n');
        }
        return sb.toString();
    }

    @Test
    void 환자정보_진단_알레르기가_케이스_값과_같다() {
        String text = all(PatientInfoView.sections(demo));
        assertTrue(text.contains("이름: 김하늘"));
        assertTrue(text.contains("28세 / F"));
        assertTrue(text.contains("등록번호: SIM-P01"));
        assertTrue(text.contains("병실: 501-1호"));
        assertTrue(text.contains("조현정동장애"));
        assertTrue(text.contains("알레르기: Penicillin"));
        assertTrue(text.contains("약물 부작용 이력: Haloperidol 부작용")); // [확인필요]는 근거 창에서만
    }

    @Test
    void 처방은_케이스의_모든_처방을_구분과_함께_보여_준다() {
        List<PatientInfoView.Section> sections = PatientInfoView.sections(demo);
        PatientInfoView.Section orders = sections.stream()
                .filter(s -> s.title().equals("처방")).findFirst().orElseThrow();
        assertEquals(demo.orders().size(), orders.lines().size());
        assertTrue(orders.lines().get(0).startsWith("[정규] Olanzapine 15mg"));
        assertTrue(orders.lines().get(4).startsWith("[PRN] Lorazepam 0.5mg"));
    }

    @Test
    void 확정되지_않은_값은_학생_화면에서_숨기고_대시로_표시한다() {
        String text = all(PatientInfoView.sections(demo));
        assertTrue(text.contains("입원 유형: " + PatientInfoView.EMPTY)); // [확인필요]는 근거 창에서만 (polish A-1)
        assertFalse(text.contains("[확인필요]"));
        assertTrue(text.contains("입원 일수: " + PatientInfoView.EMPTY));
    }

    @Test
    void 정답과_모범기록_구역은_없다() {
        for (PatientInfoView.Section s : PatientInfoView.sections(demo)) {
            assertFalse(s.title().contains("정답"));
            assertFalse(s.title().contains("모범"));
        }
    }
}
