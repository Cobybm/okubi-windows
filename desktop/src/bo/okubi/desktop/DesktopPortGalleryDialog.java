package bo.okubi.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.Rectangle;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Phase 77F: read-only, large-screen modal viewer for an individual port.
 * Reuses the same verified source list, profile cache and manual priority.
 * No ROMs, saves, executables, game processes or emulator settings are touched.
 */
public final class DesktopPortGalleryDialog extends JDialog {
    private static final Color BACKGROUND = new Color(13, 9, 23);
    private static final Color LABEL = new Color(239, 227, 248);
    private final DesktopPortArtwork preview;

    public DesktopPortGalleryDialog(Window owner, String title,
                                    DesktopPortArtworkService service,
                                    DesktopPortArtworkService.Ref ref,
                                    int initialIndex) {
        super(owner, "OKUBI 遊 · Galería CINEMA", ModalityType.DOCUMENT_MODAL);
        if (service == null || ref == null)
            throw new IllegalArgumentException("Verified source service and port required");
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 10));
        getContentPane().setBackground(BACKGROUND);

        JPanel header = new JPanel(new BorderLayout(8, 5));
        header.setBackground(BACKGROUND);
        header.setBorder(BorderFactory.createEmptyBorder(16, 20, 0, 20));
        JLabel name = new JLabel(title == null || title.isBlank()
                ? ref.fullName() : title);
        name.setForeground(LABEL);
        name.setFont(new Font("Segoe UI", Font.BOLD, 19));
        header.add(name, BorderLayout.NORTH);
        JLabel hint = new JLabel("◀ ▶ / LB RB: cambiar captura  ·  B / Esc: volver"
                + "  ·  Las imágenes personales tienen prioridad");
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        hint.setForeground(new Color(185, 167, 207));
        header.add(hint, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        preview = new DesktopPortArtwork(service, ref, 900, 490, initialIndex, true);
        preview.setBorder(BorderFactory.createEmptyBorder(5, 14, 3, 14));
        add(preview, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        footer.setBackground(BACKGROUND);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 14, 10, 15));
        JLabel label = new JLabel("Solo imágenes oficiales asociadas a este proyecto");
        label.setForeground(new Color(185, 167, 207));
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        footer.add(label);
        JButton close = DesktopPortsTheme.button("CERRAR", this::dispose);
        close.getAccessibleContext().setAccessibleDescription(
                "Cerrar galería y regresar al catálogo de ports");
        footer.add(close);
        add(footer, BorderLayout.SOUTH);

        new DesktopPortGalleryInput(this, preview);
        int maxWidth = 1100, maxHeight = 720;
        GraphicsConfiguration graphics = owner == null ? null : owner.getGraphicsConfiguration();
        if (graphics != null) {
            Rectangle screen = graphics.getBounds();
            maxWidth = Math.max(480, (int) Math.floor(screen.width * .92));
            maxHeight = Math.max(360, (int) Math.floor(screen.height * .86));
        }
        setSize(Math.min(980, maxWidth), Math.min(650, maxHeight));
        setMinimumSize(new Dimension(Math.min(520, getWidth()),
                Math.min(340, getHeight())));
        setLocationRelativeTo(owner);
    }

    public DesktopPortArtwork preview() { return preview; }
}
