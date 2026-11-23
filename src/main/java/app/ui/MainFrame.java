package app.ui;

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
    private final Map<Screen, JPanel> panels = new EnumMap<>(Screen.class);
    /** 마지막으로 보여 준 화면. 화면이 바뀔 때만 패널을 다시 채운다(입력 중인 내용 보호). */
    private Screen shownScreen;
    private final Map<Screen, JButton> menuButtons = new EnumMap<>(Screen.class);
    /** 왼쪽 메뉴. 로그인 전에는 숨긴다 (polish 10). */
    private JPanel sideMenu;
    private final JLabel screenLabel = new JLabel();
    private final JLabel patientLabel = new JLabel();
    private final JLabel userLabel = new JLabel();
    private final JLabel noticeLabel = new JLabel();
    /** 상단 오른쪽의 안내 배지 (교육용·DEMO·AI·파일 저장 모드). */
    private final JPanel badgePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
    /** 로그아웃 버튼 동작. 기본은 상태만 지우며, 앱 시작 시 AuthService.logout으로 바꾼다. */
    private Runnable logoutAction;
    /** 화면에만 있고 아직 AppState에 없는 작성 중 내용(예: SBAR 입력칸)을 알려 주는 함수들. */
    private final java.util.List<java.util.function.BooleanSupplier> draftChecks = new java.util.ArrayList<>();
    /** 떠나기 확인 대화상자. 테스트에서 바꿀 수 있게 함수로 둔다. 메시지를 받아 '예'면 true. */
    private java.util.function.Predicate<String> confirmLeave = message -> javax.swing.JOptionPane.showConfirmDialog(
            this, message, "작성 중인 기록", javax.swing.JOptionPane.YES_NO_OPTION,
            javax.swing.JOptionPane.WARNING_MESSAGE) == javax.swing.JOptionPane.YES_OPTION;

    public MainFrame(AppState state) {
        super("간호 차팅 연습 프로그램");
        this.state = state;
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setJMenuBar(buildMenuBar());
        add(buildStatusBar(), BorderLayout.NORTH);
        sideMenu = buildSideMenu();
        add(sideMenu, BorderLayout.WEST);
        add(content, BorderLayout.CENTER);
        for (Screen s : Screen.values()) register(s, placeholder(s));
        refresh();
        setSize(new Dimension(1280, 820));
        setMinimumSize(new Dimension(960, 640));
        setLocationRelativeTo(null);
    }

    /** 공통 테마(FlatLaf 라이트 + Theme 색·모양)를 적용한다. 창을 만들기 전에 한 번 호출한다. */
    public static void installTheme() {
        Theme.install();
    }

    /** 로그아웃 버튼의 동작을 정한다. */
    public void setLogoutAction(Runnable action) { this.logoutAction = action; }

    /** 화면에만 있는 작성 중 내용을 확인하는 함수를 추가한다 (예: SbarPanel::hasDraft). */
    public void addDraftCheck(java.util.function.BooleanSupplier check) { draftChecks.add(check); }

    /** 떠나기 확인 방식을 바꾼다 (테스트용). */
    void setConfirmLeave(java.util.function.Predicate<String> confirm) { this.confirmLeave = confirm; }

    /** 실습 중 작성한 기록(관찰·투약·SBAR 입력)이 있는지. */
    boolean hasUnsavedWork() {
        if (state.currentCase() == null) return false;
        if (state.hasStudentRecords()) return true;
        for (java.util.function.BooleanSupplier c : draftChecks) if (c.getAsBoolean()) return true;
        return false;
    }

    /** 작성 중인 기록이 있으면 확인을 받는다. 계속해도 되면 true. */
    private boolean okToLeave(String action) {
        return !hasUnsavedWork() || confirmLeave.test("제출하지 않은 기록이 있습니다. " + action
                + " 작성한 관찰·투약·SBAR 기록이 저장되지 않고 사라집니다. 계속할까요?");
    }

    /** 화면 패널을 등록한다. 같은 화면을 다시 등록하면 교체한다. */
    public void register(Screen screen, JPanel panel) {
        panels.put(screen, panel);
        if (screen == shownScreen) shownScreen = null; // 교체된 화면은 다음 refresh에서 다시 채운다
        Theme.decorate(panel); // 모양만 테마에 맞춘다 (동작은 그대로)
        JScrollPane scroll = new JScrollPane(new Theme.WidthTrackingPanel(panel));
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Theme.BG);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        JScrollPane old = scrolls.put(screen, scroll);
        if (old != null) content.remove(old);
        content.add(scrolls.get(screen), screen.name());
    }

    /** 화면을 전환한다. 이동할 수 없으면 false. */
    public boolean show(Screen screen) {
        if (screen == Screen.HOME && state.current() != Screen.HOME && !okToLeave("개인 메인으로 가면")) return false;
        if (!state.go(screen)) return false;
        refresh();
        return true;
    }

    /** 상태(AppState)에 맞춰 화면·상태줄·메뉴 활성 여부를 갱신한다. */
    public void refresh() {
        Screen now = state.current();
        cards.show(content, now.name());
        if (now != shownScreen) {
            shownScreen = now;
            // 패널이 JScrollPane 안에 있어 componentShown이 오지 않으므로 여기서 다시 채운다.
            if (panels.get(now) instanceof Reloadable r) r.reload();
            JScrollPane sp = scrolls.get(now);
            if (sp != null) javax.swing.SwingUtilities.invokeLater(() -> sp.getVerticalScrollBar().setValue(0)); // 새 화면은 맨 위부터
        }
        sideMenu.setVisible(state.showSideMenu());
        screenLabel.setText(state.screenLabel());
        patientLabel.setText(state.patientLabel());
        userLabel.setText(state.userLabel());
        noticeLabel.setText(String.join("  |  ", state.notices()));
        refreshBadges();
        menuButtons.forEach((s, b) -> {
            b.setEnabled(state.canGo(s));
            boolean selected = s == now;
            b.setBackground(selected ? Theme.NAVY_LIGHT : Theme.CARD);
            b.setForeground(selected ? Theme.NAVY : Theme.TEXT);
            b.setFont(Theme.font(selected ? java.awt.Font.BOLD : java.awt.Font.PLAIN, 13f));
        });
        setTitle("간호 차팅 연습 프로그램 - " + state.current().title());
    }

    /** 상단 남색 헤더: 프로그램 이름, 현재 환자·사용자·화면, 안내 배지. */
    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout(16, 0));
        bar.setBackground(Theme.NAVY);
        bar.setBorder(BorderFactory.createEmptyBorder(10, 18, 10, 18));
        JLabel brand = new JLabel("간호 차팅 연습 EMR");
        brand.setFont(Theme.font(java.awt.Font.BOLD, 17f));
        brand.setForeground(Color.WHITE);
        JLabel sub = new JLabel("정신건강의학과 병동 · 교육용 시뮬레이션");
        sub.setFont(Theme.font(java.awt.Font.PLAIN, 12f));
        sub.setForeground(new Color(0xC7D2FE));
        JPanel left = new JPanel(new GridLayout(2, 1));
        left.setOpaque(false);
        left.add(brand);
        left.add(sub);
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 8));
        center.setOpaque(false);
        for (JLabel l : new JLabel[] {patientLabel, userLabel, screenLabel}) {
            l.setForeground(Color.WHITE);
            l.setFont(Theme.font(java.awt.Font.PLAIN, 13f));
            center.add(l);
        }
        patientLabel.setFont(Theme.font(java.awt.Font.BOLD, 14f));
        badgePanel.setOpaque(false);
        noticeLabel.setVisible(false); // 문구는 배지로 보여 준다 (점검 도구가 읽을 수 있게 남겨 둠)
        bar.add(left, BorderLayout.WEST);
        bar.add(center, BorderLayout.CENTER);
        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.add(badgePanel, BorderLayout.CENTER);
        right.add(noticeLabel, BorderLayout.SOUTH);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    /** 안내 문구를 종류별 색 배지로 다시 그린다. */
    private void refreshBadges() {
        badgePanel.removeAll();
        for (String n : state.notices()) {
            Color fg = Theme.ORANGE;
            Color bg = Theme.ORANGE_BG;
            if (n.equals(AppState.DEMO_NOTICE)) {
                fg = Theme.RED;
                bg = Theme.RED_BG;
            } else if (n.equals(AppState.AI_NOTICE)) {
                fg = Theme.PURPLE;
                bg = Theme.PURPLE_BG;
            } else if (n.equals(AppState.FILE_MODE_NOTICE)) {
                fg = Theme.BLUE;
                bg = Theme.BLUE_BG;
            }
            badgePanel.add(Theme.badge(n, fg, bg));
        }
        badgePanel.revalidate();
        badgePanel.repaint();
    }

    /** 왼쪽 메뉴: 실습 순서대로 화면 이동, 맨 아래 로그아웃. 현재 화면은 남색으로 강조한다. */
    private JPanel buildSideMenu() {
        JPanel menu = new JPanel(new GridLayout(0, 1, 0, 4));
        menu.setBackground(Theme.CARD);
        menu.setBorder(BorderFactory.createEmptyBorder(14, 10, 10, 10));
        JLabel title = new JLabel("간호 EMR 워크스페이스");
        title.setFont(Theme.font(java.awt.Font.BOLD, 12f));
        title.setForeground(Theme.MUTED);
        menu.add(title);
        for (Screen s : PRACTICE_MENU) {
            JButton b = new JButton(s.title());
            b.setHorizontalAlignment(SwingConstants.LEFT);
            b.putClientProperty("JButton.buttonType", "borderless");
            b.setBackground(Theme.CARD);
            b.setFocusable(false);
            b.addActionListener(e -> show(s));
            menuButtons.put(s, b);
            menu.add(b);
        }
        JButton logout = new JButton("로그아웃");
        logout.setHorizontalAlignment(SwingConstants.LEFT);
        logout.putClientProperty("JButton.buttonType", "borderless");
        logout.setForeground(Theme.RED);
        logout.setFocusable(false);
        logout.addActionListener(e -> logout());
        menu.add(logout);
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.setBackground(Theme.CARD);
        wrap.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Theme.LINE));
        wrap.add(menu, BorderLayout.NORTH);
        wrap.setPreferredSize(new Dimension(190, 0));
        return wrap;
    }

    /** 로그아웃한다. 작성 중인 기록이 있으면 먼저 확인한다. 취소하면 false. */
    public boolean logout() {
        if (!okToLeave("로그아웃하면")) return false;
        if (logoutAction != null) logoutAction.run();
        else state.logout();
        refresh();
        return true;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu file = new JMenu("파일");
        // 학생 화면에서 숨긴 근거 번호·검토 상태는 이 창에서 본다 (polish A-1)
        JMenuItem evidence = new JMenuItem("근거·출처 보기");
        evidence.addActionListener(e -> new EvidenceDialog(this, state.currentCase()).setVisible(true));
        file.add(evidence);
        file.addSeparator();
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
