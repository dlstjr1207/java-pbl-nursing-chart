package app.ui;

import app.domain.PatientCase;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
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
public class PatientInfoPanel extends JPanel {
    private final AppState state;
    private final JPanel body = new JPanel();

    /** onNavigate: 이동할 화면을 알려 주는 콜백(보통 MainFrame::show). */
    public PatientInfoPanel(AppState state, Consumer<Screen> onNavigate) {
        super(new BorderLayout(8, 8));
        this.state = state;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        add(body, BorderLayout.CENTER);
        add(buildNavigation(onNavigate), BorderLayout.SOUTH);
        // CardLayout으로 화면이 나타날 때마다 현재 케이스로 갱신한다.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                reload();
            }
        });
        reload();
    }

    /** 현재 케이스로 내용을 다시 그린다. 케이스가 없으면 안내만 보여 준다. */
    public void reload() {
        body.removeAll();
        PatientCase c = state.currentCase();
        if (c == null) {
            body.add(new JLabel("실습 중인 케이스가 없습니다. 개인 메인에서 실습을 시작하세요."));
        } else {
            for (PatientInfoView.Section s : PatientInfoView.sections(c)) {
                body.add(sectionPanel(s));
            }
        }
        body.revalidate();
        body.repaint();
    }

    private JPanel sectionPanel(PatientInfoView.Section s) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(s.title()));
        JTextArea area = new JTextArea(String.join("\n", s.lines()));
        area.setEditable(false); // 읽기 전용
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
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
