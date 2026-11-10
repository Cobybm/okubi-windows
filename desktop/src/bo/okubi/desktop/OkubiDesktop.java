package bo.okubi.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * OKUBI Desktop 0.3: Windows emulator with bundled open-source cores, not an APK wrapper.
 * Plays user-provided ROMs through user-selected external emulators.
 * The Android app is never accessed or modified by this program.
 */
public final class OkubiDesktop {
    private static final Color BG = new Color(12, 9, 20);
    private static final Color PANEL = new Color(27, 21, 39);
    private static final Color PANEL_LIGHT = new Color(39, 30, 52);
    private static final Color TEXT = new Color(245, 241, 250);
    private static final Color MUTED = new Color(172, 158, 190);
    private static final Color PINK = new Color(245, 58, 155);
    private static final Color VIOLET = new Color(130, 87, 225);
    private final DesktopLibrary library;
    private final DesktopPlayStats statistics;
    private final DesktopCloudSave cloudSave;
    private final DesktopSmartSaveGuard smartGuard;
    private boolean saveGuardBusy;
    private JButton smartGuardToggle;
    private final OnlineCoverService coverService =
            new OnlineCoverService(OnlineCoverService.defaultDirectory());
    private boolean cloudBusy;
    private boolean crossSaveBusy;

    private JFrame window;
    private JPanel gamesPanel;
    private int loadedGameCards;
    private JButton loadMoreCards;
    private boolean addingGameCards;
    private JTextField search;
    private final DesktopSearchRefresh searchRefresh =
            new DesktopSearchRefresh(150, this::renderGames);
    private JLabel status;
    private JLabel footerVersion;
    private JLabel gameCount;
    private JPanel heroHolder;
    private String activeFilter = "TODOS";
    private final DesktopConsoleNavigation gamepad = new DesktopConsoleNavigation();
    private final DesktopControllerFocusGate libraryFocusGate = new DesktopControllerFocusGate();
    private boolean suppressDetailsUntilRelease;
    private final java.util.ArrayList<DesktopLibrary.Game> displayedGames = new java.util.ArrayList<>();
    private final java.util.ArrayList<JPanel> displayedCards = new java.util.ArrayList<>();
    private int libraryColumns = 2;
    private CinemaCategoryTab[] categoryTabs;
    private JPanel categoryBar;
    private JPanel sidebarCategories;
    private java.awt.Component categoryGap;
    private java.awt.Component heroGap;
    private JScrollPane gamesScroll;
    private javax.swing.JComponent leftSidebar;
    private JButton consoleButton;
    private JPanel toolbarHeader;
    private JPanel toolbarTitle;
    private JPanel toolbarCommands;
    private JLabel toolbarSubtitle;
    private CinemaButton[] toolbarButtons;
    private DesktopHeaderLayout.Mode toolbarMode;
    private javax.swing.Timer gamepadTimer;
    private Path selectedRom;
    private int selectedIndex = -1;
    private boolean consoleMode;
    private java.awt.Rectangle normalBounds;

    private OkubiDesktop() throws IOException {
        library = new DesktopLibrary(DesktopLibrary.defaultSettingsFile());
        statistics = new DesktopPlayStats(DesktopPlayStats.defaultFile());
        cloudSave = new DesktopCloudSave(DesktopCloudSave.defaultSettingsFile());
        smartGuard = new DesktopSmartSaveGuard(
                DesktopSmartSaveGuard.defaultPreferences(),
                DesktopSaveVault.defaultVaultRoot(),
                DesktopSaves.defaultRoot());
    }

