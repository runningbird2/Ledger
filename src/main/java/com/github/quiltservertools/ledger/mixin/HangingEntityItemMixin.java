package com.github.quiltservertools.ledger.mixin;

import com.github.quiltservertools.ledger.utility.EntityPlacementTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.HangingEntityItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(HangingEntityItem.class)
public abstract class HangingEntityItemMixin {
    @WrapOperation(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean ledgerTrackHangingEntityPlacement(
            Level world,
            Entity entity,
            Operation<Boolean> original,
            UseOnContext context
    ) {
        EntityPlacementTracker.begin(context.getPlayer());
        try {
            return original.call(world, entity);
        } finally {
            EntityPlacementTracker.end();
        }
    }
}
