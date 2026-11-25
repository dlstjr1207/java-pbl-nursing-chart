package app.tools;

import app.cases.CaseLoader;
import app.domain.PatientCase;
import app.ui.AppState;
import app.ui.PatientInfoPanel;
import app.ui.Theme;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * 환자 정보 화면(원자료 메모)을 그림 파일로 저장하는 점검 도구 (개발용, 프로그램에 포함 안 됨).
 * 화면을 띄우지 않고 창 컴포넌트만 그리므로 다른 화면 내용은 찍히지 않는다.
 * 사용: RawNotesCapture &lt;출력.png&gt; [케이스 JSON 파일(없으면 PC-01)]. -Dscreen=observation이면 관찰 화면을 그린다.
 */
public final class RawNotesCapture {
    public static void main(String[] args) throws Exception {
        PatientCase c = args.length > 1
                ? app.domain.DomainJson.fromJson(java.nio.file.Files.readString(java.nio.file.Path.of(args[1])), PatientCase.class)
                : new CaseLoader().load("PC-01");
        SwingUtilities.invokeAndWait(() -> {
            Theme.install();
            AppState s = new AppState();
            s.login("캡처");
            s.startPractice(c);
            JFrame f = new JFrame();
            f.setUndecorated(true);
            javax.swing.JPanel p = "observation".equals(System.getProperty("screen"))
                    ? new app.ui.ObservationPanel(s, x -> { }) : new PatientInfoPanel(s, x -> { });
            p.setBackground(Theme.BG);
            f.setContentPane(p);
            f.pack();
            f.setSize(1180, 2400);
            f.validate();
            f.validate(); // 줄바꿈 글은 폭이 정해진 뒤에 높이가 정해진다
            int h = Math.min(2400, p.getPreferredSize().height + 20);
            f.setSize(1180, h);
            f.validate();
            System.out.println("크기 1180x" + h);
            BufferedImage img = new BufferedImage(1180, h, BufferedImage.TYPE_INT_RGB);
            var g = img.createGraphics();
            f.getRootPane().paint(g);
            g.dispose();
            try {
                ImageIO.write(img, "png", new File(args[0]));
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            f.dispose();
        });
    }
}
