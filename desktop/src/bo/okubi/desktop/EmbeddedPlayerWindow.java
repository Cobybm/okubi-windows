package bo.okubi.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * First embedded OKUBI Windows player. Core execution, SRAM/RTC and JNI calls
 * live on ONE worker; Swing painting and keyboard events live on the EDT.
 */
public final class EmbeddedPlayerWindow {
    private static final AtomicBoolean SESSION_BUSY = new AtomicBoolean(false);
    private static final Color BG = new Color(12, 9, 20);
    private static final Color PINK = new Color(241, 67, 155);
    private static final Color MUTED = new Color(197, 184, 210);
    private final JFrame window;
    private final Picture picture;
    private final JLabel info;
    private final DesktopLibrary.Game game;
    private final Path selectedCore;
    private final Runnable afterLaunch;
    private final DesktopPlayStats statistics;
    private final DesktopPlaySettings playSettings;
    private final DesktopHudSettings hudSettings;
    private final DesktopVideoScaling videoScaling;
    private final JPanel controlOverlay;
    private final JPanel root;
    private final JPanel hudHeader;
    private final JPanel hudFooter;
    private final javax.swing.Timer hudTimer;
    private DesktopHudSettings.Mode hudMode;
    private boolean hudRevealed;
    private boolean pauseBeforeMenu;
    private volatile DesktopPlaySettings.Filter displayFilter;
    private volatile DesktopVideoScaling.Mode scalingMode;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicInteger buttons = new AtomicInteger();
    private final AtomicBoolean quickSave = new AtomicBoolean();
    private final AtomicBoolean quickLoad = new AtomicBoolean();
    private final AtomicBoolean capture = new AtomicBoolean();
    private final AtomicBoolean muted = new AtomicBoolean();
    private Rectangle previousSize;

