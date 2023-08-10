package sovran.kotlin

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class DispatchQueue {

    private val channel = Channel<Task>(UNLIMITED)

    private val dispatcher: CloseableCoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val scope = CoroutineScope(SupervisorJob())

    fun start() {
        consume()
    }

    fun stop() {
        channel.cancel()
        dispatcher.close()
    }

    private fun consume() = scope.launch(dispatcher) {
        for (task in channel) {
            if (task is SyncTask<*>) {
                task.run()
            }
        }
    }

    fun <T> sync(closure: suspend () -> T) : T? {
        val task = SyncTask(closure)
        channel.trySend(task)
        task.latch.await()
        return task.result
    }

    abstract class Task {
        val latch: CountDownLatch = CountDownLatch(1)
    }

    class SyncTask<T>(
        val closure: suspend () -> T?
    ) : Task() {
        var result: T? = null

        suspend fun run() {
            result = closure()
            latch.countDown()
        }
    }
}