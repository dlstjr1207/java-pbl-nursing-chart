package app.ui;

import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.ui.FlatLineBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.util.Set;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

/**
 * 화면 공통 테마 (단계 5, docs/design/screens의 Figma 기준).
 * 남색 헤더, 왼쪽 메뉴, 연회색 배경 위의 흰 카드, 위험도 배지, 둥근 버튼을 한곳에서 정한다.
 * 화면 패널은 기존처럼 TitledBorder로 구역을 나누면 decorate()가 흰 카드 모양으로 바꿔 준다.
 */
public final class Theme {
    // ---- 색 (Figma에서 고른 값) ----
    public static final Color NAVY = new Color(0x0F2A6B);
    public static final Color NAVY_DARK = new Color(0x0A1F52);
    public static final Color NAVY_LIGHT = new Color(0xE8EEFB);
    public static final Color BG = new Color(0xF3F5F9);
    public static final Color CARD = Color.WHITE;
    public static final Color LINE = new Color(0xE1E6EF);
    public static final Color TEXT = new Color(0x1F2937);
    public static final Color MUTED = new Color(0x6B7280);
    public static final Color RED = new Color(0xC62828);
    public static final Color RED_BG = new Color(0xFDECEC);
    public static final Color ORANGE = new Color(0xB45309);
    public static final Color ORANGE_BG = new Color(0xFEF3E2);
    public static final Color BLUE = new Color(0x1D4ED8);
    public static final Color BLUE_BG = new Color(0xE7EEFD);
    public static final Color GREEN = new Color(0x15803D);
    public static final Color GREEN_BG = new Color(0xE7F6EC);
    public static final Color PURPLE = new Color(0x5B21B6);
    public static final Color PURPLE_BG = new Color(0xEFEAFD);
    /** 입력칸 배경 (Figma의 연한 회청색). */
    public static final Color INPUT = new Color(0xF7F9FD);

    /** 남색으로 강조하는 주요 동작 버튼의 글자 (저장·제출·로그인 등). */
    private static final Set<String> PRIMARY = Set.of("로그인", "가입하기", "실습 시작", "관찰 기록 저장",
            "투약 기록 저장", "SBAR 제출");

    private Theme() {
    }

    /** FlatLaf 라이트 테마와 공통 모양(둥근 모서리, 배경색, 포커스 색)을 적용한다. 창을 만들기 전에 한 번 호출한다. */
    public static void install() {
        FlatLightLaf.setup();
        UIManager.put("Panel.background", BG);
        UIManager.put("Button.arc", 10);
        UIManager.put("Component.arc", 10);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Component.focusColor", BLUE);
        UIManager.put("Component.accentColor", NAVY);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("TitledBorder.titleColor", NAVY);
        UIManager.put("List.selectionBackground", NAVY_LIGHT);
        UIManager.put("List.selectionForeground", TEXT);
        UIManager.put("OptionPane.background", CARD);
    }

    /** 기본 글꼴을 크기만 바꿔 돌려준다 (한글은 시스템 글꼴이 처리). */
    public static Font font(int style, float size) {
        Font base = UIManager.getFont("Label.font");
        if (base == null) base = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        return base.deriveFont(style, size);
    }

