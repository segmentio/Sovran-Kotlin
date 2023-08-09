package sovran.kotlin

import kotlinx.coroutines.*
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import kotlin.reflect.KClass

typealias SubscriptionID = Int

interface AsyncStore {
    suspend fun <StateT : State> subscribe(
        subscriber: Subscriber,
        stateClazz: KClass<StateT>,
        initialState: Boolean = false,
        queue: DispatchQueue? = null,
        handler: Handler<StateT>
    ): SubscriptionID

    suspend fun unsubscribe(subscriptionID: SubscriptionID)

    suspend fun <StateT : State> provide(state: StateT)

    suspend fun <ActionT : Action<StateT>, StateT : State> dispatch(action: ActionT, stateClazz: KClass<StateT>)

    suspend fun <ActionT : AsyncAction<StateT, ResultT>, StateT : State, ResultT> dispatch(action: ActionT, stateClazz: KClass<StateT>)

    suspend fun <StateT : State> currentState(clazz: KClass<StateT>): StateT?

    fun shutdown()
}

class Store : AsyncStore {

    private val sovranDispatcher: CloseableCoroutineDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val sovranContext = CoroutineScope(SupervisorJob()).coroutineContext + sovranDispatcher

    internal val internalStore = SynchronousStore()

    override suspend fun <StateT : State> subscribe(
        subscriber: Subscriber,
        stateClazz: KClass<StateT>,
        initialState: Boolean,
        queue: DispatchQueue?,
        handler: Handler<StateT>
    ): SubscriptionID = withContext(sovranContext) {
        internalStore.subscribe(subscriber, stateClazz, initialState, queue, handler)
    }

    override suspend fun unsubscribe(subscriptionID: SubscriptionID) = withContext(sovranContext) {
        internalStore.unsubscribe(subscriptionID)
    }

    override suspend fun <StateT : State> provide(state: StateT) = withContext(sovranContext) {
        internalStore.provide(state)
    }

    override suspend fun <ActionT : Action<StateT>, StateT : State> dispatch(
        action: ActionT,
        stateClazz: KClass<StateT>
    ) = withContext(sovranContext) {
        internalStore.dispatch(action, stateClazz)
    }

    override suspend fun <ActionT : AsyncAction<StateT, ResultT>, StateT : State, ResultT> dispatch(
        action: ActionT,
        stateClazz: KClass<StateT>
    ) = withContext(sovranContext) {
        internalStore.dispatch(action, stateClazz)
    }

    override suspend fun <StateT : State> currentState(clazz: KClass<StateT>): StateT? = withContext(sovranContext) {
        internalStore.currentState(clazz)
    }

    override fun shutdown() {
        internalStore.shutdown()
        sovranDispatcher.close()
    }

    internal class Subscription<StateT : State>(
        obj: Subscriber, val handler: Handler<StateT>,
        val key: KClass<StateT>,
        val queue: DispatchQueue
    ) {
        val subscriptionID: SubscriptionID = createNextSubscriptionID()
        val owner: WeakReference<Any> = WeakReference(obj)

        companion object {
            private var nextSubscriptionID = 1
            fun createNextSubscriptionID(): SubscriptionID {
                val result = nextSubscriptionID
                nextSubscriptionID += 1
                return result
            }
        }
    }
}

