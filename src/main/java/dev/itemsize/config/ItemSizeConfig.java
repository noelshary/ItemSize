package dev.itemsize.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;

import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Client-side scale profiles, stored by registry ID so modded items work too. */
public final class ItemSizeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type PROFILE_MAP = new TypeToken<Map<String, ScaleProfile>>() { }.getType();
    private static final Map<String, ScaleProfile> PROFILES = new HashMap<>();
    private static final float MIN_SCALE = 0.10f;
    private static final float MAX_SCALE = 5.00f;

    private ItemSizeConfig() { }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("itemsize.json");
    }

    public static synchronized void load() {
        Path path = file();
        if (!Files.exists(path)) {
            return;
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            Map<String, ScaleProfile> loaded = GSON.fromJson(reader, PROFILE_MAP);
            PROFILES.clear();
            if (loaded != null) {
                loaded.forEach((id, profile) -> {
                    if (id != null && profile != null) {
                        profile.sanitize();
                        PROFILES.put(id, profile);
                    }
                });
            }
        } catch (Exception exception) {
            System.err.println("[ItemSize] Could not read config: " + exception.getMessage());
        }
    }

    public static synchronized void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(PROFILES, PROFILE_MAP, writer);
            }
        } catch (Exception exception) {
            System.err.println("[ItemSize] Could not save config: " + exception.getMessage());
        }
    }

    public static synchronized ScaleProfile getProfile(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return PROFILES.computeIfAbsent(id, ignored -> new ScaleProfile());
    }

    public static synchronized float getScale(Item item, ItemDisplayContext context) {
        if (item == null) {
            return 1.0f;
        }

        ScaleProfile profile = getProfile(item);
        return switch (context) {
            case GROUND -> profile.groundScale;
            case GUI -> profile.inventoryScale;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                    THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> profile.handScale;
            default -> 1.0f;
        };
    }

    public static synchronized void setScale(Item item, ScaleType type, float value) {
        if (item == null) {
            return;
        }

        float safeValue = Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
        ScaleProfile profile = getProfile(item);
        switch (type) {
            case GROUND -> profile.groundScale = safeValue;
            case HAND -> profile.handScale = safeValue;
            case INVENTORY -> profile.inventoryScale = safeValue;
        }
    }

    public static synchronized void reset(Item item) {
        if (item == null) {
            return;
        }
        PROFILES.remove(BuiltInRegistries.ITEM.getKey(item).toString());
    }

    public enum ScaleType {
        GROUND,
        HAND,
        INVENTORY
    }

    public static final class ScaleProfile {
        public float groundScale = 1.0f;
        public float handScale = 1.0f;
        public float inventoryScale = 1.0f;

        private void sanitize() {
            groundScale = clamp(groundScale);
            handScale = clamp(handScale);
            inventoryScale = clamp(inventoryScale);
        }

        private static float clamp(float value) {
            if (!Float.isFinite(value)) {
                return 1.0f;
            }
            return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
        }
    }
}
