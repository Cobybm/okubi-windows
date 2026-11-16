package bo.okubi.desktop;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;

/**
 * Browse curated homebrew without replacing the existing ROM import flow.
 * Only an allowlisted open-source Game Boy entry supports direct download.
 * Other entries open their rights holder's official page.
 */
public final class DesktopHomebrewDialog {
    private DesktopHomebrewDialog() { }

    public static void show(JFrame owner, Consumer<Path> onImported) {
        List<DesktopHomebrewDownloads.Entry> entries = DesktopHomebrewDownloads.entries();
        JDialog dialog = new JDialog(owner, "OKUBI 遊 · Descubrir juegos",
                Dialog.ModalityType.MODELESS);
        dialog.setSize(new Dimension(760, 520));
        dialog.setMinimumSize(new Dimension(560, 420));
        dialog.setLocationRelativeTo(owner);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(javax.swing.BorderFactory.createEmptyBorder(18, 18, 18, 18));
        dialog.setContentPane(root);
        JLabel title = new JLabel("DESCUBRE · Juegos gratuitos y fuentes oficiales");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        root.add(title, BorderLayout.NORTH);

        javax.swing.DefaultListModel<String> model = new javax.swing.DefaultListModel<>();
        for (DesktopHomebrewDownloads.Entry entry : entries)
            model.addElement(entry.title() + "   ·   " + entry.platform());
        JList<String> list = new JList<>(model);
        list.setSelectedIndex(0);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(300, 280));
        root.add(scroll, BorderLayout.WEST);

        JTextArea description = new JTextArea();
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setFont(description.getFont().deriveFont(14f));
        description.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        root.add(new JScrollPane(description), BorderLayout.CENTER);
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        JButton download = new JButton("Descargar e importar");
        JButton official = new JButton("Abrir página oficial");
        JButton close = new JButton("Cerrar");
        JLabel state = new JLabel(" ");
        bottom.add(state);
        bottom.add(download);
        bottom.add(official);
        bottom.add(close);
        root.add(bottom, BorderLayout.SOUTH);

        Runnable refresh = () -> {
            DesktopHomebrewDownloads.Entry entry = entries.get(Math.max(0, list.getSelectedIndex()));
            boolean direct = DesktopHomebrewDownloads.authorizedDirect(entry);
            description.setText(entry.title() + "\n\nPlataforma: " + entry.platform()
                    + "\n\nFuente oficial: " + entry.page()
                    + "\n\n" + (direct
                    ? "Descarga directa autorizada. OKUBI verificará el archivo y lo "
                    + "agregará a tu biblioteca personal sin tocar partidas."
                    : "La descarga automática no está autorizada o no se puede verificar "
                    + "para esta ficha. Visita la página oficial del creador; "
                    + "si obtienes una copia legítima puedes importarla en OKUBI.")
                    + "\n\nNo se incluyen ROMs comerciales, BIOS ni claves de consola.");
            download.setEnabled(direct);
        };
        list.addListSelectionListener(event -> { if (!event.getValueIsAdjusting()) refresh.run(); });
        official.addActionListener(event -> {
            DesktopHomebrewDownloads.Entry entry = entries.get(Math.max(0, list.getSelectedIndex()));
            try {
                java.net.URI url = java.net.URI.create(entry.page());
                if (!"https".equalsIgnoreCase(url.getScheme())) throw new IOException("Enlace inválido");
                if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                    throw new IOException("No se encontró un navegador compatible.");
                Desktop.getDesktop().browse(url);
            } catch (IOException | IllegalArgumentException error) {
                JOptionPane.showMessageDialog(dialog, error.getMessage(), "OKUBI",
                        JOptionPane.WARNING_MESSAGE);
            }
        });
        download.addActionListener(event -> {
            DesktopHomebrewDownloads.Entry entry = entries.get(Math.max(0, list.getSelectedIndex()));
            if (!DesktopHomebrewDownloads.authorizedDirect(entry)) return;
            int confirm = JOptionPane.showConfirmDialog(dialog,
                    "Descargar " + entry.title() + " desde Homebrew Hub?\n"
                    + "La copia se guardará en tu carpeta OKUBI/homebrew.",
                    "OKUBI · Descarga gratuita autorizada", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;
            download.setEnabled(false);
            list.setEnabled(false);
            state.setText("Descargando y verificando...");
            new SwingWorker<DesktopHomebrewDownloads.Receipt, Void>() {
                @Override protected DesktopHomebrewDownloads.Receipt doInBackground()
                        throws Exception {
                    return DesktopHomebrewDownloads.download(
                            entry, DesktopHomebrewDownloads.defaultDirectory());
                }
                @Override protected void done() {
                    list.setEnabled(true);
                    refresh.run();
                    if (!dialog.isDisplayable()) return;
                    try {
                        DesktopHomebrewDownloads.Receipt receipt = get();
                        onImported.accept(receipt.file());
                        state.setText("JUEGO IMPORTADO · SHA-256 verificado");
                        JOptionPane.showMessageDialog(dialog,
                                "Juego disponible en tu biblioteca:\n"
                                + receipt.file().getFileName()
                                + "\nSHA-256: " + receipt.sha256(),
                                "OKUBI · Importación completa",
                                JOptionPane.INFORMATION_MESSAGE);
                    } catch (Exception failure) {
                        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                        state.setText("No se pudo completar la descarga.");
                        JOptionPane.showMessageDialog(dialog, cause.getMessage(), "OKUBI",
                                JOptionPane.WARNING_MESSAGE);
                    }
                }
            }.execute();
        });
        close.addActionListener(event -> dialog.dispose());
        refresh.run();
        dialog.setVisible(true);
    }
}
