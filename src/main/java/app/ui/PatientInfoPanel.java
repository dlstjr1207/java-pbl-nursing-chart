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

    /**
     * 현재 케이스로 내용을 다시 그린다. 위에는 환자 배너, 그 아래 상황 브리핑(과제), 그 아래 구역 카드를 두 열로 둔다.
     * 카드는 내용 높이만큼만 차지한다(빈 여백 없음, polish 9).
     */
    @Override
    public void reload() {
        body.removeAll();
        PatientCase c = state.currentCase();
        if (c == null) {
            body.add(new JLabel("실습 중인 케이스가 없습니다. 개인 메인에서 실습을 시작하세요."));
        } else {
            body.add(banner(c));
            body.add(javax.swing.Box.createVerticalStrut(10));
            if (c.briefing() != null && !c.briefing().isBlank()) {
                JPanel brief = fitHeight(briefingCard(c.briefing()));
                brief.setAlignmentX(LEFT_ALIGNMENT);
                body.add(brief);
                body.add(javax.swing.Box.createVerticalStrut(10));
            }
            JPanel left = column();
            JPanel right = column();
            int leftWeight = 0;
            int rightWeight = 0;
            for (PatientInfoView.Section s : PatientInfoView.sections(c)) {
                if (s.title().equals("환자 기본 정보")) continue; // 배너에 있음
                int w = weight(s);
                // 두 열의 길이가 비슷해지도록 짧은 쪽에 넣는다
                if (leftWeight <= rightWeight) {
                    add(left, sectionPanel(s));
                    leftWeight += w;
                } else {
                    add(right, sectionPanel(s));
                    rightWeight += w;
                }
            }
            JPanel grid = new JPanel(new java.awt.GridLayout(1, 2, 10, 0));
            grid.setOpaque(false);
            grid.add(top(left));
            grid.add(top(right));
            grid.setAlignmentX(LEFT_ALIGNMENT);
            body.add(grid);
        }
        body.revalidate();
        body.repaint();
    }

    /** 상황 브리핑(과제) 카드. 큰 글씨, 줄바꿈으로 전체가 보이게 한다. 관찰 화면과 같은 카드. */
    static JPanel briefingCard(String text) {
        JPanel p = Theme.cardPanel("상황 브리핑 (과제)", new BorderLayout());
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setFont(Theme.font(java.awt.Font.PLAIN, 15f));
        area.setForeground(Theme.TEXT);
        p.add(area, BorderLayout.CENTER);
        return p;
    }

    /** 세로로 늘어나지 않고 내용 높이만큼만 차지하게 감싼다. */
    private static JPanel fitHeight(JPanel inner) {
        JPanel wrap = new JPanel(new BorderLayout()) {
            @Override
            public java.awt.Dimension getMaximumSize() {
                return new java.awt.Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        wrap.setOpaque(false);
        wrap.add(inner, BorderLayout.CENTER);
        return wrap;
    }

    private static JPanel column() {
        JPanel col = new JPanel();
        col.setLayout(new BoxLayout(col, BoxLayout.Y_AXIS));
        col.setOpaque(false);
        return col;
    }

    private static void add(JPanel col, JPanel card) {
        if (col.getComponentCount() > 0) col.add(javax.swing.Box.createVerticalStrut(10));
        card.setAlignmentX(LEFT_ALIGNMENT);
        col.add(card);
    }

    /** 열을 위쪽에 붙여 카드가 늘어나지 않게 한다. */
    private static JPanel top(JPanel col) {
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setOpaque(false);
        wrap.add(col, BorderLayout.NORTH);
        return wrap;
    }

    /** 대략적인 카드 길이 (줄 수 + 긴 줄의 줄바꿈). */
    private static int weight(PatientInfoView.Section s) {
        int w = 2;
        for (String line : s.lines()) w += 1 + line.length() / 45;
        return w;
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
        }
        // 검토 상태(예: "팀 확정 초안…")는 학생 화면에 두지 않는다. 상단 "교육용 예시 · 임상 검수 전" 배지로 충분하고,
        // 원문은 "파일 → 근거·출처 보기"에서 본다 (polish 1).
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
