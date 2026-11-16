package bo.okubi.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * Local, offline SAVE VAULT UI. Every hash, archive verification and file
 * operation is delegated to SwingWorker, never the event-dispatch thread.
 * Restore requires explicit confirmation and is blocked while emulating.
 */
public final class DesktopSaveVaultDialog {
    private static final Color BG = new Color(17, 12, 29);
    private static final Color PANEL = new Color(32, 23, 45);
    private static final Color PINK = new Color(245, 58, 155);
    private static final Color TEXT = new Color(240, 236, 248);
    private static final Color MUTED = new Color(177, 166, 192);

    private final JDialog dialog;
    private final DesktopSaveVault vault;
    private final JComboBox<DesktopLibrary.Game> games;
    private final DefaultListModel<DesktopSaveVault.Entry> model = new DefaultListModel<>();
    private final JList<DesktopSaveVault.Entry> history = new JList<>(model);
    private final JLabel status;
    private final JButton copy;
    private final JButton recover;
    private final JButton refresh;
    private final JButton closeButton;
    private boolean busy;

    public DesktopSaveVaultDialog(JFrame parent, List<DesktopLibrary.Game> entries,
                                  Path selectedRom) {
        if (parent == null || entries == null || entries.isEmpty())
            throw new IllegalArgumentException("SAVE VAULT necesita juegos en biblioteca");
        vault = new DesktopSaveVault(DesktopSaveVault.defaultVaultRoot(),
                DesktopSaves.defaultRoot());
        dialog = new JDialog(parent, "OKUBI · SAVE VAULT · Partidas protegidas", true);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(BG);
        root.setBorder(BorderFactory.createEmptyBorder(18, 20, 18, 20));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JLabel heading = label("SAVE VAULT  ·  PROTECCIÓN DE PARTIDAS", 19, PINK, true);
        top.add(heading);
        top.add(Box.createVerticalStrut(7));
        top.add(label("Copias independientes de SRAM y RTC. Los estados F5/F9 no se modifican.",
                12, MUTED, false));
        top.add(Box.createVerticalStrut(13));
        games = new JComboBox<>(entries.toArray(new DesktopLibrary.Game[0]));
        games.setFont(new Font("Segoe UI", Font.BOLD, 13));
        games.setForeground(TEXT);
        games.setBackground(PANEL);
        games.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
        games.setPreferredSize(new Dimension(760, 43));
        games.setRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list,
                    Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index,
                        isSelected, cellHasFocus);
                if (value instanceof DesktopLibrary.Game game)
                    setText(game.title() + "  ·  " + game.console.label
                            + (game.isAvailable() ? "" : "  [ROM no encontrada]"));
                return this;
            }
        });
        for (int index = 0; index < games.getItemCount(); index++)
            if (games.getItemAt(index).rom.equals(selectedRom)) {
                games.setSelectedIndex(index);
                break;
            }
        top.add(games);
        root.add(top, BorderLayout.NORTH);

        history.setBackground(PANEL);
        history.setForeground(TEXT);
        history.setSelectionBackground(new Color(97, 39, 91));
        history.setSelectionForeground(TEXT);
        history.setFixedCellHeight(46);
        history.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        history.setCellRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list,
                    Object value, int index, boolean selected, boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof DesktopSaveVault.Entry entry) {
                    setText("  " + entry.title());
                    if (!entry.valid()) setForeground(new Color(255, 165, 146));
                    setToolTipText(entry.valid()
                            ? "Respaldo verificado por SHA-256 · " + entry.folder()
                            : "No se puede recuperar: " + entry.problem());
                }
                return this;
            }
        });
        history.setToolTipText("Copias locales verificadas, de más reciente a más antigua");
        JScrollPane scroll = new JScrollPane(history);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(90, 62, 112)));
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        root.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 12));
        bottom.setOpaque(false);
        status = label("Abriendo historial local...", 12, MUTED, false);
        bottom.add(status, BorderLayout.NORTH);

        JPanel controls = new JPanel(new GridLayout(1, 4, 10, 0));
        controls.setOpaque(false);
        copy = new CinemaButton("＋ CREAR COPIA", true, this::makeBackup);
        recover = new CinemaButton("↶ RECUPERAR", false, this::restore);
        refresh = new CinemaButton("↻ ACTUALIZAR", false, this::refreshHistory);
        closeButton = new CinemaButton("✕ CERRAR", false, this::requestClose);
        controls.add(copy);
        controls.add(recover);
        controls.add(refresh);
        controls.add(closeButton);
        controls.setPreferredSize(new Dimension(780, 45));
        bottom.add(controls, BorderLayout.CENTER);
        bottom.add(label("Antes de restaurar, OKUBI crea otra copia de tu partida actual. "
                + "No abras un juego mientras operas aquí.", 11, MUTED, false),
                BorderLayout.SOUTH);
        root.add(bottom, BorderLayout.SOUTH);

        history.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) updateActions();
        });
        games.addActionListener(e -> {
            if (!busy && dialog.isDisplayable()) refreshHistory();
        });
        dialog.setContentPane(root);
        dialog.setSize(Math.min(930, Math.max(690, parent.getWidth() - 60)),
                Math.min(625, Math.max(460, parent.getHeight() - 65)));
        dialog.setLocationRelativeTo(parent);
        dialog.getRootPane().registerKeyboardAction(e -> requestClose(),
                javax.swing.KeyStroke.getKeyStroke("ESCAPE"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowOpened(java.awt.event.WindowEvent e) {
                refreshHistory();
            }
            @Override public void windowClosing(java.awt.event.WindowEvent e) {
                requestClose();
            }
        });
        updateActions();
    }

    public void show() { dialog.setVisible(true); }

    private void requestClose() {
        if (busy) {
            status.setText("Operación de partidas en curso. No cierres OKUBI hasta que termine.");
            java.awt.Toolkit.getDefaultToolkit().beep();
            return;
        }
        dialog.dispose();
    }

    private static JLabel label(String text, int size, Color color, boolean bold) {
        JLabel result = new JLabel(text);
        result.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size));
        result.setForeground(color);
        result.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        return result;
    }

    private DesktopLibrary.Game game() {
        return (DesktopLibrary.Game) games.getSelectedItem();
    }

    private void updateActions() {
        DesktopLibrary.Game game = game();
        copy.setEnabled(!busy && game != null && game.isAvailable());
        DesktopSaveVault.Entry entry = history.getSelectedValue();
        recover.setEnabled(!busy && game != null && game.isAvailable()
                && entry != null && entry.valid());
        refresh.setEnabled(!busy);
        games.setEnabled(!busy);
        closeButton.setEnabled(!busy);
    }

    private <T> void execute(String working,
                             java.util.concurrent.Callable<T> task,
                             Consumer<T> onDone) {
        if (busy || EmbeddedPlayerWindow.hasActiveSession()) {
            status.setText("Cierra el emulador antes de gestionar partidas.");
            return;
        }
        busy = true;
        status.setText(working);
        updateActions();
        SwingWorker<T, Void> worker = new SwingWorker<>() {
            @Override protected T doInBackground() throws Exception {
                return task.call();
            }
            @Override protected void done() {
                busy = false;
                updateActions();
                if (!dialog.isDisplayable()) return;
                try { onDone.accept(get()); }
                catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    status.setText("Operación interrumpida. Revisa el historial.");
                } catch (java.util.concurrent.ExecutionException error) {
                    String detail = error.getCause() == null ? error.getMessage()
                            : error.getCause().getMessage();
                    status.setText("No se completó: " + detail);
                    JOptionPane.showMessageDialog(dialog, "No se completó la operación.\n"
                            + detail + "\nTus respaldos previos permanecen en SAVE VAULT.",
                            "SAVE VAULT · Revisión necesaria", JOptionPane.WARNING_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void refreshHistory() {
        DesktopLibrary.Game selected = game();
        if (selected == null) return;
        execute("Verificando copias y huellas SHA-256...", () -> vault.history(selected),
                list -> {
                    model.clear();
                    for (DesktopSaveVault.Entry entry : list) model.addElement(entry);
                    if (!list.isEmpty()) history.setSelectedIndex(0);
                    status.setText(list.isEmpty()
                            ? "Aún no hay copias para este juego. Usa CREAR COPIA."
                            : list.size() + " copias locales · ordenadas por fecha · SHA-256");
                    updateActions();
                });
    }

    private void makeBackup() {
        DesktopLibrary.Game selected = game();
        if (selected == null || !selected.isAvailable()) return;
        execute("Creando copia protegida (SRAM/RTC)...", () -> vault.backup(selected),
                result -> {
                    status.setText("Copia verificada: " + result.title());
                    refreshHistory();
                });
    }

    private void restore() {
        DesktopSaveVault.Entry entry = history.getSelectedValue();
        DesktopLibrary.Game selected = game();
        if (entry == null || !entry.valid() || selected == null
                || EmbeddedPlayerWindow.hasActiveSession()) return;
        String missing = (!entry.ram() ? "No incluye SRAM. " : "")
                + (!entry.rtc() ? "No incluye RTC. " : "");
        int approved = JOptionPane.showConfirmDialog(dialog,
                "Se restaurará la copia:\n" + entry.title()
                + "\n\nJuego: " + selected.title()
                + "\n" + missing
                + "\nLos componentes ausentes NO se borrarán."
                + "\nAntes de reemplazar nada se guardará tu progreso actual "
                + "en una copia PROTECCIÓN PREVIA.\n\n¿Confirmas la restauración?",
                "SAVE VAULT · Confirmar recuperación",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (approved != JOptionPane.YES_OPTION) return;
        execute("Verificando y recuperando copia...", () -> vault.restore(selected, entry),
                previous -> {
                    status.setText(previous == null
                            ? "Restauración verificada · no había progreso anterior."
                            : "Restauración verificada · partida anterior protegida.");
                    refreshHistory();
                });
    }
}
