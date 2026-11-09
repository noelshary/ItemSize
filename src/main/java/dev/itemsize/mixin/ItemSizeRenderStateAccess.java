package dev.itemsize.mixin;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;

public interface ItemSizeRenderStateAccess {
    void itemsize$setItemAndContext(Item item, ItemDisplayContext context);
    Item itemsize$getItem();
    ItemDisplayContext itemsize$getContext();
}