    public static void main(String[] args) {
        // Validate the actual Windows app-image before using any user profile.
        // No GUI, ROM loading, settings writes or gamepad polling in this mode.
        if (args != null && args.length == 1
                && "--verify-windows-release".equals(args[0])) {
            System.exit(DesktopWindowsReleaseVerifier.runCli(System.out, System.err));
            return;
        }
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new OkubiDesktop().show();
            } catch (Exception error) {
                JOptionPane.showMessageDialog(null, "No se pudo abrir OKUBI Desktop:\n"
                        + error.getMessage(), "OKUBI Desktop", JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static final String[] CJK_FONTS = {
            "Yu Gothic UI", "Yu Gothic", "Meiryo", "MS Gothic", "Noto Sans CJK JP"
    };

    /** Prefer an installed Japanese-capable system font, not a bundled font. */
    private static Font brandFont(int size) {
        for (String family : CJK_FONTS) {
            Font available = new Font(family, Font.BOLD, size);
            if (available.getFamily(Locale.ENGLISH).equalsIgnoreCase(family)
                    && available.canDisplay('遊')) return available;
        }
        Font fallback = new Font(Font.DIALOG, Font.BOLD, size);
        return fallback;
    }

    private static JLabel label(String value, int size, Color color, boolean strong) {
        JLabel text = new JLabel(value);
        text.setForeground(color);
        Font regular = new Font("Segoe UI", strong ? Font.BOLD : Font.PLAIN, size);
        text.setFont(value.indexOf('遊') >= 0 ? brandFont(size) : regular);
        return text;
    }

    private static JPanel panel(Color color) {
        JPanel result = new JPanel();
        result.setBackground(color);
        return result;
    }

    private JButton button(String value, boolean primary, Runnable callback) {
        return new CinemaButton(value, primary, callback);
    }

    private void show() {
        window = new JFrame("OKUBI Desktop 1.0.13 · SMART SESSION");
        // Never terminate the JVM while a game worker still owns SRAM/RTC.
        window.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        window.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent event) {
                boolean gameActive = EmbeddedPlayerWindow.hasActiveSession();
                if (!DesktopShutdownPolicy.mayExit(gameActive, cloudBusy,
                        crossSaveBusy, saveGuardBusy)) {
                    JOptionPane.showMessageDialog(window,
                            DesktopShutdownPolicy.blockedReason(gameActive,
                                    cloudBusy, crossSaveBusy, saveGuardBusy)
                            + "\n\nNo cierres OKUBI hasta que termine la operación."
                            + "\nTus archivos anteriores no serán borrados.",
                            "OKUBI · Protección de partidas",
                            JOptionPane.INFORMATION_MESSAGE);
                    return;
                }
                searchRefresh.stop();
                window.dispose();
                System.exit(0);
            }
        });
        window.setMinimumSize(new Dimension(800, 520));
        window.setSize(1140, 730);
        window.setLocationRelativeTo(null);
        window.getRootPane().registerKeyboardAction(
                event -> search.requestFocusInWindow(),
                javax.swing.KeyStroke.getKeyStroke("control F"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        window.getRootPane().registerKeyboardAction(
                event -> importGames(),
                javax.swing.KeyStroke.getKeyStroke("control O"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        window.getRootPane().registerKeyboardAction(
                event -> randomGame(),
                javax.swing.KeyStroke.getKeyStroke("control R"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        window.getRootPane().registerKeyboardAction(
                event -> showSmartPerformance(),
                javax.swing.KeyStroke.getKeyStroke("control P"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        // Phase 67: opt-in maintenance (never deletes user saves or ROMs).
        window.getRootPane().registerKeyboardAction(
                event -> showMaintenance(),
                javax.swing.KeyStroke.getKeyStroke("control shift M"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        window.getRootPane().registerKeyboardAction(event -> {
            if (!search.isFocusOwner() && !EmbeddedPlayerWindow.hasActiveSession())
                showSelectedGameHub();
        }, javax.swing.KeyStroke.getKeyStroke("I"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        // Mouse and keyboard access to all four categories remains even
        // when the horizontal tab bar is hidden on a short display.
        String[] filterKeys = {"control 1", "control 2", "control 3", "control 4"};
        String[] filterNames = {"TODOS", "FAVORITOS", "RECIENTES", "MAS_JUGADOS"};
        for (int i = 0; i < filterKeys.length; i++) {
            final String filterName = filterNames[i];
            window.getRootPane().registerKeyboardAction(
                    event -> changeFilter(filterName),
                    javax.swing.KeyStroke.getKeyStroke(filterKeys[i]),
                    javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
        bindConsoleKeys();

        JPanel root = panel(BG);
        root.setLayout(new BorderLayout(0, 0));
        window.setContentPane(root);

        toolbarHeader = new JPanel(new BorderLayout(20, 0)) {
            @Override protected void paintComponent(Graphics source) {
                super.paintComponent(source);
                Graphics2D g = (Graphics2D)source.create();
                g.setPaint(new GradientPaint(0, 0, new Color(40, 19, 61),
                        getWidth(), 0, new Color(18, 12, 29)));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.dispose();
            }
        };
        toolbarHeader.setOpaque(false);
        toolbarTitle = new JPanel();
        toolbarTitle.setOpaque(false);
        toolbarTitle.setLayout(new BoxLayout(toolbarTitle, BoxLayout.Y_AXIS));
        String brand = brandFont(27).canDisplay('遊') ? "OKUBI 遊" : "OKUBI";
        JLabel brandName = label(brand, 27, PINK, true);
        brandName.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        toolbarTitle.add(brandName);
        toolbarTitle.add(Box.createVerticalStrut(5));
        toolbarSubtitle = label("TU CONSOLA. TUS JUEGOS. SIN LÍMITES.",
                12, MUTED, true);
        toolbarSubtitle.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        toolbarTitle.add(toolbarSubtitle);

        // Every action stays available even on an 800px laptop window:
        // FULL: entire labels, COMPACT: short legible labels,
        // STACKED: two rows of three full-width controls.
        toolbarCommands = new JPanel(new GridLayout(1, 6, 8, 0));
        toolbarCommands.setOpaque(false);
        toolbarButtons = new CinemaButton[DesktopHeaderLayout.COMMAND_COUNT];
        Runnable[] handlers = {
                this::toggleConsoleMode, this::randomGame, this::showCloudSave,
                this::showStatsDashboard, this::configureEmulators, this::importGames
        };
        boolean[] featured = {true, false, true, false, false, true};
        for (int i = 0; i < toolbarButtons.length; i++) {
            toolbarButtons[i] = new CinemaButton(
                    DesktopHeaderLayout.FULL_LABELS[i],
                    featured[i], handlers[i]);
            toolbarButtons[i].setPreferredSize(new Dimension(95, 38));
            toolbarCommands.add(toolbarButtons[i]);
        }
        consoleButton = toolbarButtons[0];
        toolbarHeader.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                adaptToolbar(toolbarHeader.getWidth());
            }
        });
        root.add(toolbarHeader, BorderLayout.NORTH);
        adaptToolbar(window.getWidth());


        JPanel body = panel(BG);
        body.setLayout(new BorderLayout(18, 0));
        body.setBorder(BorderFactory.createEmptyBorder(18, 20, 12, 20));
        root.add(body, BorderLayout.CENTER);

        JPanel sidebar = panel(PANEL);
        sidebar.setBorder(BorderFactory.createEmptyBorder(18, 12, 16, 12));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        // The four library filters already live in the horizontal tab bar.
        // Show them here only on displays too short to fit those tabs.
        sidebarCategories = panel(PANEL);
        sidebarCategories.setLayout(new BoxLayout(sidebarCategories, BoxLayout.Y_AXIS));
        JLabel libraryHeading = label("BIBLIOTECA", 11, PINK, true);
        libraryHeading.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        sidebarCategories.add(libraryHeading);
        sidebarCategories.add(Box.createVerticalStrut(10));
        String[] compactItems = {"Todos", "Favoritos", "Recientes", "Más jugados"};
        String[] compactFilters = {"TODOS", "FAVORITOS", "RECIENTES", "MAS_JUGADOS"};
        for (int i = 0; i < compactItems.length; i++) {
            final String filter = compactFilters[i];
            JButton link = button(compactItems[i], false, () -> changeFilter(filter));
            link.setAlignmentX(JButton.LEFT_ALIGNMENT);
            link.setMaximumSize(new Dimension(Integer.MAX_VALUE, 37));
            sidebarCategories.add(link);
            sidebarCategories.add(Box.createVerticalStrut(7));
        }
        sidebar.add(sidebarCategories);
        sidebar.add(Box.createVerticalStrut(12));

        JLabel nav = label("HERRAMIENTAS", 11, PINK, true);
        nav.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        sidebar.add(nav);
        sidebar.add(Box.createVerticalStrut(12));
        // No duplicates of Importar ROM, CLOUD SAVE, Estadísticas or Motores:
        // those functions remain in the six upper buttons.
        String[] utilityItems = {"▤   Importar carpeta", "Portadas en Internet",
                "⇄   Cross Save local", "◈   SAVE VAULT",
                "◈   SMART PC · PS1/PS2/PS3"};
        Runnable[] utilityActions = {this::importFolder, this::refreshOnlineCovers,
                this::showBackupNotice, this::showSaveVault, this::showSmartPerformance};
        for (int i = 0; i < utilityItems.length; i++) {
            JButton link = button(utilityItems[i], false, utilityActions[i]);
            link.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
            link.setAlignmentX(JButton.LEFT_ALIGNMENT);
            sidebar.add(link);
            sidebar.add(Box.createVerticalStrut(10));
        }
        smartGuardToggle = button(smartGuard.enabled()
                ? "🛡 AUTO RESPALDO: ON" : "🛡 AUTO RESPALDO: OFF",
                false, this::toggleSmartSaveGuard);
        smartGuardToggle.setToolTipText(
                "Alternar copias automáticas verificadas antes de jugar; "
                + "nunca altera las copias manuales de SAVE VAULT");
        smartGuardToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
        smartGuardToggle.setAlignmentX(JButton.LEFT_ALIGNMENT);
        sidebar.add(smartGuardToggle);
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(Box.createVerticalStrut(8));
        JLabel keys = label("CTRL+1–4 · FILTROS", 10, MUTED, true);
        keys.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        sidebar.add(keys);
        sidebar.add(Box.createVerticalStrut(7));
        JLabel hint = label("LB/RB · CAMBIAR LISTA", 10, MUTED, false);
        hint.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        sidebar.add(hint);
        sidebarCategories.setVisible(false);
        JScrollPane sidebarScroll = new JScrollPane(sidebar);
        sidebarScroll.setBorder(null);
        sidebarScroll.setPreferredSize(new Dimension(215, 0));
        sidebarScroll.setMinimumSize(new Dimension(195, 0));
        sidebarScroll.getViewport().setBackground(PANEL);
        sidebarScroll.setHorizontalScrollBarPolicy(
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        sidebarScroll.setVerticalScrollBarPolicy(
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        sidebarScroll.getVerticalScrollBar().setUnitIncrement(30);
        leftSidebar = sidebarScroll;
        body.add(sidebarScroll, BorderLayout.WEST);

        JPanel main = panel(BG);
        main.setLayout(new BorderLayout(0, 14));
        body.add(main, BorderLayout.CENTER);

        JPanel searchRow = panel(BG);
        searchRow.setLayout(new BorderLayout(12, 0));
        JPanel headlines = panel(BG);
        headlines.setLayout(new BoxLayout(headlines, BoxLayout.Y_AXIS));
        headlines.add(label("Mis juegos", 21, TEXT, true));
        gameCount = label("Juegos locales", 11, MUTED, false);
        headlines.add(gameCount);
        searchRow.add(headlines, BorderLayout.WEST);
        search = new JTextField() {
            @Override protected void paintComponent(Graphics source) {
                super.paintComponent(source);
                if (getText().isEmpty() && !isFocusOwner()) {
                    Graphics2D graphics = (Graphics2D) source.create();
                    try {
                        graphics.setColor(MUTED);
                        graphics.setFont(getFont());
                        java.awt.FontMetrics metrics = graphics.getFontMetrics();
                        graphics.drawString("Buscar en tu biblioteca...  (Ctrl+F)",
                                14, (getHeight() - metrics.getHeight()) / 2
                                        + metrics.getAscent());
                    } finally { graphics.dispose(); }
                }
            }
        };
        search.setToolTipText("Buscar por nombre de juego o consola");
        search.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        search.setForeground(TEXT);
        search.setCaretColor(PINK);
        search.setBackground(PANEL);
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(79, 58, 104), 1),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)));
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { searchRefresh.request(); }
            @Override public void removeUpdate(DocumentEvent e) { searchRefresh.request(); }
            @Override public void changedUpdate(DocumentEvent e) { searchRefresh.request(); }
        });
        searchRow.add(search, BorderLayout.CENTER);

        JPanel headerStack = panel(BG);
        headerStack.setLayout(new BoxLayout(headerStack, BoxLayout.Y_AXIS));
        heroHolder = panel(BG);
        heroHolder.setLayout(new BorderLayout());
        heroHolder.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        heroHolder.setPreferredSize(new Dimension(0, 196));
        heroHolder.setMaximumSize(new Dimension(Integer.MAX_VALUE, 196));
        headerStack.add(heroHolder);
        heroGap = Box.createVerticalStrut(17);
        headerStack.add(heroGap);
        searchRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        searchRow.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        headerStack.add(searchRow);

        categoryGap = Box.createVerticalStrut(10);
        headerStack.add(categoryGap);
        categoryBar = panel(BG);
        categoryBar.setLayout(new GridLayout(1, 4, 8, 0));
        categoryBar.setPreferredSize(new Dimension(0, 38));
        categoryBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        categoryBar.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        String[] categories = {"TODOS", "FAVORITOS", "RECIENTES", "MAS_JUGADOS"};
        String[] titles = {"TODOS", "FAVORITOS", "RECIENTES", "MÁS JUGADOS"};
        categoryTabs = new CinemaCategoryTab[categories.length];
        for (int i = 0; i < categories.length; i++) {
            final String next = categories[i];
            categoryTabs[i] = new CinemaCategoryTab(titles[i], next,
                    () -> changeFilter(next));
            categoryBar.add(categoryTabs[i]);
        }
        headerStack.add(categoryBar);
        main.add(headerStack, BorderLayout.NORTH);

        gamesPanel = panel(BG);
        gamesPanel.setLayout(new BoxLayout(gamesPanel, BoxLayout.Y_AXIS));
        gamesScroll = new JScrollPane(gamesPanel);
        gamesScroll.setBorder(null);
        gamesScroll.setViewportBorder(null);
        gamesScroll.getViewport().setBackground(BG);
        gamesScroll.getVerticalScrollBar().setUnitIncrement(35);
        gamesScroll.getVerticalScrollBar().addAdjustmentListener(event -> {
            if (addingGameCards || event.getValueIsAdjusting()
                    || !DesktopLibraryBatch.canLoad(loadedGameCards, displayedGames.size()))
                return;
            javax.swing.JScrollBar bar = gamesScroll.getVerticalScrollBar();
            if (bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 200)
                appendGameCards();
        });
        // Rebuild only when the number of columns changes. This preserves
        // selection and avoids continuous repaint loops while resizing.
        gamesScroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                int width = gamesScroll.getViewport().getWidth();
                if (width > 0 && DesktopLibraryGrid.columnsForWidth(width) != libraryColumns)
                    renderGames();
            }
        });
        main.add(gamesScroll, BorderLayout.CENTER);

        JPanel bottom = panel(PANEL);
        bottom.setLayout(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(10, 22, 10, 22));
        status = label("A JUGAR  ·  Y FICHA  ·  X FAVORITO  ·  LB/RB FILTROS",
                12, MUTED, false);
        status.setToolTipText("CRUCETA / FLECHAS: ELEGIR · A / ENTER: JUGAR · X / F: FAVORITO · LB/RB: FILTROS · START / F11: MODO CONSOLA");
        // CENTER receives only the space remaining beside the version,
        // so a long status can never paint over the version on an 800px PC.
        bottom.add(status, BorderLayout.CENTER);
        footerVersion = label("v1.0.13-beta", 11, PINK, true);
        footerVersion.setToolTipText("OKUBI Desktop 1.0.13 · SMART PERFORMANCE MULTICONSOLE");
        bottom.add(footerVersion, BorderLayout.EAST);
        root.add(bottom, BorderLayout.SOUTH);

        window.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                adaptLibraryDensity();
                adaptFooter();
            }
        });
        adaptLibraryDensity();
        adaptFooter();
        renderGames();
        window.setVisible(true);
        // XInput is queried only while the desktop library has focus; never
        // hijack a controller from the active Libretro player session.
        gamepadTimer = new javax.swing.Timer(85, event -> pollController());
        gamepadTimer.setCoalesce(true);
        gamepadTimer.start();
    }

    /** Reflow only on breakpoint changes to avoid resize loops and focus loss. */
    private void adaptToolbar(int availableWidth) {
        if (toolbarHeader == null || toolbarButtons == null || availableWidth <= 0)
            return;
        java.awt.FontMetrics metrics = toolbarButtons[0].getFontMetrics(
                toolbarButtons[0].getFont());
        int brandWidth = toolbarTitle.getPreferredSize().width;
        int widestFull = Math.max(DesktopHeaderLayout.widest(
                DesktopHeaderLayout.FULL_LABELS, metrics),
                DesktopHeaderLayout.buttonWidth("◱ SALIR DE CONSOLA", metrics));
        int widestCompact = DesktopHeaderLayout.widest(
                DesktopHeaderLayout.COMPACT_LABELS, metrics);
        DesktopHeaderLayout.Mode selected = DesktopHeaderLayout.choose(
                availableWidth, brandWidth, widestFull, widestCompact);
        if (selected != toolbarMode) {
            toolbarMode = selected;
            toolbarHeader.removeAll();
            toolbarCommands.setLayout(new GridLayout(
                    DesktopHeaderLayout.rows(selected),
                    DesktopHeaderLayout.columns(selected), 8, 8));
            if (selected == DesktopHeaderLayout.Mode.STACKED) {
                toolbarHeader.setLayout(new BorderLayout(0, 10));
                toolbarHeader.setBorder(BorderFactory.createEmptyBorder(9, 18, 10, 18));
                toolbarSubtitle.setVisible(false);
                toolbarHeader.add(toolbarTitle, BorderLayout.NORTH);
                toolbarHeader.add(toolbarCommands, BorderLayout.CENTER);
            } else {
                toolbarHeader.setLayout(new BorderLayout(18, 0));
                toolbarHeader.setBorder(BorderFactory.createEmptyBorder(15, 28, 15, 28));
                toolbarSubtitle.setVisible(true);
                toolbarHeader.add(toolbarTitle, BorderLayout.WEST);
                toolbarHeader.add(toolbarCommands, BorderLayout.CENTER);
            }
            toolbarHeader.revalidate();
            toolbarHeader.repaint();
        }
        refreshToolbarButtons();
    }

    private void refreshToolbarButtons() {
        if (toolbarButtons == null || toolbarMode == null) return;
        for (int i = 0; i < toolbarButtons.length; i++) {
            String display = DesktopHeaderLayout.label(i, toolbarMode, consoleMode);
            String complete = DesktopHeaderLayout.label(
                    i, DesktopHeaderLayout.Mode.FULL, consoleMode);
            toolbarButtons[i].setText(display);
            toolbarButtons[i].setToolTipText(complete);
            toolbarButtons[i].getAccessibleContext().setAccessibleName(
                    CinemaIcons.withoutEmoji(complete));
        }
    }

