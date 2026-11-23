package app.ui;

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
        int y = 0;
        add(new JLabel("회원가입"), LoginPanel.at(0, y++, 2));
        add(new JLabel("이름"), LoginPanel.at(0, y, 1));
        add(nameField, LoginPanel.at(1, y++, 1));
        add(nameError, LoginPanel.at(1, y++, 1));
        add(new JLabel("아이디 (영문·숫자 4~20자)"), LoginPanel.at(0, y, 1));
        add(idField, LoginPanel.at(1, y++, 1));
        add(idError, LoginPanel.at(1, y++, 1));
        add(new JLabel("비밀번호 (8자 이상)"), LoginPanel.at(0, y, 1));
        add(pwField, LoginPanel.at(1, y++, 1));
        add(pwError, LoginPanel.at(1, y++, 1));
        add(new JLabel("비밀번호 확인"), LoginPanel.at(0, y, 1));
        add(confirmField, LoginPanel.at(1, y++, 1));
        add(confirmError, LoginPanel.at(1, y++, 1));
        add(submit, LoginPanel.at(0, y, 1));
        add(back, LoginPanel.at(1, y, 1));
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
