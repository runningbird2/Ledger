package com.github.quiltservertools.ledger.commands.subcommands

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.actions.ActionType
import com.github.quiltservertools.ledger.actionutils.ActionSearchParams
import com.github.quiltservertools.ledger.actionutils.Preview
import com.github.quiltservertools.ledger.commands.BuildableCommand
import com.github.quiltservertools.ledger.commands.CommandConsts
import com.github.quiltservertools.ledger.commands.arguments.SearchParamArgument
import com.github.quiltservertools.ledger.database.DatabaseManager
import com.github.quiltservertools.ledger.utility.Context
import com.github.quiltservertools.ledger.utility.LiteralNode
import com.github.quiltservertools.ledger.utility.MessageUtils
import kotlinx.coroutines.launch
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object PreviewCommand : BuildableCommand {
    override fun build(): LiteralNode {
        return Commands.literal("preview")
            .requires(Permissions.require("ledger.commands.preview", CommandConsts.PERMISSION_LEVEL))
            .then(
                Commands.literal("rollback")
                .then(
                    SearchParamArgument.argument(CommandConsts.PARAMS)
                        .executes {
                            preview(
                                it,
                                SearchParamArgument.get(it, CommandConsts.PARAMS),
                                Preview.Type.ROLLBACK
                            )
                        }
                )
            )
            .then(
                Commands.literal("restore")
                .then(
                    SearchParamArgument.argument(CommandConsts.PARAMS)
                        .executes {
                            preview(
                                it,
                                SearchParamArgument.get(it, CommandConsts.PARAMS),
                                Preview.Type.RESTORE
                            )
                        }
                )
            )
            .then(Commands.literal("apply").executes { apply(it) })
            .then(Commands.literal("cancel").executes { cancel(it) })
            .build()
    }

    fun preview(
        context: Context,
        params: ActionSearchParams,
        type: Preview.Type,
        actionTransformer: (List<ActionType>) -> List<ActionType> = { it },
        applyHandler: (Context, ActionSearchParams) -> Int = defaultApplyHandler(type)
    ): Int {
        val source = context.source
        val player = source.playerOrException
        params.ensureSpecific()
        Ledger.launch {
            MessageUtils.warnBusy(source)
            val actions = actionTransformer(DatabaseManager.previewActions(params, type))

            if (actions.isEmpty()) {
                source.sendFailure(Component.translatable("error.ledger.command.no_results"))
                return@launch
            }

            Ledger.previewCache[player.uuid]?.cancel(player)
            Ledger.previewCache[player.uuid] = Preview(params, actions, player, type) {
                applyHandler(it, params)
            }
        }
        return 1
    }

    fun apply(context: Context, requiredType: Preview.Type? = null): Int {
        val uuid = context.source.playerOrException.uuid
        val preview = Ledger.previewCache[uuid]

        if (preview == null || (requiredType != null && !preview.isType(requiredType))) {
            context.source.sendFailure(Component.translatable("error.ledger.no_preview"))
            return -1
        }

        preview.apply(context)
        Ledger.previewCache.remove(uuid)
        return 1
    }

    fun cancel(context: Context, requiredType: Preview.Type? = null): Int {
        val uuid = context.source.playerOrException.uuid
        val preview = Ledger.previewCache[uuid]

        if (preview == null || (requiredType != null && !preview.isType(requiredType))) {
            context.source.sendFailure(Component.translatable("error.ledger.no_preview"))
            return -1
        }

        preview.cancel(context.source.playerOrException)
        Ledger.previewCache.remove(uuid)
        return 1
    }

    private fun defaultApplyHandler(type: Preview.Type): (Context, ActionSearchParams) -> Int =
        when (type) {
            Preview.Type.ROLLBACK -> { previewContext, previewParams ->
                RollbackCommand.rollback(previewContext, previewParams)
            }

            Preview.Type.RESTORE -> { previewContext, previewParams ->
                RestoreCommand.restore(previewContext, previewParams)
            }
        }
}
