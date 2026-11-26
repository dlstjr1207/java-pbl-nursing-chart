package app.ui;

import app.domain.SbarRecommendation;
import app.domain.SbarReport;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * SBAR 인계 화면 (S5). S·B·A는 JTextArea, R은 시각·중재 행 목록(JList)으로 입력한다.
 * 검증 실패 시 칸별 안내를 보이고 입력은 유지한다. 성공하면 저장 후 개인 메인으로 이동한다.
 */
public class SbarPanel extends JPanel implements Reloadable {
    private final SbarController controller;
    private final Consumer<Screen> onNavigate;

    private final JTextArea sArea = new JTextArea(3, 40);
    private final JTextArea bArea = new JTextArea(3, 40);
    private final JTextArea aArea = new JTextArea(3, 40);
    private final JLabel sErr = errLabel();
    private final JLabel bErr = errLabel();
    private final JLabel aErr = errLabel();
    private final JLabel rErr = errLabel();
    private final JTextField timeField = new JTextField(6);
    private final JTextField interField = new JTextField(30);
    private final DefaultListModel<SbarRecommendation> rowModel = new DefaultListModel<>();
    private final JList<SbarRecommendation> rowList = new JList<>(rowModel);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton submitButton = new JButton("SBAR 제출");
    /** R 행 입력칸 줄과 제출 버튼 줄 (제출한 기록 보기 중에는 숨긴다) */
    private JPanel rowInput;
    private JPanel buttonRow;
    /** 입력칸이 속한 실습(시작 시각). 새 실습이 시작되면 칸을 비운다. */
    private Object practiceKey;

    /** @param onNavigate 화면 이동(제출 성공 시 개인 메인) 요청 함수 */
    public SbarPanel(SbarController controller, Consumer<Screen> onNavigate) {
        super(new BorderLayout(8, 8));
        this.controller = controller;
        this.onNavigate = onNavigate;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel form = new JPanel(new GridLayout(0, 1, 4, 4));
        form.add(section("S 상황", sArea, sErr));
        form.add(section("B 배경", bArea, bErr));
        form.add(section("A 평가", aArea, aErr));
        form.add(buildRecommendation());

        add(new JScrollPane(form), BorderLayout.CENTER);
        JPanel south = new JPanel(new BorderLayout());
        south.add(statusLabel, BorderLayout.NORTH);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonRow = buttons;
        submitButton.addActionListener(e -> submit());
        buttons.add(submitButton);
        for (Screen s : new Screen[] {Screen.PATIENT, Screen.OBSERVATION, Screen.MEDICATION}) {
            JButton b = new JButton(s.title() + "(으)로 이동");
            b.addActionListener(e -> onNavigate.accept(s));
            buttons.add(b);
        }
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
    }

    private static JLabel errLabel() {
        JLabel l = new JLabel(" ");
        l.setForeground(new Color(0xC62828));
        return l;
    }

