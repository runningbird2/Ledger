package com.github.quiltservertools.ledger.mixin;

import com.github.quiltservertools.ledger.utility.EntityPlacementTracker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MobBucketItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(MobBucketItem.class)
public abstract class MobBucketItemMixin {
    @WrapOperation(
            method = "checkExtraContent",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/MobBucketItem;spawn(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;)V"
            )
    )
    private void ledgerTrackMobBucketPlacement(
            MobBucketItem instance,
            ServerLevel world,
            ItemStack stack,
            BlockPos pos,
            Operation<Void> original,
            LivingEntity user,
            Level level,
            ItemStack usedStack,
            BlockPos usedPos
    ) {
        EntityPlacementTracker.begin(user instanceof Player player ? player : null);
        try {
            original.call(instance, world, stack, pos);
        } finally {
            EntityPlacementTracker.end();
        }
    }
}
