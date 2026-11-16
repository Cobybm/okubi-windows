package bo.okubi.desktop;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextArea;

/**
 * Audio / external-media control surface. No player state, ROMs, disk I/O or
 * system-wide audio action is performed until the user presses its button.
 *
 * A two-column, three-row grid prevents CinemaButton text from being elided
 * on Windows where JOptionPane formerly allocated only ~115px per action.
 */
public final class DesktopMediaPanel extends JPanel {
    static final int PANEL_WIDTH = 632;
    static final int PANEL_HEIGHT = 340;
    private static final Color BACKGROUND = new Color(25, 18, 38);
    private static final Color PINK = new Color(241, 67, 155);
    private static final Color MUTED = new Color(197, 184, 210);

    private final JLabel volumeLabel;
    private final JSlider volumeSlider;
    private final JPanel controls;

    public DesktopMediaPanel(AtomicInteger gameVolume, AtomicInteger speedIndex,
                             Runnable previous, Runnable playPause, Runnable next,
                             Runnable systemMute, Runnable muteGame, Runnable cycleSpeed) {
        super(new BorderLayout(12, 16));
        if (gameVolume == null || speedIndex == null || previous == null
                || playPause == null || next == null || systemMute == null
                || muteGame == null || cycleSpeed == null)
            throw new IllegalArgumentException("Missing media controls");

        setName("okubi-audio-panel");
        setOpaque(true);
        setBackground(BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(18, 20, 12, 20));
        setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));

        Font titleFont = new Font("Segoe UI", Font.BOLD, 17);
        String brandName = titleFont.canDisplay('遊') ? "OKUBI 遊" : "OKUBI";
        JLabel title = new JLabel(brandName + "  ·  MÚSICA Y AUDIO");
        title.setName("okubi-audio-title");
        title.setFont(titleFont);
        title.setForeground(PINK);
        JPanel header = new JPanel(new BorderLayout(14, 0));
        header.setOpaque(false);
        header.add(new DesktopOkubiLogo(52), BorderLayout.WEST);
        header.add(title, BorderLayout.CENTER);
        add(header, BorderLayout.NORTH);

        controls = new JPanel(new GridLayout(3, 2, 12, 10));
        controls.setName("okubi-audio-controls");
        controls.setOpaque(false);
        controls.setPreferredSize(new Dimension(PANEL_WIDTH - 40, 162));

        controls.add(action("previous", "◀ ANTERIOR", previous));
        controls.add(action("play-pause", "▶ PAUSA / REANUDAR", playPause));
        controls.add(action("next", "SIGUIENTE ▶", next));
        controls.add(action("system-mute", "SILENCIAR WINDOWS", systemMute));
        controls.add(action("game-mute", "SILENCIAR SOLO JUEGO", muteGame));
        CinemaButton speed = action("speed",
                "VELOCIDAD F6 · " + DesktopSpeedControl.label(speedIndex.get()),
                () -> {});
        // Label stays in sync with F6. Audio continues using the existing
        // DesktopSpeedControl and never changes the separate system volume.
        speed.addActionListener(event -> {
            cycleSpeed.run();
            speed.setText("VELOCIDAD F6 · "
                    + DesktopSpeedControl.label(speedIndex.get()));
        });
        controls.add(speed);
        add(controls, BorderLayout.CENTER);

        JPanel lower = new JPanel(new BorderLayout(8, 7));
        lower.setOpaque(false);
        volumeLabel = new JLabel();
        volumeLabel.setName("okubi-game-volume-label");
        volumeLabel.setForeground(MUTED);
        volumeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        volumeSlider = new JSlider(0, 100, gameVolume.get());
        volumeSlider.setName("okubi-game-volume-slider");
        volumeSlider.setOpaque(false);
        volumeSlider.setToolTipText("Solo volumen del juego, no modifica Windows ni música externa");
        updateVolume(gameVolume, gameVolume.get());
        volumeSlider.addChangeListener(event -> updateVolume(gameVolume, volumeSlider.getValue()));
        JPanel volumeRow = new JPanel(new BorderLayout(3, 5));
        volumeRow.setOpaque(false);
        volumeRow.add(volumeLabel, BorderLayout.NORTH);
        volumeRow.add(volumeSlider, BorderLayout.CENTER);
        lower.add(volumeRow, BorderLayout.NORTH);

        JTextArea explanation = new JTextArea(
                "Anterior, pausa y siguiente controlan el reproductor activo "
              + "si Windows lo admite. Silenciar Windows afecta a TODO el sonido "
              + "del PC. Silenciar solo juego y su volumen no afectan a tu música.");
        explanation.setName("okubi-audio-note");
        explanation.setEditable(false);
        explanation.setFocusable(false);
        explanation.setOpaque(false);
        explanation.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        explanation.setForeground(MUTED);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setRows(3);
        lower.add(explanation, BorderLayout.CENTER);
        add(lower, BorderLayout.SOUTH);
    }

    private static CinemaButton action(String id, String title, Runnable callback) {
        CinemaButton button = new CinemaButton(title, false, callback);
        button.setName("okubi-media-" + id);
        button.setPreferredSize(new Dimension(240, 44));
        button.setMinimumSize(new Dimension(180, 40));
        return button;
    }

    private void updateVolume(AtomicInteger gameVolume, int value) {
        gameVolume.set(Math.max(0, Math.min(100, value)));
        volumeLabel.setText("Volumen solo del juego: " + gameVolume.get() + "%");
    }

    JPanel controlsForTest() {
        return controls;
    }

    JSlider sliderForTest() {
        return volumeSlider;
    }
}
