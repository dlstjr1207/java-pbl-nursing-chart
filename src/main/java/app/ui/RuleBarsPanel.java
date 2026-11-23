package app.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JPanel;

/**
 * 항목별 점수 막대 (결과 화면, polish B-3). 배점(D30) 대비 받은 점수를 가로 막대로 그린다.
 * 산출하지 못한 항목은 "미산출"로 적는다. 배점·채점 방식은 바꾸지 않고 보여 주기만 한다.
 */
public class RuleBarsPanel extends JPanel {
    private static final int ROW = 30;
    private static final int LABEL_W = 170;
    private static final int VALUE_W = 90;

    /** 규칙 ID → {받은 점수(없으면 null), 배점} */
    private Map<String, double[]> rows = new LinkedHashMap<>();

    public RuleBarsPanel() {
        setOpaque(false);
    }

    /** 배점 순서대로 받은 점수를 넣는다. score가 null이면 미산출. */
    public void setScores(Map<String, Integer> weights, Map<String, Double> scores) {
        rows = new LinkedHashMap<>();
        weights.forEach((rule, w) -> {
            Double v = scores.get(rule);
            rows.put(rule, new double[] {v == null ? Double.NaN : v, w});
        });
        setPreferredSize(new Dimension(400, rows.size() * ROW + 8));
        revalidate();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setFont(Theme.font(Font.PLAIN, 13f));
        FontMetrics fm = g.getFontMetrics();
        int barW = Math.max(60, getWidth() - LABEL_W - VALUE_W - 16);
        int y = 4;
        for (Map.Entry<String, double[]> e : rows.entrySet()) {
            double got = e.getValue()[0];
            double max = e.getValue()[1];
            int base = y + (ROW + fm.getAscent()) / 2 - 3;
            g.setColor(Theme.TEXT);
            g.drawString(CheckLabels.rule(e.getKey()), 0, base);
            int bx = LABEL_W;
            int by = y + 8;
            int bh = ROW - 16;
            g.setColor(Theme.LINE);
            g.fillRoundRect(bx, by, barW, bh, bh, bh);
            String value;
            if (Double.isNaN(got)) {
                value = "미산출";
            } else {
                double ratio = max <= 0 ? 0 : Math.max(0, Math.min(1, got / max));
                g.setColor(color(ratio));
                g.fillRoundRect(bx, by, (int) Math.round(barW * ratio), bh, bh, bh);
                value = fmt(got) + " / " + fmt(max);
            }
            g.setColor(Theme.TEXT);
            g.drawString(value, bx + barW + 10, base);
            y += ROW;
        }
        g.dispose();
    }

    /** 다 받으면 초록, 절반 이상 남색, 그 아래 주황. */
    private static Color color(double ratio) {
        if (ratio >= 0.999) return Theme.GREEN;
        if (ratio >= 0.5) return Theme.NAVY;
        return Theme.ORANGE;
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.1f", v);
    }
}
