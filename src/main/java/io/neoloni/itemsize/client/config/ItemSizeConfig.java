package io.neoloni.itemsize.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Persistent, per-item display scale settings. */
public final class ItemSizeConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type MAP_TYPE = new TypeToken<Map<String, ScaleProfile>>() {}.getType();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("itemsize.json");
    private static final Map<String, ScaleProfile> PROFILES = new LinkedHashMap<>();

    // ItemSize intentionally focuses on decorative, utility and building items.
    // Weapon- and ammunition-type items are excluded from the editor and renderer.
    private static final Set<String> EXCLUDED_PATH_PARTS = Set.of(
            "sword", "bow", "crossbow", "trident", "mace", "shield",
            "dagger", "knife", "spear", "lance", "halberd", "weapon",
            "gun", "pistol", "rifle", "firearm", "blade", "arrow", "bolt"
    );

    private ItemSizeConfig() {}

    public enum Dimension {
        GROUND("WORLD", "Ground"),
        HAND("HAND", "Hand"),
        INVENTORY("INVENTORY", "Inventory");

        public final String label;
        public final String description;

        Dimension(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    public static final class ScaleProfile {
        public float ground = 1.0f;
        public float hand = 1.0f;
        public float inventory = 1.0f;

        public float get(Dimension dimension) {
            return switch (dimension) {
                case GROUND -> ground;
                case HAND -> hand;
                case INVENTORY -> inventory;
            };
        }

        public void set(Dimension dimension, float value) {
            float safe = clamp(value);
            switch (dimension) {
                case GROUND -> ground = safe;
                case HAND -> hand = safe;
                case INVENTORY -> inventory = safe;
            }
        }
    }

    public static synchronized void load() {
        PROFILES.clear();
        if (!Files.exists(FILE)) return;
        try {
            Map<String, ScaleProfile> loaded = GSON.fromJson(Files.readString(FILE), MAP_TYPE);
            if (loaded != null) {
                loaded.forEach((id, profile) -> {
                    if (id != null && profile != null) {
                        profile.ground = clamp(profile.ground);
                        profile.hand = clamp(profile.hand);
                        profile.inventory = clamp(profile.inventory);
                        PROFILES.put(id, profile);
                    }
                });
            }
        } catch (Exception exception) {
            System.err.println("[ItemSize] Could not read config: " + exception.getMessage());
        }
    }

    public static synchronized void save() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(PROFILES));
        } catch (IOException exception) {
            System.err.println("[ItemSize] Could not save config: " + exception.getMessage());
        }
    }

    public static synchronized ScaleProfile profile(Item item) {
        String id = BuiltInRegistries.ITEM.getKey(item).toString();
        return PROFILES.computeIfAbsent(id, ignored -> new ScaleProfile());
    }

    public static synchronized float scaleFor(ItemStack stack, ItemDisplayContext context) {
        if (stack == null || stack.isEmpty() || !isAllowed(stack.getItem())) return 1.0f;
        ScaleProfile profile = profile(stack.getItem());
        return switch (context) {
            case GROUND -> profile.ground;
            case GUI -> profile.inventory;
            case FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND,
                 THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> profile.hand;
            default -> 1.0f;
        };
    }

    public static synchronized void set(Item item, Dimension dimension, float value) {
        if (!isAllowed(item)) return;
        profile(item).set(dimension, value);
    }

    public static boolean isAllowed(Item item) {
        if (item == null) return false;
        String path = BuiltInRegistries.ITEM.getKey(item).getPath().toLowerCase(Locale.ROOT);
        return EXCLUDED_PATH_PARTS.stream().noneMatch(path::contains);
    }

    public static synchronized void reset(Item item) {
        PROFILES.remove(BuiltInRegistries.ITEM.getKey(item).toString());
        save();
    }

    public static float clamp(float value) {
        if (!Float.isFinite(value)) return 1.0f;
        return Math.max(0.10f, Math.min(3.00f, value));
    }

    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }
}
