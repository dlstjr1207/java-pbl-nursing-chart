package app.ui;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.EnumMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

/**
 * 프로그램의 유일한 JFrame. 상단 상태줄, 왼쪽 메뉴, 가운데 화면(CardLayout)으로 구성한다.
 * 화면은 JPanel을 등록해 Screen 이름으로 전환한다. 이동 가능 여부는 AppState가 판단한다.
 */
public class MainFrame extends JFrame {
    private static final Screen[] PRACTICE_MENU =
            new Screen[] {Screen.HOME, Screen.PATIENT, Screen.OBSERVATION, Screen.MEDICATION, Screen.SBAR};

    private final AppState state;
    private final CardLayout cards = new CardLayout();
    private final JPanel content = new JPanel(cards);
    private final Map<Screen, JScrollPane> scrolls = new EnumMap<>(Screen.class);
    private final Map<Screen, JButton> menuButtons = new EnumMap<>(Screen.class);
    private final JLabel screenLabel = new JLabel();
    private final JLabel patientLabel = new JLabel();
    private final JLabel userLabel = new JLabel();
    private final JLabel noticeLabel = new JLabel();
    /** 로그아웃 버튼 동작. 기본은 상태만 지우며, 앱 시작 시 AuthService.logout으로 바꾼다. */
    private Runnable logoutAction;

    public MainFrame(AppState state) {
        super("간호 차팅 연습 프로그램");
        this.state = state;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setJMenuBar(buildMenuBar());
        add(buildStatusBar(), BorderLayout.NORTH);
        add(buildSideMenu(), BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        for (Screen s : Screen.values()) register(s, placeholder(s));
        refresh();
        setSize(new Dimension(1000, 680));
        setLocationRelativeTo(null);
    }

    /** FlatLaf 라이트 테마를 적용한다. 창을 만들기 전에 한 번 호출한다. */
    public static void installTheme() {
        FlatLightLaf.setup();
    }

    /** 로그아웃 버튼의 동작을 정한다. */
    public void setLogoutAction(Runnable action) { this.logoutAction = action; }

    /** 화면 패널을 등록한다. 같은 화면을 다시 등록하면 교체한다. */
    public void register(Screen screen, JPanel panel) {
        JScrollPane old = scrolls.put(screen, new JScrollPane(panel));
        if (old != null) content.remove(old);
        content.add(scrolls.get(screen), screen.name());
    }

    /** 화면을 전환한다. 이동할 수 없으면 false. */
    public boolean show(Screen screen) {
        if (!state.go(screen)) return false;
        refresh();
        return true;
    }

    /** 상태(AppState)에 맞춰 화면·상태줄·메뉴 활성 여부를 갱신한다. */
    public void refresh() {
        cards.show(content, state.current().name());
        screenLabel.setText(state.screenLabel());
        patientLabel.setText(state.patientLabel());
        userLabel.setText(state.userLabel());
        noticeLabel.setText(String.join("  |  ", state.notices()));
        menuButtons.forEach((s, b) -> b.setEnabled(state.canGo(s)));
        setTitle("간호 차팅 연습 프로그램 - " + state.current().title());
    }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new GridLayout(2, 1));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 4));
        row1.add(userLabel);
        row1.add(patientLabel);
        row1.add(screenLabel);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 2));
        noticeLabel.setForeground(new Color(0xB45309));
        row2.add(noticeLabel);
        bar.add(row1);
        bar.add(row2);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));
        return bar;
    }

    private JPanel buildSideMenu() {
        JPanel menu = new JPanel(new GridLayout(0, 1, 0, 4));
        menu.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        for (Screen s : PRACTICE_MENU) {
            JButton b = new JButton(s.title());
            b.addActionListener(e -> show(s));
            menuButtons.put(s, b);
            menu.add(b);
        }
        JButton logout = new JButton("로그아웃");
        logout.addActionListener(e -> {
            if (logoutAction != null) logoutAction.run();
            else state.logout();
            refresh();
        });
        menu.add(logout);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(menu, BorderLayout.NORTH);
        wrap.setPreferredSize(new Dimension(160, 0));
        return wrap;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("파일");
        JMenuItem exit = new JMenuItem("종료");
        exit.addActionListener(e -> dispose());
        file.add(exit);
        bar.add(file);
        JMenu go = new JMenu("이동");
        for (Screen s : PRACTICE_MENU) {
            JMenuItem item = new JMenuItem(s.title());
            item.addActionListener(e -> show(s));
            go.add(item);
        }
        bar.add(go);
        return bar;
    }

    /** 각 화면 작업(T-16, T-21a, T-22~25)이 실제 패널로 교체하기 전까지 쓰는 자리 표시 패널. */
    private static JPanel placeholder(Screen s) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(new JLabel(s.title() + " (구현 예정)", SwingConstants.CENTER), BorderLayout.CENTER);
        return p;
    }
}