    private EmbeddedPlayerWindow(DesktopLibrary.Game game, Path core, Runnable afterLaunch,
                                 DesktopPlayStats statistics) throws IOException {
        this.game = game;
        playSettings = new DesktopPlaySettings(DesktopPlaySettings.defaultFile());
        hudSettings = new DesktopHudSettings(DesktopHudSettings.defaultFile());
        videoScaling = new DesktopVideoScaling(DesktopVideoScaling.defaultFile());
        scalingMode = videoScaling.mode(game.console);
        hudMode = hudSettings.mode(game.console);
        hudRevealed = hudMode == DesktopHudSettings.Mode.AUTO;
        displayFilter = playSettings.filter(game.console);
        selectedCore = core;
        this.afterLaunch = afterLaunch;
        this.statistics = statistics;
        window = new JFrame("OKUBI · " + game.title());
        window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        window.setMinimumSize(new Dimension(720, 450));
        window.setSize(990, 760);
        window.setLocationRelativeTo(null);

        root = new JPanel(new BorderLayout());
        root.setBackground(BG);
        window.setContentPane(root);
        JPanel top = new JPanel(new BorderLayout(12, 0));
        top.setBackground(new Color(33, 20, 48));
        top.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        String brand = new Font(Font.DIALOG, Font.BOLD, 15).canDisplay('遊')
                ? "OKUBI 遊" : "OKUBI";
        JLabel name = new JLabel(brand + "  |  " + game.title());
        name.setFont(new Font("Segoe UI", Font.BOLD, 17));
        name.setForeground(PINK);
        top.add(name, BorderLayout.CENTER);
        JPanel actions = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.add(new CinemaButton("F5 Guardar", false, () -> quickSave.set(true)));
        actions.add(new CinemaButton("F9 Cargar", false, this::confirmLoad));
        actions.add(new CinemaButton("☰ MENÚ / ESC", true, this::toggleControlCenter));
        actions.add(new CinemaButton("⛶", false, this::toggleMaximize));
        actions.add(new CinemaButton("Salir", false, window::dispose));
        top.add(actions, BorderLayout.EAST);
        JPanel compact = new JPanel(new BorderLayout(10, 0));
        compact.setBackground(new Color(28, 19, 41));
        compact.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        JLabel compactName = new JLabel(brand + "  ·  " + game.title());
        compactName.setFont(new Font("Segoe UI", Font.BOLD, 13));
        compactName.setForeground(PINK);
        compact.add(compactName, BorderLayout.CENTER);
        JPanel compactButtons = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.RIGHT, 6, 0));
        compactButtons.setOpaque(false);
        CinemaButton compactMenu = new CinemaButton("☰ AJUSTES / ESC", true,
                this::toggleControlCenter);
        compactMenu.setPreferredSize(new Dimension(152, 27));
        CinemaButton compactClose = new CinemaButton("SALIR", false, window::dispose);
        compactClose.setPreferredSize(new Dimension(70, 27));
        compactButtons.add(compactMenu);
        compactButtons.add(compactClose);
        compact.add(compactButtons, BorderLayout.EAST);

        hudHeader = new JPanel(new java.awt.CardLayout());
        hudHeader.add(top, "FULL");
        hudHeader.add(compact, "COMPACT");
        hudHeader.setOpaque(false);
        root.add(hudHeader, BorderLayout.NORTH);

        picture = new Picture();
        picture.setFocusable(true);
        picture.setBackground(Color.BLACK);
        picture.setDoubleBuffered(true);
        picture.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        picture.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_P && !event.isConsumed()
                        && !Boolean.TRUE.equals((Boolean) picture.getClientProperty("pauseHeld"))) {
                    paused.set(!paused.get());
                    info(paused.get() ? "PAUSA · pulsa P para continuar"
                            : "Jugando · guardado automático cada 30 segundos");
                    picture.putClientProperty("pauseHeld", Boolean.TRUE);
                    event.consume();
                } else if (event.getKeyCode() == KeyEvent.VK_F1) {
                    peekHud();
                    event.consume();
                } else if (event.getKeyCode() == KeyEvent.VK_F2) {
                    if (!Boolean.TRUE.equals(picture.getClientProperty("scaleHeld"))) {
                        cycleScaling();
                        picture.putClientProperty("scaleHeld", Boolean.TRUE);
                    }
                    event.consume();
                } else if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    // The focused game panel handles Escape directly; overlay
                    // buttons use the root pane's WHEN_IN_FOCUSED_WINDOW binding.
                    toggleControlCenter();
                    event.consume();
                } else if (event.getKeyCode() == KeyEvent.VK_F11) {
                    if (!Boolean.TRUE.equals((Boolean) picture.getClientProperty("fullHeld"))) {
                        toggleMaximize();
                        picture.putClientProperty("fullHeld", Boolean.TRUE);
                    }
                    event.consume();
                } else if (event.getKeyCode() == KeyEvent.VK_F5) {
                    if (!Boolean.TRUE.equals(picture.getClientProperty("quickSaveHeld"))) {
                        quickSave.set(true);
                        picture.putClientProperty("quickSaveHeld", Boolean.TRUE);
                    }
                    event.consume();
                } else if (event.getKeyCode() == KeyEvent.VK_F9) {
                    if (!Boolean.TRUE.equals(picture.getClientProperty("quickLoadHeld"))) {
                        confirmLoad();
                        picture.putClientProperty("quickLoadHeld", Boolean.TRUE);
                    }
                    event.consume();
                } else if (event.getKeyCode() == KeyEvent.VK_F12) {
                    if (!Boolean.TRUE.equals(picture.getClientProperty("captureHeld"))) {
                        capture.set(true);
                        picture.putClientProperty("captureHeld", Boolean.TRUE);
                    }
                    event.consume();
                } else if (event.getKeyCode() == KeyEvent.VK_M) {
                    if (!Boolean.TRUE.equals(picture.getClientProperty("muteHeld"))) {
                        toggleMute();
                        picture.putClientProperty("muteHeld", Boolean.TRUE);
                    }
                    event.consume();
                } else {
                    int bit = playSettings.bitFor(game.console, event.getKeyCode());
                    if (bit >= 0 && !controlOverlay.isVisible())
                        buttons.getAndUpdate(value -> value | (1 << bit));
                }
            }
            @Override public void keyReleased(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_P)
                    picture.putClientProperty("pauseHeld", Boolean.FALSE);
                else if (event.getKeyCode() == KeyEvent.VK_F2)
                    picture.putClientProperty("scaleHeld", Boolean.FALSE);
                else if (event.getKeyCode() == KeyEvent.VK_F11)
                    picture.putClientProperty("fullHeld", Boolean.FALSE);
                else if (event.getKeyCode() == KeyEvent.VK_F5)
                    picture.putClientProperty("quickSaveHeld", Boolean.FALSE);
                else if (event.getKeyCode() == KeyEvent.VK_F9)
                    picture.putClientProperty("quickLoadHeld", Boolean.FALSE);
                else if (event.getKeyCode() == KeyEvent.VK_F12)
                    picture.putClientProperty("captureHeld", Boolean.FALSE);
                else if (event.getKeyCode() == KeyEvent.VK_M)
                    picture.putClientProperty("muteHeld", Boolean.FALSE);
                int bit = playSettings.bitFor(game.console, event.getKeyCode());
                if (bit >= 0) buttons.getAndUpdate(value -> value & ~(1 << bit));
            }
        });
        root.add(picture, BorderLayout.CENTER);

        // An actual pause-overlay above the in-game video, not an external
        // emulator/settings window. Works for keyboard focus and accessibility.
        controlOverlay = createControlOverlay();
        window.setGlassPane(controlOverlay);
        controlOverlay.setVisible(false);
        window.getRootPane().getInputMap(
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                "okubi-toggle-control-center");
        window.getRootPane().getActionMap().put("okubi-toggle-control-center",
                new javax.swing.AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                        toggleControlCenter();
                    }
                });
        window.getRootPane().getInputMap(
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0),
                "okubi-peek-hud");
        window.getRootPane().getActionMap().put("okubi-peek-hud",
                new javax.swing.AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                        peekHud();
                    }
                });
        window.getRootPane().getInputMap(
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0),
                "okubi-cycle-scaling");
        window.getRootPane().getActionMap().put("okubi-cycle-scaling",
                new javax.swing.AbstractAction() {
                    @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                        cycleScaling();
                    }
                });

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBackground(new Color(29, 19, 41));
        footer.setBorder(BorderFactory.createEmptyBorder(9, 16, 9, 16));
        JLabel help = new JLabel("ESC  MENÚ    |    F1  HUD    |    F2  ESCALA    |    F5  GUARDAR    |    F9  CARGAR    |    F12  CAPTURA    |    F11  PANTALLA    |    P  PAUSA");
        help.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        help.setForeground(MUTED);
        footer.add(help, BorderLayout.NORTH);
        info = new JLabel("Iniciando núcleo Libretro…");
        info.setForeground(PINK);
        info.setFont(new Font("Segoe UI", Font.BOLD, 12));
        footer.add(info, BorderLayout.SOUTH);
        hudFooter = footer;
        root.add(hudFooter, BorderLayout.SOUTH);

        hudTimer = new javax.swing.Timer(2900, event -> {
            // Swing timer runs on the EDT: only GUI elements are affected.
            if (!controlOverlay.isVisible() && DesktopHudSettings.autoHide(hudMode)) {
                hudRevealed = false;
                updateHud();
            }
        });
        hudTimer.setRepeats(false);
        MouseAdapter wakeOnPointer = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) { pointerActivity(); }
        };
        picture.addMouseMotionListener(wakeOnPointer);
        picture.addMouseListener(wakeOnPointer);

        MouseAdapter keepActionsAccessible = new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) {
                if (hudMode == DesktopHudSettings.Mode.AUTO) hudTimer.stop();
            }
            @Override public void mouseExited(MouseEvent event) {
                if (hudMode == DesktopHudSettings.Mode.AUTO && hudRevealed)
                    hudTimer.restart();
            }
            @Override public void mouseMoved(MouseEvent event) {
                if (hudMode == DesktopHudSettings.Mode.AUTO) hudTimer.stop();
            }
        };
        observeHudHover(top, keepActionsAccessible);
        observeHudHover(compact, keepActionsAccessible);
        observeHudHover(hudFooter, keepActionsAccessible);
        updateHud();
        if (hudMode == DesktopHudSettings.Mode.AUTO) hudTimer.restart();

        window.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent event) { running.set(false); }
            @Override public void windowClosed(WindowEvent event) {
                running.set(false);
                hudTimer.stop();
            }
            @Override public void windowDeactivated(WindowEvent event) {
                buttons.set(0);
                paused.set(true); // Never continue when another program takes focus.
            }
            @Override public void windowActivated(WindowEvent event) {
                if (!controlOverlay.isVisible()) picture.requestFocusInWindow();
            }
        });
    }

    public static boolean hasActiveSession() { return SESSION_BUSY.get(); }

    public static void launch(DesktopLibrary.Game game, Path core, Runnable afterLaunch,
                              DesktopPlayStats statistics) throws IOException {
        if (!SESSION_BUSY.compareAndSet(false, true))
            throw new IOException("Ya hay una partida abierta. Ciérrala antes de iniciar otra.");
        try {
            EmbeddedPlayerWindow session = new EmbeddedPlayerWindow(game, core,
                    afterLaunch, statistics);
            session.window.setVisible(true);
            session.picture.requestFocusInWindow();
            Thread worker = new Thread(session::emulate,
                    "OKUBI-Windows-Libretro-" + game.console.name());
            worker.setDaemon(true);
            worker.start();
        } catch (RuntimeException error) {
            SESSION_BUSY.set(false);
            throw error;
        }
    }

    private void observeHudHover(java.awt.Component component, MouseAdapter monitor) {
        component.addMouseListener(monitor);
        component.addMouseMotionListener(monitor);
        if (component instanceof java.awt.Container container)
            for (java.awt.Component child : container.getComponents())
                observeHudHover(child, monitor);
    }

    private void pointerActivity() {
        // Only AUTO wakes on motion. HIDDEN can always be escaped with
        // F1 (temporary) or ESC (full, keyboard-accessible settings).
        if (hudMode == DesktopHudSettings.Mode.AUTO && !controlOverlay.isVisible()) {
            hudRevealed = true;
            updateHud();
            hudTimer.restart();
        }
    }

    private void peekHud() {
        if (controlOverlay.isVisible()) return;
        if (DesktopHudSettings.autoHide(hudMode)) {
            hudRevealed = true;
            updateHud();
            hudTimer.restart();
        }
        picture.requestFocusInWindow();
    }

    private void setHudMode(DesktopHudSettings.Mode mode, JLabel feedback) {
        try {
            hudSettings.setMode(game.console, mode);
            hudMode = mode;
            hudTimer.stop();
            hudRevealed = mode == DesktopHudSettings.Mode.AUTO;
            updateHud();
            feedback.setText("HUD " + game.console.label + ": "
                    + mode.label + " · F1 muestra barras temporalmente");
        } catch (IOException problem) {
            feedback.setText("No se pudo guardar el modo HUD: "
                    + problem.getMessage());
        }
    }

    /** Called from Swing EDT only; never pauses, resets or alters Libretro. */
    private void updateHud() {
        DesktopHudSettings.View view = DesktopHudSettings.view(hudMode,
                hudRevealed, controlOverlay.isVisible());
        java.awt.CardLayout cards = (java.awt.CardLayout)hudHeader.getLayout();
        cards.show(hudHeader, view.compactHeader() ? "COMPACT" : "FULL");
        hudHeader.setVisible(view.header());
        hudFooter.setVisible(view.footer());
        root.revalidate();
        root.repaint();
    }

    private void toggleMute() {
        boolean silent = !muted.get();
        muted.set(silent);
        info(silent ? "Audio silenciado · pulsa M para reactivarlo"
                : "Audio reactivado");
        picture.requestFocusInWindow();
    }

    private void confirmLoad() {
        // Loading a state can discard unsaved progress; require confirmation.
        int response = javax.swing.JOptionPane.showConfirmDialog(window,
                "¿Volver al último estado rápido? Se perderá el progreso "
                + "actual que no hayas guardado dentro del juego.",
                "OKUBI · Cargar estado rápido",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE);
        if (response == javax.swing.JOptionPane.YES_OPTION) quickLoad.set(true);
        picture.requestFocusInWindow();
    }

    private void setFilter(DesktopPlaySettings.Filter filter, javax.swing.JLabel feedback) {
        try {
            playSettings.setFilter(game.console, filter);
            displayFilter = filter;
            picture.repaint();
            feedback.setText("Filtro guardado para " + game.console.label + ": "
                    + filter.display);
        } catch (IOException problem) {
            feedback.setText("No se pudo guardar: " + problem.getMessage());
        }
    }

    /** Only changes painted coordinates; Libretro frame and game state stay untouched. */
    private void setScalingMode(DesktopVideoScaling.Mode mode, JLabel feedback) {
        if (mode == null) return;
        try {
            videoScaling.setMode(game.console, mode);
            scalingMode = mode;
            picture.repaint();
            String message = "Escala " + game.console.name() + ": " + mode.label
                    + " · " + DesktopVideoScaling.warning(mode);
            feedback.setText(message);
            info(message);
        } catch (IOException problem) {
            feedback.setText("No se pudo guardar el escalado: " + problem.getMessage());
        }
    }

    private void cycleScaling() {
        if (controlOverlay.isVisible()) return;
        DesktopVideoScaling.Mode next = DesktopVideoScaling.next(scalingMode);
        try {
            videoScaling.setMode(game.console, next);
            scalingMode = next;
            picture.repaint();
            info("F2 · " + next.label + " · " + DesktopVideoScaling.warning(next));
        } catch (IOException problem) {
            info("No se pudo guardar la escala: " + problem.getMessage());
        }
        picture.requestFocusInWindow();
    }

    private JPanel createControlOverlay() {
        JPanel glass = new JPanel(new java.awt.GridBagLayout()) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.setColor(new Color(6, 4, 13, 220));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.dispose();
            }
        };
        glass.setOpaque(false);
        glass.setFocusable(true);

        JPanel body = new JPanel(new BorderLayout(14, 12));
        body.setBackground(new Color(27, 17, 43));
        body.setBorder(javax.swing.BorderFactory.createCompoundBorder(
                javax.swing.BorderFactory.createLineBorder(PINK, 2),
                javax.swing.BorderFactory.createEmptyBorder(18, 20, 18, 20)));
        body.setPreferredSize(new Dimension(740, 580));
        body.setMaximumSize(new Dimension(840, 650));

        JLabel feedback = new JLabel("ESC para continuar");
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new javax.swing.BoxLayout(heading,
                javax.swing.BoxLayout.Y_AXIS));
        JLabel name = new JLabel("OKUBI 遊  /  CINEMA CONTROL CENTER");
        name.setForeground(PINK);
        name.setFont(new Font("Segoe UI", Font.BOLD, 17));
        name.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        heading.add(name);
        heading.add(javax.swing.Box.createVerticalStrut(8));
        JPanel hudSelection = new JPanel(new java.awt.FlowLayout(
                java.awt.FlowLayout.LEFT, 8, 0));
        hudSelection.setOpaque(false);
        JLabel modeLabel = new JLabel("HUD:");
        modeLabel.setForeground(MUTED);
        modeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        hudSelection.add(modeLabel);
        javax.swing.JComboBox<DesktopHudSettings.Mode> modeChoice =
                new javax.swing.JComboBox<>(DesktopHudSettings.Mode.values());
        modeChoice.setSelectedItem(hudMode);
        modeChoice.setPreferredSize(new Dimension(290, 28));
        modeChoice.setForeground(new Color(25, 15, 38));
        modeChoice.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        modeChoice.setToolTipText("Modo de barras por consola. F1 las muestra temporalmente.");
        modeChoice.addActionListener(event -> {
            DesktopHudSettings.Mode chosen =
                    (DesktopHudSettings.Mode)modeChoice.getSelectedItem();
            if (chosen != null) setHudMode(chosen, feedback);
        });
        hudSelection.add(modeChoice);
        hudSelection.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        heading.add(hudSelection);
        header.add(heading, BorderLayout.CENTER);
        header.add(new CinemaButton("✕ VOLVER", true, this::toggleControlCenter),
                BorderLayout.EAST);
        body.add(header, BorderLayout.NORTH);

        JPanel options = new JPanel(new BorderLayout(16, 10));
        options.setOpaque(false);
        JPanel visuals = new JPanel();
        visuals.setBackground(new Color(36, 26, 55));
        visuals.setLayout(new javax.swing.BoxLayout(visuals,
                javax.swing.BoxLayout.Y_AXIS));
        visuals.setBorder(javax.swing.BorderFactory.createEmptyBorder(14, 15, 14, 15));
        visuals.setPreferredSize(new Dimension(265, 0));
        JLabel graphicsLabel = new JLabel("VISUAL ENGINE");
        graphicsLabel.setForeground(PINK);
        graphicsLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        visuals.add(graphicsLabel);
        visuals.add(javax.swing.Box.createVerticalStrut(11));
        JLabel note = new JLabel("Filtro por consola · " + game.console.label);
        note.setForeground(MUTED);
        visuals.add(note);
        visuals.add(javax.swing.Box.createVerticalStrut(15));
        feedback.setForeground(new Color(199, 174, 224));
        for (DesktopPlaySettings.Filter mode : DesktopPlaySettings.Filter.values()) {
            javax.swing.JRadioButton radio =
                    new javax.swing.JRadioButton(mode.display, displayFilter == mode);
            radio.setActionCommand(mode.name());
            radio.setForeground(new Color(248, 241, 252));
            radio.setOpaque(false);
            radio.setFont(new Font("Segoe UI", Font.BOLD, 12));
            radio.setFocusPainted(false);
            radio.addActionListener(e -> setFilter(mode, feedback));
            // Group added below after the buttons are created.
            visuals.add(radio);
            visuals.add(javax.swing.Box.createVerticalStrut(11));
        }
        javax.swing.ButtonGroup filters = new javax.swing.ButtonGroup();
        for (java.awt.Component component : visuals.getComponents())
            if (component instanceof javax.swing.JRadioButton radio)
                filters.add(radio);
        visuals.add(javax.swing.Box.createVerticalStrut(10));
        JLabel scalingTitle = new JLabel("SMART SCALING · F2 CAMBIAR");
        scalingTitle.setForeground(PINK);
        scalingTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        visuals.add(scalingTitle);
        visuals.add(javax.swing.Box.createVerticalStrut(6));
        javax.swing.JComboBox<DesktopVideoScaling.Mode> scaleChoice =
                new javax.swing.JComboBox<>(DesktopVideoScaling.Mode.values());
        scaleChoice.setSelectedItem(scalingMode);
        scaleChoice.setMaximumSize(new Dimension(280, 31));
        scaleChoice.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        scaleChoice.setToolTipText("Ajustar conserva todo; Llenar recorta y Estirar deforma.");
        scaleChoice.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        scaleChoice.addActionListener(event -> {
            DesktopVideoScaling.Mode chosen =
                    (DesktopVideoScaling.Mode)scaleChoice.getSelectedItem();
            if (chosen != null) setScalingMode(chosen, feedback);
        });
        visuals.add(scaleChoice);
        visuals.add(javax.swing.Box.createVerticalStrut(9));
        JLabel scaleHint = new JLabel("Recortar / deformar: modos opcionales");
        scaleHint.setForeground(MUTED);
        scaleHint.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        visuals.add(scaleHint);
        visuals.add(javax.swing.Box.createVerticalStrut(10));
        JLabel controllerTitle = new JLabel("PERFIL MANDO XBOX / XINPUT");
        controllerTitle.setForeground(PINK);
        controllerTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        visuals.add(controllerTitle);
        javax.swing.ButtonGroup layouts = new javax.swing.ButtonGroup();
        for (DesktopPlaySettings.GamepadLayout layout :
                DesktopPlaySettings.GamepadLayout.values()) {
            javax.swing.JRadioButton choice = new javax.swing.JRadioButton(
                    layout.display, playSettings.gamepadLayout(game.console) == layout);
            choice.setForeground(new Color(248, 241, 252));
            choice.setOpaque(false);
            choice.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            layouts.add(choice);
            visuals.add(choice);
            choice.addActionListener(event -> {
                try {
                    playSettings.setGamepadLayout(game.console, layout);
                    buttons.set(0);
                    feedback.setText("Mando de " + game.console.label
                            + ": " + layout.display);
                } catch (IOException error) {
                    feedback.setText("Perfil de mando no guardado: "
                            + error.getMessage());
                }
            });
        }
        visuals.add(javax.swing.Box.createVerticalGlue());
        visuals.add(new CinemaButton("F5 GUARDAR ESTADO", false,
                () -> quickSave.set(true)));
        visuals.add(javax.swing.Box.createVerticalStrut(9));
        visuals.add(new CinemaButton("F9 CARGAR ESTADO", false, this::confirmLoad));
        visuals.add(javax.swing.Box.createVerticalStrut(9));
        visuals.add(new CinemaButton("F12 CAPTURA PNG", false,
                () -> capture.set(true)));
        visuals.add(javax.swing.Box.createVerticalStrut(9));
        visuals.add(new CinemaButton("M SILENCIAR AUDIO", false, this::toggleMute));
        // All modes, filters and quick-save commands remain reachable at
        // 720x450 and higher display scaling (150% / 200%).
        javax.swing.JScrollPane visualScroll = new javax.swing.JScrollPane(visuals);
        visualScroll.setBorder(null);
        visualScroll.setPreferredSize(new Dimension(280, 0));
        visualScroll.setVerticalScrollBarPolicy(
                javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        visualScroll.setHorizontalScrollBarPolicy(
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        visualScroll.getVerticalScrollBar().setUnitIncrement(16);
        options.add(visualScroll, BorderLayout.WEST);

        JPanel bindings = new JPanel();
        bindings.setBackground(new Color(36, 26, 55));
        bindings.setLayout(new javax.swing.BoxLayout(bindings,
                javax.swing.BoxLayout.Y_AXIS));
        bindings.setBorder(javax.swing.BorderFactory.createEmptyBorder(13, 15, 13, 15));
        JLabel bindingTitle = new JLabel("PERSONALIZAR TECLADO  ·  " + game.console.name());
        bindingTitle.setForeground(PINK);
        bindingTitle.setFont(new Font("Segoe UI", Font.BOLD, 13));
        bindings.add(bindingTitle);
        bindings.add(javax.swing.Box.createVerticalStrut(9));
        for (DesktopPlaySettings.Action action : DesktopPlaySettings.Action.values()) {
            JPanel line = new JPanel(new BorderLayout(10, 2));
            line.setOpaque(false);
            JLabel actionLabel = new JLabel(action.label);
            actionLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
            actionLabel.setForeground(new Color(249, 239, 252));
            line.add(actionLabel, BorderLayout.CENTER);
            CinemaButton captureKey = new CinemaButton(
                    KeyEvent.getKeyText(playSettings.key(game.console, action)),
                    false, () -> { });
            captureKey.setPreferredSize(new Dimension(154, 29));
            captureKey.addActionListener(event -> {
                captureKey.setText("Pulsa una tecla...");
                captureKey.requestFocusInWindow();
            });
            captureKey.addKeyListener(new KeyAdapter() {
                @Override public void keyPressed(KeyEvent event) {
                    if (!"Pulsa una tecla...".equals(captureKey.getText())) return;
                    int chosen = event.getKeyCode();
                    if (chosen == KeyEvent.VK_ESCAPE) return;
                    try {
                        playSettings.remap(game.console, action, chosen);
                        buttons.set(0);
                        captureKey.setText(KeyEvent.getKeyText(chosen));
                        feedback.setText(action.label + " = "
                                + KeyEvent.getKeyText(chosen));
                    } catch (IOException problem) {
                        captureKey.setText(KeyEvent.getKeyText(
                                playSettings.key(game.console, action)));
                        feedback.setText(problem.getMessage());
                    }
                    event.consume();
                }
            });
            line.add(captureKey, BorderLayout.EAST);
            line.setMaximumSize(new Dimension(Integer.MAX_VALUE, 35));
            bindings.add(line);
            bindings.add(javax.swing.Box.createVerticalStrut(4));
        }
        bindings.add(javax.swing.Box.createVerticalStrut(12));
        bindings.add(new CinemaButton("RESTAURAR TECLAS Y FILTRO", false, () -> {
            int confirm = javax.swing.JOptionPane.showConfirmDialog(window,
                    "¿Restaurar las teclas y el filtro de " + game.console.label + "?",
                    "Restaurar perfil", javax.swing.JOptionPane.YES_NO_OPTION);
            if (confirm != javax.swing.JOptionPane.YES_OPTION) return;
            try {
                playSettings.reset(game.console);
                displayFilter = playSettings.filter(game.console);
                buttons.set(0);
                feedback.setText("Perfil restaurado. Reabre el menú para ver las teclas y mando.");
                picture.repaint();
            } catch (IOException problem) {
                feedback.setText("Error al restaurar: " + problem.getMessage());
            }
        }));
        javax.swing.JScrollPane keyScroll = new javax.swing.JScrollPane(bindings);
        keyScroll.setBorder(null);
        keyScroll.setVerticalScrollBarPolicy(
                javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
        keyScroll.getVerticalScrollBar().setUnitIncrement(15);
        options.add(keyScroll, BorderLayout.CENTER);
        body.add(options, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setOpaque(false);
        feedback.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        bottom.add(feedback, BorderLayout.CENTER);
        JLabel hint = new JLabel("Perfiles separados por GB · GBC · GBA · SNES");
        hint.setForeground(MUTED);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        bottom.add(hint, BorderLayout.SOUTH);
        body.add(bottom, BorderLayout.SOUTH);
        java.awt.GridBagConstraints placement = new java.awt.GridBagConstraints();
        placement.gridx = 0;
        placement.gridy = 0;
        placement.fill = java.awt.GridBagConstraints.BOTH;
        placement.weightx = 1;
        placement.weighty = 1;
        placement.insets = new java.awt.Insets(14, 22, 14, 22);
        glass.add(body, placement);
        return glass;
    }

    private void toggleControlCenter() {
        boolean opening = !controlOverlay.isVisible();
        if (opening) {
            pauseBeforeMenu = paused.get();
            paused.set(true);
            buttons.set(0);
            hudTimer.stop();
            controlOverlay.setVisible(true);
            updateHud();
            controlOverlay.requestFocusInWindow();
        } else {
            buttons.set(0);
            controlOverlay.setVisible(false);
            paused.set(pauseBeforeMenu);
            hudRevealed = hudMode == DesktopHudSettings.Mode.AUTO;
            updateHud();
            if (hudMode == DesktopHudSettings.Mode.AUTO) hudTimer.restart();
            picture.requestFocusInWindow();
        }
    }

    private void toggleMaximize() {
        if (previousSize == null) {
            previousSize = window.getBounds();
            window.setExtendedState(JFrame.MAXIMIZED_BOTH);
        } else {
            window.setExtendedState(JFrame.NORMAL);
            window.setBounds(previousSize);
            previousSize = null;
        }
        picture.requestFocusInWindow();
    }

    private void info(String message) {
        SwingUtilities.invokeLater(() -> info.setText(message));
    }

    /** Libretro key IDs match those used by the Android bridge. */
    public static int bitForKey(int code) {
        return switch (code) {
            case KeyEvent.VK_UP -> 0;
            case KeyEvent.VK_DOWN -> 1;
            case KeyEvent.VK_LEFT -> 2;
            case KeyEvent.VK_RIGHT -> 3;
            case KeyEvent.VK_X -> 4;   // Nintendo A
            case KeyEvent.VK_Z -> 5;   // Nintendo B
            case KeyEvent.VK_S -> 7;   // SNES X
            case KeyEvent.VK_A -> 8;   // SNES Y
            case KeyEvent.VK_Q -> 10;  // L
            case KeyEvent.VK_W -> 11;  // R
            case KeyEvent.VK_ENTER -> 12;
            case KeyEvent.VK_SPACE -> 13;
            default -> -1;
        };
    }

    private int safeButtons() {
        int nativePad = playSettings.applyGamepadLayout(game.console,
                WindowsRetroNative.gamepadMask());
        int mask = buttons.get() | nativePad;
        if ((mask & 3) == 3) mask &= ~3; // UP + DOWN
        if ((mask & 12) == 12) mask &= ~12; // LEFT + RIGHT
        return mask;
    }

    private void emulate() {
        boolean opened = false;
        SourceDataLine sound = null;
        DesktopSaves saves = null;
        String saveError = null;
        DesktopPlayStats.Session playSession = null;
        try {
            saves = new DesktopSaves(DesktopSaves.defaultRoot(), game.console, game.rom);
            WindowsRetroNative.open(selectedCore, game.rom, game.console, saves.directory);
            opened = true;
            saves.restore();
            // Only count real sessions after the core and battery restored.
            // A failure to store statistics must NEVER prevent playing or saving.
            try { playSession = statistics.begin(game.console, game.rom); }
            catch (IOException trackingError) {
                info("Estadísticas no disponibles: " + trackingError.getMessage());
            }
            int rate = WindowsRetroNative.sampleRate();
            sound = createSound(rate);
            final int width = game.console == DesktopLibrary.Console.GBA ? 240
                    : game.console == DesktopLibrary.Console.SNES ? 256 : 160;
            final int height = game.console == DesktopLibrary.Console.GBA ? 160
                    : game.console == DesktopLibrary.Console.SNES ? 224 : 144;
            final int[] pixels = new int[width * height];
            final short[] audio = new short[WindowsRetroNative.MAX_AUDIO_SAMPLES];
            final byte[] pcm = new byte[audio.length * 2];
            double fps = WindowsRetroNative.fps();
            if (fps <= 30 || fps >= 121) fps = 60;
            long interval = Math.max(8_000_000L, (long)(1_000_000_000.0 / fps));
            long next = System.nanoTime();
            int frames = 0;
            info("OKUBI ULTRA · F5 guardar · F9 cargar · F12 captura · Xbox conectado automáticamente");
            SwingUtilities.invokeLater(afterLaunch);
            while (running.get()) {
                if (playSession != null) {
                    try {
                        playSession.tick(!paused.get(), System.nanoTime());
                    } catch (IOException trackingError) {
                        info("No se pudieron registrar minutos: "
                                + trackingError.getMessage());
                        playSession = null;
                    }
                }
                // JNI and save-state operations stay on this ONE worker:
                // the EDT never touches native core state or cartridge RAM.
                if (quickSave.getAndSet(false)) {
                    try {
                        saves.saveState(selectedCore, WindowsRetroNative.saveState());
                        saves.store();
                        info("Estado rápido guardado · F9 para cargar");
                    } catch (IOException problem) {
                        info("No se guardó el estado: " + problem.getMessage());
                    }
                }
                if (quickLoad.getAndSet(false)) {
                    try {
                        byte[] snapshot = saves.readState(selectedCore);
                        if (!WindowsRetroNative.loadState(snapshot))
                            throw new IOException("Formato del estado no compatible con el núcleo.");
                        buttons.set(0);
                        info("Estado restaurado · pulsa P si la partida está en pausa");
                    } catch (IOException problem) {
                        info("No se cargó el estado: " + problem.getMessage());
                    }
                }
                if (capture.getAndSet(false)) {
                    try {
                        Path screenshot = saves.screenshot(picture.current);
                        info("Captura PNG: " + screenshot.getFileName());
                    } catch (IOException problem) {
                        info("Captura no disponible: " + problem.getMessage());
                    }
                }
                if (paused.get()) {
                    LockSupport.parkNanos(25_000_000L);
                    next = System.nanoTime();
                    continue;
                }
                int count = WindowsRetroNative.step(safeButtons(), pixels, audio);
                if (count < 0) throw new IOException("El núcleo dejó de ejecutar fotogramas.");
                BufferedImage image = new BufferedImage(width, height,
                        BufferedImage.TYPE_INT_ARGB);
                image.setRGB(0, 0, width, height, pixels, 0, width);
                picture.setFrame(image);
                if (sound != null && count > 0 && !muted.get()) {
                    int samples = Math.min(count, audio.length);
                    for (int i = 0; i < samples; i++) {
                        pcm[i * 2] = (byte)(audio[i] & 255);
                        pcm[i * 2 + 1] = (byte)((audio[i] >>> 8) & 255);
                    }
                    if (sound.available() >= samples * 2)
                        sound.write(pcm, 0, samples * 2);
                }
                if (++frames % Math.max(1, (int)(fps * 30)) == 0) {
                    try { saves.store(); }
                    catch (IOException error) {
                        saveError = error.getMessage();
                        info("Atención: no se pudo guardar · " + saveError);
                    }
                }
                next += interval;
                long remaining = next - System.nanoTime();
                if (remaining > 0) LockSupport.parkNanos(remaining);
                else if (remaining < -interval * 5) next = System.nanoTime();
            }
        } catch (IOException | UnsatisfiedLinkError | RuntimeException error) {
            final String details = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            info("No se pudo iniciar: " + details);
            SwingUtilities.invokeLater(() -> {
                javax.swing.JOptionPane.showMessageDialog(window, details,
                        "OKUBI · Núcleo Libretro", javax.swing.JOptionPane.WARNING_MESSAGE);
                window.dispose();
            });
        } finally {
            if (playSession != null) {
                try { playSession.finish(); }
                catch (IOException trackingError) {
                    info("No se pudo confirmar el tiempo jugado: "
                            + trackingError.getMessage());
                }
            }
            SwingUtilities.invokeLater(afterLaunch);
            if (opened) {
                try { saves.store(); }
                catch (IOException error) { saveError = error.getMessage(); }
                WindowsRetroNative.close();
            }
            if (sound != null) {
                sound.stop();
                sound.close();
            }
            SESSION_BUSY.set(false);
            if (saveError != null) {
                final String failed = saveError;
                SwingUtilities.invokeLater(() ->
                        javax.swing.JOptionPane.showMessageDialog(window,
                                "No se pudo confirmar el guardado automático:\n" + failed,
                                "Protección de partidas", javax.swing.JOptionPane.WARNING_MESSAGE));
            }
        }
    }

    private SourceDataLine createSound(int rate) {
        if (rate < 8_000 || rate > 192_000) return null;
        try {
            AudioFormat format = new AudioFormat(rate, 16, 2, true, false);
            DataLine.Info device = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine audio = (SourceDataLine) AudioSystem.getLine(device);
            audio.open(format, Math.max(4096, rate / 8));
            audio.start();
            return audio;
        } catch (Exception unavailable) {
            info("Audio no disponible: continúa en modo silencioso");
            return null;
        }
    }

    private final class Picture extends JPanel {
        private volatile BufferedImage current;

        void setFrame(BufferedImage image) {
            current = image;
            repaint();
        }

        @Override protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            BufferedImage source = current;
            if (source == null) {
                graphics.setColor(new Color(160, 144, 180));
                graphics.setFont(new Font("Segoe UI", Font.BOLD, 16));
                graphics.drawString("Iniciando emulación…", 22, 42);
                return;
            }
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            DesktopPlaySettings.Filter mode = displayFilter;
            DesktopVideoScaling.Frame frame =
                    DesktopVideoScaling.calculate(source.getWidth(),
                            source.getHeight(), w, h, scalingMode, mode);
            if (!frame.drawable()) return;
            final int[] dest = {frame.x(), frame.y(), frame.width(), frame.height()};
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        mode == DesktopPlaySettings.Filter.SMOOTH
                                ? RenderingHints.VALUE_INTERPOLATION_BILINEAR
                                : RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                // Fullviewport clip is essential when FILL intentionally
                // renders a larger image at negative x/y coordinates.
                g.setClip(0, 0, w, h);
                g.drawImage(source, dest[0], dest[1],
                        dest[2], dest[3], null);
                if (mode == DesktopPlaySettings.Filter.LCD ||
                        mode == DesktopPlaySettings.Filter.CRT) {
                    g.clipRect(dest[0], dest[1], dest[2], dest[3]);
                    if (mode == DesktopPlaySettings.Filter.CRT) {
                        // Lightweight CRT scanlines, no GL shaders or ROM edits.
                        double pixelH = dest[3] / (double)source.getHeight();
                        g.setColor(new Color(0, 0, 0, 58));
                        for (int row = 1; row < source.getHeight(); row += 1) {
                            int y = dest[1] + (int)Math.round(row * pixelH);
                            if (y >= dest[1] + dest[3]) break;
                            g.drawLine(dest[0], y, dest[0] + dest[2], y);
                        }
                    } else {
                        double pixelW = dest[2] / (double)source.getWidth();
                        double pixelH = dest[3] / (double)source.getHeight();
                        if (pixelW >= 2.4 && pixelH >= 2.4) {
                            g.setColor(new Color(0, 0, 0, 49));
                            for (int col = 1; col < source.getWidth(); col++)
                                g.drawLine(dest[0] + (int)Math.round(col * pixelW),
                                        dest[1], dest[0] + (int)Math.round(col * pixelW),
                                        dest[1] + dest[3]);
                            for (int row = 1; row < source.getHeight(); row++)
                                g.drawLine(dest[0],
                                        dest[1] + (int)Math.round(row * pixelH),
                                        dest[0] + dest[2],
                                        dest[1] + (int)Math.round(row * pixelH));
                        }
                    }
                }
            } finally {
                g.dispose();
            }
        }
    }
}