    /** Show a compact version number whenever the status needs more space. */
    private void adaptFooter() {
        if (window == null || footerVersion == null) return;
        footerVersion.setText(DesktopCinemaLayout.showFooterVersion(window.getWidth())
                ? "DESKTOP 1.0.13 · SMART PC" : "v1.0.13-beta");
    }

    /** Keep game cards accessible on short laptop-height windows. */
    private void adaptLibraryDensity() {
        if (heroHolder == null || categoryBar == null || window == null) return;
        int height = window.getHeight();
        boolean showHero = DesktopCinemaLayout.showHero(height);
        boolean showCategories = DesktopCinemaLayout.showCategoryTabs(height);
        heroHolder.setVisible(showHero);
        heroGap.setVisible(showHero);
        categoryBar.setVisible(showCategories);
        categoryGap.setVisible(showCategories);
        if (sidebarCategories != null)
            sidebarCategories.setVisible(!showCategories);
    }

    private void refreshCategoryTabs() {
        if (categoryTabs == null) return;
        for (CinemaCategoryTab tab : categoryTabs)
            tab.setActiveCategory(tab.category().equals(activeFilter));
    }

    private void renderGames() {
        if (gamesPanel == null) return;
        gamesPanel.removeAll();
        displayedGames.clear();
        displayedCards.clear();
        loadedGameCards = 0;
        loadMoreCards = null;
        int viewportWidth = gamesScroll == null ? 0 : gamesScroll.getViewport().getWidth();
        if (viewportWidth <= 0)
            viewportWidth = Math.max(DesktopLibraryGrid.MIN_CARD_WIDTH,
                    window.getWidth() - (consoleMode ? 80 : 310));
        libraryColumns = DesktopLibraryGrid.columnsForWidth(viewportWidth);
        List<DesktopLibrary.Game> all = library.games();
        refreshCategoryTabs();
        if (gameCount != null)
            gameCount.setText(all.size() + " juegos  ·  "
                    + DesktopPlayStats.duration(statistics.totalSeconds())
                    + " reales  ·  " + statistics.totalSessions() + " sesiones");
        String filter = search == null ? "" :
                search.getText().trim().toLowerCase(Locale.ROOT);
        java.util.ArrayList<DesktopLibrary.Game> visibleGames = new java.util.ArrayList<>();
        for (DesktopLibrary.Game game : all) {
            if (!game.title().toLowerCase(Locale.ROOT).contains(filter)
                    && !game.console.label.toLowerCase(Locale.ROOT).contains(filter)) continue;
            if ("FAVORITOS".equals(activeFilter) && !game.favorite) continue;
            if ("RECIENTES".equals(activeFilter) && game.lastPlayed == 0) continue;
            visibleGames.add(game);
        }
        if ("RECIENTES".equals(activeFilter))
            visibleGames.sort((a, b) -> Long.compare(b.lastPlayed, a.lastPlayed));
        if ("MAS_JUGADOS".equals(activeFilter))
            visibleGames.sort((a, b) -> Long.compare(
                    statistics.get(b.console, b.rom).seconds,
                    statistics.get(a.console, a.rom).seconds));
        displayedGames.addAll(visibleGames);
        int matching = -1;
        for (int i = 0; i < displayedGames.size(); i++)
            if (displayedGames.get(i).rom.equals(selectedRom)) {
                matching = i;
                break;
            }
        selectedIndex = displayedGames.isEmpty() ? -1 :
                matching >= 0 ? matching : 0;
        if (selectedIndex >= 0) selectedRom = displayedGames.get(selectedIndex).rom;
        // Show the game actually selected in this filter, not the previous tab's hero.
        refreshHero(all);
        // Only build the first batch. Further rows are added as needed.
        appendGameCardsUntil(DesktopLibraryBatch.initialCount(
                displayedGames.size(), selectedIndex));
        if (visibleGames.isEmpty()) {
            JPanel empty = panel(PANEL);
            empty.setLayout(new BorderLayout(0, 12));
            empty.setBorder(BorderFactory.createEmptyBorder(28, 22, 28, 22));
            empty.add(label(all.isEmpty() ? "Tu consola empieza aquí"
                    : "Sin resultados · " + activeFilter.toLowerCase(Locale.ROOT),
                    17, TEXT, true), BorderLayout.NORTH);
            empty.add(label(all.isEmpty() ?
                    "Importa ROMs propias con el botón superior."
                    : "Prueba con otro nombre o consola.", 12, MUTED, false),
                    BorderLayout.CENTER);
            empty.add(button("+ Importar ROM", true, this::importGames),
                    BorderLayout.SOUTH);
            JPanel row = panel(BG);
            row.setLayout(new GridLayout(1, libraryColumns, 14, 0));
            row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
            row.setPreferredSize(new Dimension(0, 215));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 215));
            row.add(empty);
            if (libraryColumns > 1) row.add(quickStartGuide());
            for (int column = 2; column < libraryColumns; column++)
                row.add(panel(BG));
            gamesPanel.add(row);
        }
        gamesPanel.revalidate();
        gamesPanel.repaint();
    }

    private void appendGameCards() {
        appendGameCardsUntil(DesktopLibraryBatch.nextCount(
                loadedGameCards, displayedGames.size()));
    }

    private void appendGameCardsUntil(int requested) {
        if (gamesPanel == null || addingGameCards) return;
        if (requested <= loadedGameCards) return;
        addingGameCards = true;
        try {
            if (loadMoreCards != null && loadMoreCards.getParent() == gamesPanel)
                gamesPanel.remove(loadMoreCards);
            loadMoreCards = null;
            int rowHeight = DesktopCinemaLayout.cardRowHeight(
                    gamesPanel.getFontMetrics(new Font("Segoe UI", Font.BOLD, 14)).getHeight());
            int limit = Math.min(displayedGames.size(), requested);
            // Keep all rows aligned to columns; the last partial row is
            // created only for the final batch.
            if (limit < displayedGames.size())
                limit = Math.min(displayedGames.size(),
                        ((limit + libraryColumns - 1) / libraryColumns) * libraryColumns);
            for (int index = loadedGameCards; index < limit; index += libraryColumns) {
                JPanel row = panel(BG);
                row.setLayout(new GridLayout(1, libraryColumns, 14, 0));
                row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
                row.setPreferredSize(new Dimension(0, rowHeight));
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, rowHeight));
                int inRow = Math.min(libraryColumns, limit - index);
                for (int column = 0; column < libraryColumns; column++) {
                    if (column < inRow) {
                        JPanel card = gameCard(displayedGames.get(index + column));
                        displayedCards.add(card);
                        row.add(card);
                    } else if (column == 1 && inRow == 1) {
                        row.add(gameCompanion(displayedGames.get(index)));
                    } else {
                        row.add(panel(BG));
                    }
                }
                gamesPanel.add(row);
                gamesPanel.add(Box.createVerticalStrut(14));
            }
            loadedGameCards = limit;
            if (DesktopLibraryBatch.canLoad(loadedGameCards, displayedGames.size())) {
                loadMoreCards = button("CARGAR MÁS JUEGOS  ·  "
                        + loadedGameCards + " / " + displayedGames.size(), false,
                        this::appendGameCards);
                loadMoreCards.setAlignmentX(JButton.CENTER_ALIGNMENT);
                loadMoreCards.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
                gamesPanel.add(loadMoreCards);
            }
            gamesPanel.revalidate();
            gamesPanel.repaint();
        } finally {
            addingGameCards = false;
        }
    }

    private void styleCardSelection(JPanel card, boolean selected) {
        if (card instanceof CinemaLibraryCard tile) {
            tile.setSelected(selected);
            return;
        }
        Color border = selected ? new Color(255, 68, 166) : new Color(83, 48, 109);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, selected ? 3 : 2, true),
                BorderFactory.createEmptyBorder(11, 11, 11, 11)));
        card.setBackground(selected ? new Color(42, 28, 56) : PANEL);
    }

    private void selectGame(int next) {
        if (displayedGames.isEmpty()) {
            selectedIndex = -1;
            selectedRom = null;
            return;
        }
        int target = Math.max(0, Math.min(displayedGames.size() - 1, next));
        // Controller and keyboard may select a game beyond the current batch.
        // Materialize that batch before touching the card index.
        if (target >= loadedGameCards)
            appendGameCardsUntil(DesktopLibraryBatch.nextCount(target, displayedGames.size()));
        if (selectedIndex >= 0 && selectedIndex < displayedCards.size()) {
            styleCardSelection(displayedCards.get(selectedIndex), false);
            displayedCards.get(selectedIndex).repaint();
        }
        selectedIndex = target;
        DesktopLibrary.Game selected = displayedGames.get(target);
        selectedRom = selected.rom;
        if (target < displayedCards.size()) {
            JPanel card = displayedCards.get(target);
            styleCardSelection(card, true);
            card.repaint();
            java.awt.Rectangle bounds = javax.swing.SwingUtilities.convertRectangle(
                    card.getParent(), card.getBounds(), gamesPanel);
            // Scroll only the cards pane, never shift the game/hero banner.
            gamesPanel.scrollRectToVisible(bounds);
        }
        refreshHero(library.games());
        DesktopPlayStats.Snapshot current = statistics.get(selected.console, selected.rom);
        setStatus("SELECCIONADO  ·  " + selected.title() + "   |   "
                + DesktopPlayStats.duration(current.seconds)
                + " jugados · A / ENTER JUGAR");
    }

    private void moveSelected(int direction) {
        int next = DesktopLibraryGrid.move(selectedIndex,
                displayedGames.size(), libraryColumns, direction);
        if (next >= 0) selectGame(next);
    }

    private void playSelected() {
        if (selectedIndex < 0 || selectedIndex >= displayedGames.size()) {
            setStatus("No hay juegos en esta categoría; importa una ROM primero.");
            return;
        }
        DesktopLibrary.Game selected = displayedGames.get(selectedIndex);
        if (selected.isAvailable()) play(selected);
        else setStatus("La ROM seleccionada ya no existe: " + selected.title());
    }

    private void favoriteSelected() {
        if (selectedIndex < 0 || selectedIndex >= displayedGames.size()) return;
        DesktopLibrary.Game game = displayedGames.get(selectedIndex);
        selectedRom = game.rom;
        try {
            library.toggleFavorite(game);
            renderGames();
            setStatus(game.favorite ? "★ Favorito añadido: " + game.title()
                    : "Favorito quitado: " + game.title());
        } catch (IOException problem) {
            showError(problem.getMessage());
        }
    }

    private void cycleCategory(int direction) {
        String[] categories = {"TODOS", "FAVORITOS", "RECIENTES", "MAS_JUGADOS"};
        int index = 0;
        for (int i = 0; i < categories.length; i++)
            if (categories[i].equals(activeFilter)) index = i;
        int next = Math.floorMod(index + direction, categories.length);
        changeFilter(categories[next]);
    }

    /** Borderless, native-resolution console presentation; no OS display-mode switch. */
    private void toggleConsoleMode() {
        if (EmbeddedPlayerWindow.hasActiveSession()) {
            setStatus("Termina primero la partida antes de cambiar el modo de biblioteca.");
            return;
        }
        if (!consoleMode) {
            normalBounds = window.getBounds();
            java.awt.Rectangle screen = window.getGraphicsConfiguration().getBounds();
            window.dispose();
            window.setUndecorated(true);
            window.setExtendedState(JFrame.NORMAL);
            window.setBounds(screen);
            consoleMode = true;
        } else {
            window.dispose();
            window.setUndecorated(false);
            window.setExtendedState(JFrame.NORMAL);
            if (normalBounds != null) window.setBounds(normalBounds);
            consoleMode = false;
        }
        leftSidebar.setVisible(!consoleMode);
        refreshToolbarButtons();
        window.setVisible(true);
        window.toFront();
        window.requestFocus();
        // Sidebar visibility changes the available card width. Keep the
        // selected ROM and gamepad navigation aligned with the new grid.
        renderGames();
        if (selectedIndex >= 0) selectGame(selectedIndex);
        setStatus(consoleMode ? "MODO CONSOLA · CRUCETA / FLECHAS NAVEGAR · A / ENTER JUGAR · START / F11 SALIR"
                : "MODO VENTANA · START / F11 PARA PANTALLA COMPLETA");
    }

    private void bindConsoleKeys() {
        javax.swing.JRootPane root = window.getRootPane();
        String[] bindings = {"UP", "DOWN", "LEFT", "RIGHT"};
        int[] directions = {DesktopConsoleNavigation.UP,
                DesktopConsoleNavigation.DOWN, DesktopConsoleNavigation.LEFT,
                DesktopConsoleNavigation.RIGHT};
        for (int i = 0; i < bindings.length; i++) {
            final int move = directions[i];
            root.registerKeyboardAction(event -> {
                if (!search.isFocusOwner() && !EmbeddedPlayerWindow.hasActiveSession())
                    moveSelected(move);
            }, javax.swing.KeyStroke.getKeyStroke(bindings[i]),
                    javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
        root.registerKeyboardAction(event -> {
            if (!search.isFocusOwner() && !EmbeddedPlayerWindow.hasActiveSession())
                playSelected();
        }, javax.swing.KeyStroke.getKeyStroke("ENTER"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(event -> {
            if (!search.isFocusOwner() && !EmbeddedPlayerWindow.hasActiveSession())
                favoriteSelected();
        }, javax.swing.KeyStroke.getKeyStroke("F"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(event -> {
            if (!EmbeddedPlayerWindow.hasActiveSession()) toggleConsoleMode();
        }, javax.swing.KeyStroke.getKeyStroke("F11"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        root.registerKeyboardAction(event -> {
            if (consoleMode) toggleConsoleMode();
            else if (!"TODOS".equals(activeFilter)) changeFilter("TODOS");
        }, javax.swing.KeyStroke.getKeyStroke("ESCAPE"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Edge-triggered XInput library navigation. Never polls while a game owns JNI core. */
    private void pollController() {
        if (window == null || !window.isActive()
                || EmbeddedPlayerWindow.hasActiveSession()) {
            gamepad.reset();
            libraryFocusGate.disarm();
            return;
        }
        int mask;
        try {
            mask = WindowsRetroNative.gamepadMask();
        } catch (UnsatisfiedLinkError | RuntimeException unavailable) {
            // Development builds without the optional native bridge retain
            // full keyboard and mouse navigation.
            gamepadTimer.stop();
            setStatus("Mando XInput no disponible; teclado y ratón siguen funcionando.");
            return;
        }
        // After any modal or game, require a fully neutral controller frame
        // before interpreting any new button press. The game owns its input.
        if (!libraryFocusGate.accepts(mask)) {
            gamepad.reset();
            return;
        }
        // A Y button still held while returning from a modal must not
        // reopen GAME HUB on the first library frame.
        if ((mask & (1 << 7)) == 0) suppressDetailsUntilRelease = false;
        int effectiveMask = suppressDetailsUntilRelease ? (mask & ~(1 << 7)) : mask;
        int events = gamepad.poll(effectiveMask, System.nanoTime());
        if ((events & DesktopConsoleNavigation.DETAILS) != 0) {
            suppressDetailsUntilRelease = true;
            showSelectedGameHub();
            return;
        }
        if ((events & DesktopConsoleNavigation.CONSOLE) != 0) {
            toggleConsoleMode();
            return;
        }
        if ((events & DesktopConsoleNavigation.BACK) != 0) {
            if (consoleMode) toggleConsoleMode();
            else changeFilter("TODOS");
            return;
        }
        if ((events & DesktopConsoleNavigation.NEXT_CATEGORY) != 0)
            cycleCategory(1);
        if ((events & DesktopConsoleNavigation.PREVIOUS_CATEGORY) != 0)
            cycleCategory(-1);
        if ((events & DesktopConsoleNavigation.UP) != 0)
            moveSelected(DesktopConsoleNavigation.UP);
        else if ((events & DesktopConsoleNavigation.DOWN) != 0)
            moveSelected(DesktopConsoleNavigation.DOWN);
        else if ((events & DesktopConsoleNavigation.LEFT) != 0)
            moveSelected(DesktopConsoleNavigation.LEFT);
        else if ((events & DesktopConsoleNavigation.RIGHT) != 0)
            moveSelected(DesktopConsoleNavigation.RIGHT);
        if ((events & DesktopConsoleNavigation.FAVORITE) != 0) favoriteSelected();
        if ((events & DesktopConsoleNavigation.ACCEPT) != 0) playSelected();
    }

    private void changeFilter(String next) {
        activeFilter = next;
        search.setText("");
        searchRefresh.immediate(this::renderGames);
        setStatus("Biblioteca · " + next + "  ·  clic en JUGAR para iniciar");
    }

    private void randomGame() {
        java.util.List<DesktopLibrary.Game> playable = new java.util.ArrayList<>();
        for (DesktopLibrary.Game game : library.games())
            if (game.isAvailable()) playable.add(game);
        if (playable.isEmpty()) {
            setStatus("Importa tus ROMs antes de elegir un juego sorpresa.");
            importGames();
            return;
        }
        play(playable.get(java.util.concurrent.ThreadLocalRandom.current()
                .nextInt(playable.size())));
    }

    private void refreshHero(List<DesktopLibrary.Game> games) {
        if (heroHolder == null) return;
        heroHolder.removeAll();
        DesktopLibrary.Game selected = games.stream()
                .filter(game -> selectedRom != null && game.rom.equals(selectedRom))
                .findFirst().orElse(null);
        DesktopLibrary.Game featured = selected != null ? selected : games.stream()
                .filter(game -> game.lastPlayed > 0 && game.isAvailable())
                .max(java.util.Comparator.comparingLong(game -> game.lastPlayed))
                .orElseGet(() -> games.stream()
                        .filter(game -> game.favorite && game.isAvailable())
                        .findFirst()
                        .orElseGet(() -> games.stream()
                                .filter(DesktopLibrary.Game::isAvailable)
                                .findFirst().orElse(null)));
        CinemaHeroBackdrop banner = new CinemaHeroBackdrop(
                featured == null ? PINK : RetroArtwork.accent(featured.console));
        // The downloaded cover now colors the whole hero, not just the
        // small boxart. OnlineCoverArtwork still shows the complete cover.
        if (featured != null) {
            DesktopLibrary.Game heroGame = featured;
            coverService.load(heroGame.console, heroGame.rom, heroGame.title(),
                    image -> {
                        // A selection may change while HTTP is still working.
                        // Only animate the banner still mounted on screen.
                        if (image != null && banner.getParent() == heroHolder)
                            banner.showArtwork(image);
                    });
        }
        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JLabel eyebrow = label(featured == null ? "●  TU NUEVA CONSOLA"
                : "●  CONTINÚA TU AVENTURA", 11, new Color(255, 154, 212), true);
        eyebrow.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        details.add(eyebrow);
        details.add(Box.createVerticalStrut(11));
        String title = featured == null ? "ELIGE TU PRIMER JUEGO" : featured.title();
        JLabel name = label(title, featured == null ? 19 : 23, TEXT, true);
        name.setToolTipText(title);
        name.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        details.add(name);
        details.add(Box.createVerticalStrut(7));
        JLabel message = label(featured == null
                ? "GB · GBC · GBA · SNES   |   Todo dentro de OKUBI"
                : featured.console.label + "  |  Emulador integrado  |  Partidas protegidas",
                11, MUTED, false);
        message.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        details.add(message);
        if (featured != null) {
            DesktopPlayStats.Snapshot snapshot =
                    statistics.get(featured.console, featured.rom);
            details.add(Box.createVerticalStrut(7));
            JLabel metrics = label(snapshot.sessions == 0
                    ? "ESTADÍSTICAS DESDE 0.7  ·  AÚN SIN SESIONES"
                    : "TIEMPO REAL: " + DesktopPlayStats.duration(snapshot.seconds)
                    + "   ·   SESIONES: " + snapshot.sessions,
                    11, new Color(113, 237, 176), true);
            metrics.setAlignmentX(JLabel.LEFT_ALIGNMENT);
            details.add(metrics);
        }
        details.add(Box.createVerticalGlue());
        JPanel heroActions = new JPanel(new GridLayout(1, featured == null ? 1 : 2, 9, 0));
        heroActions.setOpaque(false);
        heroActions.setAlignmentX(JPanel.LEFT_ALIGNMENT);
        heroActions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 43));
        heroActions.setPreferredSize(new Dimension(0, 43));
        heroActions.add(button(featured == null ? "＋ IMPORTAR JUEGO"
                : "▶ JUGAR", true, featured == null ? this::importGames
                : () -> play(featured)));
        if (featured != null)
            heroActions.add(button("VER FICHA", false, () -> showGameHub(featured)));
        details.add(heroActions);
        banner.add(details, BorderLayout.CENTER);
        if (featured != null) {
            OnlineCoverArtwork art = new OnlineCoverArtwork(featured, true, coverService);
            art.setPreferredSize(new Dimension(196, 162));
            banner.add(art, BorderLayout.EAST);
        } else {
            JPanel motif = new RetroArtwork(DesktopLibrary.Console.GBA,
                    "OKUBI ULTRA", true);
            motif.setPreferredSize(new Dimension(185, 150));
            banner.add(motif, BorderLayout.EAST);
        }
        heroHolder.add(banner, BorderLayout.CENTER);
        heroHolder.revalidate();
        heroHolder.repaint();
    }

    /**
     * With only one game there used to be an empty half-row. Fill that space
     * with a useful, nonselectable companion card rather than duplicating ROMs.
     * This does not add entries to displayedGames or change controller indexes.
     */
    private JPanel gameCompanion(DesktopLibrary.Game game) {
        JPanel panel = panel(new Color(28, 23, 45));
        panel.setLayout(new BorderLayout(10, 9));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(82, 54, 107), 1, true),
                BorderFactory.createEmptyBorder(15, 18, 13, 18)));
        JPanel copy = panel(PANEL);
        copy.setOpaque(false);
        copy.setLayout(new BoxLayout(copy, BoxLayout.Y_AXIS));
        JLabel eyebrow = label("TU CENTRO DE PARTIDA", 11, PINK, true);
        eyebrow.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        copy.add(eyebrow);
        copy.add(Box.createVerticalStrut(7));
        DesktopPlayStats.Snapshot snap = statistics.get(game.console, game.rom);
        JLabel title = label(game.title(), 16, TEXT, true);
        title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        title.setToolTipText(game.title());
        copy.add(title);
        copy.add(Box.createVerticalStrut(6));
        JLabel stats = label(
                DesktopPlayStats.duration(snap.seconds) + " jugados  ·  "
                        + snap.sessions + " sesiones  ·  "
                        + game.console.name(),
                11, new Color(145, 225, 184), false);
        stats.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        copy.add(stats);
        panel.add(copy, BorderLayout.NORTH);
        JPanel actions = panel(PANEL);
        actions.setOpaque(false);
        actions.setLayout(new GridLayout(1, 2, 9, 0));
        actions.add(button("⇄ CROSS SAVE", false, this::showBackupNotice));
        actions.add(button("☁ CLOUD SAVE", false, this::showCloudSave));
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel quickStartGuide() {
        JPanel guide = panel(PANEL);
        guide.setLayout(new BorderLayout(10, 10));
        guide.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(82, 54, 107), 1, true),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)));
        guide.add(label("CONTROLES DE TU CONSOLA", 13, PINK, true),
                BorderLayout.NORTH);
        guide.add(label("Flechas para elegir  ·  Enter para jugar  ·  F11 modo consola",
                11, MUTED, false), BorderLayout.CENTER);
        guide.add(button("+ AÑADIR JUEGOS", false, this::importGames),
                BorderLayout.SOUTH);
        return guide;
    }

    private JPanel gameCard(DesktopLibrary.Game game) {
        CinemaLibraryCard card = new CinemaLibraryCard(
                game.title(), RetroArtwork.accent(game.console));
        card.setLayout(new BorderLayout(12, 0));
        styleCardSelection(card, game.rom.equals(selectedRom));
        OnlineCoverArtwork cover = new OnlineCoverArtwork(game, false, coverService);
        cover.setPreferredSize(new Dimension(112, 155));
        card.add(cover, BorderLayout.WEST);
        JPanel body = panel(PANEL);
        body.setOpaque(false);
        body.setLayout(new BorderLayout(0, 7));
        JPanel description = panel(PANEL);
        description.setOpaque(false);
        description.setLayout(new BoxLayout(description, BoxLayout.Y_AXIS));
        JLabel console = label("● " + game.console.label.toUpperCase(Locale.ROOT),
                10, RetroArtwork.accent(game.console), true);
        description.add(console);
        description.add(Box.createVerticalStrut(7));
        JLabel name = label(game.title(), 14, TEXT, true);
        name.setToolTipText(game.rom.toString());
        description.add(name);
        description.add(Box.createVerticalStrut(8));
        String last = game.lastPlayed > 0
                ? "Último inicio: " + DateFormat.getDateInstance(DateFormat.SHORT)
                        .format(new Date(game.lastPlayed))
                : "Listo para jugar";
        description.add(label(last, 10, MUTED, false));
        DesktopPlayStats.Snapshot stats = statistics.get(game.console, game.rom);
        if (stats.sessions > 0) {
            description.add(Box.createVerticalStrut(5));
            description.add(label(DesktopPlayStats.duration(stats.seconds)
                    + " reales  ·  " + stats.sessions + " sesiones",
                    10, new Color(130, 235, 174), true));
        }
        if (!game.isAvailable()) {
            description.add(Box.createVerticalStrut(5));
            description.add(label("ROM no encontrada", 10,
                    new Color(255, 158, 137), true));
        }
        body.add(description, BorderLayout.CENTER);
        JPanel actions = panel(PANEL);
        actions.setOpaque(false);
        actions.setLayout(new BorderLayout(6, 0));
        JButton fave = button(game.favorite ? "★" : "☆", false, () -> {
            try { selectedRom = game.rom; library.toggleFavorite(game); renderGames(); }
            catch (IOException error) { showError(error.getMessage()); }
        });
        fave.setToolTipText(game.favorite ? "Quitar favorito" : "Añadir favorito");
        fave.setPreferredSize(new Dimension(47, 34));
        actions.add(fave, BorderLayout.WEST);
        JButton play = button("▶ JUGAR", true, () -> play(game));
        play.setEnabled(game.isAvailable());
        actions.add(play, BorderLayout.CENTER);
        JButton info = button("i", false, () -> showGameHub(game));
        info.setPreferredSize(new Dimension(36, 34));
        info.setToolTipText("Abrir ficha GAME HUB PRO de " + game.title());
        info.getAccessibleContext().setAccessibleName("Ver ficha de " + game.title());
        actions.add(info, BorderLayout.EAST);
        body.add(actions, BorderLayout.SOUTH);
        card.add(body, BorderLayout.CENTER);
        // The cover, title and every non-button area can now be clicked.
        // The real JUGAR and FAVORITO buttons keep their own callbacks.
        DesktopLibraryMouse.bind(card, () -> {
            for (int i = 0; i < displayedGames.size(); i++)
                if (displayedGames.get(i).rom.equals(game.rom)) {
                    selectGame(i);
                    break;
                }
        }, () -> {
            if (game.isAvailable()) play(game);
        });
        return card;
    }

    private void showSelectedGameHub() {
        if (selectedIndex < 0 || selectedIndex >= displayedGames.size()) {
            setStatus("Selecciona un juego para abrir su ficha (tecla I).");
            return;
        }
        showGameHub(displayedGames.get(selectedIndex));
    }

    private void showGameHub(DesktopLibrary.Game game) {
        if (game == null) return;
        if (EmbeddedPlayerWindow.hasActiveSession()) {
            setStatus("Cierra la partida activa antes de abrir la ficha GAME HUB PRO.");
            return;
        }
        selectedRom = game.rom;
        DesktopPlayStats.Snapshot snapshot = statistics.get(game.console, game.rom);
        javax.swing.JDialog dialog = new javax.swing.JDialog(window,
                "OKUBI · GAME HUB PRO · " + game.title(),
                java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        final DesktopGameHubPanel[] holder = new DesktopGameHubPanel[1];
        Runnable close = dialog::dispose;
        DesktopGameHubPanel panel = new DesktopGameHubPanel(game, snapshot,
                coverService,
                () -> { dialog.dispose(); play(game); },
                () -> {
                    try {
                        library.toggleFavorite(game);
                        holder[0].updateFavorite(game.favorite);
                        renderGames();
                        setStatus(game.favorite
                                ? "★ Favorito añadido: " + game.title()
                                : "Favorito quitado: " + game.title());
                    } catch (IOException error) { showError(error.getMessage()); }
                },
                () -> {
                    dialog.dispose();
                    selectedRom = game.rom;
                    showSaveVault();
                },
                close);
        holder[0] = panel;
        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(panel);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(25);
        dialog.setContentPane(scroll);
        // Clamp against the actual screen, not only the parent dimensions,
        // so Game Hub remains navigable on laptop displays and high scaling.
        java.awt.Dimension screenSize =
                java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        dialog.setSize(DesktopCinemaLayout.gameHubSize(
                window.getWidth(), window.getHeight(),
                screenSize.width, screenSize.height));
        dialog.setLocationRelativeTo(window);
        // Modal gets its own Xbox input. The library polling is inactive
        // while the dialog is active; the emulator owns input while playing.
        DesktopGameHubInput hubInput = new DesktopGameHubInput(dialog, panel);
        dialog.getRootPane().registerKeyboardAction(event -> {
            if (game.isAvailable()) {
                dialog.dispose();
                play(game);
            }
        }, javax.swing.KeyStroke.getKeyStroke("control ENTER"),
                javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW);
        // Hashes and screenshot decoding are never executed on the Swing EDT.
        // The scanner only reads existing files and reports their presence.
        javax.swing.SwingWorker<DesktopGameHubMedia.Snapshot, Void> scanner =
                new javax.swing.SwingWorker<>() {
            @Override protected DesktopGameHubMedia.Snapshot doInBackground() {
                return DesktopGameHubMedia.inspect(game, DesktopSaves.defaultRoot());
            }
            @Override protected void done() {
                if (!dialog.isDisplayable()) return;
                try {
                    panel.showMedia(get());
                } catch (Exception ignored) {
                    panel.showMedia(new DesktopGameHubMedia.Snapshot(
                            false, false, java.util.List.of(),
                            "No se pudieron leer las capturas. Tus guardados siguen intactos."));
                }
            }
        };
        scanner.execute();
        dialog.setVisible(true);
    }

    private void refreshOnlineCovers() {
        setStatus("Revisando portadas de Libretro · tus juegos y partidas no se modifican");
        coverService.retryMissing(() -> {
            renderGames();
            setStatus("Buscando portadas online · las disponibles se guardarán para uso sin conexión");
        });
    }

    private void importGames() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Importar copias personales de juegos");
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Juegos GB, GBC, GBA, SNES", "gb", "gbc", "gba", "sfc", "smc"));
        if (chooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) return;
        int added = 0, repeated = 0;
        for (java.io.File file : chooser.getSelectedFiles()) {
            try {
                if (library.addGame(file.toPath())) added++; else repeated++;
            } catch (IOException error) { showError(error.getMessage()); }
        }
        renderGames();
        setStatus("Importados: " + added + " · Ya estaban: " + repeated);
    }

    private void importFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecciona una carpeta con tus juegos");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) return;
        try {
            int added = library.addFolder(chooser.getSelectedFile().toPath());
            renderGames();
            setStatus("Carpeta revisada · " + added + " juegos nuevos encontrados");
        } catch (IOException error) { showError(error.getMessage()); }
    }

    /**
     * Actual in-emulator sessions only. Old dates and external launches
     * cannot be reconstructed as genuine played minutes.
     */
    private void showStatsDashboard() {
        java.util.List<DesktopLibrary.Game> games =
                new java.util.ArrayList<>(library.games());
        games.removeIf(game -> statistics.get(game.console, game.rom).sessions == 0);
        games.sort((a, b) -> Long.compare(
                statistics.get(b.console, b.rom).seconds,
                statistics.get(a.console, a.rom).seconds));

        JPanel report = panel(PANEL);
        report.setBorder(BorderFactory.createEmptyBorder(17, 20, 17, 20));
        report.setLayout(new BorderLayout(0, 16));
        JPanel heading = panel(PANEL);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(label("OKUBI GAME HUB  ·  ESTADÍSTICAS REALES",
                20, PINK, true));
        heading.add(Box.createVerticalStrut(9));
        heading.add(label("Tiempo de juego: "
                        + DesktopPlayStats.duration(statistics.totalSeconds())
                        + "     |     Sesiones: " + statistics.totalSessions()
                        + "     |     Juegos con actividad: " + games.size(),
                13, TEXT, true));
        heading.add(Box.createVerticalStrut(5));
        heading.add(label("Registro local desde 0.7 · No cuenta pausas ni tiempo externo",
                11, MUTED, false));
        report.add(heading, BorderLayout.NORTH);

        JPanel rankings = panel(PANEL);
        rankings.setLayout(new BoxLayout(rankings, BoxLayout.Y_AXIS));
        if (games.isEmpty()) {
            rankings.add(label("Todavía no hay sesiones registradas.",
                    16, TEXT, true));
            rankings.add(Box.createVerticalStrut(12));
            rankings.add(label("Abre una ROM en el emulador integrado y juega.",
                    12, MUTED, false));
        } else {
            long max = Math.max(1,
                    statistics.get(games.get(0).console, games.get(0).rom).seconds);
            for (int i = 0; i < Math.min(15, games.size()); i++) {
                DesktopLibrary.Game game = games.get(i);
                DesktopPlayStats.Snapshot snapshot =
                        statistics.get(game.console, game.rom);
                JPanel entry = panel(new Color(40, 29, 55));
                entry.setBorder(BorderFactory.createEmptyBorder(11, 13, 11, 13));
                entry.setLayout(new BorderLayout(10, 8));
                String text = (i + 1) + ".  " + game.title() + "  ["
                        + game.console.name() + "]";
                JLabel title = label(text, 13, TEXT, true);
                title.setToolTipText(game.rom.toString());
                entry.add(title, BorderLayout.NORTH);
                javax.swing.JProgressBar time = new javax.swing.JProgressBar(0, 1000);
                time.setValue((int)Math.min(1000,
                        snapshot.seconds * 1000.0 / max));
                time.setForeground(RetroArtwork.accent(game.console));
                time.setBackground(BG);
                time.setBorderPainted(false);
                time.setStringPainted(false);
                time.setPreferredSize(new Dimension(230, 11));
                entry.add(time, BorderLayout.CENTER);
                entry.add(label(DesktopPlayStats.duration(snapshot.seconds)
                        + "  ·  " + snapshot.sessions + " partidas iniciadas",
                        11, MUTED, false), BorderLayout.EAST);
                rankings.add(entry);
                rankings.add(Box.createVerticalStrut(8));
            }
        }
        JScrollPane scroll = new JScrollPane(rankings);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(PANEL);
        scroll.setPreferredSize(new Dimension(760, 410));
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        report.add(scroll, BorderLayout.CENTER);
        report.add(label("Los guardados SRAM, RTC y estados siguen separados "
                + "de las estadísticas.", 11, MUTED, false), BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(window, report,
                "OKUBI · Game Hub · Estadísticas",
                JOptionPane.PLAIN_MESSAGE);
    }

    /** Windows PS1/PS2/PS3 manager; legacy GBA/SNES library is untouched. */
    private void showSmartPerformance() {
        if (cloudBusy || saveGuardBusy || crossSaveBusy
                || EmbeddedPlayerWindow.hasActiveSession()) {
            setStatus("SMART PC · Termina el juego o respaldo antes de abrir el catálogo.");
            return;
        }
        try {
            new DesktopSmartPerformanceDialog(window).setVisible(true);
            setStatus("SMART PC · Análisis y perfiles guardados en catálogo separado.");
        } catch (IOException problem) {
            showError("SMART PC · " + problem.getMessage());
        }
    }

    private void configureEmulators() {
        JPanel options = panel(PANEL);
        options.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        for (DesktopLibrary.Console console : DesktopLibrary.Console.values()) {
            Path builtIn = null;
            String builtinStatus;
            try {
                builtIn = BundledCores.resolve(console);
                builtinStatus = builtIn == null ? "FALTANTE" : "INCLUIDO Y LISTO";
            } catch (IOException invalid) {
                builtinStatus = "ARCHIVO DAÑADO";
            }
            JPanel consolePanel = panel(PANEL);
            consolePanel.setLayout(new BoxLayout(consolePanel, BoxLayout.Y_AXIS));
            JLabel title = label(console.label.toUpperCase(Locale.ROOT)
                    + "  ·  " + builtinStatus, 12,
                    "INCLUIDO Y LISTO".equals(builtinStatus) ? new Color(111, 232, 174) : PINK,
                    true);
            title.setAlignmentX(JLabel.LEFT_ALIGNMENT);
            consolePanel.add(title);
            consolePanel.add(Box.createVerticalStrut(5));

            JPanel nativeRow = panel(PANEL);
            nativeRow.setLayout(new BorderLayout(8, 4));
            Path currentCore = library.libretroCore(console);
            JLabel coreLabel = label("Dentro de OKUBI: "
                    + (currentCore == null ? "SIN CONFIGURAR" : currentCore.getFileName()),
                    11, TEXT, false);
            nativeRow.add(coreLabel, BorderLayout.CENTER);
            nativeRow.add(button("Elegir núcleo .dll", false, () -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Núcleo Libretro Windows x64 · " + console.label);
                chooser.setFileFilter(new FileNameExtensionFilter(
                        "Núcleo Libretro Windows x64 (.dll)", "dll"));
                if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
                    int confirmation = JOptionPane.showConfirmDialog(window,
                            "Las DLL ejecutan código en tu computadora. "
                            + "Utiliza solamente núcleos Libretro confiables de Windows x64.\n\n"
                            + chooser.getSelectedFile().getName(),
                            "Confirmar núcleo", JOptionPane.OK_CANCEL_OPTION);
                    if (confirmation != JOptionPane.OK_OPTION) return;
                    try {
                        library.setLibretroCore(console, chooser.getSelectedFile().toPath());
                        coreLabel.setText("Dentro de OKUBI: "
                                + library.libretroCore(console).getFileName());
                        setStatus("Núcleo integrado para " + console.label);
                    } catch (IOException error) { showError(error.getMessage()); }
                }
            }), BorderLayout.EAST);
            consolePanel.add(nativeRow);

            JPanel externalRow = panel(PANEL);
            externalRow.setLayout(new BorderLayout(8, 4));
            Path executable = library.emulator(console);
            JLabel externalLabel = label("Emulador externo: "
                    + (executable == null ? "SIN CONFIGURAR" : executable.getFileName()),
                    11, MUTED, false);
            externalRow.add(externalLabel, BorderLayout.CENTER);
            externalRow.add(button("Elegir .exe", false, () -> {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Emulador externo · " + console.label);
                chooser.setFileFilter(new FileNameExtensionFilter("Aplicación Windows", "exe"));
                if (chooser.showOpenDialog(window) == JFileChooser.APPROVE_OPTION) {
                    try {
                        library.setEmulator(console, chooser.getSelectedFile().toPath());
                        externalLabel.setText("Emulador externo: "
                                + library.emulator(console).getFileName());
                        setStatus("Emulador externo configurado para " + console.label);
                    } catch (IOException error) { showError(error.getMessage()); }
                }
            }), BorderLayout.EAST);
            consolePanel.add(externalRow);
            consolePanel.add(Box.createVerticalStrut(12));
            options.add(consolePanel);
        }
        options.add(label("JUGAR utiliza automáticamente los núcleos incluidos "
                + "sin configurar nada.", 11, MUTED, false));
        options.add(label("Los botones de selección manual son opciones "
                + "avanzadas y de respaldo.", 11, MUTED, false));
        JOptionPane.showMessageDialog(window, options,
                "OKUBI · Emuladores incluidos (GB/GBC/GBA/SNES)", JOptionPane.PLAIN_MESSAGE);
    }

    private void toggleSmartSaveGuard() {
        if (saveGuardBusy || cloudBusy || crossSaveBusy
                || EmbeddedPlayerWindow.hasActiveSession()) {
            setStatus("SMART SAVE GUARD · Termina primero la operación actual.");
            return;
        }
        try {
            smartGuard.setEnabled(!smartGuard.enabled());
            smartGuardToggle.setText(smartGuard.enabled()
                    ? "🛡 AUTO RESPALDO: ON" : "🛡 AUTO RESPALDO: OFF");
            setStatus(smartGuard.enabled()
                    ? "SMART SAVE GUARD activado · Copia automática antes de JUGAR."
                    : "SMART SAVE GUARD desactivado · Copias manuales intactas.");
        } catch (IOException error) {
            showError("SMART SAVE GUARD · No se guardó la preferencia: "
                    + error.getMessage());
        }
    }

    private void play(DesktopLibrary.Game game) {
        if (game == null || !game.isAvailable()) {
            setStatus("No se puede iniciar: la ROM ya no está disponible.");
            return;
        }
        if (saveGuardBusy || cloudBusy || crossSaveBusy
                || EmbeddedPlayerWindow.hasActiveSession()) {
            setStatus("SMART SAVE GUARD · Termina el respaldo o juego anterior.");
            return;
        }
        if (!smartGuard.enabled()) {
            launchWithResolvedEmulator(game);
            return;
        }
        saveGuardBusy = true;
        smartGuardToggle.setEnabled(false);
        setStatus("SMART SAVE GUARD · Verificando progreso antes de jugar...");
        javax.swing.SwingWorker<DesktopSaveVault.AutomaticResult, Void> task =
                new javax.swing.SwingWorker<>() {
            @Override protected DesktopSaveVault.AutomaticResult doInBackground()
                    throws Exception {
                return smartGuard.beforePlay(game);
            }
            @Override protected void done() {
                saveGuardBusy = false;
                smartGuardToggle.setEnabled(true);
                try {
                    DesktopSaveVault.AutomaticResult result = get();
                    if (result == DesktopSaveVault.AutomaticResult.DAILY_LIMIT) {
                        int approved = JOptionPane.showConfirmDialog(window,
                                "SMART SAVE GUARD alcanzó el máximo de 8 copias "
                                + "automáticas de este juego por día.\n"
                                + "No se borró ningún respaldo.\n\n"
                                + "¿Quieres iniciar SIN una nueva copia automática?",
                                "OKUBI · Copias automáticas limitadas",
                                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                        if (approved != JOptionPane.YES_OPTION) return;
                    } else if (result == DesktopSaveVault.AutomaticResult.CREATED)
                        setStatus("SMART SAVE GUARD · Copia nueva verificada; iniciando "
                                + game.title());
                    else if (result == DesktopSaveVault.AutomaticResult.UNCHANGED)
                        setStatus("SMART SAVE GUARD · Progreso ya protegido; iniciando "
                                + game.title());
                    else if (result == DesktopSaveVault.AutomaticResult.NO_SAVE)
                        setStatus("SMART SAVE GUARD · Juego sin SRAM/RTC todavía; iniciando.");
                    launchWithResolvedEmulator(game);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    setStatus("SMART SAVE GUARD · Inicio cancelado: lectura interrumpida.");
                } catch (java.util.concurrent.ExecutionException problem) {
                    Throwable cause = problem.getCause();
                    String message = cause == null ? problem.getMessage() : cause.getMessage();
                    int approved = JOptionPane.showConfirmDialog(window,
                            "No fue posible crear una copia automática de "
                            + game.title() + ":\n" + message + "\n\n"
                            + "Tus partidas no se modificaron.\n"
                            + "¿Iniciar el juego SIN este nuevo respaldo?",
                            "OKUBI · Confirmar juego sin protección",
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                    if (approved == JOptionPane.YES_OPTION)
                        launchWithResolvedEmulator(game);
                    else
                        setStatus("SMART SAVE GUARD · Inicio cancelado por seguridad.");
                }
            }
        };
        task.execute();
    }

    private void launchWithResolvedEmulator(DesktopLibrary.Game game) {
        if (cloudBusy || crossSaveBusy || EmbeddedPlayerWindow.hasActiveSession()) return;
        // The built-in cores are always preferred: import a ROM and press JUGAR.
        // Legacy custom DLLs and EXE launchers remain fallback options.
        Path core;
        try {
            core = BundledCores.resolve(game.console);
        } catch (IOException problem) {
            showError(problem.getMessage());
            return; // Never load a damaged core.
        }
        if (core == null) core = library.libretroCore(game.console);
        if (core != null) {
            if (!Files.isRegularFile(core)) {
                showError("El núcleo Libretro configurado no existe. "
                        + "Configura otra DLL desde Emuladores.");
                return;
            }
            try {
                EmbeddedPlayerWindow.launch(game, core, () -> {
                    try {
                        library.played(game);
                        setStatus("Jugando dentro de OKUBI: " + game.title());
                        renderGames();
                    } catch (IOException error) { showError(error.getMessage()); }
                }, statistics);
            } catch (IOException error) { showError(error.getMessage()); }
            return;
        }
        Path executable = library.emulator(game.console);
        if (executable == null) {
            JOptionPane.showMessageDialog(window,
                    "Esta copia no incluye el núcleo integrado para " + game.console.label
                    + ".\nExtrae de nuevo el paquete oficial COMPLETO de OKUBI Desktop.\n"
                    + "No necesitas cambiar tus partidas ni reinstalar OKUBI Android.\n"
                    + "También puedes utilizar un emulador externo desde Motores.",
                    "Falta núcleo integrado", JOptionPane.WARNING_MESSAGE);
            configureEmulators();
            return;
        }
        try {
            EmulatorLauncher.launch(executable, game.rom);
            library.played(game);
            setStatus("Iniciado en emulador externo: " + game.title());
            renderGames();
        } catch (IOException error) { showError(error.getMessage()); }
    }

    private <T> void cloudTask(String description, java.util.concurrent.Callable<T> operation,
                               java.util.function.Consumer<T> whenFinished) {
        if (cloudBusy || saveGuardBusy || crossSaveBusy
                || EmbeddedPlayerWindow.hasActiveSession()) {
            setStatus("Cierra la partida o termina la operación de respaldo anterior.");
            return;
        }
        cloudBusy = true;
        setStatus("CLOUD SAVE · " + description + "...");
        javax.swing.SwingWorker<T, Void> task = new javax.swing.SwingWorker<>() {
            @Override protected T doInBackground() throws Exception {
                return operation.call();
            }
            @Override protected void done() {
                cloudBusy = false;
                try {
                    whenFinished.accept(get());
                } catch (InterruptedException problem) {
                    Thread.currentThread().interrupt();
                    showError("CLOUD SAVE: operación interrumpida.");
                } catch (java.util.concurrent.ExecutionException problem) {
                    Throwable cause = problem.getCause();
                    showError("CLOUD SAVE: " + (cause == null
                            ? problem.getMessage() : cause.getMessage()));
                }
            }
        };
        task.execute();
    }

    private boolean configureCloudFolder() {
        if (cloudBusy || EmbeddedPlayerWindow.hasActiveSession()) return false;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecciona tu carpeta sincronizada de Google Drive");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (cloudSave.folder() != null && java.nio.file.Files.isDirectory(cloudSave.folder()))
            chooser.setCurrentDirectory(cloudSave.folder().toFile());
        if (chooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) return false;
        try {
            cloudSave.selectFolder(chooser.getSelectedFile().toPath(),
                    DesktopSaves.defaultRoot());
            JOptionPane.showMessageDialog(window,
                    "Carpeta enlazada dentro de OKUBI:\n" + cloudSave.folder()
                    + "\n\nImportante: el cliente Google Drive para escritorio "
                    + "es quien sube los archivos a Internet. "
                    + "OKUBI no tiene acceso a tu cuenta ni puede confirmar "
                    + "que Google ya los sincronizó.\n\n"
                    + "Revisa el estado de Drive antes de apagar el PC.",
                    "CLOUD SAVE · Carpeta seleccionada",
                    JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (IOException problem) {
            showError(problem.getMessage());
            return false;
        }
    }

    private void showCloudSave() {
        if (cloudBusy || EmbeddedPlayerWindow.hasActiveSession()) {
            JOptionPane.showMessageDialog(window,
                    "Cierra primero el juego y termina cualquier copia "
                    + "anterior. Los guardados SRAM/RTC deben estar completos.",
                    "CLOUD SAVE · Ocupado", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (cloudSave.folder() == null && !configureCloudFolder()) return;
        java.util.List<DesktopLibrary.Game> games =
                new java.util.ArrayList<>(library.games());
        games.removeIf(game -> !game.isAvailable());
        if (games.isEmpty()) {
            JOptionPane.showMessageDialog(window,
                    "Importa primero una ROM de tu propiedad a la biblioteca "
                    + "para comprobar que los respaldos sean del mismo juego.",
                    "CLOUD SAVE · Biblioteca vacía", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JPanel content = panel(PANEL);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(12, 15, 12, 15));
        content.add(label("☁ CLOUD SAVE  ·  DRIVE FOLDER", 19, PINK, true));
        content.add(Box.createVerticalStrut(10));
        content.add(label("Carpeta: " + cloudSave.folder(), 11, MUTED, false));
        content.add(Box.createVerticalStrut(13));
        content.add(label("Elige tu juego (debe ser la misma ROM en ambos dispositivos):",
                12, TEXT, true));
        String[] labels = new String[games.size()];
        int preferred = 0;
        for (int i = 0; i < games.size(); i++) {
            DesktopLibrary.Game game = games.get(i);
            labels[i] = game.title() + " · " + game.console.label;
            if (game.rom.equals(selectedRom)) preferred = i;
        }
        javax.swing.JComboBox<String> selection = new javax.swing.JComboBox<>(labels);
        selection.setSelectedIndex(preferred);
        selection.setBackground(new Color(45, 30, 64));
        selection.setForeground(TEXT);
        selection.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        content.add(Box.createVerticalStrut(8));
        content.add(selection);
        content.add(Box.createVerticalStrut(12));
        content.add(label("Solo guardados de cartucho SRAM / RTC. Nunca estados rápidos.",
                11, new Color(156, 235, 191), true));
        content.add(label("No hay sincronización automática ni sobreescrituras silenciosas.",
                11, MUTED, false));

        Object[] actions = {"REVISAR / RESTAURAR", "SUBIR NUEVA VERSIÓN",
                "CAMBIAR CARPETA", "CROSS SAVE LOCAL", "SALIR"};
        int chosen = JOptionPane.showOptionDialog(window, content,
                "OKUBI 0.9 · CLOUD SAVE",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, actions, actions[0]);
        if (chosen == -1 || chosen == 4) return;
        if (chosen == 2) {
            configureCloudFolder();
            return;
        }
        if (chosen == 3) {
            showBackupNotice();
            return;
        }
        DesktopLibrary.Game game = games.get(selection.getSelectedIndex());
        if (chosen == 0)
            cloudTask("Analizando las versiones de " + game.title(),
                    () -> cloudSave.inspect(game, DesktopSaves.defaultRoot()),
                    report -> showCloudVersions(game, report));
        if (chosen == 1)
            cloudTask("Comparando partida local con Drive",
                    () -> cloudSave.inspect(game, DesktopSaves.defaultRoot()),
                    report -> confirmCloudUpload(game, report));
    }

    private void confirmCloudUpload(DesktopLibrary.Game game,
                                    DesktopCloudSave.Report report) {
        if (report.localSha256 == null) {
            JOptionPane.showMessageDialog(window,
                    "Este juego no tiene aún SRAM/RTC guardados en PC. "
                    + "Guarda dentro del juego, ciérralo y vuelve a intentarlo.",
                    "CLOUD SAVE · Sin progreso local", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (report.status == DesktopCloudSave.Status.IDENTICAL) {
            JOptionPane.showMessageDialog(window,
                    "La última versión de esta ROM en la carpeta "
                    + "ya tiene el mismo contenido SRAM/RTC que tu PC. "
                    + "No hace falta subir otro archivo.",
                    "CLOUD SAVE · Sin cambios", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String conflict = report.status == DesktopCloudSave.Status.CONFLICT
                ? "⚠ CONFLICTO: el último respaldo de la carpeta "
                  + "y tu partida del PC son DIFERENTES.\n"
                  + "No podemos saber cuál es el progreso más reciente.\n\n"
                : "Se creará una copia adicional del guardado de PC.\n\n";
        int confirmed = JOptionPane.showConfirmDialog(window,
                conflict + "¿Guardar una nueva versión INMUTABLE de "
                + game.title() + "?\n"
                + "No se borrará ni reemplazará ningún respaldo anterior.\n\n"
                + "Comprueba luego que Google Drive para escritorio "
                + "termine de subir el archivo.",
                "CLOUD SAVE · Confirmar nueva versión",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmed != JOptionPane.YES_OPTION) return;
        cloudTask("Verificando y copiando SRAM/RTC",
                () -> cloudSave.upload(game, DesktopSaves.defaultRoot(), true),
                result -> {
                    JOptionPane.showMessageDialog(window,
                            (result.alreadyPresent
                                    ? "Ya existe una copia idéntica. No se duplicó.\n"
                                    : "Versión nueva guardada en tu carpeta sincronizada.\n")
                            + result.file
                            + "\n\nOKUBI comprobó la copia LOCAL. "
                            + "Google Drive para escritorio gestiona "
                            + "su subida: comprueba allí su estado. "
                            + "No se borró ninguna versión anterior.",
                            "CLOUD SAVE · Copia local verificada",
                            JOptionPane.INFORMATION_MESSAGE);
                    setStatus("CLOUD SAVE · Copia guardada: " + game.title());
                });
    }

    private void showCloudVersions(DesktopLibrary.Game game,
                                   DesktopCloudSave.Report report) {
        JPanel area = panel(PANEL);
        area.setBorder(BorderFactory.createEmptyBorder(13, 16, 13, 16));
        area.setLayout(new BoxLayout(area, BoxLayout.Y_AXIS));
        area.add(label("☁ " + game.title() + "  ·  " + game.console.name(),
                17, PINK, true));
        area.add(Box.createVerticalStrut(10));
        Color summary = report.status == DesktopCloudSave.Status.CONFLICT
                ? new Color(255, 166, 108) : new Color(133, 231, 182);
        area.add(label(report.status.label, 13, summary, true));
        area.add(Box.createVerticalStrut(7));
        area.add(label("Versiones verificadas para esta ROM: "
                        + report.revisions.size(), 12, TEXT, true));
        if (report.ignoredFiles > 0) {
            area.add(label(report.ignoredFiles
                            + " archivos de otros juegos o no válidos se omitieron.",
                    11, MUTED, false));
        }
        area.add(Box.createVerticalStrut(12));
        if (report.revisions.isEmpty()) {
            area.add(label("No se encontró un .okubisave compatible "
                    + "en la carpeta seleccionada.", 12, MUTED, false));
            JOptionPane.showMessageDialog(window, area,
                    "CLOUD SAVE · Sin versiones", JOptionPane.PLAIN_MESSAGE);
            return;
        }
        javax.swing.JComboBox<DesktopCloudSave.Revision> revisions =
                new javax.swing.JComboBox<>(
                        report.revisions.toArray(new DesktopCloudSave.Revision[0]));
        revisions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 45));
        revisions.setBackground(new Color(45, 30, 64));
        revisions.setForeground(TEXT);
        area.add(revisions);
        area.add(Box.createVerticalStrut(12));
        area.add(label("Si eliges restaurar, se hará una copia recuperable "
                + "de los guardados actuales del PC.", 11, MUTED, false));
        area.add(label("Las fechas de Drive NO determinan qué versión es mejor.",
                11, MUTED, false));
        Object[] options = {"RESTAURAR VERSIÓN ELEGIDA", "CANCELAR"};
        int answer = JOptionPane.showOptionDialog(window, area,
                "CLOUD SAVE · Historial seguro",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, options, options[1]);
        if (answer != 0) return;
        DesktopCloudSave.Revision target =
                (DesktopCloudSave.Revision) revisions.getSelectedItem();
        if (target == null) return;
        if (report.localSha256 != null
                && report.localSha256.equals(target.portableSha256)) {
            JOptionPane.showMessageDialog(window,
                    "Esa versión ya es idéntica a la partida del PC. "
                    + "No hay nada que restaurar.",
                    "CLOUD SAVE · Misma versión",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int confirmed = JOptionPane.showConfirmDialog(window,
                "Vas a restaurar a PC la versión:\n" + target.file.getFileName()
                + "\n\nSRAM: " + target.ramBytes + " bytes"
                + "\nRTC: " + target.rtcBytes + " bytes"
                + (target.skippedNonPortableState
                    ? "\nEstado de emulación: será OMITIDO."
                    : "")
                + "\n\nEsto puede reemplazar tu progreso actual. "
                + "Antes de escribir, OKUBI hará un respaldo verificable "
                + "para que puedas revertir la operación.\n"
                + "¿Confirmas la restauración?",
                "CLOUD SAVE · Confirmar recuperación",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirmed != JOptionPane.YES_OPTION) return;
        cloudTask("Restaurando partida con copia de recuperación",
                () -> cloudSave.download(
                        game, DesktopSaves.defaultRoot(), target),
                result -> {
                    JOptionPane.showMessageDialog(window,
                            "Restauración comprobada: " + game.title()
                            + "\nSRAM instalada: " + result.ramInstalled
                            + "\nRTC instalado: " + result.rtcInstalled
                            + "\nEstado rápido omitido: " + result.skippedState
                            + "\nCopia anterior: "
                            + (result.recoveryBackup == null
                               ? "No había diferencias o partida previa."
                               : result.recoveryBackup)
                            + "\n\nAbre el juego y verifica tu progreso.",
                            "CLOUD SAVE · Restauración verificada",
                            JOptionPane.INFORMATION_MESSAGE);
                    setStatus("CLOUD SAVE · Partida restaurada: " + game.title());
                });
    }

    /** New offline protection history; preserves Cross Save separately. */
    private void showSaveVault() {
        if (EmbeddedPlayerWindow.hasActiveSession() || cloudBusy || saveGuardBusy
                || crossSaveBusy) {
            setStatus("SAVE VAULT · Cierra el juego y termina otro respaldo antes de entrar.");
            return;
        }
        java.util.List<DesktopLibrary.Game> games = new java.util.ArrayList<>(library.games());
        games.removeIf(game -> !game.isAvailable());
        if (games.isEmpty()) {
            JOptionPane.showMessageDialog(window,
                    "Primero importa una de tus ROMs para consultar sus partidas.",
                    "SAVE VAULT · Sin juegos", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new DesktopSaveVaultDialog(window, games, selectedRom).show();
    }

    private void showBackupNotice() {
        if (EmbeddedPlayerWindow.hasActiveSession() || saveGuardBusy
                || cloudBusy || crossSaveBusy) {
            JOptionPane.showMessageDialog(window,
                    "Primero cierra el juego desde OKUBI. Necesitamos que el "
                    + "último guardado SRAM/RTC se confirme en el disco antes de "
                    + "exportar, importar o restaurar una partida.",
                    "CROSS SAVE · Partida en ejecución", JOptionPane.WARNING_MESSAGE);
            return;
        }

        java.util.List<DesktopLibrary.Game> games =
                new java.util.ArrayList<>(library.games());
        games.removeIf(game -> !game.isAvailable());
        if (games.isEmpty()) {
            JOptionPane.showMessageDialog(window,
                    "Primero importa una ROM a tu biblioteca. "
                    + "El respaldo se verifica contra el contenido exacto "
                    + "de ese cartucho para evitar partidas equivocadas.",
                    "CROSS SAVE · Seleccionar juego", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        JPanel content = panel(PANEL);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(13, 16, 13, 16));
        JLabel heading = label("CROSS SAVE  ·  ANDROID ↔ PC", 18, PINK, true);
        heading.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        content.add(heading);
        content.add(Box.createVerticalStrut(12));
        content.add(label("Selecciona el juego que recibirá o enviará la partida:",
                12, TEXT, true));

        String[] names = new String[games.size()];
        int active = 0;
        for (int i = 0; i < games.size(); i++) {
            DesktopLibrary.Game game = games.get(i);
            names[i] = game.title() + "  ·  " + game.console.label;
            if (game.rom.equals(selectedRom)) active = i;
        }
        javax.swing.JComboBox<String> choice = new javax.swing.JComboBox<>(names);
        choice.setSelectedIndex(active);
        choice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        choice.setBackground(new Color(52, 36, 72));
        choice.setForeground(TEXT);
        choice.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        content.add(Box.createVerticalStrut(10));
        content.add(choice);
        content.add(Box.createVerticalStrut(14));
        content.add(label("El archivo .okubisave es el MISMO formato de OKUBI Android.",
                11, MUTED, false));
        content.add(label("Solo se transfieren SRAM y RTC; los estados rápidos no.",
                11, MUTED, false));
        content.add(Box.createVerticalStrut(7));
        content.add(label("La partida anterior se respalda antes de importar.",
                11, new Color(137, 229, 185), true));

        Object[] actions = {"EXPORTAR AL CELULAR", "IMPORTAR DEL CELULAR",
                "VER RESPALDOS", "CANCELAR"};
        int action = JOptionPane.showOptionDialog(window, content,
                "OKUBI · UNIVERSAL CROSS SAVE",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE,
                null, actions, actions[0]);
        if (action < 0 || action == 3) return;
        DesktopLibrary.Game selected = games.get(choice.getSelectedIndex());
        try {
            if (action == 0) exportCrossSave(selected);
            else if (action == 1) importCrossSave(selected);
            else if (action == 2) {
                DesktopSaves saves = new DesktopSaves(
                        DesktopSaves.defaultRoot(), selected.console, selected.rom);
                Path recoveries = saves.directory.resolve("cross-save-recovery");
                JOptionPane.showMessageDialog(window,
                        "Guardados de esta ROM:\n" + saves.directory
                        + "\n\nCopias protegidas antes de importar:\n"
                        + recoveries
                        + "\n\nPara restaurar una copia anterior, utiliza "
                        + "IMPORTAR DEL CELULAR y selecciona uno de "
                        + "los archivos .okubisave de esa carpeta. "
                        + "Se creará otro respaldo antes de reemplazar nada.",
                        "CROSS SAVE · Recuperación", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (IOException problem) {
            showError(problem.getMessage());
        }
    }

    /**
     * Hashing a ROM and validating a .okubisave can be slow. Keep it off the
     * Swing EDT, and prevent game/Cloud/Vault overlap until disk I/O ends.
     */
    private <T> void universalTask(String description,
            java.util.concurrent.Callable<T> operation,
            java.util.function.Consumer<T> whenFinished) {
        if (crossSaveBusy || cloudBusy || saveGuardBusy
                || EmbeddedPlayerWindow.hasActiveSession()) {
            setStatus("UNIVERSAL CROSS SAVE · Cierra el juego y otro respaldo primero.");
            return;
        }
        crossSaveBusy = true;
        setStatus("UNIVERSAL CROSS SAVE · " + description + "...");
        javax.swing.SwingWorker<T, Void> worker = new javax.swing.SwingWorker<>() {
            @Override protected T doInBackground() throws Exception {
                return operation.call();
            }
            @Override protected void done() {
                crossSaveBusy = false;
                try {
                    whenFinished.accept(get());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    setStatus("UNIVERSAL CROSS SAVE · Operación interrumpida.");
                } catch (java.util.concurrent.ExecutionException problem) {
                    Throwable source = problem.getCause();
                    showError("UNIVERSAL CROSS SAVE · "
                            + (source == null ? problem.getMessage()
                                    : source.getMessage()));
                }
            }
        };
        worker.execute();
    }

    private record UniversalImportCheck(DesktopCrossSave.Preview preview,
            DesktopUniversalSavePreflight.Result readiness) { }

    private void exportCrossSave(DesktopLibrary.Game selected) throws IOException {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Exportar .okubisave para Android · " + selected.title());
        chooser.setSelectedFile(new java.io.File(
                selected.title().replaceAll("[^a-zA-Z0-9._-]", "_")
                + ".okubisave"));
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Respaldo universal OKUBI (.okubisave)", "okubisave"));
        if (chooser.showSaveDialog(window) != JFileChooser.APPROVE_OPTION) return;
        Path requested = chooser.getSelectedFile().toPath().toAbsolutePath();
        if (!requested.getFileName().toString().toLowerCase(Locale.ROOT)
                .endsWith(".okubisave"))
            requested = requested.resolveSibling(requested.getFileName() + ".okubisave");
        final Path destination = requested;
        universalTask("Exportando y verificando archivo Android compatible",
                () -> DesktopCrossSave.export(
                        selected, DesktopSaves.defaultRoot(), destination),
                written -> {
                    JOptionPane.showMessageDialog(window,
                            "Respaldo creado y verificado:\n" + written
                            + "\n\nCopia este archivo al celular y usa "
                            + "Importar respaldo en OKUBI Android con la misma ROM.\n"
                            + "No se transfieren estados rápidos ni archivos ROM.",
                            "UNIVERSAL CROSS SAVE · Exportación verificada",
                            JOptionPane.INFORMATION_MESSAGE);
                    setStatus("UNIVERSAL CROSS SAVE · Exportado " + selected.title());
                });
    }

    private void importCrossSave(DesktopLibrary.Game selected) throws IOException {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Elegir respaldo exportado desde OKUBI Android");
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Respaldo OKUBI (.okubisave)", "okubisave"));
        if (chooser.showOpenDialog(window) != JFileChooser.APPROVE_OPTION) return;
        Path source = chooser.getSelectedFile().toPath();

        universalTask("Comparando ROM, SRAM, RTC y estados del respaldo", () -> {
            DesktopCrossSave.Preview preview = DesktopCrossSave.inspect(
                    selected, source, DesktopSaves.defaultRoot());
            DesktopUniversalSavePreflight.Result readiness =
                    DesktopUniversalSavePreflight.evaluate(
                            selected, preview, DesktopSaves.defaultRoot());
            return new UniversalImportCheck(preview, readiness);
        }, checked -> {
            DesktopUniversalSavePreflight.Result readiness = checked.readiness();
            if (!readiness.canInstall()) {
                JOptionPane.showMessageDialog(window,
                        "Juego: " + selected.title() + "\n"
                        + "SRAM Android: " + readiness.incomingRamBytes() + " bytes\n"
                        + "RTC Android: " + readiness.incomingRtcBytes() + " bytes\n\n"
                        + readiness.message()
                        + "\n\nNo se escribió ninguna partida.",
                        "UNIVERSAL CROSS SAVE · Revisión sin cambios",
                        readiness.status() ==
                                DesktopUniversalSavePreflight.Status.IDENTICAL
                            ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
                setStatus("UNIVERSAL CROSS SAVE · " + readiness.status()
                        + " · " + selected.title());
                return;
            }

            String confirmation = "ROM: " + selected.title() + "\n"
                    + "Sistema: " + checked.preview().system + "\n"
                    + "SRAM: " + readiness.incomingRamBytes() + " bytes\n"
                    + "RTC: " + readiness.incomingRtcBytes() + " bytes\n"
                    + (readiness.willSkipQuickState()
                        ? "Estado rápido: se OMITIRÁ; no es portátil.\n"
                        : "Estado rápido: no incluido.\n")
                    + "\n" + readiness.message()
                    + "\n\nLa partida PC podría cambiar. OKUBI guarda primero "
                    + "una copia recuperable y comprueba cada archivo.\n\n"
                    + "¿Confirmas la importación?";
            int approved = JOptionPane.showConfirmDialog(window, confirmation,
                    "UNIVERSAL CROSS SAVE · Confirmar restauración",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (approved != JOptionPane.YES_OPTION) return;
            universalTask("Importando SRAM/RTC y guardando recuperación", () ->
                DesktopCrossSave.install(selected,
                        DesktopSaves.defaultRoot(), checked.preview()),
                result -> {
                    JOptionPane.showMessageDialog(window,
                            "Partida importada y verificada.\n"
                            + (result.ramInstalled ? "SRAM restaurada.\n" : "")
                            + (result.rtcInstalled ? "RTC restaurado.\n" : "")
                            + (result.skippedState
                                ? "Estado rápido omitido por incompatibilidad.\n" : "")
                            + "\nRespaldo anterior: "
                            + (result.recoveryBackup == null
                                ? "No había progreso previo distinto."
                                : result.recoveryBackup)
                            + "\n\nAbre el juego y comprueba tu progreso.",
                            "UNIVERSAL CROSS SAVE · Importación finalizada",
                            JOptionPane.INFORMATION_MESSAGE);
                    setStatus("UNIVERSAL CROSS SAVE · Partida verificada para "
                            + selected.title());
                });
        });
    }

    private void showMaintenance() {
        if (EmbeddedPlayerWindow.hasActiveSession()) {
            JOptionPane.showMessageDialog(window,
                    "Cierra el juego antes de revisar la caché temporal.",
                    "OKUBI · SMART MAINTENANCE", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        new DesktopMaintenanceDialog(window,
                DesktopMaintenanceService.defaultService()).setVisible(true);
    }

    private void setStatus(String message) {
        if (status != null) {
            status.setText(message);
            status.setToolTipText(message);
        }
    }

    private void showError(String message) {
        setStatus("Aviso: " + message);
        JOptionPane.showMessageDialog(window, message,
                "OKUBI Desktop", JOptionPane.WARNING_MESSAGE);
    }
}
