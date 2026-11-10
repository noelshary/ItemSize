package io.neoloni.itemsize.mixin;

import io.neoloni.itemsize.api.ItemSizeTrackedState;
import io.neoloni.itemsize.client.config.ItemSizeConfig;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Remembers which item stack and display context an {@link ItemStackRenderState}
 * was built for, so the scale can be applied when it is submitted for rendering.
 */
@Mixin(ItemModelResolver.class)
public abstract class ItemModelResolverMixin {
    @Inject(method = "updateForLiving", at = @At("TAIL"))
    private void itemsize$rememberLiving(ItemStackRenderState state, ItemStack stack,
                                         ItemDisplayContext context, LivingEntity entity, CallbackInfo ci) {
        ((ItemSizeTrackedState) state).itemsize$setSource(ItemSizeConfig.bypass ? ItemStack.EMPTY : stack, context);
    }

    @Inject(method = "updateForNonLiving", at = @At("TAIL"))
    private void itemsize$rememberEntity(ItemStackRenderState state, ItemStack stack,
                                         ItemDisplayContext context, Entity entity, CallbackInfo ci) {
        ((ItemSizeTrackedState) state).itemsize$setSource(ItemSizeConfig.bypass ? ItemStack.EMPTY : stack, context);
    }

    @Inject(method = "updateForTopItem", at = @At("TAIL"))
    private void itemsize$rememberTopItem(ItemStackRenderState state, ItemStack stack,
                                          ItemDisplayContext context, Level level, ItemOwner owner,
                                          int seed, CallbackInfo ci) {
        ((ItemSizeTrackedState) state).itemsize$setSource(ItemSizeConfig.bypass ? ItemStack.EMPTY : stack, context);
    }
}
