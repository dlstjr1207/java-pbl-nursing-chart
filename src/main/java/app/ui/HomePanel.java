package app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import app.domain.Attempt;
import java.awt.GridLayout;
import java.util.function.Supplier;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * 개인 메인 화면. 실습 시작(기본 케이스, AI 생성은 비활성+이유)과 평가지표·과거 기록 목록을 보여 준다.
 * 기록을 누르면 결과 화면(ResultPanel)으로 연다.
 * 로직은 HomeController가 맡고, 이 패널은 배치와 버튼 이벤트만 다룬다.
 */
public class HomePanel extends JPanel implements Reloadable {
    private final HomeController controller;
    private final Runnable onChanged;
    private final JLabel messageLabel = new JLabel(" ");
    private final Supplier<ResultView> resultViewSupplier;
    private final JTextArea metricsArea = new JTextArea(5, 40);
    /** 과거 기록을 누르면 결과 화면으로 연다 (polish B-4). Main에서 정한다. */
    private java.util.function.Consumer<Attempt> onOpen = a -> { };
    private final DefaultListModel<Attempt> historyModel = new DefaultListModel<>();
    private final JList<Attempt> historyList = new JList<>(historyModel);

    /** onChanged: 화면 이동 후 MainFrame을 갱신하는 콜백. */
    public HomePanel(HomeController controller, Runnable onChanged) {
        this(controller, onChanged, () -> null);
    }

    /** resultViewSupplier: 로그인한 사용자의 기록 조회기를 돌려주는 함수 (로그인 전이면 null). */
    public HomePanel(HomeController controller, Runnable onChanged, Supplier<ResultView> resultViewSupplier) {
        super(new BorderLayout(12, 12));
        this.resultViewSupplier = resultViewSupplier;
        this.controller = controller;
        this.onChanged = onChanged;
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(buildStartArea(), BorderLayout.NORTH);
        add(buildRecordArea(), BorderLayout.CENTER);
        add(messageLabel, BorderLayout.SOUTH);
        messageLabel.setForeground(new Color(0xB91C1C));
    }

    /** 실습 시작 영역: 기본 케이스 버튼과 AI 생성 버튼(비활성 시 이유 표시). */
    private JPanel buildStartArea() {
        JPanel area = new JPanel(new GridLayout(0, 1, 0, 6));
        area.setBorder(BorderFactory.createTitledBorder("실습 시작"));
        area.add(new JLabel(controller.defaultCaseDescription()));
        JButton start = new JButton("실습 시작");
        start.addActionListener(e -> {
            String error = controller.startDefaultPractice();
            messageLabel.setText(error == null ? " " : error);
            onChanged.run();
        });
        area.add(start);
        JButton ai = new JButton("AI로 새 케이스 생성");
        ai.setEnabled(controller.aiGenerationAvailable());
        // AI 호출은 수 초~수십 초가 걸릴 수 있어 SwingWorker로 백그라운드에서 실행한다.
        ai.addActionListener(e -> {
            start.setEnabled(false);
            ai.setEnabled(false);
            messageLabel.setText("AI가 새 케이스를 만드는 중입니다. 잠시 기다려 주세요…");
            new javax.swing.SwingWorker<app.cases.CaseGenerator.Result, Void>() {
                @Override
                protected app.cases.CaseGenerator.Result doInBackground() {
                    return controller.generateCase();
                }

                @Override
                protected void done() {
                    start.setEnabled(true);
                    ai.setEnabled(controller.aiGenerationAvailable());
                    try {
                        String notice = controller.applyGenerated(get());
                        messageLabel.setText(notice == null ? " " : notice);
                        onChanged.run();
                    } catch (Exception ex) {
                        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                        messageLabel.setText("케이스를 불러오지 못했습니다: " + cause.getMessage());
                    }
                }
            }.execute();
        });
        area.add(ai);
        if (!controller.aiGenerationAvailable()) area.add(new JLabel(controller.aiGenerationReason()));
        return area;
    }

    /** 기록을 눌렀을 때 할 일을 정한다 (결과 화면 열기). */
    public void setOnOpen(java.util.function.Consumer<Attempt> onOpen) { this.onOpen = onOpen; }

    /** 평가지표와 과거 기록 목록. 기록을 누르면 결과 화면이 열린다. */
    private JPanel buildRecordArea() {
        JPanel area = new JPanel(new BorderLayout(6, 6));
        area.setBorder(BorderFactory.createTitledBorder("평가지표 · 과거 기록"));
        metricsArea.setEditable(false);
        area.add(new JScrollPane(metricsArea), BorderLayout.NORTH);
        historyList.setVisibleRowCount(8);
        area.add(new JScrollPane(historyList), BorderLayout.CENTER);
        JLabel hint = new JLabel("기록을 누르면 결과 화면이 열립니다. 결과 화면에서 기록을 내보낼 수 있습니다.");
        hint.setForeground(Theme.MUTED);
        area.add(hint, BorderLayout.SOUTH);
        historyList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Attempt a = historyList.getSelectedValue();
            if (a != null) onOpen.accept(a);
        });
        historyList.setCellRenderer((l, v, i, sel, foc) -> {
            JLabel label = new JLabel("  " + ResultView.historyLine(v) + "   ›");
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(6, 4, 6, 4));
            label.setBackground(sel ? l.getSelectionBackground() : l.getBackground());
            return label;
        });
        refreshRecords();
        return area;
    }

    @Override
    public void reload() { refreshRecords(); }

    /** 로그인한 사용자의 저장 기록으로 평가지표와 목록을 갱신한다. 로그인 전이면 안내만 둔다. */
    public void refreshRecords() {
        ResultView view = resultViewSupplier.get();
        historyModel.clear();
        if (view == null) {
            metricsArea.setText("평가지표: 로그인 후 표시됩니다.");
            return;
        }
        metricsArea.setText(String.join("\n", view.metrics()));
        view.history().forEach(historyModel::addElement);
        if (historyModel.isEmpty()) metricsArea.append("\n아직 저장된 실습 기록이 없습니다.");
    }
}
