package com.github.quiltservertools.ledger.commands.subcommands

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.actions.LoggedItemProvider
import com.github.quiltservertools.ledger.commands.BuildableCommand
import com.github.quiltservertools.ledger.commands.CommandConsts
import com.github.quiltservertools.ledger.database.DatabaseManager
import com.github.quiltservertools.ledger.utility.addItem
import com.github.quiltservertools.ledger.utility.Context
import com.github.quiltservertools.ledger.utility.LiteralNode
import com.github.quiltservertools.ledger.utility.TextColorPallet
import com.github.quiltservertools.ledger.utility.hasPlayer
import com.github.quiltservertools.ledger.utility.literal
import com.github.quiltservertools.ledger.utility.launchMain
import com.mojang.brigadier.arguments.IntegerArgumentType
import kotlinx.coroutines.launch
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.Commands.argument
import net.minecraft.commands.Commands.literal
import net.minecraft.network.chat.Component

object ItemCommand : BuildableCommand {
    override fun build(): LiteralNode {
        return literal("item")
            .requires(Permissions.require("ledger.commands.item", CommandConsts.PERMISSION_LEVEL))
            .then(
                argument("id", IntegerArgumentType.integer(1))
                    .executes { give(it, IntegerArgumentType.getInteger(it, "id")) }
            )
            .build()
    }

    private fun give(context: Context, actionId: Int): Int {
        val source = context.source
        if (!source.hasPlayer()) {
            source.sendFailure(
                "Only players can receive logged items.".literal().setStyle(TextColorPallet.primary)
            )
            return 0
        }

        Ledger.launch {
            val action = DatabaseManager.getAction(actionId)

            source.level.launchMain {
                val player = source.playerOrException
                if (action == null) {
                    source.sendFailure(
                        "No Ledger action exists with id $actionId.".literal().setStyle(TextColorPallet.primary)
                    )
                    return@launchMain
                }

                if (action !is LoggedItemProvider) {
                    source.sendFailure(
                        "Ledger action $actionId does not contain a retrievable item.".literal()
                            .setStyle(TextColorPallet.primary)
                    )
                    return@launchMain
                }

                val stack = action.getLoggedItem(source.server)
                if (stack.isEmpty) {
                    source.sendFailure(
                        "Ledger action $actionId does not contain a retrievable item.".literal()
                            .setStyle(TextColorPallet.primary)
                    )
                    return@launchMain
                }

                if (!addItem(stack.copy(), player.inventory)) {
                    source.sendFailure(
                        "You do not have enough inventory space for that item.".literal()
                            .setStyle(TextColorPallet.primary)
                    )
                    return@launchMain
                }

                player.inventory.setChanged()
                player.containerMenu.broadcastChanges()

                source.sendSuccess(
                    {
                        Component.literal("Copied ").append(stack.itemName)
                            .append(" from Ledger action $actionId")
                            .setStyle(TextColorPallet.primary)
                    },
                    false
                )
            }
        }

        return 1
    }
}
