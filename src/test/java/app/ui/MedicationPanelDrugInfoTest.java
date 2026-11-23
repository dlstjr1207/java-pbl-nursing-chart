package app.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;

import app.cases.CaseLoader;
import org.junit.jupiter.api.Test;

/** 투약 화면에서 처방을 고르면 저장된 약 정보가 보이는지 (단계 7). 화면은 띄우지 않고 패널만 만든다. */
class MedicationPanelDrugInfoTest {

    @Test
    void PC01_처방을_고르면_그_약의_허가정보가_보인다() {
        AppState state = new AppState();
        state.login("테스터");
        state.startPractice(new CaseLoader().load("PC-01"));
        MedicationPanel panel = new MedicationPanel(state, s -> { });
        // 첫 처방(아리피프라졸)이 선택된 상태
        assertTrue(panel.drugInfoText().contains("아리피졸정10밀리그램"), panel.drugInfoText());
        assertTrue(panel.drugInfoText().contains("[용법용량]"));
    }
}
