package com.github.quiltservertools.ledger.commands.subcommands

import com.github.quiltservertools.ledger.commands.BuildableCommand
import com.github.quiltservertools.ledger.commands.CommandConsts
import com.github.quiltservertools.ledger.utility.LiteralNode
import com.github.quiltservertools.ledger.utility.ModSpawnManager
import com.github.quiltservertools.ledger.utility.RestrictedLedgerAccess
import com.github.quiltservertools.ledger.utility.TextColorPallet
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.Commands
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent

object ModSpawnCommand : BuildableCommand {
    override fun build(): LiteralNode =
        Commands.literal("modspawn")
            .requires {
                it.entity is net.minecraft.server.level.ServerPlayer &&
                        Permissions.check(
                            it.playerOrException,
                            RestrictedLedgerAccess.MODSPAWN_PERMISSION,
                            CommandConsts.PERMISSION_LEVEL
                        )
            }
            .executes {
                val source = it.source
                val player = source.playerOrException
                when (ModSpawnManager.start(player)) {
                    ModSpawnManager.StartResult.STARTED -> {
                        source.sendSuccess(
                            {
                                Component.translatable("text.ledger.modspawn.start").setStyle(TextColorPallet.primary)
                            },
                            false
                        )
                        source.sendSystemMessage(
                            Component.translatable("text.ledger.modspawn.return")
                                .setStyle(TextColorPallet.primary)
                                .withStyle { style ->
                                    style.withHoverEvent(
                                        HoverEvent.ShowText(Component.translatable("text.ledger.modspawn.return.hover"))
                                    ).withClickEvent(
                                        ClickEvent.RunCommand("/modspawn stop")
                                    )
                                }
                        )
                        1
                    }

                    ModSpawnManager.StartResult.ALREADY_ACTIVE -> {
                        source.sendFailure(Component.translatable("error.ledger.modspawn.active"))
                        -1
                    }

                    ModSpawnManager.StartResult.FAILED -> {
                        source.sendFailure(Component.translatable("error.ledger.modspawn.failed"))
                        -1
                    }
                }
            }
            .then(
                Commands.literal("stop")
                    .executes {
                        val source = it.source
                        when (ModSpawnManager.stop(source.playerOrException)) {
                            ModSpawnManager.StopResult.STOPPED -> {
                                source.sendSuccess(
                                    {
                                        Component.translatable("text.ledger.modspawn.stop")
                                            .setStyle(TextColorPallet.primary)
                                    },
                                    false
                                )
                                1
                            }

                            ModSpawnManager.StopResult.NOT_ACTIVE -> {
                                source.sendFailure(Component.translatable("error.ledger.modspawn.inactive"))
                                -1
                            }
                        }
                    }
            )
            .build()
}
