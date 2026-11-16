package bo.okubi.desktop;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.geom.Path2D;
import javax.swing.JPanel;

/** Native-size OKUBI mark: sword crossing a retro controller. No downloaded images. */
public final class DesktopOkubiLogo extends JPanel {
    private final int pixels;
    public DesktopOkubiLogo(int pixels) {
        this.pixels=Math.max(32,pixels);
        setOpaque(false);
        setPreferredSize(new Dimension(this.pixels,this.pixels));
        getAccessibleContext().setAccessibleName("Logo OKUBI, espada atravesando mando");
    }
    public static BufferedImage icon(int pixels) {
        DesktopOkubiLogo logo=new DesktopOkubiLogo(pixels);
        logo.setSize(pixels,pixels);
        BufferedImage img=new BufferedImage(pixels,pixels,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=img.createGraphics();
        logo.paint(g);
        g.dispose();
        return img;
    }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g=(Graphics2D)graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            double sx=getWidth()/108.0,sy=getHeight()/108.0;
            g.scale(sx,sy);
            g.setColor(new Color(11,7,19));
            g.fillRoundRect(0,0,108,108,22,22);
            g.setColor(new Color(25,12,34));
            g.fillRoundRect(10,10,88,88,24,24);
            g.setStroke(new BasicStroke(6f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.setColor(new Color(250,40,105));
            g.drawArc(19,19,69,64,10,170);
            Path2D controller=new Path2D.Double();
            controller.moveTo(28,52);
            controller.curveTo(32,47,39,45,47,47);
            controller.lineTo(61,47);
            controller.curveTo(69,45,77,47,80,52);
            controller.lineTo(85,69);
            controller.curveTo(87,78,80,82,74,77);
            controller.lineTo(64,69);
            controller.lineTo(44,69);
            controller.lineTo(34,77);
            controller.curveTo(28,82,21,78,23,69);
            controller.closePath();
            g.setColor(new Color(20,16,34));
            g.fill(controller);
            g.setColor(new Color(243,71,131));
            g.setStroke(new BasicStroke(2.5f));
            g.draw(controller);
            g.setColor(new Color(252,218,233));
            g.setStroke(new BasicStroke(3f,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND));
            g.drawLine(35,53,35,65);g.drawLine(29,59,41,59);
            g.setColor(new Color(245,68,132));g.fillOval(65,51,7,7);
            g.setColor(new Color(188,101,237));g.fillOval(73,57,7,7);
            Path2D blade=new Path2D.Double();
            blade.moveTo(51,15);blade.lineTo(57,15);blade.lineTo(56,35);
            blade.lineTo(53,82);blade.lineTo(50,92);blade.lineTo(48,82);
            blade.lineTo(51,35);blade.closePath();
            g.setColor(new Color(27,11,38));g.fill(blade);
            g.setColor(new Color(250,212,228));g.setStroke(new BasicStroke(1f));g.draw(blade);
            g.setColor(new Color(252,241,247));
            Path2D stripe=new Path2D.Double();
            stripe.moveTo(52,22);stripe.lineTo(55,22);stripe.lineTo(54,79);
            stripe.lineTo(52,88);stripe.closePath();g.fill(stripe);
            g.setColor(new Color(213,37,95));g.fillRoundRect(43,34,22,4,2,2);
            g.setColor(new Color(233,178,206));g.fillRoundRect(50,10,7,7,2,2);
        } finally { g.dispose(); }
    }
}
