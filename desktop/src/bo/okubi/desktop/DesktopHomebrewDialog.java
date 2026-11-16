package bo.okubi.desktop;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
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
 * Emulator-oriented free game discovery. Fixed links go directly to the
 * original author or a moderated homebrew directory; nothing copyrighted is
 * mirrored. Direct one-click imports remain strictly allowlisted by downloader.
 */
public final class DesktopHomebrewDialog {
    private static final String[] FILTERS = {
        "Todas las consolas", "Game Boy / GBC", "Game Boy Advance",
        "Nintendo NES", "Super Nintendo", "PlayStation 1",
        "SEGA / Mega Drive", "ScummVM / PC"
    };
    private DesktopHomebrewDialog() { }

    private static boolean matches(String filter, DesktopHomebrewDownloads.Entry e) {
        String p=e.platform().toUpperCase(Locale.ROOT);
        return switch(filter) {
            case "Game Boy / GBC" -> p.contains("GB");
            case "Game Boy Advance" -> p.contains("GBA");
            case "Nintendo NES" -> p.equals("NES");
            case "Super Nintendo" -> p.equals("SNES");
            case "PlayStation 1" -> p.equals("PS1");
            case "SEGA / Mega Drive" -> p.contains("SEGA");
            case "ScummVM / PC" -> p.contains("SCUMMVM");
            default -> true;
        };
    }

    private static DesktopHomebrewDownloads.Entry chosen(
            List<DesktopHomebrewDownloads.Entry> visible, JList<String> list) {
        int i=list.getSelectedIndex();
        return i>=0 && i<visible.size() ? visible.get(i) : null;
    }

    private static String compatibilityNote(String platform) {
        String p=platform.toUpperCase(Locale.ROOT);
        if(p.matches(".*(GB|GBC|GBA).*"))
            return "OKUBI Windows incluye núcleos para GB, GBC y GBA.";
        if(p.equals("SNES"))
            return "OKUBI Windows incluye núcleo SNES. Descarga una ROM .sfc/.smc.";
        if(p.equals("PS1"))
            return "Para PS1 se necesita un emulador externo compatible, por ejemplo DuckStation. "
                    + "Conserva juntos los archivos .bin y .cue; abre el archivo .cue.";
        if(p.equals("NES"))
            return "Para NES se necesita un emulador externo compatible. "
                    + "Esta ficha no instala el emulador automáticamente.";
        if(p.contains("SEGA"))
            return "Para Mega Drive/Genesis se necesita un emulador externo compatible.";
        if(p.contains("SCUMMVM"))
            return "Para estos juegos hace falta ScummVM; no son ROMs de Game Boy/SNES.";
        return "Comprueba la plataforma, los derechos y el emulador requerido antes de importar.";
    }

    public static void show(JFrame owner, Consumer<Path> onImported) {
        List<DesktopHomebrewDownloads.Entry> entries=DesktopHomebrewDownloads.entries();
        List<DesktopHomebrewDownloads.Entry> visible=new ArrayList<>();
        JDialog dialog=new JDialog(owner,"OKUBI 遊 · Juegos gratuitos para emuladores",
                Dialog.ModalityType.MODELESS);
        dialog.setSize(new Dimension(870,560));
        dialog.setMinimumSize(new Dimension(650,440));
        dialog.setLocationRelativeTo(owner);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

        JPanel root=new JPanel(new BorderLayout(12,12));
        root.setBorder(javax.swing.BorderFactory.createEmptyBorder(17,17,17,17));
        dialog.setContentPane(root);
        JLabel title=new JLabel("DESCUBRE · DESCARGAS PARA EMULADORES");
        title.setFont(title.getFont().deriveFont(Font.BOLD,19f));
        root.add(title,BorderLayout.NORTH);

        JPanel side=new JPanel();
        side.setLayout(new BoxLayout(side,BoxLayout.Y_AXIS));
        JComboBox<String> filter=new JComboBox<>(FILTERS);
        filter.setMaximumSize(new Dimension(380,36));
        side.add(new JLabel("Elige una consola:"));
        side.add(filter);
        javax.swing.DefaultListModel<String> model=new javax.swing.DefaultListModel<>();
        JList<String> list=new JList<>(model);
        JScrollPane scroll=new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(330,330));
        side.add(scroll);
        root.add(side,BorderLayout.WEST);

        JTextArea description=new JTextArea();
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setFont(description.getFont().deriveFont(14f));
        description.setBorder(javax.swing.BorderFactory.createEmptyBorder(12,12,12,12));
        root.add(new JScrollPane(description),BorderLayout.CENTER);

