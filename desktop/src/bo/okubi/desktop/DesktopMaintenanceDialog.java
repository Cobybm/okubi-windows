package bo.okubi.desktop;

import java.awt.*;
import java.io.IOException;
import javax.swing.*;

/** Windows-only maintenance surface: explicit, opt-in, safe-cache cleanup. */
public final class DesktopMaintenanceDialog extends JDialog {
    private static final Color BG = new Color(19, 15, 29);
    private static final Color FG = new Color(245, 239, 250);
    private static final Color MUTED = new Color(193, 174, 211);
    private final DesktopMaintenanceService service;
    private final JLabel summary = new JLabel();
    private final JButton clean = new JButton("LIMPIAR CACHÉ TEMPORAL");
    private final JButton refresh = new JButton("ANALIZAR");
    private DesktopMaintenanceService.Report current;

    public DesktopMaintenanceDialog(JFrame owner, DesktopMaintenanceService service) {
        super(owner, "OKUBI 遊 · SMART MAINTENANCE", true);
        this.service = service;
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(22, 24, 22, 24));
        JLabel title = new JLabel("SMART MAINTENANCE · ESPACIO TEMPORAL");
        title.setFont(new Font("Dialog", Font.BOLD, 18));
        title.setForeground(FG);
        root.add(title, BorderLayout.NORTH);
        JPanel body = new JPanel(new GridLayout(3, 1, 0, 10));
        body.setOpaque(false);
        summary.setForeground(FG);
        body.add(summary);
        JLabel note = new JLabel("Solo caché de vistas previas generadas por OKUBI.");
        note.setForeground(MUTED);
        body.add(note);
        JLabel safety = new JLabel("Nunca se eliminan partidas, ROMs, BIOS ni perfiles.");
        safety.setForeground(MUTED);
        body.add(safety);
        root.add(body, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actions.setOpaque(false);
        refresh.addActionListener(event -> inspectAsync());
        clean.addActionListener(event -> confirmClean());
        JButton close = new JButton("CERRAR");
        close.addActionListener(event -> dispose());
        actions.add(refresh);
        actions.add(clean);
        actions.add(close);
        root.add(actions, BorderLayout.SOUTH);
        setContentPane(root);
        setSize(620, 260);
        setMinimumSize(new Dimension(550, 250));
        setLocationRelativeTo(owner);
        current = null;
        clean.setEnabled(false);
        inspectAsync();
    }

    private void inspectAsync() {
        refresh.setEnabled(false);
        clean.setEnabled(false);
        summary.setText("Analizando caché segura...");
        new SwingWorker<DesktopMaintenanceService.Report, Void>() {
            @Override protected DesktopMaintenanceService.Report doInBackground() throws IOException {
                return service.inspect();
            }
            @Override protected void done() {
                refresh.setEnabled(true);
                try {
                    current = get();
                    summary.setText(String.format(
                            "Archivos temporales: %d · Espacio recuperable: %.2f MiB",
                            current.files(), current.bytes() / 1048576.0));
                    clean.setEnabled(current.files() > 0);
                } catch (Exception problem) {
                    current = null;
                    summary.setText("La carpeta no se puede revisar de forma segura.");
                    JOptionPane.showMessageDialog(DesktopMaintenanceDialog.this,
                            "No se eliminó nada. " + problem.getMessage(),
                            "SMART MAINTENANCE", JOptionPane.WARNING_MESSAGE);
                }
            }
        }.execute();
    }

    private void confirmClean() {
        if (current == null || current.files() == 0) return;
        int result = JOptionPane.showConfirmDialog(this,
                "Se eliminarán únicamente " + current.files()
                + " archivos de caché de vistas previas ("
                + String.format("%.2f", current.bytes() / 1048576.0)
                + " MiB).\n¿Continuar? Esto no incluye partidas ni juegos.",
                "Confirmar limpieza de caché", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) return;
        clean.setEnabled(false);
        refresh.setEnabled(false);
        summary.setText("Limpiando caché seleccionada...");
        new SwingWorker<DesktopMaintenanceService.Report, Void>() {
            @Override protected DesktopMaintenanceService.Report doInBackground() throws IOException {
                return service.cleanConfirmed();
            }
            @Override protected void done() {
                refresh.setEnabled(true);
                try {
                    DesktopMaintenanceService.Report removed = get();
                    JOptionPane.showMessageDialog(DesktopMaintenanceDialog.this,
                            "Limpieza completada: " + removed.files()
                                    + " archivos de caché. Las partidas no se tocaron.",
                            "SMART MAINTENANCE", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception problem) {
                    JOptionPane.showMessageDialog(DesktopMaintenanceDialog.this,
                            "Limpieza interrumpida. " + problem.getMessage(),
                            "SMART MAINTENANCE", JOptionPane.WARNING_MESSAGE);
                }
                inspectAsync();
            }
        }.execute();
    }
}
