package com.github.quiltservertools.ledger.utility

import com.github.quiltservertools.ledger.actionutils.ActionSearchParams
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import net.minecraft.commands.CommandSourceStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.levelgen.structure.BoundingBox
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object RestrictedLedgerAccess {
    const val SEARCH_PERMISSION = "ledger.commands.moderator.search"
    const val ROLLBACK_PERMISSION = "ledger.commands.moderator.rollback"
    const val MODSPAWN_PERMISSION = "ledger.commands.moderator.modspawn"

    private const val ORIGIN_LIMIT = 1000
    private val ORIGIN_SEARCH_BOUNDS =
        BoundingBox(-ORIGIN_LIMIT, -Int.MAX_VALUE, -ORIGIN_LIMIT, ORIGIN_LIMIT, Int.MAX_VALUE, ORIGIN_LIMIT)
    private val ALLOWED_ACTIONS = setOf("block-break", "block-place")

    fun restrictSearchParams(source: CommandSourceStack, params: ActionSearchParams): ActionSearchParams {
        requirePlayerWithinOrigin(source)
        return copyWithRestrictions(
            params,
            restrictedBounds = when (params.bounds) {
                null, ActionSearchParams.GLOBAL -> ORIGIN_SEARCH_BOUNDS
                else -> clampToOrigin(params.bounds)
            }
        )
    }

    fun restrictRollbackParams(source: CommandSourceStack, params: ActionSearchParams): ActionSearchParams {
        requirePlayerWithinOrigin(source)

        if (params.bounds == ActionSearchParams.GLOBAL) {
            throw SimpleCommandExceptionType(Component.translatable("error.ledger.restricted.range_global")).create()
        }

        return copyWithRestrictions(
            params,
            restrictedBounds = params.bounds?.let(::clampToOrigin)
        )
    }

    fun requirePlayerWithinOrigin(source: CommandSourceStack): ServerPlayer {
        val player = source.playerOrException
        if (abs(player.blockX) > ORIGIN_LIMIT || abs(player.blockZ) > ORIGIN_LIMIT) {
            throw SimpleCommandExceptionType(Component.translatable("error.ledger.restricted.origin")).create()
        }
        return player
    }

    private fun copyWithRestrictions(
        params: ActionSearchParams,
        restrictedBounds: BoundingBox?
    ): ActionSearchParams = params.copy(
        bounds = restrictedBounds,
        actions = restrictActions(params.actions),
        objects = params.objects?.toMutableSet(),
        sourceNames = params.sourceNames?.toMutableSet(),
        sourcePlayerIds = params.sourcePlayerIds?.toMutableSet(),
        worlds = params.worlds?.toMutableSet()
    )

    private fun clampToOrigin(bounds: BoundingBox): BoundingBox {
        val minX = max(bounds.minX(), -ORIGIN_LIMIT)
        val maxX = min(bounds.maxX(), ORIGIN_LIMIT)
        val minZ = max(bounds.minZ(), -ORIGIN_LIMIT)
        val maxZ = min(bounds.maxZ(), ORIGIN_LIMIT)

        if (minX > maxX || minZ > maxZ) {
            throw SimpleCommandExceptionType(Component.translatable("error.ledger.restricted.origin")).create()
        }

        return BoundingBox(minX, bounds.minY(), minZ, maxX, bounds.maxY(), maxZ)
    }

    private fun restrictActions(actions: MutableSet<Negatable<String>>?): MutableSet<Negatable<String>> {
        val positiveRequested = actions.orEmpty().filter { it.allowed }
        val deniedAllowedActions = actions.orEmpty()
            .filterNot { it.allowed }
            .map { it.property }
            .filter(ALLOWED_ACTIONS::contains)
            .toSet()

        if (positiveRequested.any { it.property !in ALLOWED_ACTIONS }) {
            throw SimpleCommandExceptionType(Component.translatable("error.ledger.restricted.action")).create()
        }

        val allowedSubset = if (positiveRequested.isEmpty()) {
            ALLOWED_ACTIONS.toMutableSet()
        } else {
            positiveRequested.mapTo(mutableSetOf()) { it.property }
        }

        allowedSubset.removeAll(deniedAllowedActions)

        return allowedSubset.mapTo(mutableSetOf()) { Negatable.allow(it) }
    }
}
