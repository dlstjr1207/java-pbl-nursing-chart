package app.ui;

import app.domain.Medication;
import app.domain.MedicationResult;
import app.domain.PatientCase;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ItemEvent;
import java.util.EnumMap;
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
 * 투약 점검 화면 (S4). 처방 선택(JComboBox), 투약 결과(JRadioButton), 구강 확인(JCheckBox)을 입력받는다.
 * 사유가 필요한 조건(PRN·거부·보류)이 되면 ItemListener로 사유 라벨을 강조한다. 검증·저장은 MedicationController.
 */
public class MedicationPanel extends JPanel implements Reloadable {
    private final AppState state;
    private final MedicationController controller;

    private final JComboBox<Medication> orderBox = new JComboBox<>();
    private final DefaultListModel<String> recordModel = new DefaultListModel<>();
    private final JTextField doseField = new JTextField(6);
    private final JTextField unitField = new JTextField(5);
    private final JTextField routeField = new JTextField(8);
    private final JTextField timeField = new JTextField(6);
    private final Map<MedicationResult, JRadioButton> resultButtons = new EnumMap<>(MedicationResult.class);
    private final ButtonGroup resultGroup = new ButtonGroup();
    private final JTextField reasonField = new JTextField(30);
    private final JLabel reasonLabel = new JLabel("사유");
    private final JTextField basisField = new JTextField(30);
    private final JCheckBox oralBox = new JCheckBox("구강 확인");
    private final JTextArea planArea = new JTextArea(2, 30);
    private final JLabel errorLabel = new JLabel(" ");

    public MedicationPanel(AppState state, Consumer<Screen> onNavigate) {
        super(new BorderLayout(8, 8));
        this.state = state;
        this.controller = new MedicationController(state);
        setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JScrollPane scroll = new JScrollPane(new JList<>(recordModel));
        scroll.setBorder(BorderFactory.createTitledBorder("내 투약 기록"));
        add(scroll, BorderLayout.CENTER);
        add(buildForm(onNavigate), BorderLayout.SOUTH);

        orderBox.setRenderer((l, value, i, sel, foc) ->
                new JLabel(value == null ? "" : MedicationController.orderLabel(value)));
        // 처방을 고르면 확정된 용량·단위·경로를 채운다([확인필요] 값은 채우지 않는다).
        orderBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) fillFromOrder();
            updateReasonHint();
        });
        reload();
    }

    private JPanel buildForm(Consumer<Screen> onNavigate) {
        JPanel form = new JPanel(new GridLayout(0, 1, 2, 2));
        form.setBorder(BorderFactory.createTitledBorder("투약 입력"));

        JPanel r1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        r1.add(new JLabel("처방"));
        r1.add(orderBox);
        form.add(r1);

        JPanel r2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        r2.add(new JLabel("용량"));
        r2.add(doseField);
        r2.add(new JLabel("단위"));
        r2.add(unitField);
        r2.add(new JLabel("경로"));
        r2.add(routeField);
        r2.add(new JLabel("시각(HH:mm)"));
        r2.add(timeField);
        form.add(r2);

        JPanel r3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        r3.add(new JLabel("결과"));
        for (MedicationResult m : MedicationResult.values()) {
            JRadioButton b = new JRadioButton(MedicationController.resultLabel(m));
            b.addItemListener(e -> updateReasonHint());
            resultButtons.put(m, b);
            resultGroup.add(b);
            r3.add(b);
        }
        r3.add(oralBox);
        form.add(r3);

        JPanel r4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        r4.add(reasonLabel);
        r4.add(reasonField);
        form.add(r4);

        JPanel r5 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        r5.add(new JLabel("처방 근거"));
        r5.add(basisField);
        form.add(r5);

        JPanel r6 = new JPanel(new BorderLayout());
        r6.add(new JLabel("재평가 계획(선택) "), BorderLayout.WEST);
        r6.add(new JScrollPane(planArea), BorderLayout.CENTER);
        form.add(r6);

        errorLabel.setForeground(new Color(0xC62828));
        form.add(errorLabel);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton save = new JButton("투약 기록 저장");
        save.addActionListener(e -> save());
        buttons.add(save);
        for (Screen s : new Screen[] {Screen.PATIENT, Screen.OBSERVATION, Screen.SBAR}) {
            JButton b = new JButton(s.title() + "(으)로 이동");
            b.addActionListener(e -> onNavigate.accept(s));
            buttons.add(b);
        }
        form.add(buttons);
        return form;
    }

    private MedicationResult selectedResult() {
        for (Map.Entry<MedicationResult, JRadioButton> e : resultButtons.entrySet()) {
            if (e.getValue().isSelected()) return e.getKey();
        }
        return null;
    }

    /** 사유가 필수인 조건이면 라벨을 빨갛게 바꾼다. */
    private void updateReasonHint() {
        boolean need = MedicationController.reasonRequired((Medication) orderBox.getSelectedItem(), selectedResult());
        reasonLabel.setText(need ? "사유(필수)" : "사유");
        reasonLabel.setForeground(need ? new Color(0xC62828) : Color.DARK_GRAY);
    }

    private void fillFromOrder() {
        Medication o = (Medication) orderBox.getSelectedItem();
        if (o == null) return;
        doseField.setText(confirmed(o.dose()));
        unitField.setText(confirmed(o.unit()));
        routeField.setText(confirmed(o.route()));
    }

    private static String confirmed(String v) {
        return v == null || v.startsWith("[") ? "" : v;
    }

    /** 입력을 모아 저장을 시도한다. 실패하면 입력은 그대로 두고 오류를 보여 준다. */
    private void save() {
        var r = controller.save((Medication) orderBox.getSelectedItem(), doseField.getText(), unitField.getText(),
                routeField.getText(), timeField.getText(), selectedResult(), reasonField.getText(),
                basisField.getText(), oralBox.isSelected(), planArea.getText());
        if (r.saved()) {
            timeField.setText("");
            resultGroup.clearSelection();
            reasonField.setText("");
            basisField.setText("");
            oralBox.setSelected(false);
            planArea.setText("");
            errorLabel.setForeground(Theme.GREEN);
            errorLabel.setText("저장했습니다.");
            updateReasonHint();
            refreshList();
        } else {
            errorLabel.setForeground(Theme.RED);
            errorLabel.setText("<html>" + String.join("<br>", r.errors().byField().values()) + "</html>");
        }
    }

    /** 현재 케이스의 처방 목록으로 선택지를 다시 채운다. */
    @Override
    public void reload() {
        orderBox.removeAllItems();
        PatientCase c = state.currentCase();
        if (c != null) controller.selectableOrders().forEach(orderBox::addItem);
        errorLabel.setText(" ");
        updateReasonHint();
        refreshList();
    }

    private void refreshList() {
        recordModel.clear();
        controller.recordLines().forEach(recordModel::addElement);
    }
}
