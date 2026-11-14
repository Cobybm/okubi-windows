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
import java.awt.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Phase 76: a fast, read-only cinema shelf for all locally linked retro and PC
 * titles. Never merges save directories, installs software or invents PC time.
 */
public final class DesktopUnifiedLibraryDialog extends JDialog {
    private static final Color BG = new Color(12, 9, 20);
    private static final Color CARD = new Color(27, 21, 39);
    private static final Color TEXT = new Color(247, 239, 254);
    private static final Color MUTED = new Color(196, 183, 210);
    private static final Color PINK = new Color(245, 58, 155);
    private static final Color TEAL = new Color(116, 229, 194);
    private static final int BATCH = 36;

    private final List<DesktopUnifiedLibrary.Item> all;
    private final OnlineCoverService covers;
    private final DesktopPortArtworkService pcArtwork =
            new DesktopPortArtworkService(DesktopPortArtworkService.defaultDirectory());
    private final Consumer<DesktopLibrary.Game> onPlayRetro;
    private final Runnable onOpenPorts;
    private final JTextField query = new JTextField();
    private final JPanel cardList = new JPanel();
    private final JScrollPane scroll = new JScrollPane(cardList);
    private final JLabel count = label("", 12, MUTED, false);
    private final JLabel totalStats = label("", 12, MUTED, false);
    private final JPanel categories = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    private final JPanel footer = new JPanel(new BorderLayout());
    private final JButton more = DesktopPortsTheme.button("VER MÁS JUEGOS", this::showMore);
    private DesktopUnifiedLibrary.Filter selected = DesktopUnifiedLibrary.Filter.ALL;
    private int limit = BATCH;
    private int lastColumns = 0;