        JPanel bottom=new JPanel(new FlowLayout(FlowLayout.RIGHT,7,4));
        JButton download=new JButton("Descargar e importar en OKUBI");
        JButton official=new JButton("Abrir página para descargar");
        JButton close=new JButton("Cerrar");
        JLabel state=new JLabel(" ");
        bottom.add(state); bottom.add(download);bottom.add(official);bottom.add(close);
        root.add(bottom,BorderLayout.SOUTH);

        Runnable refresh=()->{
            DesktopHomebrewDownloads.Entry entry=chosen(visible,list);
            boolean direct=entry!=null && DesktopHomebrewDownloads.authorizedDirect(entry);
            official.setEnabled(entry!=null);
            download.setEnabled(direct);
            if(entry==null) {
                description.setText("No hay juegos en esta categoría. "
                        +"Selecciona otra consola o usa el catálogo general.");
                return;
            }
            description.setText(entry.title()+"\n\nConsola: "+entry.platform()
                    +"\n\nPágina de descarga del autor o proyecto:\n"+entry.page()
                    +"\n\n"+compatibilityNote(entry.platform())
                    +"\n\n"+(direct
                    ?"DESCARGA DIRECTA: OKUBI puede obtener este juego gratuito "
                    +"y verificar su archivo para incorporarlo automáticamente."
                    :"DESCARGAR DESDE EL AUTOR: pulsa «Abrir página para descargar», "
                    +"descarga la ROM gratuita ofrecida por el creador y después "
                    +"impórtala en tu biblioteca de OKUBI.")
                    +"\n\nAlgunas páginas contienen otros títulos de pago. "
                    +"OKUBI no ofrece BIOS ni copias comerciales no autorizadas.");
            description.setCaretPosition(0);
        };
        Runnable applyFilter=()->{
            String category=(String)filter.getSelectedItem();
            visible.clear();
            model.removeAllElements();
            for(DesktopHomebrewDownloads.Entry entry:entries) {
                if(matches(category,entry)) {
                    visible.add(entry);
                    model.addElement(entry.title()+"   ["+entry.platform()+"]");
                }
            }
            list.setSelectedIndex(visible.isEmpty()?-1:0);
            refresh.run();
        };
        filter.addActionListener(event->applyFilter.run());
        list.addListSelectionListener(event->{
            if(!event.getValueIsAdjusting())refresh.run();
        });
        official.addActionListener(event->{
            DesktopHomebrewDownloads.Entry entry=chosen(visible,list);
            if(entry==null) return;
            try{
                java.net.URI url=java.net.URI.create(entry.page());
                if(!"https".equalsIgnoreCase(url.getScheme()) || url.getUserInfo()!=null)
                    throw new IOException("Enlace inválido");
                if(!Desktop.isDesktopSupported()
                        || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                    throw new IOException("No se encontró un navegador compatible.");
                Desktop.getDesktop().browse(url);
            }catch(IOException|IllegalArgumentException error) {
                JOptionPane.showMessageDialog(dialog,error.getMessage(),"OKUBI",
                        JOptionPane.WARNING_MESSAGE);
            }
        });
        download.addActionListener(event->{
            DesktopHomebrewDownloads.Entry entry=chosen(visible,list);
            if(!DesktopHomebrewDownloads.authorizedDirect(entry))return;
            int confirm=JOptionPane.showConfirmDialog(dialog,
                    "¿Descargar "+entry.title()+" de una fuente autorizada?\n"
                    +"Se guardará en OKUBI/homebrew sin modificar tus partidas.",
                    "OKUBI · Descarga gratuita",JOptionPane.YES_NO_OPTION);
            if(confirm!=JOptionPane.YES_OPTION)return;
            download.setEnabled(false);
            list.setEnabled(false);
            filter.setEnabled(false);
            state.setText("Descargando y verificando...");
            new SwingWorker<DesktopHomebrewDownloads.Receipt,Void>() {
                @Override protected DesktopHomebrewDownloads.Receipt doInBackground()
                        throws Exception {
                    return DesktopHomebrewDownloads.download(
                            entry,DesktopHomebrewDownloads.defaultDirectory());
                }
                @Override protected void done() {
                    list.setEnabled(true);
                    filter.setEnabled(true);
                    refresh.run();
                    if(!dialog.isDisplayable())return;
                    try {
                        DesktopHomebrewDownloads.Receipt receipt=get();
                        state.setText("ROM verificada · incorporando a biblioteca");
                        onImported.accept(receipt.file());
                    }catch(Exception failure){
                        Throwable cause=failure.getCause()==null?failure:failure.getCause();
                        state.setText("Error de descarga.");
                        JOptionPane.showMessageDialog(dialog,cause.getMessage(),"OKUBI",
                                JOptionPane.WARNING_MESSAGE);
                    }
                }
            }.execute();
        });
        close.addActionListener(event->dialog.dispose());
        applyFilter.run();
        dialog.setVisible(true);
    }
}
