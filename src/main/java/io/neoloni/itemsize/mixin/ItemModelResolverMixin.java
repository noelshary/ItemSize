package io.neoloni.itemsize.mixin;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
 @Inject(method="updateForLiving",at=@At("TAIL"))
 private void itemsize$rememberLiving(ItemStackRenderState state,ItemStack stack,ItemDisplayContext context,LivingEntity entity,CallbackInfo ci){((ItemSizeTrackedState)state).itemsize$setSource(stack,context);}
 @Inject(method="updateForNonLiving",at=@At("TAIL"))
 private void itemsize$rememberEntity(ItemStackRenderState state,ItemStack stack,ItemDisplayContext context,Entity entity,CallbackInfo ci){((ItemSizeTrackedState)state).itemsize$setSource(stack,context);}
 @Inject(method="updateForTopItem",at=@At("TAIL"))
 private void itemsize$rememberTopItem(ItemStackRenderState state,ItemStack stack,ItemDisplayContext context,Level level,ItemOwner owner,int seed,CallbackInfo ci){((ItemSizeTrackedState)state).itemsize$setSource(stack,context);}
}