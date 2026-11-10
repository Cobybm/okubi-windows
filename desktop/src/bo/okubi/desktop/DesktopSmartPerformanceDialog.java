package bo.okubi.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Console-style local PC readiness dashboard. PS emulators are NEVER
 * downloaded, bundled or silently configured. ROM/save ownership unchanged.
 */
public final class DesktopSmartPerformanceDialog extends JDialog {
    private static final Color BG = new Color(19, 15, 29);
    private static final Color PANEL = new Color(36, 27, 49);
    private static final Color TEXT = new Color(242, 237, 249);
    private static final Color MUTED = new Color(192, 177, 208);
    private static final Color PINK = new Color(246, 94, 188);

    private final DesktopMulticonsoleCatalog catalog;
    private final DefaultListModel<DesktopMulticonsoleCatalog.Game> model =
            new DefaultListModel<>();
    private final JList<DesktopMulticonsoleCatalog.Game> gameList =
            new JList<>(model) {
                @Override protected void paintComponent(java.awt.Graphics original) {
                    super.paintComponent(original);
                    if (!model.isEmpty()) return;
                    java.awt.Graphics2D g = (java.awt.Graphics2D)original.create();
                    try {
                        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
                        g.setColor(DesktopSmartPcVisuals.MUTED);
                        g.setFont(new Font("Segoe UI", Font.BOLD, 15));
                        String heading = "TU BIBLIOTECA ESTÁ VACÍA";
                        java.awt.FontMetrics fm = g.getFontMetrics();
                        int width = getWidth();
                        int y = Math.max(70, getHeight() / 2 - 28);
                        g.drawString(heading, Math.max(8,
                                (width - fm.stringWidth(heading)) / 2), y);
                        g.setColor(DesktopSmartPcVisuals.PINK);
                        g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                        String line1 = "Agrega juegos propios de PS1, PS2 o PS3";
                        String line2 = "y OKUBI indicará cómo podrían funcionar.";
                        fm = g.getFontMetrics();
                        g.drawString(line1, Math.max(8,
                                (width - fm.stringWidth(line1)) / 2), y + 28);
                        g.drawString(line2, Math.max(8,
                                (width - fm.stringWidth(line2)) / 2), y + 49);
                    } finally { g.dispose(); }
                }
            };
    private final JTextArea hardwareText = area(5);
    private final JTextArea details = area(9);
    private final JComboBox<DesktopSmartPerformance.Mode> mode =
            new JComboBox<>(DesktopSmartPerformance.Mode.values());
    private final JLabel status = new JLabel("Selecciona un juego o importa una carpeta.");
    private final JLabel[] readiness =
            new JLabel[DesktopSmartPerformance.Console.values().length];
    private JButton play;
    private JButton rate;
    private JButton smartConfigButton;
    private final DesktopSmartPlayHistory feedback;
    private final DesktopPcsx2SmartConfig smartConfig;
    private final DesktopSmartSessionHistory sessions;
    private DesktopSmartHardware.Snapshot hardware;
    private Process running;
    private boolean settingMode;

