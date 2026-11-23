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
 * 개인 메인 화면. 실습 시작(기본 케이스, AI 생성은 비활성+이유)과 결과·기록 영역 자리를 보여 준다.
 * 로직은 HomeController가 맡고, 이 패널은 배치와 버튼 이벤트만 다룬다.
 */
public class HomePanel extends JPanel implements Reloadable {
    private final HomeController controller;
    private final Runnable onChanged;
    private final JLabel messageLabel = new JLabel(" ");
    private final Supplier<ResultView> resultViewSupplier;
    private final JTextArea metricsArea = new JTextArea(5, 40);
    private final JTextArea detailArea = new JTextArea();
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

    /** 평가지표·과거 기록·상세 결과 영역. 화면이 보일 때마다 저장된 기록으로 다시 채운다. */
    private JPanel buildRecordArea() {
        JPanel area = new JPanel(new BorderLayout(6, 6));
        area.setBorder(BorderFactory.createTitledBorder("평가지표 · 과거 기록 · 결과"));
        metricsArea.setEditable(false);
        detailArea.setEditable(false);
        detailArea.setLineWrap(true);
        detailArea.setWrapStyleWord(true);
        area.add(new JScrollPane(metricsArea), BorderLayout.NORTH);
        JPanel center = new JPanel(new GridLayout(1, 2, 6, 6));
        center.add(new JScrollPane(historyList));
        center.add(new JScrollPane(detailArea));
        area.add(center, BorderLayout.CENTER);
        // 선택한 기록을 텍스트 파일로 내보낸다 (T-26).
        JButton export = new JButton("선택한 기록 내보내기");
        export.addActionListener(e -> exportSelected());
        area.add(export, BorderLayout.SOUTH);
        // 목록에서 기록을 고르면 제출 당시 입력과 점검 결과를 보여 준다.
        historyList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            Attempt a = historyList.getSelectedValue();
            if (a == null) return;
            detailArea.setText(ResultView.detailText(a));
            detailArea.setCaretPosition(0);
        });
        historyList.setCellRenderer((l, v, i, sel, foc) -> {
            JLabel label = new JLabel(ResultView.historyLine(v));
            label.setOpaque(true);
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
        detailArea.setText("");
        if (view == null) {
            metricsArea.setText("평가지표: 로그인 후 표시됩니다.");
            return;
        }
        metricsArea.setText(String.join("\n", view.metrics()));
        view.history().forEach(historyModel::addElement);
        if (historyModel.isEmpty()) detailArea.setText("아직 저장된 실습 기록이 없습니다.");
    }

    /** 선택한 기록을 저장 대화상자로 고른 위치에 내보낸다. 실패하면 안내 문구를 보여 준다. */
    private void exportSelected() {
        Attempt a = historyList.getSelectedValue();
        if (a == null) {
            messageLabel.setText("내보낼 실습 기록을 먼저 선택해 주세요.");
            return;
        }
        javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
        chooser.setSelectedFile(new java.io.File(app.export.ResultExporter.suggestedFileName(a)));
        if (chooser.showSaveDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) return;
        java.nio.file.Path target = chooser.getSelectedFile().toPath();
        String error = new app.export.ResultExporter().export(a, target);
        messageLabel.setText(error == null ? "저장했습니다: " + target : error);
    }
}
