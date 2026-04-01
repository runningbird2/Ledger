package com.github.quiltservertools.ledger.commands.subcommands

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.commands.BuildableCommand
import com.github.quiltservertools.ledger.commands.CommandConsts
import com.github.quiltservertools.ledger.commands.arguments.SearchParamArgument
import com.github.quiltservertools.ledger.database.DatabaseManager
import com.github.quiltservertools.ledger.utility.Context
import com.github.quiltservertools.ledger.utility.LiteralNode
import com.github.quiltservertools.ledger.utility.MessageUtils
import com.github.quiltservertools.ledger.utility.RestrictedLedgerAccess
import com.github.quiltservertools.ledger.utility.TextColorPallet
import com.mojang.brigadier.arguments.IntegerArgumentType
import kotlinx.coroutines.launch
import me.lucko.fabric.api.permissions.v0.Permissions
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component

object RestrictedSearchCommand : BuildableCommand {
    override fun build(): LiteralNode =
        Commands.literal("search")
            .requires {
                it.entity is net.minecraft.server.level.ServerPlayer &&
                        Permissions.check(
                            it.playerOrException,
                            RestrictedLedgerAccess.SEARCH_PERMISSION,
                            CommandConsts.PERMISSION_LEVEL
                        )
            }
            .then(
                Commands.literal("page")
                    .then(
                        Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes {
                                page(
                                    it,
                                    IntegerArgumentType.getInteger(it, "page")
                                )
                            }
                    )
            )
            .then(
                SearchParamArgument.argument(
                    CommandConsts.PARAMS,
                    RestrictedLedgerAccess.DISALLOWED_PARAMS,
                    RestrictedLedgerAccess.ALLOWED_ACTIONS,
                    RestrictedLedgerAccess.DISALLOWED_SOURCE_SUGGESTIONS
                )
                    .executes {
                        val restrictedParams =
                            RestrictedLedgerAccess.restrictSearchParams(
                                it.source,
                                SearchParamArgument.get(
                                    it,
                                    CommandConsts.PARAMS,
                                    RestrictedLedgerAccess.DISALLOWED_PARAMS
                                )
                            )
                        SearchCommand.search(
                            it,
                            restrictedParams,
                            { page -> "/search page $page" },
                            RestrictedLedgerAccess::sanitizeRestrictedActions
                        )
                    }
            )
            .build()

    private fun page(context: Context, page: Int): Int {
        val source = context.source
        val params = Ledger.searchCache[source.textName]

        if (params == null) {
            source.sendFailure(Component.translatable("error.ledger.no_cached_params"))
            return -1
        }

        val restrictedParams = RestrictedLedgerAccess.restrictSearchParams(source, params)
        Ledger.searchCache[source.textName] = restrictedParams

        Ledger.launch {
            MessageUtils.warnBusy(source)
            val results = DatabaseManager.searchActions(restrictedParams, page)
            val transformedResults = results.copy(
                actions = RestrictedLedgerAccess.sanitizeRestrictedActions(results.actions)
            )
            if (transformedResults.actions.isEmpty() || transformedResults.page > transformedResults.pages) {
                source.sendFailure(Component.translatable("error.ledger.no_more_pages"))
                return@launch
            }

            MessageUtils.sendSearchResults(
                source,
                transformedResults,
                Component.translatable("text.ledger.header.search").setStyle(TextColorPallet.primary),
                pageCommandFactory = { nextPage -> "/search page $nextPage" },
                actionTransformer = RestrictedLedgerAccess::sanitizeRestrictedActions
            )
        }

        return 1
    }
}
