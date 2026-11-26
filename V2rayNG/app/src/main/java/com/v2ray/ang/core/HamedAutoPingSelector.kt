package com.v2ray.ang.core

import android.content.Context
import com.v2ray.ang.dto.RealPingEvent
import com.v2ray.ang.handler.MmkvManager
import com.v2ray.ang.service.RealPingWorkerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Selects the fastest working config from a candidate list by running real-ping probes
 * sequentially in batches of [BATCH_SIZE] (default 5).
 *
 * As soon as a batch yields at least one positive delay (> 0 ms), waits up to [DEFAULT_GRACE_WINDOW_MS]
 * (or until all 5 probes in that batch finish) so peer responders in the same batch can compete,
 * cancels any remaining slow probes in that batch, and returns the lowest-latency winner immediately.
 */
object HamedAutoPingSelector {
    const val BATCH_SIZE = 5
    const val DEFAULT_GRACE_WINDOW_MS = 350L

    data class BatchProgress(
        val batchIndex: Int,
        val totalBatches: Int,
        val startNumber: Int,
        val endNumber: Int,
        val totalCount: Int,
    )

    data class SelectionResult(
        val guid: String,
        val delayMillis: Long,
        val progress: BatchProgress,
    )

    data class BatchControl(
        val cancel: () -> Unit,
    )

    /**
     * Pure JVM-testable 5-by-5 batch selector.
     */
    suspend fun selectFirstRespondingInBatches(
        candidateGuids: List<String>,
        batchSize: Int = BATCH_SIZE,
        graceWindowMs: Long = DEFAULT_GRACE_WINDOW_MS,
        onBatchStart: suspend (BatchProgress) -> Unit = {},
        startBatch: (
            batchGuids: List<String>,
            onResult: (guid: String, delayMillis: Long) -> Unit,
            onFinish: () -> Unit,
        ) -> BatchControl,
    ): SelectionResult? {
        if (candidateGuids.isEmpty()) return null
        val safeBatchSize = batchSize.coerceAtLeast(1)
        val chunks = candidateGuids.chunked(safeBatchSize)
        val totalBatches = chunks.size
        val totalCount = candidateGuids.size

        var offset = 0
        for ((idx, chunk) in chunks.withIndex()) {
            val progress = BatchProgress(
                batchIndex = idx + 1,
                totalBatches = totalBatches,
                startNumber = offset + 1,
                endNumber = offset + chunk.size,
                totalCount = totalCount,
            )
            offset += chunk.size
            onBatchStart(progress)

            val firstHit = CompletableDeferred<Unit>()
            val batchFinished = CompletableDeferred<Unit>()
            val batchDoneOrHit = CompletableDeferred<Unit>()
            val lock = Any()
            val validResults = mutableMapOf<String, Long>()
            var completedCount = 0

            val control = startBatch(
                chunk,
                { guid, delay ->
                    synchronized(lock) {
                        if (delay > 0L) {
                            val prev = validResults[guid]
                            if (prev == null || delay < prev) {
                                validResults[guid] = delay
                            }
                            if (!firstHit.isCompleted) {
                                firstHit.complete(Unit)
                            }
                            if (!batchDoneOrHit.isCompleted) {
                                batchDoneOrHit.complete(Unit)
                            }
                        }
                        completedCount++
                        if (completedCount >= chunk.size) {
                            if (!batchFinished.isCompleted) batchFinished.complete(Unit)
                            if (!batchDoneOrHit.isCompleted) batchDoneOrHit.complete(Unit)
                        }
                    }
                },
                {
                    if (!batchFinished.isCompleted) batchFinished.complete(Unit)
                    if (!batchDoneOrHit.isCompleted) batchDoneOrHit.complete(Unit)
                },
            )

            try {
                batchDoneOrHit.await()
                if (firstHit.isCompleted && !batchFinished.isCompleted && graceWindowMs > 0L) {
                    withTimeoutOrNull(graceWindowMs) {
                        batchFinished.await()
                    }
                }
            } catch (ce: CancellationException) {
                runCatching { control.cancel() }
                throw ce
            }

            runCatching { control.cancel() }

            val best = synchronized(lock) {
                validResults.entries.minByOrNull { it.value }?.let { it.key to it.value }
            }
            if (best != null) {
                return SelectionResult(
                    guid = best.first,
                    delayMillis = best.second,
                    progress = progress,
                )
            }
        }
        return null
    }

    /**
     * Runs real ping tests on [candidateGuids] in batches of 5 via [RealPingWorkerService],
     * stores tested delays in [MmkvManager], and returns the first winning server.
     */
    suspend fun measureAndSelectFirstWorking(
        context: Context,
        candidateGuids: List<String>,
        onBatchStart: suspend (BatchProgress) -> Unit = {},
    ): SelectionResult? {
        if (candidateGuids.isEmpty()) return null
        CoreNativeManager.initCoreEnv(context)
        return selectFirstRespondingInBatches(
            candidateGuids = candidateGuids,
            batchSize = BATCH_SIZE,
            graceWindowMs = DEFAULT_GRACE_WINDOW_MS,
            onBatchStart = onBatchStart,
        ) { batchGuids, onResult, onFinish ->
            val worker = RealPingWorkerService(
                context = context.applicationContext,
                guids = batchGuids,
                onlyTcp = false,
                onEvent = { event ->
                    when (event) {
                        is RealPingEvent.Result -> {
                            runCatching {
                                MmkvManager.encodeServerTestDelayMillis(event.guid, event.delayMillis)
                            }
                            onResult(event.guid, event.delayMillis)
                        }
                        is RealPingEvent.Finish -> onFinish()
                        is RealPingEvent.Progress -> Unit
                    }
                },
            )
            worker.start()
            BatchControl { worker.cancel() }
        }
    }
}
