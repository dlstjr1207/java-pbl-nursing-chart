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
    /** 세로축 최소 범위와 눈금 간격 (NaN이면 값에 맞춰 자동). */
    private double axisMin = Double.NaN;
    private double axisMax = Double.NaN;
    private double tick = Double.NaN;

    public VitalChartPanel() {
        setPreferredSize(new Dimension(420, 220));
        setBackground(Color.WHITE);
    }

    /** 그릴 점과 제목을 바꾸고 다시 그린다. 세로축은 값에 맞춰 자동으로 잡는다. */
    public void setData(String title, List<ChartPoint> points) {
        setData(title, points, Double.NaN, Double.NaN, Double.NaN);
    }

    /**
     * 세로축 최소 범위(axisMin~axisMax)와 눈금 간격을 정해 그린다 (polish 7). 값이 범위 밖이면 눈금 단위로 넓힌다.
     */
    public void setData(String title, List<ChartPoint> points, double axisMin, double axisMax, double tick) {
        this.title = title;
        this.points = new ArrayList<>(points);
        this.axisMin = axisMin;
        this.axisMax = axisMax;
        this.tick = tick;
        repaint();
    }

    /** 실제로 쓰는 세로축 범위 {min, max} (테스트·확인용). */
    public double[] axisRange() {
        if (Double.isNaN(axisMin)) return autoRange();
        double min = axisMin;
        double max = axisMax;
        for (ChartPoint p : points) {
            while (p.value() < min) min -= tick;
            while (p.value() > max) max += tick;
        }
        return new double[] {min, max};
    }

    private double[] autoRange() {
        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (ChartPoint p : points) {
            min = Math.min(min, p.value());
            max = Math.max(max, p.value());
        }
        if (points.isEmpty()) return new double[] {0, 1};
        if (max - min < 1e-9) { min -= 1; max += 1; } // 값이 하나뿐이어도 그릴 수 있게
        double margin = (max - min) * 0.1;
        return new double[] {min - margin, max + margin};
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
        double[] range = axisRange();
        double min = range[0];
        double max = range[1];
        if (Double.isNaN(tick)) {
            g.drawString(format(max), 4, PAD_T + 4);
            g.drawString(format(min), 4, PAD_T + h);
        } else {
            // 눈금: 옅은 가로선과 왼쪽 숫자
            for (double v = Math.ceil(min / tick) * tick; v <= max + 1e-9; v += tick) {
                int y = PAD_T + (int) Math.round(h * (max - v) / (max - min));
                g.setColor(new Color(0xE5E7EB));
                g.drawLine(PAD_L + 1, y, PAD_L + w, y);
                g.setColor(Color.DARK_GRAY);
                String s = format(v);
                g.drawString(s, PAD_L - 6 - g.getFontMetrics().stringWidth(s), y + 4);
            }
        }

        // 점은 시각순으로 같은 간격에 놓는다.
        int n = points.size();
        int[] xs = new int[n];
        int[] ys = new int[n];
        for (int i = 0; i < n; i++) {
            // 양 끝 시각 글자가 잘리지 않게 좌우 24px 안쪽에 놓는다
            int inner = Math.max(1, w - 48);
            xs[i] = n == 1 ? PAD_L + w / 2 : PAD_L + 24 + (int) Math.round(inner * i / (double) (n - 1));
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