    public DesktopSmartPerformanceDialog(JFrame owner) throws IOException {
        super(owner, "OKUBI · SMART PERFORMANCE · PS1 / PS2 / PS3", true);
        catalog = new DesktopMulticonsoleCatalog(DesktopMulticonsoleCatalog.defaultFile());
        feedback = new DesktopSmartPlayHistory(DesktopSmartPlayHistory.defaultFile());
        smartConfig = new DesktopPcsx2SmartConfig(DesktopPcsx2SmartConfig.defaultFolder());
        sessions = new DesktopSmartSessionHistory(DesktopSmartSessionHistory.defaultFile());
        setSize(1000, 665);
        setMinimumSize(new Dimension(760, 555));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel container = new JPanel(new BorderLayout(14, 12));
        container.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        container.setBackground(BG);
        setContentPane(container);

        JPanel header = new JPanel(new BorderLayout(12, 8));
        header.setBackground(PANEL);
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(73, 52, 91)),
                BorderFactory.createEmptyBorder(10, 13, 9, 13)));
        JLabel title = new JLabel("SMART PC  ·  ¿QUÉ CONSOLAS PUEDE EJECUTAR TU EQUIPO?");
        title.setForeground(PINK);
        title.setFont(new Font("Segoe UI", Font.BOLD, 15));
        header.add(title, BorderLayout.NORTH);
        hardwareText.setBackground(PANEL);
        hardwareText.setText("Detectando CPU, memoria RAM y GPU...\n"
                + "La compatibilidad de cada juego requiere comprobarse.");
        header.add(hardwareText, BorderLayout.CENTER);

        JPanel summary = new JPanel(new BorderLayout(4, 5));
        summary.setBackground(PANEL);
        summary.setPreferredSize(new Dimension(235, 137));
        JPanel consoleRatings = new JPanel(new GridLayout(3, 1, 5, 4));
        consoleRatings.setOpaque(false);
        int ratingIndex = 0;
        for (DesktopSmartPerformance.Console console :
                DesktopSmartPerformance.Console.values()) {
            JLabel rating = new JLabel(
                    DesktopSmartPcVisuals.ratingLabel(console, null));
            rating.setOpaque(true);
            rating.setBackground(DesktopSmartPcVisuals.FIELD);
            rating.setForeground(DesktopSmartPcVisuals.MUTED);
            rating.setFont(new Font("Segoe UI", Font.BOLD, 12));
            rating.setBorder(BorderFactory.createEmptyBorder(3, 11, 3, 9));
            rating.setToolTipText("Evaluación del hardware; no del juego individual");
            readiness[ratingIndex++] = rating;
            consoleRatings.add(rating);
        }
        summary.add(consoleRatings, BorderLayout.CENTER);
        JButton redetect = button("↻ ANALIZAR MI PC", this::detectHardware);
        summary.add(redetect, BorderLayout.SOUTH);
        header.add(summary, BorderLayout.EAST);
        container.add(header, BorderLayout.NORTH);

        JPanel main = new JPanel(new GridLayout(1, 2, 14, 0));
        main.setBackground(BG);
        gameList.setBackground(PANEL);
        gameList.setForeground(TEXT);
        gameList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        gameList.setFixedCellHeight(48);
        gameList.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        gameList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(
                    JList<?> list, Object value, int index, boolean selected,
                    boolean focused) {
                super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof DesktopMulticonsoleCatalog.Game game) {
                    String rating = hardware == null ? "Por analizar"
                            : DesktopSmartPerformance.recommend(game.console(),
                            hardware, feedback.effectiveMode(game, hardware))
                                    .rating().label;
                    var observed = feedback.observation(game, hardware).result();
                    if (observed != DesktopSmartPlayHistory.Result.UNTESTED)
                        rating = observed.label + " · " + rating;
                    setText("<html><b>" + html(game.title()) + "</b><br><small>"
                            + html(game.console().label) + " · "
                            + html(rating) + "</small></html>");
                    setToolTipText(game.file().toString());
                    setBackground(selected ? new Color(86, 43, 94) : PANEL);
                    setForeground(TEXT);
                }
                return this;
            }
        });
        gameList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) refreshDetails();
        });
        JScrollPane libraryScroll = new JScrollPane(gameList);
        libraryScroll.setBorder(DesktopSmartPcVisuals.panelBorder());
        libraryScroll.getViewport().setBackground(PANEL);
        JPanel left = new JPanel(new BorderLayout(0, 8));
        left.setBackground(PANEL);
        left.setBorder(BorderFactory.createEmptyBorder(10, 11, 11, 11));
        JLabel libraryTitle = new JLabel("BIBLIOTECA PLAYSTATION");
        libraryTitle.setForeground(PINK);
        libraryTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        left.add(libraryTitle, BorderLayout.NORTH);
        left.add(libraryScroll, BorderLayout.CENTER);
        main.add(left);

        JPanel right = new JPanel(new BorderLayout(9, 9));
        right.setBackground(PANEL);
        right.setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        JLabel sub = new JLabel("ESTADO Y AJUSTES DEL JUEGO");
        sub.setForeground(PINK);
        right.add(sub, BorderLayout.NORTH);
        JScrollPane detailsScroll = new JScrollPane(details);
        detailsScroll.setBorder(DesktopSmartPcVisuals.panelBorder());
        detailsScroll.getViewport().setBackground(PANEL);
        right.add(detailsScroll, BorderLayout.CENTER);
        JPanel options = new JPanel(new GridLayout(3, 1, 0, 7));
        options.setBackground(PANEL);
        JPanel profileRow = new JPanel(new BorderLayout(8, 0));
        profileRow.setBackground(PANEL);
        JLabel profile = new JLabel("Perfil por juego");
        profile.setForeground(TEXT);
        profileRow.add(profile, BorderLayout.WEST);
        DesktopSmartPcVisuals.darkCombo(mode);
        profileRow.add(mode, BorderLayout.CENTER);
        options.add(profileRow);
        rate = button("¿CÓMO FUNCIONÓ ESTE JUEGO?", this::rateGame);
        rate.setToolTipText("Resultado confirmado por ti en esta computadora; nunca FPS inventados");
        options.add(rate);
        smartConfigButton = button("SMART CONFIG PS2 · INACTIVO", this::toggleSmartConfig);
        smartConfigButton.setToolTipText("Opt-in: ajuste PCSX2 por juego, sin tocar su configuración global");
        options.add(smartConfigButton);
        mode.addActionListener(e -> {
            if (settingMode) return;
            var game = gameList.getSelectedValue();
            if (game == null) return;
            try {
                catalog.mode(game, (DesktopSmartPerformance.Mode)mode.getSelectedItem());
                refreshCatalog(game.file());
            } catch (IOException problem) {
                report(problem);
            }
        });
        right.add(options, BorderLayout.SOUTH);
        main.add(right);
        container.add(main, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 8));
        footer.setBackground(BG);
        // Two rows prevent button clipping at 800 px or Windows 125/150% DPI.
        JPanel tools = new JPanel(new GridLayout(2, 3, 9, 7));
        tools.setBackground(BG);
        tools.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
        tools.add(button("Añadir juego", this::addFile));
        tools.add(button("Importar carpeta", this::addFolder));
        tools.add(button("Elegir emulador .exe", this::chooseEmulator));
        tools.add(button("Página oficial", this::officialPage));
        tools.add(button("Quitar de lista", this::removeSelection));
        play = button("JUGAR", this::launch);
        tools.add(play);
        footer.add(tools, BorderLayout.NORTH);
        status.setForeground(MUTED);
        status.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        status.setPreferredSize(new Dimension(0, 22));
        status.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 0));
        footer.add(status, BorderLayout.SOUTH);
        container.add(footer, BorderLayout.SOUTH);
        refreshCatalog(null);
        detectHardware();
    }

    private static String html(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;");
    }
    private static JTextArea area(int rows) {
        JTextArea text = new JTextArea(rows, 25);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        text.setBackground(PANEL);
        text.setForeground(TEXT);
        text.setCaretColor(PINK);
        text.setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
        return text;
    }
    private static JButton button(String label, Runnable action) {
        return DesktopSmartPcVisuals.button(label, action);
    }

    private void refreshReadiness() {
        int i = 0;
        for (DesktopSmartPerformance.Console console :
                DesktopSmartPerformance.Console.values()) {
            JLabel rating = readiness[i++];
            rating.setText(DesktopSmartPcVisuals.ratingLabel(console, hardware));
            if (hardware == null) {
                rating.setForeground(DesktopSmartPcVisuals.MUTED);
            } else {
                var grade = DesktopSmartPerformance.recommend(console, hardware,
                        DesktopSmartPerformance.Mode.AUTO).rating();
                rating.setForeground(DesktopSmartPcVisuals.ratingColor(grade));
            }
        }
    }
    private void refreshCatalog(Path selected) {
        var previous = selected == null ? gameList.getSelectedValue() : null;
        Path target = selected == null
                ? previous == null ? null : previous.file() : selected;
        model.clear();
        int index = -1;
        for (var game : catalog.games()) {
            model.addElement(game);
            if (game.file().equals(target)) index = model.size() - 1;
        }
        if (index >= 0) gameList.setSelectedIndex(index);
        else if (!model.isEmpty()) gameList.setSelectedIndex(0);
        refreshDetails();
    }
    private void refreshDetails() {
        var game = gameList.getSelectedValue();
        play.setEnabled(game != null);
        rate.setEnabled(game != null && hardware != null
                && !DesktopSmartPlayHistory.hardwareId(hardware).isBlank());
        boolean ps2 = game != null
                && game.console() == DesktopSmartPerformance.Console.PS2;
        smartConfigButton.setEnabled(ps2 && hardware != null
                && !DesktopSmartPlayHistory.hardwareId(hardware).isBlank());
        smartConfigButton.setText(ps2 && smartConfig.enabled(game, hardware)
                ? "SMART CONFIG PS2 · ACTIVADO (desactivar)"
                : "SMART CONFIG PS2 · INACTIVO (activar)");
        settingMode = true;
        mode.setEnabled(game != null);
        mode.setSelectedItem(game == null ? DesktopSmartPerformance.Mode.AUTO : game.mode());
        settingMode = false;
        if (game == null) {
            details.setText("Importa una carpeta de juegos de tu propiedad.\n\n"
                    + "PS1: .cue, .chd, .pbp, .m3u\n"
                    + "PS2: .iso, .cso, .chd\n"
                    + "PS3: EBOOT.BIN extraído de contenido legítimo.\n\n"
                    + "Los archivos .iso y .chd pueden pertenecer a varias consolas: "
                    + "confirma la consola al añadirlos individualmente.\n\n"
                    + "No descargamos juegos, BIOS, firmware ni emuladores.");
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append("Juego: ").append(game.title()).append("\n");
        text.append("Consola: ").append(game.console().label).append("\n");
        text.append("Archivo: ").append(game.file()).append("\n");
        text.append("Disponible: ").append(game.available() ? "SÍ" : "NO").append("\n");
        Path exe = catalog.emulator(game.console());
        text.append("Emulador: ").append(exe == null ? "Falta elegir el ejecutable de "
                + game.console().emulatorName : exe.getFileName()).append("\n\n");
        if (hardware == null)
            text.append("Estado: pendiente de analizar hardware.");
        else {
            var tested = feedback.observation(game, hardware);
            var preferred = feedback.effectiveMode(game, hardware);
            var r = DesktopSmartPerformance.recommend(game.console(), hardware, preferred);
            var graphics = game.console() == DesktopSmartPerformance.Console.PS2
                    ? DesktopSmartGraphicsPlanner.plan(r, hardware, tested.result())
                    : null;
            text.append("RESULTADO EN ESTA PC:\n").append(tested.result().label)
                    .append("\n\nSESIONES DEL EMULADOR EN ESTA PC:\n")
                    .append(sessions.stats(game, hardware).summary())
                    .append("\n\nCOMPATIBILIDAD ESTIMADA DEL HARDWARE:\n")
                    .append(r.rating().label)
                    .append("\nPerfil para la próxima apertura: ").append(r.mode().label)
                    .append("\nResolución sugerida en emulador: ")
                    .append(r.suggestedScale()).append("×\n\n")
                    .append(r.reason()).append("\n\n")
                    .append("La compatibilidad específica de este juego, su región "
                            + "y versión no ha sido consultada en una base de títulos.\n")
                    .append("OKUBI recomienda un inicio seguro y no promete FPS.\n")
                    .append(game.console() == DesktopSmartPerformance.Console.PS2
                            ? smartConfig.enabled(game, hardware)
                                ? "SMART GRAPHICS 2.0 ACTIVADO (consentimiento):\n"
                                    + graphics.preview() + "\n"
                                    + "Antes de iniciar se comprobará si PCSX2 admite -gamecfg. "
                                    + "La configuración se aplica SOLO a esta sesión y puede "
                                    + "sustituir temporalmente otros ajustes específicos del título."
                                : "SMART GRAPHICS 2.0 desactivado: OKUBI no cambia "
                                    + "ningún ajuste gráfico. Para usarlo, activa SMART CONFIG."
                            : "PS1/PS3: sin cambios automáticos de configuración interna.");
        }
        details.setText(text.toString());
        details.setCaretPosition(0);
    }
    private void detectHardware() {
        status.setText("Analizando CPU, RAM y GPU sin cambiar Windows...");
        new SwingWorker<DesktopSmartHardware.Snapshot, Void>() {
            @Override protected DesktopSmartHardware.Snapshot doInBackground() {
                return DesktopSmartHardware.detect();
            }
            @Override protected void done() {
                try {
                    hardware = get();
                    hardwareText.setText("CPU: " + hardware.processor()
                            + "\nNúcleos lógicos: " + hardware.logicalCores()
                            + "   RAM: " + String.format(Locale.ROOT, "%.1f",
                            hardware.memoryGiB()) + " GiB"
                            + "\nGPU: " + hardware.graphicsAdapter()
                            + "\nEste diagnóstico es orientativo; no mide FPS por juego.");
                    refreshReadiness();
                    gameList.repaint();
                    refreshDetails();
                    status.setText("Análisis listo. Los juegos muestran compatibilidad estimada.");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (ExecutionException problem) {
                    hardware = null;
                    refreshReadiness();
                    status.setText("No se detectó hardware; selección manual disponible.");
                }
            }
        }.execute();
    }
    private void addFile() {
        JFileChooser choose = new JFileChooser();
        choose.setDialogTitle("Seleccionar juego legítimo PS1 / PS2 / PS3");
        if (choose.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path path = choose.getSelectedFile().toPath();
        var suggested = DesktopSmartPerformance.suggestConsole(
                path.getFileName().toString());
        if (suggested == null) {
            report(new IOException("Formato no admitido en esta fase: "
                    + path.getFileName()));
            return;
        }
        DesktopSmartPerformance.Console console = suggested;
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".iso") || name.endsWith(".chd")) {
            var selected = JOptionPane.showInputDialog(this,
                    "¿De qué consola es realmente este archivo?\n"
                    + "El formato por sí solo no permite adivinarlo.",
                    "Verificar consola", JOptionPane.QUESTION_MESSAGE, null,
                    new DesktopSmartPerformance.Console[]{
                            DesktopSmartPerformance.Console.PS1,
                            DesktopSmartPerformance.Console.PS2}, suggested);
            if (!(selected instanceof DesktopSmartPerformance.Console c)) return;
            console = c;
        }
        try {
            catalog.addGame(path, console);
            refreshCatalog(path.toAbsolutePath().normalize());
            status.setText("Juego añadido como referencia: " + path.getFileName());
        } catch (IOException problem) { report(problem); }
    }
    private void addFolder() {
        JFileChooser choose = new JFileChooser();
        choose.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        choose.setDialogTitle("Importar referencias PS1/PS2/PS3 (4 niveles)");
        if (choose.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path folder = choose.getSelectedFile().toPath();
        status.setText("Buscando juegos sin copiar ROMs...");
        new SwingWorker<Integer, Void>() {
            @Override protected Integer doInBackground() throws IOException {
                return catalog.addFolder(folder);
            }
            @Override protected void done() {
                try {
                    int total = get();
                    refreshCatalog(null);
                    status.setText(total + " juegos añadidos. Revisa .iso/.chd ambiguos.");
                } catch (Exception problem) {
                    report(problem);
                }
            }
        }.execute();
    }
    private DesktopSmartPerformance.Console currentConsole() {
        var game = gameList.getSelectedValue();
        if (game != null) return game.console();
        Object chosen = JOptionPane.showInputDialog(this, "Elige consola",
                "Configuración de emulador", JOptionPane.QUESTION_MESSAGE,
                null, DesktopSmartPerformance.Console.values(),
                DesktopSmartPerformance.Console.PS1);
        return chosen instanceof DesktopSmartPerformance.Console console ? console : null;
    }
    private void chooseEmulator() {
        var console = currentConsole();
        if (console == null) return;
        JFileChooser choose = new JFileChooser();
        choose.setDialogTitle("Seleccionar " + console.emulatorName + " oficial (.exe)");
        choose.setFileFilter(new FileNameExtensionFilter("Ejecutable de Windows", "exe"));
        if (choose.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        int confirmed = JOptionPane.showConfirmDialog(this,
                "Selecciona solo el emulador descargado de su página oficial.\n"
                + "OKUBI NO instalará BIOS, firmware ni ejecutará otros programas.\n\n"
                + choose.getSelectedFile().getAbsolutePath(),
                "Confirmar ejecutable externo", JOptionPane.YES_NO_OPTION);
        if (confirmed != JOptionPane.YES_OPTION) return;
        try {
            catalog.emulator(console, choose.getSelectedFile().toPath());
            refreshDetails();
            status.setText(console.emulatorName + " configurado para tus juegos.");
        } catch (IOException problem) { report(problem); }
    }
    private void officialPage() {
        var console = currentConsole();
        if (console == null) return;
        try {
            if (!Desktop.isDesktopSupported()) throw new IOException("Sin navegador.");
            Desktop.getDesktop().browse(URI.create(console.officialUrl));
        } catch (Exception problem) {
            status.setText("Página oficial: " + console.officialUrl);
            JOptionPane.showMessageDialog(this, console.officialUrl,
                    "Descarga oficial de " + console.emulatorName,
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }
    private void removeSelection() {
        var game = gameList.getSelectedValue();
        if (game == null) return;
        int ok = JOptionPane.showConfirmDialog(this,
                "Solo quitar de la biblioteca:\n" + game.title()
                + "\n\nNO se borrarán ROMs ni partidas.",
                "Quitar referencia", JOptionPane.YES_NO_OPTION);
        if (ok != JOptionPane.YES_OPTION) return;
        try {
            catalog.remove(game);
            refreshCatalog(null);
        } catch (IOException problem) { report(problem); }
    }
    private void launch() {
        var game = gameList.getSelectedValue();
        if (game == null || !game.available()) {
            report(new IOException("Juego no disponible."));
            return;
        }
        if (running != null && running.isAlive()) {
            status.setText("Ya hay una sesión externa abierta desde OKUBI.");
            return;
        }
        if (EmbeddedPlayerWindow.hasActiveSession()) {
            status.setText("Cierra primero el juego Libretro integrado.");
            return;
        }
        Path exe = catalog.emulator(game.console());
        if (exe == null) {
            status.setText("Falta elegir " + game.console().emulatorName + ".");
            chooseEmulator();
            return;
        }
        if (hardware == null) {
            status.setText("Termina primero el diagnóstico de hardware.");
            return;
        }
        var observed = feedback.observation(game, hardware).result();
        var preferred = feedback.effectiveMode(game, hardware);
        var recommendation = DesktopSmartPerformance.recommend(
                game.console(), hardware, preferred);
        boolean warning = recommendation.rating() != DesktopSmartPerformance.Rating.LIKELY
                || observed == DesktopSmartPlayHistory.Result.FAILED
                || observed == DesktopSmartPlayHistory.Result.SLOW;
        if (warning) {
            int permission = JOptionPane.showConfirmDialog(this,
                    "AVISO: " + recommendation.rating().label + "\n\n"
                    + recommendation.reason() + "\n\n"
                    + (game.console() == DesktopSmartPerformance.Console.PS2
                            && smartConfig.enabled(game, hardware)
                            ? "SMART CONFIG aplicará su perfil temporal en PCSX2.\n"
                            : "No se cambiará la configuración interna del emulador.\n")
                    + "¿Intentar abrir este juego de todas formas?",
                    "SMART PERFORMANCE · Riesgo de rendimiento",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (permission != JOptionPane.YES_OPTION) return;
        }
        final boolean applyPcsx2Profile =
                game.console() == DesktopSmartPerformance.Console.PS2
                && smartConfig.enabled(game, hardware);
        final DesktopSmartHardware.Snapshot pcAtLaunch = hardware;
        final java.util.concurrent.atomic.AtomicLong launchedAtNanos =
                new java.util.concurrent.atomic.AtomicLong();
        final DesktopSmartGraphicsPlanner.Plan graphicsPlan = applyPcsx2Profile
                ? DesktopSmartGraphicsPlanner.plan(recommendation, pcAtLaunch, observed)
                : null;
        play.setEnabled(false);
        status.setText("Iniciando " + game.title() + (graphicsPlan == null
                ? " sin cambiar gráficos..." : " con SMART GRAPHICS "
                        + graphicsPlan.scale() + "×..."));
        new SwingWorker<Process, Void>() {
            @Override protected Process doInBackground() throws IOException {
                Process process;
                if (applyPcsx2Profile) {
                    // Probe is NOT gameplay; do not count preflight time.
                    DesktopPcsx2CliProbe.verify(exe);
                    Path profile = smartConfig.prepareGraphicsProfile(
                            game, pcAtLaunch, graphicsPlan);
                    process = DesktopMulticonsoleLauncher.launchWithGameConfig(
                            game.console(), recommendation.mode(), exe,
                            game.file(), profile);
                } else {
                    process = DesktopMulticonsoleLauncher.launch(game.console(),
                            recommendation.mode(), exe, game.file());
                }
                launchedAtNanos.set(System.nanoTime());
                return process;
            }
            @Override protected void done() {
                play.setEnabled(true);
                try {
                    running = get();
                    status.setText("Emulador iniciado. Los guardados permanecen "
                            + "bajo control del emulador externo.");
                    Process launched = running;
                    Path launchedGame = game.file();
                    launched.onExit().thenRun(() -> {
                        long processStart = launchedAtNanos.get();
                        long nanos = processStart == 0L ? 0L
                                : Math.max(0L, System.nanoTime() - processStart);
                        long duration = java.util.concurrent.TimeUnit.NANOSECONDS
                                .toSeconds(nanos);
                        try {
                            sessions.completed(game, pcAtLaunch,
                                    duration, launched.exitValue());
                        } catch (IOException failure) {
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                if (isDisplayable())
                                    status.setText("No se pudo guardar el tiempo de sesión: "
                                            + failure.getMessage());
                            });
                            return;
                        }
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            if (isDisplayable()
                                    && gameList.getSelectedValue() != null
                                    && launchedGame.equals(gameList.getSelectedValue().file())) {
                                refreshDetails();
                                status.setText("Sesión registrada. Indica cómo funcionó "
                                        + "y, opcionalmente, los FPS que observaste.");
                            }
                        });
                    });
                } catch (Exception problem) {
                    report(problem);
                }
            }
        }.execute();
    }
    /**
     * Optional per-game opt-in: never edits PCSX2 global/per-title settings.
     * Disabling omits -gamecfg on future launches; no private saves touched.
     */
    private void toggleSmartConfig() {
        var game = gameList.getSelectedValue();
        if (game == null || game.console() != DesktopSmartPerformance.Console.PS2
                || hardware == null) return;
        boolean old = smartConfig.enabled(game, hardware);
        if (!old) {
            int answer = JOptionPane.showConfirmDialog(this,
                    "Activar ajuste gráfico automático PCSX2 PARA ESTE JUEGO?\n\n"
                    + "OKUBI generará un archivo .ini propio para este juego y PC,\n"
                    + "y lo utilizará mediante -gamecfg solo cuando abras desde OKUBI.\n"
                    + "Elegirá 1×/2×, FXAA y filtrado anisotrópico limitado\n"
                    + "según hardware y perfil, sin cambiar BIOS, tarjetas ni guardados.\n"
                    + "Comprobará la opción -gamecfg de TU versión de PCSX2.\n\n"
                    + "ATENCIÓN: mientras el perfil esté activo, PCSX2 podría no aplicar\n"
                    + "otras opciones personalizadas específicas de este juego.\n"
                    + "Si desactivas SMART CONFIG, dejará de enviarse -gamecfg.\n\n"
                    + "¿Deseas activarlo?",
                    "SMART CONFIG · consentimiento por juego",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (answer != JOptionPane.YES_OPTION) return;
        }
        try {
            smartConfig.setEnabled(game, hardware, !old);
            refreshDetails();
            status.setText(old ? "SMART CONFIG desactivado: ajustes originales de PCSX2."
                    : "SMART GRAPHICS activado: resolución, FXAA y filtrado "
                            + "limitados para el próximo inicio PS2.");
        } catch (IOException error) {
            report(error);
        }
    }

    /** Explicit confirmation; no FPS guesses based on process exit time. */
    private void rateGame() {
        var game = gameList.getSelectedValue();
        if (game == null || hardware == null
                || DesktopSmartPlayHistory.hardwareId(hardware).isBlank()) {
            status.setText("Detecta la PC y selecciona el juego para registrar resultado.");
            return;
        }
        Object[] choices = {"Funcionó bien", "Funcionó lento", "No inició", "Cancelar"};
        int chosen = JOptionPane.showOptionDialog(this,
                "¿Cómo funcionó «" + game.title() + "» en ESTA computadora?\n"
                + "Solo guarda tu respuesta, sin tocar partidas ni cambiar los "
                + "ajustes internos de DuckStation, PCSX2 o RPCS3.",
                "OKUBI · Resultado comprobado por ti",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, choices, choices[0]);
        if (chosen < 0 || chosen >= 3) return;
        var result = switch (chosen) {
            case 0 -> DesktopSmartPlayHistory.Result.GOOD;
            case 1 -> DesktopSmartPlayHistory.Result.SLOW;
            default -> DesktopSmartPlayHistory.Result.FAILED;
        };
        try {
            feedback.confirm(game, hardware, result);
            String input = JOptionPane.showInputDialog(this,
                    "Opcional: introduce los FPS observados en el contador "
                            + "de PCSX2/DuckStation/RPCS3.\n"
                            + "No los medimos automáticamente. Déjalo vacío si no sabes.",
                    "FPS informados por el usuario",
                    JOptionPane.QUESTION_MESSAGE);
            if (input != null && !input.trim().isEmpty()) {
                try {
                    int fps = Integer.parseInt(input.trim());
                    if (fps < 1 || fps > 240)
                        throw new NumberFormatException("Fuera de rango");
                    sessions.userFps(game, hardware, fps);
                } catch (NumberFormatException incorrect) {
                    JOptionPane.showMessageDialog(this,
                            "Introduce un número de FPS de 1 a 240, "
                                    + "o deja el campo vacío. "
                                    + "La valoración del juego sí quedó guardada.",
                            "FPS no guardados", JOptionPane.INFORMATION_MESSAGE);
                }
            }
            gameList.repaint();
            refreshDetails();
            status.setText("Resultado registrado para este juego y esta PC; "
                    + "los FPS solo aparecen si tú los informaste.");
        } catch (IOException problem) {
            report(problem);
        }
    }

    private void report(Exception error) {
        Throwable source = error.getCause() == null ? error : error.getCause();
        status.setText("Error: " + source.getMessage());
        JOptionPane.showMessageDialog(this, source.getMessage(),
                "OKUBI SMART PERFORMANCE", JOptionPane.WARNING_MESSAGE);
    }
}
