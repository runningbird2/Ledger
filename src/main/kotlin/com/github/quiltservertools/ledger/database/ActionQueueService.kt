package com.github.quiltservertools.ledger.database

import com.github.quiltservertools.ledger.Ledger
import com.github.quiltservertools.ledger.actions.ActionType
import com.github.quiltservertools.ledger.config.DatabaseSpec
import com.github.quiltservertools.ledger.logWarn
import com.github.quiltservertools.ledger.utility.ticks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.LinkedBlockingDeque
import kotlin.time.Duration.Companion.seconds

object ActionQueueService {
    private const val RETRY_DELAY_SECONDS = 5L

    private val queue = LinkedBlockingDeque<ActionType>()
    private lateinit var job: Job

    val size: Int get() = queue.size

    fun start() {
        job = Ledger.launch {
            prepareNextBatch()
        }
    }

    fun addToQueue(action: ActionType): Boolean {
        if (action.isBlacklisted()) return false

        return queue.offerLast(action)
    }

    suspend fun drainAll() {
        job.cancel()
        while (queue.isNotEmpty()) {
            drainBatch()
        }
    }

    private suspend fun drainBatch() {
        val batch = mutableListOf<ActionType>()
        queue.drainTo(batch, Ledger.config[DatabaseSpec.batchSize])

        if (batch.isEmpty()) {
            return
        }

        try {
            DatabaseManager.logActionBatch(batch)
        } catch (throwable: Throwable) {
            if (throwable is CancellationException || throwable is Error) {
                throw throwable
            }

            requeueFailedBatch(batch)
            logWarn(
                "Failed to persist ${batch.size} Ledger actions; requeued the batch and will retry in ${RETRY_DELAY_SECONDS}s.",
                throwable
            )
            delay(RETRY_DELAY_SECONDS.seconds)
        }
    }

    private fun requeueFailedBatch(batch: List<ActionType>) {
        for (index in batch.indices.reversed()) {
            queue.addFirst(batch[index])
        }
    }

    private suspend fun prepareNextBatch() {
        job = Ledger.launch {
            if (queue.size < Ledger.config[DatabaseSpec.batchSize]) {
                delay(Ledger.config[DatabaseSpec.batchDelay].ticks)
            }
            if (queue.isNotEmpty()) drainBatch()
            prepareNextBatch()
        }
    }
}
