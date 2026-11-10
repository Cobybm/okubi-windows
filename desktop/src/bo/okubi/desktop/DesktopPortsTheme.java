package bo.okubi.desktop;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JLabel;
import javax.swing.border.Border;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicComboBoxUI;

/**
 * Controls for the Ports dialog that do not inherit Windows native
 * white-on-white themes. No global UIManager/LAF changes to retro screens.
 */
public final class DesktopPortsTheme {
    public static final Color PANEL = new Color(32, 24, 45);
    public static final Color BUTTON = new Color(59, 36, 82);
    public static final Color BUTTON_ACTIVE = new Color(126, 43, 117);
    public static final Color LIST_SELECTED = new Color(83, 44, 115);
    public static final Color FOREGROUND = new Color(248, 240, 252);
    public static final Color BORDER = new Color(113, 75, 148);

    private DesktopPortsTheme() { }

    public static JButton button(String text, Runnable onClick) {
        JButton b = new JButton(text);
        // WindowsButtonUI ignores colors for themed controls. BasicUI respects
        // the per-component CINEMA palette without changing the whole app.
        b.setUI(new BasicButtonUI());
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setBorderPainted(true);
        b.setBackground(BUTTON);
        b.setForeground(FOREGROUND);
        b.setFont(new Font("Segoe UI", Font.BOLD, 11));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        b.getAccessibleContext().setAccessibleName(text);
        if (onClick != null) b.addActionListener(e -> onClick.run());
        return b;
    }

    public static <T> void combo(JComboBox<T> combo) {
        combo.setUI(new BasicComboBoxUI() {
            @Override protected JButton createArrowButton() {
                JButton arrow = new JButton("▾");
                arrow.setUI(new BasicButtonUI());
                arrow.setOpaque(true);
                arrow.setContentAreaFilled(true);
                arrow.setForeground(FOREGROUND);
                arrow.setBackground(BUTTON);
                arrow.setBorder(BorderFactory.createEmptyBorder(4, 9, 4, 9));
                arrow.setFocusable(false);
                return arrow;
            }
        });
        combo.setBackground(PANEL);
        combo.setForeground(FOREGROUND);
        combo.setOpaque(true);
        combo.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        combo.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(
                    JList<?> list, Object item, int index,
                    boolean selected, boolean focused) {
                JLabel text = (JLabel) super.getListCellRendererComponent(
                        list, item, index, selected, focused);
                text.setOpaque(true);
                text.setBackground(selected && index >= 0 ? LIST_SELECTED : PANEL);
                text.setForeground(FOREGROUND);
                text.setFont(new Font("Segoe UI", Font.PLAIN, 13));
                text.setBorder(BorderFactory.createEmptyBorder(7, 10, 7, 10));
                return text;
            }
        });
    }
}
