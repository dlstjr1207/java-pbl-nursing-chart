package app;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("간호 차팅 연습 프로그램");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(new JLabel("Nursing Charting Trainer", SwingConstants.CENTER));
            frame.setSize(960, 640);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
