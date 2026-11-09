package dev.itemsize.mixin;

import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "appendItemLayers", at = @At("TAIL"))
    private void itemsize$captureItem(
            ItemStackRenderState output,
            ItemStack stack,
            ItemDisplayContext context,
            Level level,
            ItemOwner owner,
            int seed,
            CallbackInfo ci
    ) {
        ((ItemSizeRenderStateAccess) output).itemsize$setItemAndContext(stack.getItem(), context);
    }
}
