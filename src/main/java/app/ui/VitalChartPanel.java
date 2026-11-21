package app.ui;

import app.ui.ObservationController.ChartPoint;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JPanel;

/**
 * 활력징후 추이 차트. 새 라이브러리 없이 Graphics2D로 선과 점을 직접 그린다.
 * 기존 기록은 속이 찬 파란 점, 학생 기록은 속이 빈 주황 점으로 출처를 구분한다.
 */
public class VitalChartPanel extends JPanel {
    private static final Color CASE_COLOR = new Color(0x2F6FDE);
    private static final Color STUDENT_COLOR = new Color(0xE07B00);
    private static final int PAD_L = 48;
    private static final int PAD_R = 16;
    private static final int PAD_T = 24;
    private static final int PAD_B = 30;

    private List<ChartPoint> points = new ArrayList<>();
    private String title = "";

    public VitalChartPanel() {
        setPreferredSize(new Dimension(420, 220));
        setBackground(Color.WHITE);
    }

    /** 그릴 점과 제목을 바꾸고 다시 그린다. */
    public void setData(String title, List<ChartPoint> points) {
        this.title = title;
        this.points = new ArrayList<>(points);
        repaint();
    }

    /** 현재 그려지는 점(테스트·확인용). */
    public List<ChartPoint> points() { return List.copyOf(points); }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth() - PAD_L - PAD_R;
        int h = getHeight() - PAD_T - PAD_B;
        g.setColor(Color.DARK_GRAY);
        g.drawString(title, PAD_L, 16);
        g.drawLine(PAD_L, PAD_T, PAD_L, PAD_T + h);
        g.drawLine(PAD_L, PAD_T + h, PAD_L + w, PAD_T + h);
        if (points.isEmpty() || w <= 0 || h <= 0) {
            g.drawString("표시할 활력징후가 없습니다.", PAD_L + 12, PAD_T + h / 2);
            g.dispose();
            return;
        }
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (ChartPoint p : points) {
            min = Math.min(min, p.value());
            max = Math.max(max, p.value());
        }
        if (max - min < 1e-9) { min -= 1; max += 1; } // 값이 하나뿐이어도 그릴 수 있게
        double margin = (max - min) * 0.1;
        min -= margin;
        max += margin;
        g.drawString(format(max), 4, PAD_T + 4);
        g.drawString(format(min), 4, PAD_T + h);

        // 점은 시각순으로 같은 간격에 놓는다.
        int n = points.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = n == 1 ? PAD_L + w / 2 : PAD_L + (int) Math.round(w * i / (double) (n - 1));
            ys[i] = PAD_T + (int) Math.round(h * (max - points.get(i).value()) / (max - min));
        }
        g.setColor(Color.LIGHT_GRAY);
        g.setStroke(new BasicStroke(1.5f));
        for (int i = 1; i < n; i++) g.drawLine(xs[i - 1], ys[i - 1], xs[i], ys[i]);
        for (int i = 0; i < n; i++) {
            ChartPoint p = points.get(i);
            g.setColor(p.fromCase() ? CASE_COLOR : STUDENT_COLOR);
            if (p.fromCase()) g.fillOval(xs[i] - 4, ys[i] - 4, 8, 8);
            else g.drawOval(xs[i] - 4, ys[i] - 4, 8, 8);
            g.setColor(Color.DARK_GRAY);
            g.drawString(p.time(), xs[i] - 14, PAD_T + h + 16);
        }
        g.dispose();
    }

    private static String format(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
