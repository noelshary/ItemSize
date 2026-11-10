package io.neoloni.itemsize.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.neoloni.itemsize.api.ItemSizeTrackedState;
import io.neoloni.itemsize.client.config.ItemSizeConfig;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies the per-item display scale around the submission of an item render state. */
@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements ItemSizeTrackedState {
    @Unique
    private ItemStack itemsize$sourceStack = ItemStack.EMPTY;
    @Unique
    private ItemDisplayContext itemsize$displayContext = ItemDisplayContext.NONE;
    @Unique
    private boolean itemsize$pushedMatrix;

    @Override
    public void itemsize$setSource(ItemStack stack, ItemDisplayContext context) {
        itemsize$sourceStack = stack == null ? ItemStack.EMPTY : stack;
        itemsize$displayContext = context == null ? ItemDisplayContext.NONE : context;
    }

    @Override
    public ItemStack itemsize$getSourceStack() {
        return itemsize$sourceStack;
    }

    @Override
    public ItemDisplayContext itemsize$getDisplayContext() {
        return itemsize$displayContext;
    }

    @Inject(method = "clear", at = @At("TAIL"))
    private void itemsize$clearStack(CallbackInfo ci) {
        itemsize$sourceStack = ItemStack.EMPTY;
        itemsize$displayContext = ItemDisplayContext.NONE;
        itemsize$pushedMatrix = false;
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void itemsize$applyScale(PoseStack poseStack, SubmitNodeCollector collector,
                                     int light, int overlay, int seed, CallbackInfo ci) {
        if (itemsize$sourceStack.isEmpty()) return;
        float scale = ItemSizeConfig.scaleFor(itemsize$sourceStack, itemsize$displayContext);
        if (Math.abs(scale - 1.0f) > 0.0001f) {
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
            itemsize$pushedMatrix = true;
        }
    }

    @Inject(method = "submit", at = @At("TAIL"))
    private void itemsize$restoreScale(PoseStack poseStack, SubmitNodeCollector collector,
                                       int light, int overlay, int seed, CallbackInfo ci) {
        if (itemsize$pushedMatrix) {
            poseStack.popPose();
            itemsize$pushedMatrix = false;
        }
    }
}
