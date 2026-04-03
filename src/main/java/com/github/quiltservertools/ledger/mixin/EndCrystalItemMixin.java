package com.github.quiltservertools.ledger.mixin;

import com.github.quiltservertools.ledger.utility.EntityPlacementTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.EndCrystalItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EndCrystalItem.class)
public abstract class EndCrystalItemMixin {
    @WrapOperation(
            method = "useOn",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"
            )
    )
    private boolean ledgerTrackEndCrystalPlacement(
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
