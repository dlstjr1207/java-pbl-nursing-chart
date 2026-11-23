package app.ui;

import app.cases.AllowedDrug;
import app.cases.AllowedDrugs;
import app.cases.CaseLoader;
import app.domain.Medication;
import app.domain.PatientCase;
import app.drugs.DrugInfo;
import app.drugs.DrugInfoCatalog;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

/**
 * "파일 → 근거·출처 보기" 창 (polish A-1). 학생 화면에서 숨긴 근거 번호·검토 상태·[검증필요] 표기를 원문 그대로 보여 준다.
 * 실습 중이 아니면 기본 케이스(PC-01)의 근거를 보여 준다.
 */
public class EvidenceDialog extends JDialog {

    public EvidenceDialog(Frame owner, PatientCase current) {
        super(owner, "근거·출처 보기", false);
        JTextArea area = new JTextArea(text(current));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        area.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JButton close = new JButton("닫기");
        close.addActionListener(e -> dispose());
        JPanel south = new JPanel();
        south.add(close);
        add(new JScrollPane(area), BorderLayout.CENTER);
        add(south, BorderLayout.SOUTH);
        setSize(new Dimension(760, 620));
        setLocationRelativeTo(owner);
    }

    /** 창에 보일 글 (Swing 없이 만들 수 있어 테스트에서 확인한다). */
    static String text(PatientCase current) {
        PatientCase c = current;
        if (c == null) {
            try {
                c = new CaseLoader().load("PC-01");
            } catch (RuntimeException e) {
                c = null;
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("이 프로그램의 환자 데이터는 모두 가상이며 교육용 예시입니다(임상 검수 전).\n")
                .append("자세한 출처와 확인 기록은 저장소의 docs/근거표.md, docs/환자케이스.md에 있습니다.\n\n");
        if (c != null) {
            sb.append("[케이스] ").append(c.id()).append(" · ").append(c.title()).append('\n');
            sb.append("  출처: ").append(c.source()).append('\n');
            if (c.reviewStatus() != null) sb.append("  검토 상태: ").append(c.reviewStatus()).append('\n');
            sb.append("\n[처방별 근거·비고 (원문)]\n");
            for (Medication m : c.orders()) {
                sb.append("  - ").append(m.drugName()).append(' ').append(m.dose()).append(m.unit() == null ? "" : m.unit())
                        .append(" (").append(m.type()).append(") 경로 ").append(m.route()).append(", ").append(m.frequency());
                if (m.note() != null) sb.append(" — ").append(m.note());
                sb.append('\n');
            }
            c.regimenHistory().forEach(r -> sb.append("  - 처방 변경 ").append(r.date()).append(": ")
                    .append(r.description()).append(" (이유: ").append(r.reason()).append(")\n"));
            c.lithiumLevel().forEach(r -> sb.append("  - 리튬 농도 시각 ").append(r.time()).append(", 값 ")
                    .append(r.value()).append(' ').append(r.unit()).append('\n'));
        }
        sb.append("\n[허용 약물·용량 범위 (AI 생성과 검사 기준, cases/allowed-drugs.json)]\n");
        for (AllowedDrug d : AllowedDrugs.load().all()) {
            sb.append("  - ").append(d.name()).append(": ").append(d.evidenceId()).append(" · ").append(d.note()).append('\n');
        }
        DrugInfoCatalog info = DrugInfoCatalog.load();
        sb.append("\n[약 정보 출처] 식품의약품안전처 의약품 제품 허가정보 (공공데이터포털 15095677)")
                .append(info.fetchedAt().isEmpty() ? "" : ", 받은 날 " + info.fetchedAt()).append('\n');
        for (DrugInfo d : info.all()) {
            sb.append("  - ").append(d.drug()).append(": ").append(d.itemName()).append(" (").append(d.company())
                    .append(", 품목기준코드 ").append(d.itemSeq()).append(")\n");
        }
        sb.append("\n표기 안내: E-xx는 근거표 번호, [검증필요]·[확인필요]는 팀 검토가 끝나지 않은 값입니다. "
                + "학생 화면에서는 이 표기를 숨깁니다.\n");
        return sb.toString();
    }
}
