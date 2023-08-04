package sovran.kotlin

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

    fun consume() {
        while (running.get()) {
            val task = queue.take()
            task.closure()
            task.semaphore.release()
        }
    }

    fun sync(closure: () -> Unit) {
        val task = Task(closure)
        queue.put(task)
        task.semaphore.acquire()
    }

    data class Task(
        val closure: () -> Unit,
        val semaphore: Semaphore = Semaphore(0)
    )
}