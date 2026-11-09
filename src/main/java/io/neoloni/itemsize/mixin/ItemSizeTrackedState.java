package io.neoloni.itemsize.mixin;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
public interface ItemSizeTrackedState {
 void itemsize$setSource(ItemStack stack,ItemDisplayContext context);
 ItemStack itemsize$getSourceStack();
 ItemDisplayContext itemsize$getDisplayContext();
}