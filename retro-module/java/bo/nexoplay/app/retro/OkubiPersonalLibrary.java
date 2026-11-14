package bo.nexoplay.app.retro;

import android.content.Context;
import android.content.res.AssetManager;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Optional personal, locally assembled cartridges.
 *
 * This source file contains NO copyrighted ROM data. The distributed public
 * CI build deliberately has no assets/personal-roms folder. Only a locally
 * personalized APK can have the user's own files inserted into that folder.
 */
public final class OkubiPersonalLibrary {
    public static final String EXTRA_ASSET = "okubi.personal.asset";
    public static final String FIRE_RED = "personal-roms/pokemon-rojo-fuego.gba";
    public static final String EMERALD = "personal-roms/pokemon-esmeralda.gba";

    public static final class Game {
        public final String asset;
        public final String title;
        public final String system;
        Game(String asset, String title) {
            this.asset = asset;
            this.title = title;
            this.system = "GBA";
        }
    }
    private static final Game[] GAMES = {
        new Game(FIRE_RED, "Pokémon Rojo Fuego"),
        new Game(EMERALD, "Pokémon Esmeralda")
    };

    private OkubiPersonalLibrary() {}

    /** Do not permit arbitrary asset traversal from intents. */
    public static boolean isAllowed(String name) {
        return FIRE_RED.equals(name) || EMERALD.equals(name);
    }

    public static boolean exists(Context context, String name) {
        if (!isAllowed(name)) return false;
        try {
            String[] contents = context.getAssets().list("personal-roms");
            if (contents == null) return false;
            return Arrays.asList(contents).contains(name.substring(name.lastIndexOf('/') + 1));
        } catch (IOException ignored) {
            return false;
        }
    }

    public static List<Game> available(Context context) {
        List<Game> found = new ArrayList<>();
        for (Game game : GAMES)
            if (exists(context, game.asset)) found.add(game);
        return found;
    }

    public static InputStream open(Context context, String name) throws IOException {
        if (!exists(context, name))
            throw new IOException("La copia personal no está incluida en esta APK");
        return context.getAssets().open(name, AssetManager.ACCESS_STREAMING);
    }

    public static Game byAsset(String name) {
        for (Game game : GAMES) if (game.asset.equals(name)) return game;
        return null;
    }
}
