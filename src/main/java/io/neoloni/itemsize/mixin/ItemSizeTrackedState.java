package io.neoloni.itemsize.mixin;

import net.minecraft.world.item.ItemStack;

/** Internal bridge used to remember which stack a render-state belongs to. */
public interface ItemSizeTrackedState {
    void itemsize$setSourceStack(ItemStack stack);
    ItemStack itemsize$getSourceStack();
}
