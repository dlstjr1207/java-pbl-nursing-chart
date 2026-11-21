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
        int y = 0;
        add(new JLabel("로그인"), at(0, y++, 2));
        add(new JLabel("아이디"), at(0, y, 1));
        add(idField, at(1, y++, 1));
        add(idError, at(1, y++, 1));
        add(new JLabel("비밀번호"), at(0, y, 1));
        add(pwField, at(1, y++, 1));
        add(pwError, at(1, y++, 1));
        add(message, at(0, y++, 2));
        add(login, at(0, y, 1));
        add(signup, at(1, y, 1));
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
