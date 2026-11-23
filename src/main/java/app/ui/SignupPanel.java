package app.ui;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/** 회원가입 화면 (D23: 이름, 아이디, 비밀번호·확인). 오류는 해당 입력칸 옆에 표시한다. */
public class SignupPanel extends JPanel {
    private final AuthFormController controller;
    private final Runnable onChange;
    private final Consumer<String> onSignedUp;
    private final JTextField nameField = new JTextField(18);
    private final JTextField idField = new JTextField(18);
    private final JPasswordField pwField = new JPasswordField(18);
    private final JPasswordField confirmField = new JPasswordField(18);
    private final JLabel nameError = LoginPanel.errorLabel();
    private final JLabel idError = LoginPanel.errorLabel();
    private final JLabel pwError = LoginPanel.errorLabel();
    private final JLabel confirmError = LoginPanel.errorLabel();

    /** @param onSignedUp 가입 성공 시 로그인 화면에 보여 줄 안내 문구를 받는 동작 */
    public SignupPanel(AuthFormController controller, Runnable onChange, Consumer<String> onSignedUp) {
        super(new GridBagLayout());
        this.controller = controller;
        this.onChange = onChange;
        this.onSignedUp = onSignedUp;
        JButton submit = new JButton("가입하기");
        JButton back = new JButton("로그인으로 돌아가기");
        submit.addActionListener(e -> submit());
        back.addActionListener(e -> {
            controller.openLogin();
            onChange.run();
        });
        // 모양: 가운데 흰 카드(제목·안내 + 입력 양식) + 오른쪽 안내 카드 (Figma 로그인 화면 기준)
        JPanel form = new JPanel(new GridBagLayout());
        form.setOpaque(false);
        int y = 0;
        form.add(new JLabel("이름"), LoginPanel.at(0, y, 1));
        form.add(nameField, LoginPanel.at(1, y++, 1));
        form.add(nameError, LoginPanel.at(1, y++, 1));
        form.add(new JLabel("아이디 (영문·숫자 4~20자)"), LoginPanel.at(0, y, 1));
        form.add(idField, LoginPanel.at(1, y++, 1));
        form.add(idError, LoginPanel.at(1, y++, 1));
        form.add(new JLabel("비밀번호 (8자 이상)"), LoginPanel.at(0, y, 1));
        form.add(pwField, LoginPanel.at(1, y++, 1));
        form.add(pwError, LoginPanel.at(1, y++, 1));
        form.add(new JLabel("비밀번호 확인"), LoginPanel.at(0, y, 1));
        form.add(confirmField, LoginPanel.at(1, y++, 1));
        form.add(confirmError, LoginPanel.at(1, y++, 1));
        form.add(submit, LoginPanel.at(0, y, 1));
        form.add(back, LoginPanel.at(1, y, 1));
        JPanel card = Theme.cardPanel(null, new java.awt.BorderLayout(0, 10));
        JLabel heading = new JLabel("회원가입");
        heading.setFont(Theme.font(java.awt.Font.BOLD, 20f));
        heading.setForeground(Theme.NAVY);
        JLabel sub = new JLabel("아이디·비밀번호·이름만 받습니다 (D23)");
        sub.setForeground(Theme.MUTED);
        JPanel head = new JPanel(new java.awt.GridLayout(2, 1, 0, 2));
        head.setOpaque(false);
        head.add(heading);
        head.add(sub);
        card.add(head, java.awt.BorderLayout.NORTH);
        card.add(form, java.awt.BorderLayout.CENTER);
        JPanel infoCard = Theme.cardPanel("가입 안내", new java.awt.BorderLayout());
        infoCard.setBackground(Theme.NAVY_LIGHT);
        javax.swing.JTextArea infoText = new javax.swing.JTextArea("아이디: 영문·숫자 4~20자, 중복 불가\n비밀번호: 8자 이상\n비밀번호는 해시로만 저장되며 평문으로 저장하지 않습니다.");
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

    /** 검증 실패면 입력은 유지하고 오류만 표시한다. 성공하면 칸을 비우고 로그인 화면으로 이동한다. */
    void submit() {
        AuthFormController.Result r = controller.signup(idField.getText(), new String(pwField.getPassword()),
                new String(confirmField.getPassword()), nameField.getText());
        Map<String, String> e = r.errors();
        nameError.setText(e.getOrDefault("name", " "));
        idError.setText(e.getOrDefault("loginId", " "));
        pwError.setText(e.getOrDefault("password", " "));
        confirmError.setText(e.getOrDefault("passwordConfirm", " "));
        if (r.success()) {
            nameField.setText("");
            idField.setText("");
            pwField.setText("");
            confirmField.setText("");
            onSignedUp.accept(r.message());
        }
        onChange.run();
    }
}
