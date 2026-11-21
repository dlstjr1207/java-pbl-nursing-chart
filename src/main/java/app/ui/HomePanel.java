package app.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
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

    /** onChanged: 화면 이동 후 MainFrame을 갱신하는 콜백. */
    public HomePanel(HomeController controller, Runnable onChanged) {
        super(new BorderLayout(12, 12));
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

    /** 평가지표·과거 기록·결과 패널 자리 (T-12, T-21b에서 채움). */
    private JPanel buildRecordArea() {
        JPanel area = new JPanel(new GridLayout(0, 1, 0, 6));
        area.setBorder(BorderFactory.createTitledBorder("평가지표 · 과거 기록 · 결과"));
        area.add(new JLabel("아직 저장된 실습 기록이 없습니다. (기록 표시는 준비 중)"));
        return area;
    }
}
