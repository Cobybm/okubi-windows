package bo.okubi.desktop;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingWorker;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

/** A curated native Swing catalogue of GitHub PC game ports; never downloads executables. */
public final class DesktopPortsDialog extends JDialog {
    private static final Color BACKGROUND = new Color(12, 9, 20);
    private static final Color CARD = new Color(32, 24, 45);
    private static final Color FOREGROUND = new Color(244, 238, 252);
    private static final Color PINK = new Color(245, 58, 155);
    private static final Color MUTED = new Color(189, 176, 207);

    private final DesktopPortsLibrary library;
    private final DesktopPortArtworkService artwork =
            new DesktopPortArtworkService(DesktopPortArtworkService.defaultDirectory());
    private final JTextField search = new JTextField();
    private final JComboBox<String> genre = new JComboBox<>();
    private final JComboBox<String> assetType = new JComboBox<>(new String[]{
            "Todos los ports", DesktopPortsCatalog.Assets.FREE.label,
            DesktopPortsCatalog.Assets.ORIGINAL.label,
            DesktopPortsCatalog.Assets.MIXED.label});
    private final JLabel count = text("", 12, MUTED, false);
    private final JPanel tiles = new JPanel();
    private final JScrollPane scroll = new JScrollPane(tiles);
    private final JPanel sectionCards = new JPanel(new CardLayout());
    private JButton curatedTab;
    private JButton exploreTab;
    private final JPanel onlineTiles = new JPanel();
    private final JScrollPane onlineScroll = new JScrollPane(onlineTiles);
    private final JTextField onlineQuery = new JTextField();
    private final JComboBox<DesktopGitHubPortsSearch.Scope> onlineScope =
            new JComboBox<>(DesktopGitHubPortsSearch.Scope.values());
    private final JLabel onlineStatus = text(
            "Explora repositorios públicos. Son candidatos, no ports Windows verificados.",
            12, MUTED, false);
    private final java.util.ArrayList<DesktopGitHubPortsSearch.Candidate> onlineResults =
            new java.util.ArrayList<>();
    private JButton onlineSearchButton;
    private JButton onlineMoreButton;
    private JButton onlinePreviousButton;
    private final java.util.Map<Integer, DesktopGitHubPortsSearch.SearchPage> pageCache =
            new java.util.HashMap<>();
    private String lastQuery = "";
    private DesktopGitHubPortsSearch.Scope lastScope =
            DesktopGitHubPortsSearch.Scope.SOURCE_PORTS;
    private int onlinePage;
    private int onlineTotal;
    private int searchGeneration;
    private boolean onlineBusy;

