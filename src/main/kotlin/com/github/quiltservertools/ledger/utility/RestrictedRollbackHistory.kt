package com.github.quiltservertools.ledger.utility

import com.github.quiltservertools.ledger.actions.ActionType
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object RestrictedRollbackHistory {
    private const val MAX_HISTORY = 5
    private val history = ConcurrentHashMap<UUID, MutableList<RollbackBatch>>()

    fun record(playerId: UUID, actionIds: Set<Int>, actions: List<ActionType>) {
        if (actionIds.isEmpty() || actions.isEmpty()) {
            return
        }

        history.compute(playerId) { _, batches ->
            val updatedBatches = batches ?: mutableListOf()
            updatedBatches.add(RollbackBatch(actionIds.toSet(), actions.sortedBy { it.id }))
            while (updatedBatches.size > MAX_HISTORY) {
                updatedBatches.removeAt(0)
            }
            updatedBatches
        }
    }

    fun pop(playerId: UUID): RollbackBatch? {
        var rollbackBatch: RollbackBatch? = null
        history.compute(playerId) { _, batches ->
            if (batches.isNullOrEmpty()) {
                return@compute null
            }

            rollbackBatch = batches.removeAt(batches.lastIndex)
            if (batches.isEmpty()) {
                null
            } else {
                batches
            }
        }

        return rollbackBatch
    }

    data class RollbackBatch(
        val actionIds: Set<Int>,
        val actions: List<ActionType>
    )
}
