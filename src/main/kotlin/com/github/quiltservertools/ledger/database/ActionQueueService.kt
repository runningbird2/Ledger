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
            drainBatch(requeueFailures = false)
        }
    }

    private suspend fun drainBatch(requeueFailures: Boolean) {
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

            if (!requeueFailures) {
                throw throwable
            }

            val retryActions = when (throwable) {
                is RemainingBatchRetryException -> throwable.remainingActions
                else -> batch
            }

            requeueFailedBatch(retryActions)
            logWarn(buildRetryMessage(batch.size, retryActions.size), throwable)
            delay(RETRY_DELAY_SECONDS.seconds)
        }
    }

    private fun requeueFailedBatch(batch: List<ActionType>) {
        for (index in batch.indices.reversed()) {
            queue.addFirst(batch[index])
        }
    }

    private fun buildRetryMessage(originalBatchSize: Int, retryActionCount: Int): String =
        if (originalBatchSize == retryActionCount) {
            "Failed to persist $retryActionCount Ledger actions; requeued the batch and will retry in ${RETRY_DELAY_SECONDS}s."
        } else {
            "Failed to persist $retryActionCount remaining Ledger actions from a batch of $originalBatchSize; " +
                "requeued the remaining actions and will retry in ${RETRY_DELAY_SECONDS}s."
        }

    private suspend fun prepareNextBatch() {
        job = Ledger.launch {
            if (queue.size < Ledger.config[DatabaseSpec.batchSize]) {
                delay(Ledger.config[DatabaseSpec.batchDelay].ticks)
            }
            if (queue.isNotEmpty()) drainBatch(requeueFailures = true)
            prepareNextBatch()
        }
    }
}