    public DesktopPortsDialog(Window owner, DesktopPortsLibrary library) {
        super(owner, "OKUBI 遊 · PORTS PC / GITHUB", ModalityType.APPLICATION_MODAL);
        this.library = library;
        setMinimumSize(new Dimension(720, 500));
        setSize(1020, 720);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) {
                artwork.close();
            }
        });
        getContentPane().setBackground(BACKGROUND);
        setLayout(new BorderLayout(13, 13));

        JPanel top = new JPanel();
        top.setOpaque(false);
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(10, 16, 5, 16));
        top.add(text("✦  PORTS PC  /  GITHUB", 21, PINK, true));
        top.add(Box.createVerticalStrut(5));
        top.add(text("Motores y juegos libres de fuentes oficiales · No instalamos nada sin tu permiso",
                12, MUTED, false));
        top.add(Box.createVerticalStrut(10));

        JPanel filters = new JPanel(new GridLayout(1, 3, 9, 0));
        filters.setOpaque(false);
        search.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        search.setBackground(CARD);
        search.setForeground(FOREGROUND);
        search.setCaretColor(PINK);
        search.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(87, 59, 117)),
                BorderFactory.createEmptyBorder(8, 9, 8, 9)));
        search.setToolTipText("Filtrar por nombre, género, motor o descripción");
        search.getAccessibleContext().setAccessibleName("Buscar ports");
        filters.add(search);
        genre.addItem("Todos");
        for (String g : DesktopPortsCatalog.genres()) genre.addItem(g);
        styleSelect(genre);
        styleSelect(assetType);
        filters.add(genre);
        filters.add(assetType);
        top.add(filters);
        top.add(Box.createVerticalStrut(7));
        top.add(count);
        add(top, BorderLayout.NORTH);

        tiles.setBackground(BACKGROUND);
        tiles.setBorder(BorderFactory.createEmptyBorder(4, 20, 14, 20));
        tiles.setLayout(new BoxLayout(tiles, BoxLayout.Y_AXIS));
        scroll.setBorder(null);
        scroll.setBackground(BACKGROUND);
        scroll.getViewport().setBackground(BACKGROUND);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        // Native Windows tabs were painting on top of cards in themed Swing.
        // Use an explicit card switcher to keep the viewport fully separate.
        sectionCards.setBackground(BACKGROUND);
        sectionCards.add(scroll, "CURATED");
        sectionCards.add(createOnlineExplorer(), "GITHUB");
        JPanel tabBar = new JPanel(new GridLayout(1, 2, 10, 0));
        tabBar.setOpaque(false);
        curatedTab = action("PORTS SELECCIONADOS", () -> showSection(false));
        exploreTab = action("EXPLORAR TODO GITHUB", () -> showSection(true));
        tabBar.add(curatedTab);
        tabBar.add(exploreTab);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        center.setBackground(BACKGROUND);
        center.setBorder(BorderFactory.createEmptyBorder(3, 14, 3, 14));
        center.add(tabBar, BorderLayout.NORTH);
        center.add(sectionCards, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        showSection(false);

        JPanel bottom = new JPanel(new BorderLayout(10, 0));
        bottom.setBackground(BACKGROUND);
        bottom.setBorder(BorderFactory.createEmptyBorder(9, 20, 14, 20));
        JLabel caution = text("Requisitos orientativos · Algunos ports necesitan tu juego original.",
                11, MUTED, false);
        bottom.add(caution, BorderLayout.CENTER);
        JPanel bottomActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        bottomActions.setOpaque(false);
        bottomActions.add(action("DETECTAR INSTALADOS", this::findInstalledPorts));
        bottomActions.add(action("EXPLORAR MÁS REPOSITORIOS",
                () -> showSection(true)));
        bottom.add(bottomActions, BorderLayout.EAST);
        add(bottom, BorderLayout.SOUTH);

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { render(); }
            @Override public void removeUpdate(DocumentEvent e) { render(); }
            @Override public void changedUpdate(DocumentEvent e) { render(); }
        });
        genre.addActionListener(e -> render());
        assetType.addActionListener(e -> render());
        render();
    }

    private JPanel createOnlineExplorer() {
        JPanel root = new JPanel(new BorderLayout(10, 10));
        root.setBackground(BACKGROUND);

        JPanel top = new JPanel();
        top.setBackground(BACKGROUND);
        top.setBorder(BorderFactory.createEmptyBorder(12, 16, 6, 16));
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.add(text("DESCUBRIMIENTO EN VIVO DE GITHUB", 16, PINK, true));
        top.add(Box.createVerticalStrut(5));
        top.add(text("Muchos resultados por categoría · Candidatos sin verificar · Sin descargas automáticas",
                11, MUTED, false));
        top.add(Box.createVerticalStrut(9));

        JPanel inputs = new JPanel(new BorderLayout(8, 0));
        inputs.setOpaque(false);
        onlineQuery.setToolTipText("Búsqueda libre: nombre, motor o palabras clave (máximo 80 caracteres)");
        onlineQuery.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        onlineQuery.setBackground(CARD);
        onlineQuery.setForeground(FOREGROUND);
        onlineQuery.setCaretColor(PINK);
        onlineQuery.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(87, 59, 117)),
                BorderFactory.createEmptyBorder(8, 9, 8, 9)));
        DesktopPortsTheme.combo(onlineScope);
        onlineScope.setToolTipText("Categoría de consulta; cambia la búsqueda cuando el texto está vacío");
        onlineScope.setPreferredSize(new Dimension(195, 38));
        onlineQuery.setToolTipText("Vacío: busca la categoría elegida. O escribe cualquier proyecto.");
        onlineQuery.addActionListener(e -> searchOnline(false));
        onlineSearchButton = action("BUSCAR EN GITHUB", () -> searchOnline(false));
        inputs.add(onlineScope, BorderLayout.WEST);
        inputs.add(onlineQuery, BorderLayout.CENTER);
        inputs.add(onlineSearchButton, BorderLayout.EAST);
        top.add(inputs);
        top.add(Box.createVerticalStrut(8));
        top.add(onlineStatus);
        root.add(top, BorderLayout.NORTH);

        onlineTiles.setBackground(BACKGROUND);
        onlineTiles.setBorder(BorderFactory.createEmptyBorder(5, 16, 10, 16));
        onlineTiles.setLayout(new BoxLayout(onlineTiles, BoxLayout.Y_AXIS));
        onlineScroll.setBorder(null);
        onlineScroll.getViewport().setBackground(BACKGROUND);
        onlineScroll.getVerticalScrollBar().setUnitIncrement(20);
        root.add(onlineScroll, BorderLayout.CENTER);

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 5));
        footer.setBackground(BACKGROUND);
        footer.add(text("Los resultados de GitHub NO garantizan compatibilidad con Windows.",
                11, MUTED, false));
        onlinePreviousButton = action("◀ ANTERIORES", this::previousOnlinePage);
        onlinePreviousButton.setEnabled(false);
        footer.add(onlinePreviousButton);
        onlineMoreButton = action("SIGUIENTES 40 ▶", () -> searchOnline(true));
        onlineMoreButton.setEnabled(false);
        footer.add(onlineMoreButton);
        root.add(footer, BorderLayout.SOUTH);
        onlineTiles.add(text(
                "Elige una categoría o escribe algo y pulsa BUSCAR EN GITHUB.",
                13, MUTED, false));
        return root;
    }

    private void searchOnline(boolean nextPage) {
        if (onlineBusy) return;
        DesktopGitHubPortsSearch.Scope scope =
                (DesktopGitHubPortsSearch.Scope) onlineScope.getSelectedItem();
        String query = onlineQuery.getText();
        if (nextPage && (!query.equals(lastQuery) || scope != lastScope)) {
            searchOnline(false);
            return;
        }
        int page = nextPage ? onlinePage + 1 : 1;
        try { DesktopGitHubPortsSearch.endpoint(scope, query, page); }
        catch (IllegalArgumentException error) {
            warning("Búsqueda no válida: " + error.getMessage());
            return;
        }
        if (!nextPage) {
            pageCache.clear();
            lastQuery = query;
            lastScope = scope;
        }
        if (nextPage && pageCache.containsKey(page)) {
            showOnlinePage(pageCache.get(page));
            return;
        }
        onlineBusy = true;
        final int generation = ++searchGeneration;
        onlineSearchButton.setEnabled(false);
        onlineMoreButton.setEnabled(false);
        onlinePreviousButton.setEnabled(false);
        onlineStatus.setText("Consultando GitHub... página " + page + " · sin descargar juegos");
        new SwingWorker<DesktopGitHubPortsSearch.SearchPage, Void>() {
            @Override protected DesktopGitHubPortsSearch.SearchPage doInBackground()
                    throws Exception {
                return DesktopGitHubPortsSearch.fetch(scope, query, page);
            }

            @Override protected void done() {
                if (generation != searchGeneration) return;
                onlineBusy = false;
                onlineSearchButton.setEnabled(true);
                try {
                    DesktopGitHubPortsSearch.SearchPage response = get();
                    pageCache.put(response.page(), response);
                    showOnlinePage(response);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    onlineStatus.setText("Búsqueda interrumpida. Puedes intentarlo otra vez.");
                    onlinePreviousButton.setEnabled(onlinePage > 1);
                    onlineMoreButton.setEnabled(onlineTotal > onlinePage * DesktopGitHubPortsSearch.PAGE_SIZE);
                } catch (java.util.concurrent.ExecutionException failed) {
                    Throwable cause = failed.getCause();
                    onlineStatus.setText("No se pudo completar la consulta. La página anterior sigue disponible.");
                    onlinePreviousButton.setEnabled(onlinePage > 1);
                    onlineMoreButton.setEnabled(onlineTotal > onlinePage * DesktopGitHubPortsSearch.PAGE_SIZE);
                    warning(cause == null ? "Fallo de GitHub."
                            : "GitHub: " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void previousOnlinePage() {
        if (onlineBusy || onlinePage <= 1) return;
        DesktopGitHubPortsSearch.SearchPage previous = pageCache.get(onlinePage - 1);
        if (previous != null) showOnlinePage(previous);
    }

    private void showOnlinePage(DesktopGitHubPortsSearch.SearchPage response) {
        onlinePage = response.page();
        onlineTotal = response.total();
        onlineResults.clear();
        onlineResults.addAll(response.candidates());
        onlinePreviousButton.setEnabled(onlinePage > 1);
        onlineMoreButton.setEnabled(response.hasNext());
        onlineStatus.setText("Página " + onlinePage + " · " + onlineResults.size()
                + " candidatos · " + onlineTotal + " coincidencias estimadas"
                + (response.incomplete() ? " · búsqueda parcial" : "")
                + " · NO verificados");
        renderOnline();
        onlineScroll.getVerticalScrollBar().setValue(0);
    }

    private JPanel candidateCard(DesktopGitHubPortsSearch.Candidate candidate) {
        JPanel panel = new JPanel(new BorderLayout(7, 7));
        panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(86, 52, 118)),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        String full = candidate.fullName();
        JLabel title = text(full, 14, FOREGROUND, true);
        title.setToolTipText(full);
        panel.add(title, BorderLayout.NORTH);

        JPanel content = new JPanel();
        content.setOpaque(false);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(text("★ " + candidate.stars() + "  ·  "
                + (candidate.language().isBlank() ? "Lenguaje no indicado" : candidate.language()),
                11, new Color(202, 174, 239), false));
        content.add(Box.createVerticalStrut(5));
        JTextArea summary = new JTextArea(candidate.description().isBlank()
                ? "Repositorio sin descripción. Revisa su documentación y sistema operativo."
                : candidate.description());
        summary.setRows(3);
        summary.setEditable(false);
        summary.setFocusable(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setBackground(CARD);
        summary.setForeground(MUTED);
        summary.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        content.add(summary);
        content.add(Box.createVerticalStrut(7));
        content.add(text("POR REVISAR · No sabemos si incluye un port Windows ni datos libres",
                10, new Color(255, 190, 132), true));
        Path linked = library.linked(candidate);
        if (linked != null) {
            content.add(Box.createVerticalStrut(5));
            JLabel label = text("VINCULADO · " + linked.getFileName(),
                    11, new Color(130, 220, 175), true);
            label.setToolTipText(linked.toString());
            content.add(label);
        }
        DesktopPortArtworkService.Ref previewRef =
                DesktopPortArtworkService.forCandidate(candidate);
        DesktopPortArtwork preview = new DesktopPortArtwork(
                artwork, previewRef, 420, 155);
        JPanel illustrated = new JPanel(new BorderLayout(0, 9));
        illustrated.setOpaque(false);
        illustrated.add(preview, BorderLayout.NORTH);
        illustrated.add(content, BorderLayout.CENTER);
        panel.add(illustrated, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        actions.setOpaque(false);
        actions.add(action("REVISAR GITHUB", () -> browse(candidate.github())));
        actions.add(action("RELEASES", () -> browse(candidate.releases())));
        actions.add(action("VINCULAR .EXE", () -> linkCandidate(candidate)));
        actions.add(action("IMAGEN", () -> chooseArtwork(previewRef, preview)));
        if (linked != null) {
            actions.add(action("▶ JUGAR", () -> launchCandidate(candidate)));
            actions.add(action("QUITAR", () -> unlinkCandidate(candidate)));
        }
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private void renderOnline() {
        onlineTiles.removeAll();
        if (onlineResults.isEmpty())
            onlineTiles.add(text("GitHub no encontró resultados para esta consulta.", 13, MUTED, false));
        else {
            for (int i = 0; i < onlineResults.size(); i += 2) {
                JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
                row.setOpaque(false);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
                row.setPreferredSize(new Dimension(0, 400));
                row.setAlignmentX(LEFT_ALIGNMENT);
                row.add(candidateCard(onlineResults.get(i)));
                if (i + 1 < onlineResults.size()) row.add(candidateCard(onlineResults.get(i + 1)));
                else {
                    JPanel empty = new JPanel();
                    empty.setOpaque(false);
                    row.add(empty);
                }
                onlineTiles.add(row);
                onlineTiles.add(Box.createVerticalStrut(12));
            }
        }
        onlineTiles.revalidate();
        onlineTiles.repaint();
    }

    /** Preview refresh and manual override never alter ROMs, EXEs or saves. */
    private void chooseArtwork(DesktopPortArtworkService.Ref ref,
                               DesktopPortArtwork panel) {
        String[] options = {
                "Elegir imagen local", "Actualizar desde GitHub",
                "Quitar imagen personalizada", "Cancelar"
        };
        int choice = JOptionPane.showOptionDialog(this,
                "Imagen para " + ref.fullName() + "\n\n"
                + "GitHub puede ofrecer un logo o una tarjeta del proyecto,\n"
                + "no necesariamente una portada o captura del juego.\n"
                + "Tu imagen personal tiene prioridad y solo se copia al caché local.",
                "OKUBI · Artwork Online",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, options[0]);
        if (choice == 0) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Seleccionar imagen para " + ref.fullName());
            chooser.setFileFilter(new FileNameExtensionFilter(
                    "Imagen PNG, JPEG o GIF (máximo 2 MB)",
                    "png", "jpg", "jpeg", "gif"));
            chooser.setAcceptAllFileFilterUsed(false);
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                artwork.chooseManualAsync(ref, chooser.getSelectedFile().toPath(),
                        panel::showImage, this::warning);
        } else if (choice == 1) {
            artwork.refresh(ref, panel::showImage);
        } else if (choice == 2) {
            try {
                artwork.clearManual(ref);
                artwork.refresh(ref, panel::showImage);
            } catch (IOException error) {
                warning("No se pudo quitar la imagen: " + error.getMessage());
            }
        }
    }

    private void linkCandidate(DesktopGitHubPortsSearch.Candidate candidate) {
        int response = JOptionPane.showConfirmDialog(this,
                "Este repositorio apareció en GitHub, pero OKUBI no confirmó\n"
                + "que sea un port funcional para Windows, ni que su código sea seguro.\n\n"
                + candidate.github()
                + "\n\nRevisa licencia, archivos, procedencia y requisitos.\n"
                + "SOLO vincula un .exe que hayas instalado y comprobado tú.",
                "OKUBI · Proyecto aún no verificado",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (response != JOptionPane.YES_OPTION) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Elegir .exe Windows para " + candidate.fullName());
        chooser.setFileFilter(new FileNameExtensionFilter("Windows .exe", "exe"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            library.link(candidate, chooser.getSelectedFile().toPath());
            renderOnline();
        } catch (IOException error) {
            warning("No se pudo vincular: " + error.getMessage());
        }
    }

    private void launchCandidate(DesktopGitHubPortsSearch.Candidate candidate) {
        Path exe = library.linked(candidate);
        if (exe == null) { warning("El .exe no existe. Revisa el vínculo."); return; }
        int answer = JOptionPane.showConfirmDialog(this,
                "EJECUTAR PROGRAMA DE TERCEROS\n\nRepositorio: " + candidate.fullName()
                + "\nArchivo: " + exe
                + "\n\nGitHub no garantiza que sea seguro ni que funcione en Windows.\n"
                + "OKUBI no lo instala, actualiza ni le da privilegios adicionales.",
                "OKUBI · Confirmar ejecución", JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try { library.launch(candidate); }
        catch (IOException error) { warning("No se pudo iniciar el .exe: " + error.getMessage()); }
    }

    private void unlinkCandidate(DesktopGitHubPortsSearch.Candidate candidate) {
        try {
            library.unlink(candidate);
            renderOnline();
        } catch (IOException error) {
            warning("No se pudo quitar el vínculo: " + error.getMessage());
        }
    }

    /**
     * User-initiated local folder scan. Never traverses arbitrary drives, reads
     * game save data, links games automatically, or starts processes.
     */
    private void findInstalledPorts() {
        JFileChooser picker = new JFileChooser();
        picker.setDialogTitle("Elige UNA carpeta con tus juegos/ports de PC");
        picker.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        picker.setAcceptAllFileFilterUsed(false);
        if (picker.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path root = picker.getSelectedFile().toPath();
        int approval = JOptionPane.showConfirmDialog(this,
                "OKUBI examinará nombres y cabeceras de archivos EXE en:\n"
                + root + "\n\n"
                + "Máximo " + DesktopInstalledPortsScanner.MAX_FILES + " archivos y "
                + DesktopInstalledPortsScanner.MAX_DEPTH + " niveles de carpetas.\n"
                + "No abrirá ROMs, partidas, BIOS ni ejecutará programas.\n"
                + "Los hallazgos son posibles coincidencias y debes aprobar cada vínculo.",
                "OKUBI · Detección local con límites",
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (approval != JOptionPane.YES_OPTION) return;

        new SwingWorker<DesktopInstalledPortsScanner.ScanResult, Void>() {
            @Override protected DesktopInstalledPortsScanner.ScanResult doInBackground()
                    throws Exception {
                return DesktopInstalledPortsScanner.scan(root);
            }

            @Override protected void done() {
                try { showDetectedPorts(get()); }
                catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (java.util.concurrent.ExecutionException error) {
                    warning("No se pudo examinar esa carpeta: " + error.getCause().getMessage());
                }
            }
        }.execute();
    }

    private void showDetectedPorts(DesktopInstalledPortsScanner.ScanResult result) {
        List<DesktopInstalledPortsScanner.Match> matches = result.matches();
        if (matches.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No encontramos EXE compatibles con los nombres del catálogo revisado.\n"
                    + "Archivos examinados: " + result.filesExamined()
                    + (result.truncated() ? " (se alcanzó un límite de seguridad)" : "")
                    + "\nTambién puedes usar VINCULAR .EXE manualmente.",
                    "OKUBI · Detección local", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String[] choices = matches.stream()
                .map(match -> match.port().title() + "  —  " + match.exe())
                .toArray(String[]::new);
        javax.swing.JList<String> select = new javax.swing.JList<>(choices);
        select.setSelectionMode(javax.swing.ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        select.setVisibleRowCount(Math.min(9, choices.length));
        select.setBackground(CARD);
        select.setForeground(FOREGROUND);
        JScrollPane selection = new JScrollPane(select);
        selection.setPreferredSize(new Dimension(690, Math.min(300, 38 + choices.length * 25)));
        JPanel content = new JPanel(new BorderLayout(6, 9));
        content.add(new JLabel("<html>Posibles ports instalados: " + choices.length
                + ". Selecciona únicamente los que reconozcas.<br>"
                + "No se instalará, ejecutará ni modificará ningún juego."
                + (result.truncated() ? "<br>Escaneo limitado para proteger el rendimiento." : "")
                + "</html>"), BorderLayout.NORTH);
        content.add(selection, BorderLayout.CENTER);

        int ok = JOptionPane.showConfirmDialog(this, content,
                "OKUBI · Vincular ports detectados", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (ok != JOptionPane.OK_OPTION || select.isSelectionEmpty()) return;

        int linked = 0;
        int errors = 0;
        for (int index : select.getSelectedIndices()) {
            DesktopInstalledPortsScanner.Match match = matches.get(index);
            Path previous = library.linked(match.port());
            if (previous != null && !previous.equals(match.exe())) {
                int replace = JOptionPane.showConfirmDialog(this,
                        "Este port ya tiene un ejecutable vinculado:\n" + previous
                        + "\n\n¿Reemplazarlo con:\n" + match.exe() + "?",
                        "OKUBI · Conservar vínculos existentes",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (replace != JOptionPane.YES_OPTION) continue;
            }
            try {
                library.link(match.port(), match.exe());
                linked++;
            } catch (IOException failed) {
                errors++;
            }
        }
        render();
        JOptionPane.showMessageDialog(this,
                "Ports vinculados: " + linked + " · Errores: " + errors
                + "\nSolo se guardaron rutas locales. Los juegos no se ejecutaron.",
                "OKUBI · Detección finalizada", JOptionPane.INFORMATION_MESSAGE);
    }

    private static JLabel text(String content, int size, Color color, boolean bold) {
        JLabel label = new JLabel(content);
        label.setForeground(color);
        label.setFont(new Font("Segoe UI", bold ? Font.BOLD : Font.PLAIN, size));
        return label;
    }

    private static void styleSelect(JComboBox<String> combo) {
        DesktopPortsTheme.combo(combo);
    }

    private static JButton action(String title, Runnable callback) {
        return DesktopPortsTheme.button(title, callback);
    }

    private void showSection(boolean exploreGitHub) {
        ((CardLayout) sectionCards.getLayout()).show(
                sectionCards, exploreGitHub ? "GITHUB" : "CURATED");
        curatedTab.setBackground(exploreGitHub
                ? DesktopPortsTheme.BUTTON : DesktopPortsTheme.BUTTON_ACTIVE);
        exploreTab.setBackground(exploreGitHub
                ? DesktopPortsTheme.BUTTON_ACTIVE : DesktopPortsTheme.BUTTON);
        curatedTab.getAccessibleContext().setAccessibleDescription(
                exploreGitHub ? "Mostrar los ports revisados" : "Pestaña actual");
        exploreTab.getAccessibleContext().setAccessibleDescription(
                exploreGitHub ? "Pestaña actual" : "Explorar repositorios GitHub");
    }

    private JPanel card(DesktopPortsCatalog.Port port) {
        JPanel panel = new JPanel(new BorderLayout(5, 7));
        panel.setBackground(CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(76, 49, 102), 1, true),
                BorderFactory.createEmptyBorder(12, 13, 12, 13)));

        JPanel heading = new JPanel(new BorderLayout(5, 0));
        heading.setOpaque(false);
        heading.add(text(port.title(), 15, FOREGROUND, true), BorderLayout.NORTH);
        heading.add(text(port.genre() + "  ·  " + port.load().label,
                11, new Color(195, 158, 235), false), BorderLayout.SOUTH);
        panel.add(heading, BorderLayout.NORTH);

        JPanel description = new JPanel();
        description.setOpaque(false);
        description.setLayout(new BoxLayout(description, BoxLayout.Y_AXIS));
        JTextArea summary = new JTextArea(port.description());
        summary.setBackground(CARD);
        summary.setForeground(MUTED);
        summary.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        summary.setEditable(false);
        summary.setFocusable(false);
        summary.setLineWrap(true);
        summary.setWrapStyleWord(true);
        summary.setRows(2);
        summary.setBorder(null);
        description.add(summary);
        description.add(Box.createVerticalStrut(5));
        description.add(text(port.requirement(), 11,
                port.assets() == DesktopPortsCatalog.Assets.ORIGINAL
                        ? new Color(255, 194, 130) : new Color(136, 224, 188), true));
        description.add(Box.createVerticalStrut(4));
        JTextArea requirements = new JTextArea(port.instruction());
        requirements.setBackground(CARD);
        requirements.setForeground(MUTED);
        requirements.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        requirements.setEditable(false);
        requirements.setFocusable(false);
        requirements.setLineWrap(true);
        requirements.setWrapStyleWord(true);
        requirements.setRows(2);
        requirements.setBorder(null);
        description.add(requirements);

        Path linked = library.linked(port);
        JLabel linkedLabel = text(linked == null ? "No vinculado"
                : "VINCULADO · " + linked.getFileName(), 10,
                linked == null ? MUTED : new Color(135, 229, 178), true);
        linkedLabel.setToolTipText(linked == null ? "Selecciona un .exe instalado por ti"
                : linked.toString());
        description.add(Box.createVerticalStrut(7));
        description.add(linkedLabel);
        DesktopPortArtworkService.Ref previewRef =
                DesktopPortArtworkService.forPort(port);
        DesktopPortArtwork preview = new DesktopPortArtwork(
                artwork, previewRef, 420, 155);
        JPanel illustrated = new JPanel(new BorderLayout(0, 9));
        illustrated.setOpaque(false);
        illustrated.add(preview, BorderLayout.NORTH);
        illustrated.add(description, BorderLayout.CENTER);
        panel.add(illustrated, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        actions.setOpaque(false);
        JButton releases = action("DESCARGAS", () -> browse(port.releases()));
        releases.setToolTipText("Consultar la página oficial del proyecto en GitHub");
        actions.add(releases);
        actions.add(action("VINCULAR .EXE", () -> link(port)));
        actions.add(action("IMAGEN", () -> chooseArtwork(previewRef, preview)));
        if (linked != null) {
            JButton launch = action("▶ JUGAR", () -> launch(port));
            launch.setBackground(new Color(130, 40, 94));
            actions.add(launch);
            actions.add(action("QUITAR", () -> unlink(port)));
        }
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private void render() {
        if (tiles == null) return;
        DesktopPortsCatalog.Assets assets = switch (assetType.getSelectedIndex()) {
            case 1 -> DesktopPortsCatalog.Assets.FREE;
            case 2 -> DesktopPortsCatalog.Assets.ORIGINAL;
            case 3 -> DesktopPortsCatalog.Assets.MIXED;
            default -> null;
        };
        List<DesktopPortsCatalog.Port> visible = DesktopPortsCatalog.filter(
                search.getText(), (String) genre.getSelectedItem(), assets);
        count.setText(visible.size() + " de " + DesktopPortsCatalog.all().size()
                + " proyectos   ·   Código abierto   ·   GitHub oficial");
        tiles.removeAll();
        if (visible.isEmpty()) {
            tiles.add(text("No se encontraron ports. Prueba con otra búsqueda.",
                    14, MUTED, false));
        } else {
            for (int i = 0; i < visible.size(); i += 2) {
                JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
                row.setBackground(BACKGROUND);
                row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 400));
                row.setPreferredSize(new Dimension(0, 400));
                row.setAlignmentX(LEFT_ALIGNMENT);
                row.add(card(visible.get(i)));
                if (i + 1 < visible.size()) row.add(card(visible.get(i + 1)));
                else {
                    JPanel empty = new JPanel();
                    empty.setOpaque(false);
                    row.add(empty);
                }
                tiles.add(row);
                tiles.add(Box.createVerticalStrut(11));
            }
        }
        tiles.revalidate();
        tiles.repaint();
        SwingUtilities.invokeLater(() -> scroll.getVerticalScrollBar().setValue(0));
    }

    private void browse(URI uri) {
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !("github.com".equalsIgnoreCase(uri.getHost()))) {
            warning("Enlace fuera de GitHub bloqueado.");
            return;
        }
        try {
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new IOException("Tu equipo no tiene navegador predeterminado.");
            Desktop.getDesktop().browse(uri);
        } catch (IOException | UnsupportedOperationException error) {
            warning("No se pudo abrir GitHub: " + error.getMessage()
                    + "\nEnlace: " + uri);
        }
    }

    private void link(DesktopPortsCatalog.Port port) {
        int approval = JOptionPane.showConfirmDialog(this,
                "OKUBI solo enlaza un juego que tú hayas instalado desde su fuente confiable.\n"
                + "No descarga, no actualiza y no certifica la firma digital de otros programas.\n"
                + "¿Quieres seleccionar el ejecutable .exe de " + port.title() + "?",
                "OKUBI · Vincular port Windows",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (approval != JOptionPane.YES_OPTION) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecciona el .exe local de " + port.title());
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setFileFilter(new FileNameExtensionFilter("Ejecutables de Windows (*.exe)", "exe"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try {
            library.link(port, chooser.getSelectedFile().toPath());
            render();
        } catch (IOException error) {
            warning("No se pudo vincular: " + error.getMessage());
        }
    }

    private void unlink(DesktopPortsCatalog.Port port) {
        try {
            library.unlink(port);
            render();
        } catch (IOException error) {
            warning("No se pudo quitar el vínculo: " + error.getMessage());
        }
    }

    private void launch(DesktopPortsCatalog.Port port) {
        Path exe = library.linked(port);
        if (exe == null) { warning("El ejecutable ya no existe. Vincúlalo de nuevo."); return; }
        int approval = JOptionPane.showConfirmDialog(this,
                "Vas a ejecutar un programa de terceros fuera de OKUBI:\n"
                + exe + "\n\nSolo continúa si confías en el archivo y su origen.\n"
                + "OKUBI no le concederá permisos de administrador ni moverá tus partidas.",
                "OKUBI · Ejecutar " + port.title(),
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (approval != JOptionPane.YES_OPTION) return;
        try {
            library.launch(port);
        } catch (IOException error) {
            warning("No se pudo ejecutar: " + error.getMessage());
        }
    }

    private void warning(String message) {
        JOptionPane.showMessageDialog(this, message, "OKUBI · Ports PC",
                JOptionPane.WARNING_MESSAGE);
    }
}