    /** 흰 카드 테두리: 둥근 회색 선 + 남색 굵은 제목. */
    public static Border card(String title) {
        Border line = new FlatLineBorder(new Insets(10, 12, 12, 12), LINE, 1f, 14);
        if (title == null || title.isBlank()) return line;
        TitledBorder t = BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0), title,
                TitledBorder.LEADING, TitledBorder.TOP, font(Font.BOLD, 14f), NAVY);
        return BorderFactory.createCompoundBorder(line, t);
    }

    /** 제목이 있는 흰 카드 패널을 만든다. */
    public static JPanel cardPanel(String title, java.awt.LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(CARD);
        p.setBorder(card(title));
        return p;
    }

    /** 남색 주요 버튼 모양. */
    public static void primary(AbstractButton b) {
        b.setBackground(NAVY);
        b.setForeground(Color.WHITE);
        b.setFont(font(Font.BOLD, 13f));
        b.putClientProperty("JButton.buttonType", null);
    }

    /** 위험도·상태 배지 (둥근 색 상자 안의 짧은 글). */
    public static JLabel badge(String text, Color fg, Color bg) {
        return new Badge(text, fg, bg);
    }

    /**
     * 화면 패널을 테마에 맞게 꾸민다(모양만 바꾸고 동작은 그대로).
     * TitledBorder 구역 → 흰 카드, 카드 안의 패널 → 흰 배경, 주요 버튼 → 남색, 목록·읽기 전용 글 → 흰 배경.
     */
    public static void decorate(Component root) {
        decorate(root, false);
    }

    private static void decorate(Component c, boolean inCard) {
        boolean card = inCard;
        if (c instanceof JComponent jc && jc.getBorder() instanceof TitledBorder tb) {
            jc.setBorder(card(tb.getTitle()));
            jc.setOpaque(true);
            jc.setBackground(CARD);
            card = true;
        } else if (c instanceof JPanel p) {
            if (inCard) p.setBackground(CARD);
        }
        if (c instanceof JScrollPane sp && !(sp.getBorder() instanceof javax.swing.border.CompoundBorder)) {
            // 카드 안의 입력칸·목록이 흰 바탕에 묻히지 않도록 얇은 테두리를 그린다
            Component view = sp.getViewport().getView();
            boolean editable = view instanceof javax.swing.text.JTextComponent t && t.isEditable();
            sp.setBorder(new FlatLineBorder(new Insets(2, 4, 2, 4), editable ? new Color(0xC9D2E3) : LINE, 1f, 8));
            sp.getViewport().setBackground(editable ? INPUT : CARD);
            if (view instanceof JTextArea area && area.isEditable()) {
                // 여러 줄 입력칸은 최소 3줄 높이로 보이게 한다
                int h = Math.max(sp.getPreferredSize().height, area.getFontMetrics(area.getFont()).getHeight() * 3 + 12);
                sp.setPreferredSize(new Dimension(sp.getPreferredSize().width, h));
            }
            if (view instanceof JComponent v) v.setBackground(editable ? INPUT : CARD);
        }
        if (c instanceof javax.swing.JTextField tf && tf.isEditable()) tf.setBackground(INPUT);
        if (c instanceof JTextArea ta && !ta.isEditable()) {
            ta.setBackground(CARD);
            ta.setForeground(TEXT);
        }
        if (c instanceof JList<?> l && !(l.getParent() instanceof javax.swing.JViewport)) l.setBackground(CARD);
        if (c instanceof JButton b && PRIMARY.contains(b.getText())) primary(b);
        if (c instanceof AbstractButton b && !(c instanceof JButton) && card) b.setBackground(CARD);
        if (c instanceof Container k) {
            for (Component child : k.getComponents()) decorate(child, card);
        }
        // 자식의 크기가 정해진 뒤에 줄 높이를 고정한다
        if (c instanceof JPanel p && p.getLayout() instanceof java.awt.GridLayout g && g.getColumns() == 1) {
            stack(p);
        }
    }

    /**
     * 한 열짜리 GridLayout 입력 양식을 위에서 아래로 쌓는 모양으로 바꾼다.
     * GridLayout은 모든 줄을 같은 높이로 늘려 빈칸이 생기므로, 줄마다 원래 높이만 쓰게 한다.
     */
    static void stack(JPanel p) {
        Component[] rows = p.getComponents();
        p.removeAll();
        p.setLayout(new javax.swing.BoxLayout(p, javax.swing.BoxLayout.Y_AXIS));
        for (Component row : rows) {
            if (row instanceof JComponent j) {
                j.setAlignmentX(Component.LEFT_ALIGNMENT);
                j.setMaximumSize(new Dimension(Integer.MAX_VALUE, Math.max(j.getPreferredSize().height, 24)));
            }
            p.add(row);
            p.add(javax.swing.Box.createVerticalStrut(4));
        }
    }

    /**
     * 화면 너비에 맞춰 줄어드는 패널 (가로 스크롤 대신 세로 스크롤만 쓴다).
     * MainFrame이 각 화면을 이 패널로 감싸 JScrollPane에 넣는다.
     */
    public static final class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable {
        public WidthTrackingPanel(Component content) {
            super(new java.awt.BorderLayout());
            setBackground(BG);
            add(content, java.awt.BorderLayout.CENTER);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(java.awt.Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(java.awt.Rectangle r, int o, int d) { return r.height - 32; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            // 내용이 창보다 작으면 창 높이에 맞추고, 크면 세로 스크롤
            return getParent() instanceof javax.swing.JViewport v && v.getHeight() > getPreferredSize().height;
        }
    }

    /** 둥근 배경을 직접 그리는 배지. */
    static final class Badge extends JLabel {
        private final Color bg;

        Badge(String text, Color fg, Color bg) {
            super(text, SwingConstants.CENTER);
            this.bg = bg;
            setForeground(fg);
            setFont(font(Font.BOLD, 12f));
            setBorder(BorderFactory.createEmptyBorder(3, 9, 3, 9));
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredSize() {
            Dimension d = super.getPreferredSize();
            return new Dimension(d.width, Math.max(d.height, 22));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
