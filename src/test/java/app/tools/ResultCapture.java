package app.tools;

import app.cases.CaseLoader;
import app.domain.ModelAnswer;
import app.domain.ObservationEntry;
import app.domain.PatientCase;
import app.domain.SbarReport;
import app.scoring.RawNotesChecker;
import app.storage.InMemoryAttemptRepository;
import app.ui.AppState;
import app.ui.ResultPanel;
import app.ui.SbarController;
import app.ui.Theme;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * 결과 화면의 R10(원자료 정리) 피드백을 그림 파일로 저장하는 점검 도구 (개발용, 프로그램에 포함 안 됨).
 * PC-01로 실습을 시작해 실제 제출 경로(SbarController.submit, AI 없음)로 두 가지 기록을 제출하고 결과 화면을 그린다.
 * <ol>
 *   <li>paste: 관찰 서술에 원자료 메모(관련 있는 메모)를 통째로 붙여 넣은 기록</li>
 *   <li>irrelevant: 모범 기록에 관련 없는 메모 내용(옆 환자 채혈, 어머니 면회 문의)을 넣은 기록</li>
 * </ol>
 * 화면을 띄우지 않고 컴포넌트만 그리므로 다른 화면 내용은 찍히지 않는다.
 * 사용: ResultCapture &lt;출력 폴더&gt;
 */
public final class ResultCapture {
    public static void main(String[] args) throws Exception {
        PatientCase pc01 = new CaseLoader().load("PC-01");
        ModelAnswer m = pc01.modelAnswer();
        ObservationEntry model = m.observationRecord();
        String pasted = RawNotesChecker.allNotes(pc01.rawNotes().stream().filter(n -> !n.irrelevant()).toList());
        capture(pc01, with(model, pasted, model.intervention()), m.sbarReport(), new File(args[0], "결과-그대로-옮김.png"));
        ObservationEntry noisy = with(model, model.narrative() + " 08:05 옆 침상 환자 채혈 같이 들어감.", model.intervention());
        SbarReport s = m.sbarReport();
        SbarReport noisySbar = new SbarReport(s.situation(), s.background() + " 13:20 어머니가 주말 면회 시간 문의함.",
                s.assessment(), s.recommendations());
        capture(pc01, noisy, noisySbar, new File(args[0], "결과-관련없는-정보.png"));
    }

    private static ObservationEntry with(ObservationEntry base, String narrative, String intervention) {
        return new ObservationEntry(base.time(), base.shift(), base.tags(), narrative, intervention, base.vitals());
    }

    private static void capture(PatientCase c, ObservationEntry obs, SbarReport sbar, File out) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Theme.install();
            AppState state = new AppState();
            state.login("캡처");
            state.startPractice(c);
            state.addObservation(obs);
            state.addMedication(c.modelAnswer().medicationRecord());
            var r = new SbarController(state, new InMemoryAttemptRepository(), () -> 1L)
                    .submit(sbar.situation(), sbar.background(), sbar.assessment(), sbar.recommendations());
            if (!r.submitted()) throw new IllegalStateException("제출 실패: " + r.errors() + " " + r.message());
            System.out.println(out.getName() + " 점수 " + r.attempt().score() + ", R10 "
                    + r.attempt().checks().stream().filter(x -> x.ruleId().equals("R10")).map(x -> x.message()).toList());
            JFrame f = new JFrame();
            f.setUndecorated(true);
            ResultPanel p = new ResultPanel(state, x -> { });
            p.setBackground(Theme.BG);
            f.setContentPane(p);
            f.pack();
            f.setSize(1180, 4000);
            f.validate();
            f.validate(); // 줄바꿈 글은 폭이 정해진 뒤에 높이가 정해진다
            int h = Math.min(4000, p.getPreferredSize().height + 20);
            f.setSize(1180, h);
            f.validate();
            BufferedImage img = new BufferedImage(1180, h, BufferedImage.TYPE_INT_RGB);
            var g = img.createGraphics();
            f.getRootPane().paint(g);
            g.dispose();
            try {
                ImageIO.write(img, "png", out);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
            f.dispose();
        });
    }
}
