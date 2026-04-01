package com.github.quiltservertools.ledger.commands.subcommands

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.actionutils.ActionSearchParams
import com.github.quiltservertools.ledger.actionutils.Preview
import com.github.quiltservertools.ledger.commands.BuildableCommand
import com.github.quiltservertools.ledger.commands.CommandConsts
import com.github.quiltservertools.ledger.commands.arguments.SearchParamArgument
import com.github.quiltservertools.ledger.database.DatabaseManager
import com.github.quiltservertools.ledger.utility.Context
import com.github.quiltservertools.ledger.utility.LiteralNode
import com.github.quiltservertools.ledger.utility.MessageUtils
import com.github.quiltservertools.ledger.utility.RestrictedLedgerAccess
import com.github.quiltservertools.ledger.utility.RestrictedRollbackHistory
import com.github.quiltservertools.ledger.utility.TextColorPallet
import com.github.quiltservertools.ledger.utility.launchMain
import com.github.quiltservertools.ledger.utility.literal
import kotlinx.coroutines.launch
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object RestrictedRollbackCommand : BuildableCommand {
    override fun build(): LiteralNode =
        Commands.literal("rollback")
            .requires {
                it.entity is net.minecraft.server.level.ServerPlayer &&
                        Permissions.check(
                            it.playerOrException,
                            RestrictedLedgerAccess.ROLLBACK_PERMISSION,
                            CommandConsts.PERMISSION_LEVEL
                        )
            }
            .then(
                Commands.literal("preview")
                    .then(
                        Commands.literal("apply")
                            .executes { PreviewCommand.apply(it, Preview.Type.ROLLBACK) }
                    )
                    .then(
                        Commands.literal("cancel")
                            .executes { PreviewCommand.cancel(it, Preview.Type.ROLLBACK) }
                    )
                    .then(
                        SearchParamArgument.argument(
                            CommandConsts.PARAMS,
                            RestrictedLedgerAccess.DISALLOWED_PARAMS,
                            RestrictedLedgerAccess.ALLOWED_ACTIONS
                        )
                            .executes {
                                previewRollback(
                                    it,
                                    RestrictedLedgerAccess.restrictRollbackParams(
                                        it.source,
                                        SearchParamArgument.get(
                                            it,
                                            CommandConsts.PARAMS,
                                            RestrictedLedgerAccess.DISALLOWED_PARAMS
                                        )
                                    )
                                )
                            }
                    )
            )
            .then(
                Commands.literal("undo")
                    .executes { undoRollback(it) }
            )
            .then(
                SearchParamArgument.argument(
                    CommandConsts.PARAMS,
                    RestrictedLedgerAccess.DISALLOWED_PARAMS,
                    RestrictedLedgerAccess.ALLOWED_ACTIONS
                )
                    .executes {
                        rollbackRestricted(
                            it,
                            RestrictedLedgerAccess.restrictRollbackParams(
                                it.source,
                                SearchParamArgument.get(
                                    it,
                                    CommandConsts.PARAMS,
                                    RestrictedLedgerAccess.DISALLOWED_PARAMS
                                )
                            )
                        )
                    }
            )
            .build()

    private fun previewRollback(context: Context, params: ActionSearchParams): Int =
        PreviewCommand.preview(
            context,
            params,
            Preview.Type.ROLLBACK,
            RestrictedLedgerAccess::sanitizeRestrictedActions
        ) { previewContext, previewParams ->
            rollbackRestricted(previewContext, previewParams)
        }

    private fun rollbackRestricted(context: Context, params: ActionSearchParams): Int =
        RollbackCommand.rollback(
            context,
            params,
            RestrictedLedgerAccess::sanitizeRestrictedActions
        ) { successContext, actionIds, actions ->
            RestrictedRollbackHistory.record(successContext.source.playerOrException.uuid, actionIds, actions)
        }

    private fun undoRollback(context: Context): Int {
        val source = context.source
        val player = source.playerOrException
        val rollbackBatch = RestrictedRollbackHistory.pop(player.uuid)

        if (rollbackBatch == null) {
            source.sendFailure(Component.translatable("error.ledger.rollback.undo.none"))
            return -1
        }

        Ledger.launch {
            MessageUtils.warnBusy(source)
            source.sendSuccess(
                {
                    Component.translatable(
                        "text.ledger.rollback.undo.start",
                        rollbackBatch.actions.size.toString().literal().setStyle(TextColorPallet.secondary)
                    ).setStyle(TextColorPallet.primary)
                },
                true
            )

            context.source.level.launchMain {
                val fails = HashMap<String, Int>()
                val restoredIds = HashSet<Int>()
                for (action in rollbackBatch.actions) {
                    if (!action.restore(context.source.server)) {
                        fails[action.identifier] = fails.getOrPut(action.identifier) { 0 } + 1
                    } else {
                        restoredIds.add(action.id)
                    }
                }

                if (restoredIds.isNotEmpty()) {
                    Ledger.launch {
                        DatabaseManager.restoreActions(restoredIds)
                    }.join()
                }

                val remainingIds = rollbackBatch.actionIds - restoredIds
                if (remainingIds.isNotEmpty()) {
                    RestrictedRollbackHistory.record(
                        player.uuid,
                        remainingIds,
                        rollbackBatch.actions.filter { it.id in remainingIds }
                    )
                }

                for (entry in fails.entries) {
                    source.sendSuccess(
                        {
                            Component.translatable("text.ledger.restore.fail", entry.key, entry.value).setStyle(
                                TextColorPallet.secondary
                            )
                        },
                        true
                    )
                }

                source.sendSuccess(
                    {
                        Component.translatable(
                            "text.ledger.rollback.undo.finish",
                            restoredIds.size.toString().literal().setStyle(TextColorPallet.secondary)
                        ).setStyle(TextColorPallet.primary)
                    },
                    true
                )
            }
        }

        return 1
    }
}
