package app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import app.domain.Attempt;
import java.awt.GridLayout;
import java.awt.event.HierarchyEvent;
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
public class HomePanel extends JPanel {
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
        // CardLayout으로 이 화면이 다시 보일 때마다 기록을 새로 읽는다.
        addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) refreshRecords();
        });
        refreshRecords();
        return area;
    }

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
}
