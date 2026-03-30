package com.github.quiltservertools.ledger.commands.subcommands

import com.github.quiltservertools.ledger.actionutils.Preview
import com.github.quiltservertools.ledger.commands.BuildableCommand
import com.github.quiltservertools.ledger.commands.CommandConsts
import com.github.quiltservertools.ledger.commands.arguments.SearchParamArgument
import com.github.quiltservertools.ledger.utility.LiteralNode
import com.github.quiltservertools.ledger.utility.RestrictedLedgerAccess
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.Commands

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
                        SearchParamArgument.argument(CommandConsts.PARAMS)
                            .executes {
                                val restrictedParams =
                                    RestrictedLedgerAccess.restrictRollbackParams(
                                        it.source,
                                        SearchParamArgument.get(it, CommandConsts.PARAMS)
                                    )
                                PreviewCommand.preview(
                                    it,
                                    restrictedParams,
                                    Preview.Type.ROLLBACK,
                                    RestrictedLedgerAccess::sanitizeRestrictedActions
                                )
                            }
                    )
            )
            .then(
                SearchParamArgument.argument(CommandConsts.PARAMS)
                    .executes {
                        val restrictedParams =
                            RestrictedLedgerAccess.restrictRollbackParams(
                                it.source,
                                SearchParamArgument.get(it, CommandConsts.PARAMS)
                            )
                        RollbackCommand.rollback(
                            it,
                            restrictedParams,
                            RestrictedLedgerAccess::sanitizeRestrictedActions
                        )
                    }
            )
            .build()
}