    private static JPanel section(String title, JTextArea area, JLabel err) {
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder(title));
        p.add(new JScrollPane(area), BorderLayout.CENTER);
        p.add(err, BorderLayout.SOUTH);
        return p;
    }

    private JPanel buildRecommendation() {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(BorderFactory.createTitledBorder("R 권고 (시각·중재)"));
        rowList.setCellRenderer((l, v, i, sel, foc) ->
                new JLabel((i + 1) + ". " + v.time() + "  " + v.intervention()));
        p.add(new JScrollPane(rowList), BorderLayout.CENTER);
        JPanel input = new JPanel(new FlowLayout(FlowLayout.LEFT));
        input.add(new JLabel("시각(HH:mm)"));
        input.add(timeField);
        input.add(new JLabel("중재"));
        input.add(interField);
        JButton add = new JButton("행 추가");
        add.addActionListener(e -> addRow());
        input.add(add);
        JButton del = new JButton("선택 행 삭제");
        del.addActionListener(e -> {
            if (rowList.getSelectedIndex() >= 0) rowModel.remove(rowList.getSelectedIndex());
        });
        input.add(del);
        JPanel bottom = new JPanel(new BorderLayout());
        rowInput = input;
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(rErr, BorderLayout.SOUTH);
        p.add(bottom, BorderLayout.SOUTH);
        return p;
    }

    /** 입력칸의 시각·중재를 R 행으로 추가한다. 빈 칸 검증은 제출 때 한다. */
    private void addRow() {
        rowModel.addElement(new SbarRecommendation(timeField.getText(), interField.getText()));
        timeField.setText("");
        interField.setText("");
    }

    private List<SbarRecommendation> rows() {
        List<SbarRecommendation> list = new ArrayList<>();
        for (int i = 0; i < rowModel.size(); i++) list.add(rowModel.get(i));
        return list;
    }

    /** 새 실습이 시작됐으면 이전 실습의 입력을 비운다. 같은 실습 안에서 화면을 오가면 입력을 유지한다. */
    @Override
    public void reload() {
        SbarReport reviewed = controller.reviewedSbar();
        boolean readOnly = reviewed != null;
        for (JTextArea a : new JTextArea[] {sArea, bArea, aArea}) a.setEditable(!readOnly);
        rowInput.setVisible(!readOnly);
        buttonRow.setVisible(!readOnly);
        if (readOnly) {
            // 제출한 SBAR를 읽기 전용으로 보인다. 다음 실습에서 칸을 비우도록 실습 표시를 지운다.
            practiceKey = REVIEW_KEY;
            clearInputs();
            sArea.setText(reviewed.situation());
            bArea.setText(reviewed.background());
            aArea.setText(reviewed.assessment());
            reviewed.recommendations().forEach(rowModel::addElement);
            statusLabel.setText(AppState.REVIEW_BANNER);
            return;
        }
        Object key = controller.practiceKey();
        if (key == null || !key.equals(practiceKey)) {
            practiceKey = key;
            clearInputs();
        }
    }

    /** 읽기 전용으로 채운 뒤임을 나타내는 표시 */
    private static final Object REVIEW_KEY = new Object();

    /** SBAR 입력칸에 쓰다 만 내용이 있는지 (화면을 떠날 때 확인용). */
    public boolean hasDraft() {
        if (practiceKey == REVIEW_KEY) return false; // 읽기 전용으로 채운 제출 기록은 작성 중인 글이 아니다
        return !sArea.getText().isBlank() || !bArea.getText().isBlank() || !aArea.getText().isBlank()
                || !rowModel.isEmpty() || !timeField.getText().isBlank() || !interField.getText().isBlank();
    }

    private void clearInputs() {
        sArea.setText("");
        bArea.setText("");
        aArea.setText("");
        rowModel.clear();
        timeField.setText("");
        interField.setText("");
        for (JLabel l : new JLabel[] {sErr, bErr, aErr, rErr}) l.setText(" ");
        statusLabel.setText(" ");
    }

    /**
     * 제출을 시도한다. 검증은 바로 하고, 실패하면 칸별 안내만 갱신하고 입력은 유지한다.
     * 통과하면 AI 해설·저장을 SwingWorker로 백그라운드에서 실행하고, 그동안 버튼을 끈다.
     */
    private void submit() {
        // 입력칸에 쓰다 만 R 행이 있으면 함께 제출 대상에 넣는다. 검증에 실패하면 그 행은 목록에 옮기지 않고
        // 입력칸에 그대로 둔다(고칠 곳이 입력칸에 남도록, 단계 9 검증에서 발견).
        List<SbarRecommendation> candidate = rows();
        if (!timeField.getText().isBlank() || !interField.getText().isBlank()) {
            candidate.add(new SbarRecommendation(timeField.getText(), interField.getText()));
        }
        var p = controller.prepare(sArea.getText(), bArea.getText(), aArea.getText(), candidate);
        var e = p.errors();
        sErr.setText(e.getOrDefault("situation", " "));
        bErr.setText(e.getOrDefault("background", " "));
        aErr.setText(e.getOrDefault("assessment", " "));
        StringBuilder rowErrors = new StringBuilder();
        e.forEach((k, v) -> {
            if (k.startsWith("rec")) rowErrors.append(v).append(' ');
        });
        rErr.setText(rowErrors.length() == 0 ? " " : rowErrors.toString().trim());
        if (!p.ok()) {
            statusLabel.setText(p.message());
            return;
        }
        submitButton.setEnabled(false);
        statusLabel.setText(controller.aiEnabled()
                ? "제출 중입니다. AI 해설을 받는 중이에요… (AI가 실패해도 기록과 점검 결과는 저장됩니다)"
                : "제출 중입니다…");
        new javax.swing.SwingWorker<SbarController.SubmitResult, Void>() {
            @Override
            protected SbarController.SubmitResult doInBackground() {
                return controller.complete(p);
            }

            @Override
            protected void done() {
                submitButton.setEnabled(true);
                SbarController.SubmitResult r;
                try {
                    r = get();
                } catch (Exception ex) {
                    statusLabel.setText("제출하지 못했습니다. 입력은 유지됩니다. (" + ex.getMessage() + ")");
                    return;
                }
                if (!r.submitted()) {
                    statusLabel.setText(r.message());
                    return;
                }
                clearInputs();
                controller.finish(r.attempt());
                onNavigate.accept(Screen.RESULT);
            }
        }.execute();
    }
}
