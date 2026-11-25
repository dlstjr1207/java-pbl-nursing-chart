package app.ui;

import app.domain.Attempt;
import app.domain.CheckResult;
import app.scoring.Scorer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * 실습 결과 화면 (polish B-3·4). SBAR 제출 직후, 또는 개인 메인의 과거 기록을 누르면 열린다.
 * 위에서부터 총점 → 항목별 점수 막대 → 고칠 점(누락 빨강·불일치 주황) → 피드백 → 잘 작성한 내용(기본 접힘)
 * → 내가 제출한 기록 → 모범 기록 → AI 해설. 내용은 저장된 Attempt에서 만들고, 점수·점검은 다시 계산하지 않는다(막대는 제출 당시 점검 결과로 계산).
 */
public class ResultPanel extends JPanel implements Reloadable {
    private final AppState state;
    private final Consumer<Screen> onNavigate;
    private final JPanel body = new JPanel();
    private final JLabel message = new JLabel(" ");
    /** 잘 작성한 내용을 펼쳤는지 (기록을 바꾸면 다시 접는다). */
    private boolean passOpen;
    private Attempt shown;

    public ResultPanel(AppState state, Consumer<Screen> onNavigate) {
        super(new BorderLayout(8, 8));
        this.state = state;
        this.onNavigate = onNavigate;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);
        add(body, BorderLayout.CENTER);
        reload();
    }

    @Override
    public void reload() {
        Attempt a = state.resultAttempt();
        if (a != shown) passOpen = false;
        shown = a;
        body.removeAll();
        message.setText(" ");
        if (a == null) {
            body.add(new JLabel("표시할 실습 기록이 없습니다."));
        } else {
            build(a);
        }
        body.revalidate();
        body.repaint();
    }

    private void build(Attempt a) {
        ResultView.Groups g = ResultView.groups(a.checks());
        addCard(header(a));
        JPanel bars = Theme.cardPanel("항목별 점수", new BorderLayout());
        RuleBarsPanel chart = new RuleBarsPanel();
        chart.setScores(Scorer.WEIGHTS, a.checks().isEmpty() ? java.util.Map.of() : Scorer.score(a.checks()).ruleScores());
        bars.add(chart, BorderLayout.CENTER);
        JLabel r9 = new JLabel("R9 SBAR 구성과 R10 원자료 정리는 배점 없이 피드백만 줍니다.");
        r9.setForeground(Theme.MUTED);
        bars.add(r9, BorderLayout.SOUTH);
        addCard(bars);

        int problems = g.missing().size() + g.mismatch().size();
        JPanel fix = Theme.cardPanel("고칠 점 — 누락·불일치 (" + problems + ")", new BorderLayout());
        JPanel fixBody = stack();
        if (problems == 0) {
            fixBody.add(text("누락·불일치가 없습니다.", Theme.GREEN));
        } else {
            if (!g.missing().isEmpty()) fixBody.add(group("누락 " + g.missing().size() + "건", g.missing(), Theme.RED));
            if (!g.mismatch().isEmpty()) fixBody.add(group("불일치 " + g.mismatch().size() + "건", g.mismatch(), Theme.ORANGE));
        }
        fix.add(fixBody, BorderLayout.CENTER);
        addCard(fix);

        if (!g.feedback().isEmpty()) {
            JPanel fb = Theme.cardPanel("피드백 — 판단 표현·SBAR 구성·원자료 정리 (배점 없음, " + g.feedback().size() + ")", new BorderLayout());
            fb.add(text(ResultView.lines(g.feedback()).strip(), Theme.TEXT), BorderLayout.CENTER);
            addCard(fb);
        }

        JPanel pass = Theme.cardPanel(null, new BorderLayout(0, 6));
        JButton toggle = new JButton((passOpen ? "▾ " : "▸ ") + "잘 작성한 내용 (" + g.passed().size() + ")"
                + (passOpen ? " — 접기" : " — 펼치기"));
        toggle.putClientProperty("JButton.buttonType", "borderless");
        toggle.setHorizontalAlignment(JButton.LEFT);
        toggle.setForeground(Theme.GREEN);
        toggle.setFont(Theme.font(Font.BOLD, 14f));
        toggle.addActionListener(e -> {
            passOpen = !passOpen;
            body.removeAll();
            build(a);
            body.revalidate();
            body.repaint();
        });
        pass.add(toggle, BorderLayout.NORTH);
        JTextArea passText = text(ResultView.lines(g.passed()).strip(), Theme.TEXT);
        passText.setVisible(passOpen);
        pass.add(passText, BorderLayout.CENTER);
        addCard(pass);

        JPanel mine = Theme.cardPanel("내가 제출한 기록", new BorderLayout());
        mine.add(text(ResultView.submittedText(a).strip(), Theme.TEXT), BorderLayout.CENTER);
        addCard(mine);

        JPanel model = Theme.cardPanel("모범 기록 (제출 후 공개, 참고용)", new BorderLayout());
        model.add(text(ResultView.modelAnswerText(a).replaceFirst("^\\[모범 기록\\] \\(제출 후 공개, 참고용\\)\\n", "").strip(),
                Theme.TEXT), BorderLayout.CENTER);
        addCard(model);

        JPanel ai = Theme.cardPanel("AI 해설 (점수에 반영되지 않음)", new BorderLayout());
        ai.add(text(ResultView.aiText(a.aiFeedback()).replaceFirst("^AI 해설 \\(점수에 반영되지 않음\\)\\n", "").strip(),
                Theme.TEXT), BorderLayout.CENTER);
        addCard(ai);
    }

    /** 총점(크게)과 기록 정보, 개인 메인·내보내기 버튼. */
    private JPanel header(Attempt a) {
        JPanel p = Theme.cardPanel(null, new BorderLayout(16, 6));
        JLabel title = new JLabel("실습 결과 · " + ResultView.historyLine(a).replace(" · 점수 " + ResultView.scoreText(a), ""));
        title.setForeground(Theme.MUTED);
        JLabel score = new JLabel(ResultView.scoreText(a));
        score.setFont(Theme.font(Font.BOLD, 44f));
        score.setForeground(Theme.NAVY);
        JLabel outOf = new JLabel("100점 만점");
        outOf.setForeground(Theme.MUTED);
        JPanel left = stack();
        left.add(title);
        left.add(score);
        left.add(outOf);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton home = new JButton("개인 메인으로");
        home.addActionListener(e -> onNavigate.accept(Screen.HOME));
        JButton export = new JButton("이 기록 내보내기");
        export.addActionListener(e -> exportShown());
        buttons.add(export);
        buttons.add(home);
        message.setForeground(Theme.MUTED);
        JPanel right = new JPanel(new BorderLayout());
        right.setOpaque(false);
        right.add(buttons, BorderLayout.NORTH);
        right.add(message, BorderLayout.SOUTH);
        p.add(left, BorderLayout.CENTER);
        p.add(right, BorderLayout.EAST);
        return p;
    }

    /** 보고 있는 기록을 저장 대화상자로 고른 위치에 내보낸다 (T-26). */
    private void exportShown() {
        if (shown == null) return;
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setSelectedFile(new java.io.File(app.export.ResultExporter.suggestedFileName(shown)));
        if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        java.nio.file.Path target = chooser.getSelectedFile().toPath();
        String error = new app.export.ResultExporter().export(shown, target);
        message.setText(error == null ? "저장했습니다: " + target.getFileName() : error);
    }

    private JPanel group(String title, List<CheckResult> list, Color color) {
        JPanel p = stack();
        JLabel t = new JLabel("● " + title);
        t.setForeground(color);
        t.setFont(Theme.font(Font.BOLD, 13f));
        p.add(t);
        p.add(text(ResultView.lines(list).strip(), color));
        p.add(Box.createVerticalStrut(6));
        return p;
    }

    private static JTextArea text(String s, Color color) {
        JTextArea area = new JTextArea(s);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setForeground(color);
        area.setFont(Theme.font(Font.PLAIN, 13f));
        area.setAlignmentX(LEFT_ALIGNMENT);
        return area;
    }

    private static JPanel stack() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        return p;
    }

    private void addCard(JPanel card) {
        if (body.getComponentCount() > 0) body.add(Box.createVerticalStrut(10));
        card.setAlignmentX(LEFT_ALIGNMENT);
        body.add(card);
    }
}
