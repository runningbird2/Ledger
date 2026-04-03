package com.github.quiltservertools.ledger.mixin;

import com.github.quiltservertools.ledger.utility.EntityPlacementTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.MinecartItem;
import net.minecraft.world.item.context.UseOnContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MinecartItem.class)
public abstract class MinecartItemMixin {
    @WrapOperation(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerLevel;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean ledgerTrackMinecartPlacement(
            ServerLevel world,
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