    public DesktopUnifiedLibraryDialog(Window owner,
                                       List<DesktopUnifiedLibrary.Item> items,
                                       OnlineCoverService covers,
                                       Consumer<DesktopLibrary.Game> onPlayRetro,
                                       Runnable onOpenPorts) {
        super(owner, "OKUBI 遊 · BIBLIOTECA CINEMA UNIFICADA",
                ModalityType.APPLICATION_MODAL);
        if (items == null || covers == null || onPlayRetro == null || onOpenPorts == null)
            throw new IllegalArgumentException("Missing library dependencies");
        this.all = List.copyOf(items);
        this.covers = covers;
        this.onPlayRetro = onPlayRetro;
        this.onOpenPorts = onOpenPorts;

        setMinimumSize(new Dimension(760, 535));
        setSize(1070, 750);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                pcArtwork.close();
            }
        });
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(0, 10));

        JPanel header = new JPanel(new BorderLayout(0, 8));
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(15, 20, 0, 20));
        header.add(hero(), BorderLayout.NORTH);

        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));
        controls.add(label("BUSCAR EN TODO OKUBI", 11, TEAL, true));
        controls.add(Box.createVerticalStrut(7));
        query.setToolTipText("Buscar por título, consola o tipo de port");
        query.getAccessibleContext().setAccessibleName("Buscar en biblioteca unificada");
        query.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        query.setBackground(CARD);
        query.setForeground(TEXT);
        query.setCaretColor(PINK);
        query.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(112, 77, 145), 1, true),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        query.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        controls.add(query);
        controls.add(Box.createVerticalStrut(10));
        categories.setOpaque(false);
        for (DesktopUnifiedLibrary.Filter f : DesktopUnifiedLibrary.Filter.values()) {
            JButton button = DesktopPortsTheme.button(f.label,
                    () -> { selected = f; limit = BATCH; render(); });
            button.setName("filter-" + f.name());
            categories.add(button);
        }
        controls.add(categories);
        controls.add(Box.createVerticalStrut(8));
        controls.add(count);
        header.add(controls, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        cardList.setOpaque(true);
        cardList.setBackground(BG);
        cardList.setLayout(new BoxLayout(cardList, BoxLayout.Y_AXIS));
        cardList.setBorder(BorderFactory.createEmptyBorder(8, 20, 10, 20));
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        scroll.getViewport().addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) {
                int cols = columnsForWidth(scroll.getViewport().getWidth());
                if (cols != lastColumns) render();
            }
        });
        add(scroll, BorderLayout.CENTER);

        footer.setBackground(BG);
        footer.setBorder(BorderFactory.createEmptyBorder(9, 20, 16, 20));
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        buttons.add(DesktopPortsTheme.button("GESTIONAR PORTS PC", () -> {
            dispose();
            onOpenPorts.run();
        }));
        buttons.add(more);
        footer.add(buttons, BorderLayout.EAST);
        footer.add(label("Juegos y partidas permanecen en sus carpetas originales.",
                11, MUTED, false), BorderLayout.WEST);
        add(footer, BorderLayout.SOUTH);

        query.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { resetSearch(); }
            @Override public void removeUpdate(DocumentEvent e) { resetSearch(); }
            @Override public void changedUpdate(DocumentEvent e) { resetSearch(); }
            private void resetSearch() { limit = BATCH; render(); }
        });
        render();
    }

    private JPanel hero() {
        DesktopUnifiedLibrary.Summary stats = DesktopUnifiedLibrary.summary(all);
        JPanel hero = new JPanel(new BorderLayout(16, 5)) {
            @Override protected void paintComponent(Graphics original) {
                super.paintComponent(original);
                Graphics2D g = (Graphics2D) original.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    int w = getWidth(), h = getHeight();
                    if (w <= 8 || h <= 8) return;
                    g.setPaint(new GradientPaint(0, 0, new Color(78, 32, 99),
                            w, h, new Color(14, 14, 31)));
                    g.fillRoundRect(0, 0, w - 1, h - 1, 20, 20);
                    g.setColor(new Color(246, 78, 162, 95));
                    g.fillRoundRect(0, 0, 6, h, 8, 8);
                    g.setColor(new Color(144, 80, 220, 26));
                    g.fillOval(w - 250, -145, 360, 350);
                } finally { g.dispose(); }
            }
        };
        hero.setOpaque(false);
        hero.setPreferredSize(new Dimension(0, 126));
        hero.setBorder(BorderFactory.createEmptyBorder(16, 22, 16, 20));

        JPanel lines = new JPanel();
        lines.setOpaque(false);
        lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
        lines.add(label("OKUBI 遊  /  CINEMA UNIFIED", 22, TEXT, true));
        lines.add(Box.createVerticalStrut(7));
        lines.add(label("UN SOLO LUGAR PARA TUS JUEGOS RETRO Y PORTS DE WINDOWS",
                11, TEAL, true));
        lines.add(Box.createVerticalStrut(8));
        totalStats.setText(stats.retro() + " RETRO    ·    " + stats.ports()
                + " PORTS PC    ·    " + stats.favorites() + " FAVORITOS"
                + "    ·    " + DesktopPlayStats.duration(stats.trackedSeconds())
                + " RETRO JUGADOS");
        lines.add(totalStats);
        hero.add(lines, BorderLayout.CENTER);
        JButton manage = DesktopPortsTheme.button("+ PORTS", () -> {
            dispose();
            onOpenPorts.run();
        });
        hero.add(manage, BorderLayout.EAST);
        return hero;
    }

    private static JLabel label(String title, int size, Color color, boolean bold) {
        JLabel result = new JLabel(title);
        result.setForeground(color);
        result.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size));
        return result;
    }

    private static JTextArea description(String text, int size, Color color, int rows) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setForeground(color);
        area.setFont(new Font("Segoe UI", Font.PLAIN, size));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setRows(rows);
        area.setBorder(null);
        return area;
    }

    static int columnsForWidth(int width) {
        return width >= 1010 ? 3 : width >= 610 ? 2 : 1;
    }

    private void showMore() {
        limit = Math.min(all.size(), limit + BATCH);
        render();
    }

    private JPanel tile(DesktopUnifiedLibrary.Item item) {
        Color accent = item.kind() == DesktopUnifiedLibrary.Kind.RETRO
                ? RetroArtwork.accent(item.retro().console)
                : item.kind() == DesktopUnifiedLibrary.Kind.PC_PORT ? TEAL : PINK;
        CinemaLibraryCard card = new CinemaLibraryCard(item.title(), accent);
        card.setLayout(new BorderLayout(12, 10));
        card.setToolTipText(item.title() + " · " + item.platform());
        card.setPreferredSize(new Dimension(0, 228));

        if (item.retro() != null) {
            OnlineCoverArtwork cover = new OnlineCoverArtwork(item.retro(), false, covers);
            cover.setPreferredSize(new Dimension(102, 147));
            card.add(cover, BorderLayout.WEST);
        } else {
            DesktopPortArtworkService.Ref ref =
                    DesktopPortArtworkService.forLibraryId(item.id());
            if (ref != null) {
                // Wide gameplay captures must be letterboxed on compact cards,
                // never cropped into indistinguishable fragments.
                DesktopPortArtwork art = new DesktopPortArtwork(
                        pcArtwork, ref, 110, 147, 0, true);
                if (DesktopCinemaArtworkPolicy.source(pcArtwork, ref)
                        != DesktopCinemaArtworkPolicy.Source.UNVERIFIED) {
                    art.enableViewer(() -> openPortGallery(item, ref));
                }
                card.add(art, BorderLayout.WEST);
            } else {
                JPanel fallback = new JPanel(new BorderLayout());
                fallback.setOpaque(false);
                fallback.setPreferredSize(new Dimension(96, 147));
                JLabel pc = label("PC", 25, accent, true);
                pc.setHorizontalAlignment(JLabel.CENTER);
                fallback.add(pc, BorderLayout.CENTER);
                card.add(fallback, BorderLayout.WEST);
            }
        }
        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JTextArea title = description(item.title(), 14, TEXT, 2);
        title.setFont(new Font("Segoe UI", Font.BOLD, 14));
        title.setToolTipText(item.title());
        details.add(title);
        details.add(Box.createVerticalStrut(7));
        details.add(description(item.subtitle(), 11, MUTED, 2));
        details.add(Box.createVerticalStrut(8));
        details.add(label(item.favorite() ? "★ FAVORITO"
                        : item.available() ? "● DISPONIBLE" : "● ARCHIVO NO DISPONIBLE",
                10, item.available() ? TEAL : new Color(255, 191, 131), true));
        if (item.isPort()) {
            details.add(Box.createVerticalStrut(5));
            DesktopPortArtworkService.Ref artworkRef =
                    DesktopPortArtworkService.forLibraryId(item.id());
            DesktopCinemaArtworkPolicy.Source imageSource =
                    DesktopCinemaArtworkPolicy.source(pcArtwork, artworkRef);
            details.add(label(DesktopCinemaArtworkPolicy.label(imageSource), 10,
                    imageSource == DesktopCinemaArtworkPolicy.Source.UNVERIFIED
                            ? MUTED : TEAL, true));
            String hint = DesktopCinemaArtworkPolicy.galleryHint(artworkRef);
            if (!hint.isBlank()) {
                details.add(Box.createVerticalStrut(3));
                details.add(label(hint + " · ABRIR EN GRANDE", 10, MUTED, false));
            }
            details.add(Box.createVerticalStrut(5));
            details.add(description("Ejecutable externo · horas no medidas", 10, MUTED, 2));
        }
        card.add(details, BorderLayout.CENTER);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 0));
        actions.setOpaque(false);
        JButton play = DesktopPortsTheme.button(item.isPort() ? "▶ ABRIR PC" : "▶ JUGAR",
                () -> play(item));
        play.setEnabled(item.available());
        actions.add(play);
        if (item.isPort()) {
            DesktopPortArtworkService.Ref imageRef =
                    DesktopPortArtworkService.forLibraryId(item.id());
            if (imageRef != null
                    && DesktopCinemaArtworkPolicy.source(pcArtwork, imageRef)
                    != DesktopCinemaArtworkPolicy.Source.UNVERIFIED) {
                actions.add(DesktopPortsTheme.button("▣ GALERÍA",
                        () -> openPortGallery(item, imageRef)));
            }
        }
        card.add(actions, BorderLayout.SOUTH);
        return card;
    }

    /** Open the same official/gallery/manual media used in the Windows ports catalog. */
    private void openPortGallery(DesktopUnifiedLibrary.Item item,
                                 DesktopPortArtworkService.Ref ref) {
        if (ref == null || !item.isPort()) return;
        DesktopPortGalleryDialog gallery = new DesktopPortGalleryDialog(
                this, item.title(), pcArtwork, ref, 0);
        gallery.setVisible(true);
    }

    private void play(DesktopUnifiedLibrary.Item item) {
        if (!item.available()) {
            JOptionPane.showMessageDialog(this, "El archivo ya no existe.\n"
                    + "Vuelve a vincularlo desde la biblioteca correspondiente.",
                    "OKUBI · Archivo no encontrado", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (item.retro() != null) {
            dispose();
            onPlayRetro.accept(item.retro());
            return;
        }
        Path exe = item.file();
        int allowed = JOptionPane.showConfirmDialog(this,
                "Vas a iniciar un programa de terceros:\n" + exe
                        + "\n\nComprueba que reconoces su origen.\n"
                        + "Los ports guardan sus partidas por separado.",
                "OKUBI · Ejecutar port PC", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (allowed != JOptionPane.YES_OPTION) return;
        try {
            if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT)
                    .contains("windows"))
                throw new IOException("Los ports externos solo se inician en Windows.");
            Path safe = DesktopPortsLibrary.checkedExecutable(exe);
            new ProcessBuilder(safe.toString()).directory(safe.getParent().toFile()).start();
            dispose();
        } catch (IOException error) {
            JOptionPane.showMessageDialog(this, "No se pudo abrir el port:\n"
                    + error.getMessage(), "OKUBI · Port PC",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void render() {
        if (cardList == null) return;
        List<DesktopUnifiedLibrary.Item> matching =
                DesktopUnifiedLibrary.filter(all, selected, query.getText());
        int width = scroll.getViewport().getWidth();
        if (width <= 0) width = Math.max(700, getWidth() - 65);
        int cols = columnsForWidth(width);
        lastColumns = cols;
        cardList.removeAll();
        count.setText(matching.size() + " RESULTADOS  /  " + all.size() + " EN TU BIBLIOTECA");
        for (java.awt.Component component : categories.getComponents()) {
            if (component instanceof JButton button && button.getName() != null) {
                boolean active = button.getName().equals("filter-" + selected.name());
                button.setBackground(active
                        ? DesktopPortsTheme.BUTTON_ACTIVE : DesktopPortsTheme.BUTTON);
            }
        }
        int visible = Math.min(matching.size(), limit);
        for (int i = 0; i < visible; i += cols) {
            JPanel row = new JPanel(new GridLayout(1, cols, 12, 0));
            row.setBackground(BG);
            row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
            row.setPreferredSize(new Dimension(0, 255));
            row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 255));
            for (int j = 0; j < cols; j++) {
                if (i + j < visible) row.add(tile(matching.get(i + j)));
                else {
                    JPanel blank = new JPanel();
                    blank.setOpaque(false);
                    row.add(blank);
                }
            }
            cardList.add(row);
            cardList.add(Box.createVerticalStrut(12));
        }
        if (matching.isEmpty()) {
            JPanel none = new JPanel(new BorderLayout(0, 10));
            none.setBackground(CARD);
            none.setBorder(BorderFactory.createEmptyBorder(20, 18, 20, 18));
            none.add(label(all.isEmpty() ? "TU BIBLIOTECA CINEMA COMIENZA AQUÍ"
                    : "No hay juegos que coincidan.", 16, TEXT, true), BorderLayout.NORTH);
            none.add(description(all.isEmpty()
                    ? "Importa tus ROMs o vincula ports Windows desde la herramienta PORTS PC."
                    : "Prueba otro filtro o término de búsqueda.", 12, MUTED, 2),
                    BorderLayout.CENTER);
            none.setPreferredSize(new Dimension(0, 105));
            none.setMaximumSize(new Dimension(Integer.MAX_VALUE, 105));
            cardList.add(none);
        }
        more.setVisible(visible < matching.size());
        more.setText("VER MÁS · " + visible + " / " + matching.size());
        cardList.revalidate();
        cardList.repaint();
    }
}
