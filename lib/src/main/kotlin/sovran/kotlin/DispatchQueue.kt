package sovran.kotlin

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.atomic.AtomicBoolean

class DispatchQueue {

    private val queue = SynchronousQueue<Task>()

    private val executor = Executors.newSingleThreadExecutor()

    private val running = AtomicBoolean(true)

    fun start() {
        executor.execute(::consume)
    }

    fun stop() {
        running.set(false)
        executor.shutdown()
    }

    private fun consume() {
        while (running.get()) {
            val task = queue.take()
            task.closure()
            task.latch.countDown()
        }
    }

    fun sync(closure: () -> Unit) {
        val task = Task(closure)
        queue.put(task)
        task.latch.await()
    }

    data class Task(
        val closure: () -> Unit,
        val latch: CountDownLatch = CountDownLatch(1)
    )
}