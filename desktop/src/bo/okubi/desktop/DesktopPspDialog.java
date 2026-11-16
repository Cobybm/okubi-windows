package bo.okubi.desktop;

import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileNameExtensionFilter;

/** PSP cockpit without bundling PPSSPP, firmware or game binaries. */
public final class DesktopPspDialog extends JDialog {
    private final DesktopPspLauncher.Library library;
    private final DefaultListModel<Path> model=new DefaultListModel<>();
    private final JList<Path> list=new JList<>(model);
    private final JTextArea description=new JTextArea();
    private final JLabel status=new JLabel("Importa tu juego PSP y configura PPSSPP.");
    private final JButton play=new JButton("▶ JUGAR PSP");

    public DesktopPspDialog(java.awt.Window owner) throws IOException {
        super(owner,"OKUBI 遊 · PSP · PPSSPP",Dialog.ModalityType.APPLICATION_MODAL);
        library=new DesktopPspLauncher.Library(DesktopPspLauncher.defaultSettings());
        setMinimumSize(new Dimension(630,430));
        setSize(780,490);
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel root=new JPanel(new BorderLayout(10,10));
        root.setBorder(BorderFactory.createEmptyBorder(16,16,16,16));
        setContentPane(root);
        JLabel heading=new JLabel("PSP · TUS JUEGOS CON PPSSPP");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD,20f));
        root.add(heading,BorderLayout.NORTH);
        list.setCellRenderer((items,value,index,selected,focus)->{
            JLabel label=new JLabel(value==null?"":value.getFileName().toString());
            label.setOpaque(true);
            label.setBorder(BorderFactory.createEmptyBorder(6,8,6,8));
            label.setForeground(selected?items.getSelectionForeground():items.getForeground());
            label.setBackground(selected?items.getSelectionBackground():items.getBackground());
            return label;
        });
        JScrollPane left=new JScrollPane(list);
        left.setPreferredSize(new Dimension(290,295));
        root.add(left,BorderLayout.WEST);
        description.setEditable(false);
        description.setLineWrap(true);
        description.setWrapStyleWord(true);
        description.setFont(description.getFont().deriveFont(14f));
        description.setBorder(BorderFactory.createEmptyBorder(9,12,9,12));
        root.add(new JScrollPane(description),BorderLayout.CENTER);
        JPanel footer=new JPanel(new BorderLayout(0,6));
        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,6,0));
        JButton add=new JButton("+ IMPORTAR ISO / CSO / CHD / PBP");
        JButton choose=new JButton("Configurar PPSSPP");
        JButton official=new JButton("Descargar PPSSPP oficial");
        JButton remove=new JButton("Quitar ficha");
        buttons.add(add);buttons.add(choose);buttons.add(official);
        buttons.add(remove);buttons.add(play);
        footer.add(buttons,BorderLayout.NORTH);
        footer.add(status,BorderLayout.SOUTH);
        root.add(footer,BorderLayout.SOUTH);
        list.addListSelectionListener(e->{if(!e.getValueIsAdjusting())refresh();});
        add.addActionListener(e->addGames());
        choose.addActionListener(e->chooseEmulator());
        official.addActionListener(e->officialPage());
        remove.addActionListener(e->remove());
        play.addActionListener(e->play());
        refreshList(null);
    }

    private void refreshList(Path preferred) {
        model.clear();
        library.games().forEach(model::addElement);
        if(preferred!=null && library.games().contains(preferred))
            list.setSelectedValue(preferred,true);
        else if(!model.isEmpty())list.setSelectedIndex(0);
        refresh();
    }

    private void refresh() {
        Path selected=list.getSelectedValue();
        Path configured=library.emulator();
        play.setEnabled(selected!=null && configured!=null);
        description.setText("MOTOR RECOMENDADO: PPSSPP OFICIAL\n"
                +"Descarga: https://www.ppsspp.org/\n\n"
                +"Emulador: "+(configured==null?"NO CONFIGURADO":configured.getFileName())
                +"\n\nFormatos PSP: ISO, CSO, CHD y EBOOT.PBP.\n"
                +"Juegos comprimidos ZIP, RAR y 7Z no se ejecutan desde este panel.\n\n"
                +"Seleccionado: "+(selected==null?"Ninguno":selected.getFileName())+"\n\n"
                +(selected!=null && selected.getFileName().toString().toLowerCase()
                    .endsWith(".pbp")
                  ?"Atención: EBOOT.PBP también puede ser PS1. Confirma que "
                    +"es realmente un juego PSP antes de abrirlo.\n\n":"")
                +"MANDO Y TECLADO:\\n"
                +"PPSSPP detecta los mandos compatibles y permite ver/remapear "
                +"todas las teclas en Ajustes → Controles → Asignación de controles.\\n"
                +"OKUBI no inventa ni altera esa configuración.\\n\\n"
                +"No modifica ajustes de PPSSPP ni sus guardados. "
                +"El programa externo debe estar instalado y ser compatible "
                +"con el juego y tu hardware.");
        description.setCaretPosition(0);
    }

    private void addGames() {
        JFileChooser picker=new JFileChooser();
        picker.setDialogTitle("Añade tus propias copias PSP");
        picker.setMultiSelectionEnabled(true);
        picker.setFileFilter(new FileNameExtensionFilter(
                "PSP (.iso .cso .chd EBOOT.PBP)","iso","cso","chd","pbp"));
        if(picker.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        int added=0;
        Path last=null;
        for(java.io.File file:picker.getSelectedFiles()){
            try {
                Path game=file.toPath().toAbsolutePath().normalize();
                if(library.add(game))added++;
                last=game;
            } catch(IOException error) {
                JOptionPane.showMessageDialog(this,error.getMessage(),
                        "No se pudo importar",JOptionPane.WARNING_MESSAGE);
            }
        }
        status.setText("Nuevos: "+added+" · partidas y ROMs originales intactas.");
        refreshList(last);
    }

    private void chooseEmulator() {
        JFileChooser picker=new JFileChooser();
        picker.setDialogTitle("Elige PPSSPPWindows64.exe desde la descarga oficial");
        picker.setFileFilter(new FileNameExtensionFilter("PPSSPP Windows","exe"));
        if(picker.showOpenDialog(this)!=JFileChooser.APPROVE_OPTION)return;
        Path candidate=picker.getSelectedFile().toPath();
        int approved=JOptionPane.showConfirmDialog(this,
                "Los archivos EXE pueden ejecutar código de terceros.\n"
                +"Usa PPSSPP de www.ppsspp.org.\n\n"
                +candidate+"\n\n¿Confirmar este emulador?",
                "Configurar PPSSPP",JOptionPane.YES_NO_OPTION);
        if(approved!=JOptionPane.YES_OPTION)return;
        try {
            library.emulator(candidate);
            status.setText("PPSSPP configurado: "+candidate.getFileName());
            refresh();
        } catch(IOException error) {
            JOptionPane.showMessageDialog(this,error.getMessage(),
                    "PPSSPP no configurado",JOptionPane.WARNING_MESSAGE);
        }
    }

    private void play() {
        Path selected=list.getSelectedValue();
        if(selected==null || library.emulator()==null)return;
        if(selected.getFileName().toString().equalsIgnoreCase("EBOOT.PBP")) {
            int approved=JOptionPane.showConfirmDialog(this,
                    "EBOOT.PBP puede contener PS1 o PSP.\n¿Confirmas que "
                    +"esta es tu copia PSP?", "Confirmar PSP",
                    JOptionPane.YES_NO_OPTION);
            if(approved!=JOptionPane.YES_OPTION)return;
        }
        try {
            DesktopPspLauncher.start(library.emulator(),selected);
            status.setText("Iniciado con PPSSPP: "+selected.getFileName());
        } catch(IOException error) {
            JOptionPane.showMessageDialog(this,error.getMessage(),
                    "Error al iniciar PSP",JOptionPane.WARNING_MESSAGE);
        }
    }

    private void remove() {
        Path selected=list.getSelectedValue();
        if(selected==null)return;
        int ok=JOptionPane.showConfirmDialog(this,
                "Solo se quitará la ficha.\nNO se borrará el juego ni sus partidas.\n"
                +selected.getFileName(),"Quitar de PSP",
                JOptionPane.YES_NO_OPTION);
        if(ok!=JOptionPane.YES_OPTION)return;
        try {
            library.remove(selected);
            refreshList(null);
            status.setText("Ficha quitada, archivo y guardados intactos.");
        } catch(IOException problem) {
            JOptionPane.showMessageDialog(this,problem.getMessage(),"OKUBI",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    private void officialPage() {
        try {
            if(!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                throw new IOException("Abre https://www.ppsspp.org/downloads/");
            Desktop.getDesktop().browse(URI.create("https://www.ppsspp.org/downloads/"));
        } catch(Exception error) {
            JOptionPane.showMessageDialog(this,
                    "https://www.ppsspp.org/downloads/","PPSSPP oficial",
                    JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
