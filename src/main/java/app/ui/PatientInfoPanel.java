package app.ui;

import app.domain.PatientCase;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * 환자 정보 화면(읽기 전용). 화면이 보일 때마다 현재 케이스로 내용을 다시 만들고,
 * 관찰·투약·SBAR 화면으로 이동하는 버튼을 둔다. 내용 구성은 PatientInfoView가 맡는다.
 */
public class PatientInfoPanel extends JPanel implements Reloadable {
    private final AppState state;
    private final JPanel body = new JPanel();

    /** onNavigate: 이동할 화면을 알려 주는 콜백(보통 MainFrame::show). */
    public PatientInfoPanel(AppState state, Consumer<Screen> onNavigate) {
        super(new BorderLayout(8, 8));
        this.state = state;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        add(body, BorderLayout.CENTER);
        add(buildNavigation(onNavigate), BorderLayout.SOUTH);
        reload();
    }

    /** 현재 케이스로 내용을 다시 그린다. 위에는 환자 배너(이름·기본 정보·배지), 아래는 구역 카드 두 줄. */
    @Override
    public void reload() {
        body.removeAll();
        PatientCase c = state.currentCase();
        if (c == null) {
            body.add(new JLabel("실습 중인 케이스가 없습니다. 개인 메인에서 실습을 시작하세요."));
        } else {
            body.add(banner(c));
            body.add(javax.swing.Box.createVerticalStrut(10));
            java.util.List<PatientInfoView.Section> sections = PatientInfoView.sections(c);
            JPanel grid = new JPanel(new java.awt.GridLayout(0, 2, 10, 10));
            grid.setOpaque(false);
            for (PatientInfoView.Section s : sections) {
                if (s.title().equals("환자 기본 정보")) continue; // 배너에 있음
                grid.add(sectionPanel(s));
            }
            grid.setAlignmentX(LEFT_ALIGNMENT);
            body.add(grid);
        }
        body.revalidate();
        body.repaint();
    }

    /** Figma 환자 정보 화면의 상단 배너: 이름, 나이/성별/등록번호/병실/입원, 진단·알레르기·케이스 상태 배지. */
    private JPanel banner(PatientCase c) {
        var p = c.patient();
        JPanel b = Theme.cardPanel(null, new BorderLayout(12, 6));
        JLabel name = new JLabel(p.name());
        name.setFont(Theme.font(java.awt.Font.BOLD, 22f));
        name.setForeground(Theme.NAVY);
        String meta = (p.age() == null ? "-" : p.age() + "세") + " / " + (p.sex() == null ? "-" : p.sex())
                + "  ·  " + PatientInfoView.text(p.patientNo()) + "  ·  " + PatientInfoView.text(p.room())
                + "  ·  " + PatientInfoView.text(p.admissionType())
                + (p.hospitalDay() == null ? "" : " " + p.hospitalDay() + "일차")
                + (p.admissionDate() == null ? "" : " (입원 " + p.admissionDate() + ")");
        JLabel info = new JLabel(meta);
        info.setForeground(Theme.MUTED);
        JPanel left = new JPanel(new java.awt.GridLayout(2, 1));
        left.setOpaque(false);
        left.add(name);
        left.add(info);
        JPanel badges = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        badges.setOpaque(false);
        badges.add(Theme.badge("진단: " + PatientInfoView.text(p.diagnosis()), Theme.BLUE, Theme.BLUE_BG));
        java.util.List<String> allergies = c.adr() == null ? java.util.List.of() : real(c.adr().allergies());
        if (!allergies.isEmpty()) {
            badges.add(Theme.badge("알레르기: " + String.join(", ", allergies), Theme.RED, Theme.RED_BG));
        }
        if (c.adr() != null && !real(c.adr().adverseReactions()).isEmpty()) {
            badges.add(Theme.badge("부작용 이력", Theme.ORANGE, Theme.ORANGE_BG));
        }
        if (c.source() == app.domain.CaseSource.AI_GENERATED) {
            badges.add(Theme.badge(AppState.AI_NOTICE, Theme.PURPLE, Theme.PURPLE_BG));
        } else if (c.source() == app.domain.CaseSource.DEMO) {
            badges.add(Theme.badge(AppState.DEMO_NOTICE, Theme.RED, Theme.RED_BG));
        } else if (c.reviewStatus() != null) {
            badges.add(Theme.badge(c.reviewStatus(), Theme.MUTED, Theme.BG));
        }
        b.add(left, BorderLayout.WEST);
        b.add(badges, BorderLayout.CENTER);
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.setMaximumSize(new java.awt.Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
        return b;
    }

    /** "없음"·빈 값을 뺀 실제 항목만. */
    private static java.util.List<String> real(java.util.List<String> items) {
        return items.stream().filter(x -> x != null && !x.isBlank() && !x.trim().equals("없음")).toList();
    }

    private JPanel sectionPanel(PatientInfoView.Section s) {
        JPanel p = Theme.cardPanel(s.title(), new BorderLayout());
        JTextArea area = new JTextArea(String.join("\n", s.lines()));
        area.setEditable(false); // 읽기 전용
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setForeground(Theme.TEXT);
        p.add(area, BorderLayout.CENTER);
        return p;
    }

    private JPanel buildNavigation(Consumer<Screen> onNavigate) {
        JPanel nav = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        for (Screen s : new Screen[] {Screen.OBSERVATION, Screen.MEDICATION, Screen.SBAR}) {
            JButton b = new JButton(s.title() + "(으)로 이동");
            b.addActionListener(e -> onNavigate.accept(s));
            nav.add(b);
        }
        return nav;
    }
}
