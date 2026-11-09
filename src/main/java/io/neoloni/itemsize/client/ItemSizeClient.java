package io.neoloni.itemsize.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.neoloni.itemsize.client.config.ItemSizeConfig;
import io.neoloni.itemsize.client.screen.ItemSizeScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class ItemSizeClient implements ClientModInitializer {
    private static KeyMapping openMenuKey;

    @Override
    public void onInitializeClient() {
        ItemSizeConfig.load();

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("itemsize", "controls")
        );
        openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.itemsize.open",
                InputConstants.Type.KEYSYM,
                InputConstants.KEY_O,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                if (client.player != null && client.screen == null) {
                    client.setScreen(new ItemSizeScreen());
                }
            }
        });
    }
}
