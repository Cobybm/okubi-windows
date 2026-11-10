package bo.nexoplay.app.retro;

import android.net.Uri;
import android.view.Surface;
import java.io.Closeable;

/**
 * Adapter boundary for a future emulator engine.
 * Engines must be independently licensed and tested per CPU architecture.
 * No emulator engine or copyrighted BIOS/game files are included here.
 */
public interface RetroCore extends Closeable {
    enum Console { GB, GBC, GBA, NES, SNES, SMS, GG, MD, PCE, A2600, PS1, NDS, N64, PSP, DC, SAT }
    enum Key { UP, DOWN, LEFT, RIGHT, A, B, C, X, Y, Z, L, R, START, SELECT }

    /** Called once before loading a game; frame renderer must respect surface lifecycle. */
    void attachVideo(Surface surface, int width, int height);
    /** Load through Android Storage Access Framework; do not assume file-system paths. */
    void load(Uri documentUri, Console console) throws Exception;
    void setKey(Key key, boolean pressed);
    void pause();
    void resume();
    /** Optional save-state support; implementation decides serialization. */
    byte[] saveState() throws Exception;
    void restoreState(byte[] state) throws Exception;
    @Override void close();
}
