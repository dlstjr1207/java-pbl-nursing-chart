package app.ui;

import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** 로그인 화면 (화면명세 '로그인'). 검증·이동 판단은 AuthFormController가 하고, 이 클래스는 입력칸과 오류 표시만 맡는다. */
public class LoginPanel extends JPanel {
    private final Runnable onChange;
    private final AuthFormController controller;
    private final JTextField idField = new JTextField(18);
    private final JPasswordField pwField = new JPasswordField(18);
    private final JLabel idError = errorLabel();
    private final JLabel pwError = errorLabel();
    private final JLabel message = errorLabel();

    /** @param onChange 화면 상태가 바뀐 뒤 프레임을 다시 그리게 하는 동작 */
    public LoginPanel(AuthFormController controller, Runnable onChange) {
        super(new GridBagLayout());
        this.controller = controller;
        this.onChange = onChange;
        JButton login = new JButton("로그인");
        JButton signup = new JButton("회원가입");
        login.addActionListener(e -> submit());
        pwField.addActionListener(e -> submit()); // Enter로도 로그인
        signup.addActionListener(e -> {
            controller.openSignup();
            onChange.run();
        });
        // 모양: 가운데 흰 카드(제목·안내 + 입력 양식) + 오른쪽 안내 카드 (Figma 로그인 화면 기준)
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        int y = 0;
        form.add(new JLabel("아이디"), at(0, y, 1));
        form.add(idField, at(1, y++, 1));
        form.add(idError, at(1, y++, 1));
        form.add(new JLabel("비밀번호"), at(0, y, 1));
        form.add(pwField, at(1, y++, 1));
        form.add(pwError, at(1, y++, 1));
        form.add(message, at(0, y++, 2));
        form.add(login, at(0, y, 1));
        form.add(signup, at(1, y, 1));
        JPanel card = Theme.cardPanel(null, new java.awt.BorderLayout(0, 10));
        JLabel heading = new JLabel("정신건강의학과 병동 간호 EMR 실습");
        heading.setFont(Theme.font(java.awt.Font.BOLD, 20f));
        heading.setForeground(Theme.NAVY);
        JLabel sub = new JLabel("교육용 시뮬레이션 · 가상 환자 기록 작성 연습");
        sub.setForeground(Theme.MUTED);
        JPanel head = new JPanel(new java.awt.GridLayout(2, 1, 0, 2));
        head.setOpaque(false);
        head.add(heading);
        head.add(sub);
        card.add(head, java.awt.BorderLayout.NORTH);
        card.add(form, java.awt.BorderLayout.CENTER);
        JPanel infoCard = Theme.cardPanel("실습 순서", new java.awt.BorderLayout());
        infoCard.setBackground(Theme.NAVY_LIGHT);
        javax.swing.JTextArea infoText = new javax.swing.JTextArea("1. 로그인 후 개인 메인에서 실습 시작\n2. 환자 정보 확인\n3. 관찰 기록 → 투약 기록 → SBAR 인계 작성\n4. 제출하면 항목별 점검 결과와 점수 확인\n\n가상 환자 기록이며 임상 검수 전의 교육용 예시입니다.");
        infoText.setEditable(false);
        infoText.setOpaque(false);
        infoText.setLineWrap(true);
        infoText.setWrapStyleWord(true);
        infoText.setColumns(22);
        infoCard.add(infoText, java.awt.BorderLayout.CENTER);
        JPanel row = new JPanel(new java.awt.GridBagLayout());
        row.setOpaque(false);
        GridBagConstraints c1 = LoginPanel.at(0, 0, 1);
        c1.anchor = GridBagConstraints.NORTH;
        row.add(card, c1);
        GridBagConstraints c2 = LoginPanel.at(1, 0, 1);
        c2.anchor = GridBagConstraints.NORTH;
        c2.fill = GridBagConstraints.VERTICAL;
        row.add(infoCard, c2);
        add(row);
    }

    /** 입력을 검증하고 결과를 칸 옆·공통 문구에 표시한다. 비밀번호 칸은 항상 비운다. */
    void submit() {
        AuthFormController.Result r = controller.login(idField.getText(), new String(pwField.getPassword()));
        showErrors(r.errors());
        message.setText(r.message() == null ? " " : r.message());
        pwField.setText("");
        if (r.success()) idField.setText("");
        onChange.run();
    }

    /** 가입 직후 등 외부에서 안내 문구를 띄운다. */
    public void showInfo(String text) {
        message.setForeground(new Color(0x15803D));
        message.setText(text);
    }

    private void showErrors(Map<String, String> errors) {
        message.setForeground(new Color(0xB91C1C));
        idError.setText(errors.getOrDefault("loginId", " "));
        pwError.setText(errors.getOrDefault("password", " "));
    }

    static JLabel errorLabel() {
        JLabel l = new JLabel(" ");
        l.setForeground(new Color(0xB91C1C));
        return l;
    }

    static GridBagConstraints at(int x, int y, int width) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = x;
        c.gridy = y;
        c.gridwidth = width;
        c.insets = new Insets(4, 6, 4, 6);
        c.anchor = GridBagConstraints.WEST;
        return c;
    }
}
