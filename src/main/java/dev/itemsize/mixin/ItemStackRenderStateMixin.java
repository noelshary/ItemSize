package dev.itemsize.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.itemsize.config.ItemSizeConfig;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.class)
public abstract class ItemStackRenderStateMixin implements ItemSizeRenderStateAccess {
    @Unique private Item itemsize$item;
    @Unique private ItemDisplayContext itemsize$context = ItemDisplayContext.NONE;
    @Unique private boolean itemsize$pushedPose;

    @Override
    public void itemsize$setItemAndContext(Item item, ItemDisplayContext context) {
        this.itemsize$item = item;
        this.itemsize$context = context;
    }

    @Override
    public Item itemsize$getItem() {
        return this.itemsize$item;
    }

    @Override
    public ItemDisplayContext itemsize$getContext() {
        return this.itemsize$context;
    }

    @Inject(method = "submit", at = @At("HEAD"))
    private void itemsize$applyScale(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            CallbackInfo ci
    ) {
        if (this.itemsize$item == null) {
            return;
        }

        float scale = ItemSizeConfig.getScale(this.itemsize$item, this.itemsize$context);
        if (Math.abs(scale - 1.0f) > 0.0001f) {
            poseStack.pushPose();
            poseStack.scale(scale, scale, scale);
            this.itemsize$pushedPose = true;
        }
    }

    @Inject(method = "submit", at = @At("RETURN"))
    private void itemsize$restorePose(
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            int overlayCoords,
            int outlineColor,
            CallbackInfo ci
    ) {
        if (this.itemsize$pushedPose) {
            poseStack.popPose();
            this.itemsize$pushedPose = false;
        }
    }
}
