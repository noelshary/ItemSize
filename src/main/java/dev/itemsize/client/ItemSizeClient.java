package dev.itemsize.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.itemsize.config.ItemSizeConfig;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class ItemSizeClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath("itemsize", "main")
    );

    private static final KeyMapping OPEN_MENU = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.itemsize.open_menu",
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_I,
            CATEGORY
    ));

    @Override
    public void onInitializeClient() {
        ItemSizeConfig.load();

        ClientTickEvents.END_CLIENT_TICK.register((Minecraft client) -> {
            while (OPEN_MENU.consumeClick()) {
                if (client.gui.screen() == null) {
                    client.gui.setScreen(new ItemSizeScreen());
                } else if (client.gui.screen() instanceof ItemSizeScreen) {
                    client.gui.setScreen(null);
                }
            }
        });
    }
}
