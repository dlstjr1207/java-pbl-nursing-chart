package app.ui;

import app.domain.PatientCase;
import app.domain.Shift;
import app.ui.ObservationController.Metric;
import app.ui.ObservationController.SaveResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * 활력징후·관찰 화면 (S3). 위쪽에 추이 차트·상황 브리핑·시각순 관찰 목록, 아래쪽에 관찰 입력 양식을 둔다.
 * 검증과 저장은 ObservationController가 맡고, 이 클래스는 입력값을 모아 전달하고 결과를 보여 준다.
 */
public class ObservationPanel extends JPanel {
    private final AppState state;
    private final ObservationController controller;

    private final JTextArea briefing = new JTextArea(3, 30);
    private final JComboBox<Metric> metricBox = new JComboBox<>(Metric.values());
    private final VitalChartPanel chart = new VitalChartPanel();
    private final DefaultListModel<String> logModel = new DefaultListModel<>();

    private final JTextField timeField = new JTextField(6);
    private final Map<Shift, JRadioButton> shiftButtons = new EnumMap<>(Shift.class);
    private final ButtonGroup shiftGroup = new ButtonGroup();
    private final List<JCheckBox> tagBoxes = new ArrayList<>();
    private final JTextArea narrativeArea = new JTextArea(3, 30);
    private final JTextField interventionField = new JTextField(30);
    private final Map<Metric, JTextField> vitalFields = new EnumMap<>(Metric.class);
    private final JLabel errorLabel = new JLabel(" ");

    /** onNavigate: 다른 화면으로 이동하는 콜백(보통 MainFrame::show). */
    public ObservationPanel(AppState state, Consumer<Screen> onNavigate) {
        super(new BorderLayout(8, 8));
        this.state = state;
        this.controller = new ObservationController(state);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        add(buildTop(), BorderLayout.CENTER);
        add(buildForm(onNavigate), BorderLayout.SOUTH);
        metricBox.addActionListener(e -> refreshChart());
        // 화면이 나타날 때마다 현재 케이스로 갱신한다.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                reload();
            }
        });
        reload();
    }

    private JPanel buildTop() {
        JPanel top = new JPanel(new GridLayout(1, 2, 8, 8));
        JPanel left = new JPanel(new BorderLayout(4, 4));
        left.setBorder(BorderFactory.createTitledBorder("활력징후 추이 (● 기존 / ○ 학생 기록)"));
        left.add(metricBox, BorderLayout.NORTH);
        left.add(chart, BorderLayout.CENTER);
        briefing.setEditable(false);
        briefing.setLineWrap(true);
        briefing.setWrapStyleWord(true);
        JScrollPane briefScroll = new JScrollPane(briefing);
        briefScroll.setBorder(BorderFactory.createTitledBorder("상황 브리핑"));
        left.add(briefScroll, BorderLayout.SOUTH);

        JList<String> list = new JList<>(logModel);
        JScrollPane logScroll = new JScrollPane(list);
        logScroll.setBorder(BorderFactory.createTitledBorder("관찰 기록 (시각순)"));
        top.add(left);
        top.add(logScroll);
        return top;
    }

    private JPanel buildForm(Consumer<Screen> onNavigate) {
        JPanel form = new JPanel(new GridLayout(0, 1, 2, 2));
        form.setBorder(BorderFactory.createTitledBorder("관찰 입력"));

        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("시각(HH:mm)"));
        row1.add(timeField);
        row1.add(new JLabel("근무"));
        for (Shift s : Shift.values()) {
            JRadioButton b = new JRadioButton(s.name());
            shiftButtons.put(s, b);
            shiftGroup.add(b);
            row1.add(b);
        }
        form.add(row1);

        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("태그"));
        for (String tag : ObservationController.TAGS) {
            JCheckBox box = new JCheckBox(tag);
            tagBoxes.add(box);
            row2.add(box);
        }
        form.add(row2);

        JPanel row3 = new JPanel(new BorderLayout());
        row3.add(new JLabel("서술 "), BorderLayout.WEST);
        row3.add(new JScrollPane(narrativeArea), BorderLayout.CENTER);
        form.add(row3);

        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row4.add(new JLabel("처치(선택)"));
        row4.add(interventionField);
        form.add(row4);

        JPanel row5 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row5.add(new JLabel("활력징후(선택)"));
        for (Metric m : Metric.values()) {
            JTextField f = new JTextField(4);
            vitalFields.put(m, f);
            row5.add(new JLabel(m.label()));
            row5.add(f);
        }
        form.add(row5);

        errorLabel.setForeground(new Color(0xC62828));
        form.add(errorLabel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton save = new JButton("관찰 기록 저장");
        save.addActionListener(e -> save());
        buttons.add(save);
        for (Screen s : new Screen[] {Screen.PATIENT, Screen.MEDICATION, Screen.SBAR}) {
            JButton b = new JButton(s.title() + "(으)로 이동");
            b.addActionListener(e -> onNavigate.accept(s));
            buttons.add(b);
        }
        form.add(buttons);
        return form;
    }

    /** 입력값을 모아 저장을 시도한다. 실패하면 입력은 그대로 두고 오류를 칸 이름과 함께 보여 준다. */
    private void save() {
        Shift shift = null;
        for (Map.Entry<Shift, JRadioButton> e : shiftButtons.entrySet()) {
            if (e.getValue().isSelected()) shift = e.getKey();
        }
        List<String> tags = new ArrayList<>();
        for (JCheckBox b : tagBoxes) if (b.isSelected()) tags.add(b.getText());
        Map<Metric, String> vitals = new EnumMap<>(Metric.class);
        vitalFields.forEach((m, f) -> vitals.put(m, f.getText()));

        SaveResult r = controller.save(timeField.getText(), shift, tags, narrativeArea.getText(),
                interventionField.getText(), vitals);
        if (r.saved()) {
            clearForm();
            errorLabel.setText("저장했습니다.");
            refreshLists();
        } else {
            errorLabel.setText("<html>" + String.join("<br>", r.errors().byField().values()) + "</html>");
        }
    }

    private void clearForm() {
        timeField.setText("");
        shiftGroup.clearSelection();
        tagBoxes.forEach(b -> b.setSelected(false));
        narrativeArea.setText("");
        interventionField.setText("");
        vitalFields.values().forEach(f -> f.setText(""));
    }

    /** 현재 케이스로 브리핑·목록·차트를 다시 채운다. */
    public void reload() {
        PatientCase c = state.currentCase();
        briefing.setText(c == null || c.briefing() == null ? "" : c.briefing());
        errorLabel.setText(" ");
        refreshLists();
    }

    private void refreshLists() {
        logModel.clear();
        controller.timeline().forEach(row -> logModel.addElement(row.text()));
        refreshChart();
    }

    private void refreshChart() {
        Metric m = (Metric) metricBox.getSelectedItem();
        if (m == null) m = Metric.values()[0];
        chart.setData(m.label(), controller.chartPoints(m));
    }
}
