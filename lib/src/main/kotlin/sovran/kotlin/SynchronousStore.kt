package sovran.kotlin

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.reflect.KClass

class SynchronousStore{

    private val syncQueue = DispatchQueue()

    private val store = Store()

    internal val states: MutableList<Store.Container>
        get() = store.states

    internal val subscriptions: MutableList<Store.Subscription<out State>>
        get() = store.subscriptions

    init {
        syncQueue.start()
    }

    /**
     * Subscribe a closure to a particular type of state.
     * Note: Subscribers are weakly held and will be discarded automatically when no longer present.
     *
     * @param subscriber    The object subscribing to a given state type.  Must conform to `Subscriber`.
     * @param stateClazz    Type of state to which you want to subscribe. Must conform to `State`
     * @param initialState  Specifies that the handler should be called with current state upon subscribing. Default is `false`
     * @param queue         A CoroutineDispatcher on which the handler is run. Default is `Dispatcher.Default`
     * @param handler       A closure to be executed when the specified state type is modified.
     * @return subscriberId that can be used to unsubscribe at a later time.
     *
     * example
     * ```
     *  store.subscribe(subscriber = this, stateClazz = MyState::class, initialState = false, queue = Dispatchers.Main) { state ->
     *      print(state)
     *  }
     * ```
     */
    fun <StateT : State> subscribe(
        subscriber: Subscriber,
        stateClazz: KClass<StateT>,
        initialState: Boolean = false,
        queue: CoroutineDispatcher = Dispatchers.Default,
        handler: Handler<StateT>
    ): SubscriptionID = syncQueue.sync {
        store.subscribe(subscriber, stateClazz, initialState, queue, handler)
    } ?: -1

    /**
     * Unsubscribe from state updates. The supplied SubscriptionID will be used to perform the
     * lookup and removal of a given subscription.
     *
     * @param subscriptionID The subscriberID given as a result from a previous subscribe() call.
     */
    fun unsubscribe(subscriptionID: SubscriptionID) {
        syncQueue.sync {
            store.unsubscribe(subscriptionID)
        }
    }

    /**
     * Provides an instance of StateT as state within the system. If a state type is
     * provided more than once, it is simply ignored.
     *
     * @param state A class instance conforming to `State`.
     */
    fun <StateT : State> provide(state: StateT) {
        syncQueue.sync {
            store.provide(state)
        }
    }

    /**
     * Synchronously dispatch an Action with the intent of changing the state. Reducers are run
     * on a serial queue in the order the attached Actions are received.
     *
     * @param action        The action to be dispatched.  Must conform to `Action`.
     * @param stateClazz    The type of message this action acts upon. Must conform to `State`
     */
    fun <ActionT : Action<StateT>, StateT : State> dispatch(action: ActionT, stateClazz: KClass<StateT>) {
        syncQueue.sync {
            store.dispatch(action, stateClazz)
        }
    }


    /**
     * Asynchronously dispatch an Action with the intent of changing the state. Reducers are run on
     * a serial queue in the order their operations complete.
     *
     * @param action        The action to be dispatched.  Must conform to `AsyncAction`.
     * @param stateClazz    The type of message this action acts upon. Must conform to `State`
     */
    fun <ActionT : AsyncAction<StateT, ResultT>, StateT : State, ResultT> dispatch(action: ActionT, stateClazz: KClass<StateT>) {
        syncQueue.sync {
            store.dispatch(action, stateClazz)
        }
    }

    /**
     * Retrieves the current state of a given type from the Store
     *
     * Example:
     * ```
     *      val state = store.currentState(MessagesState::class)
     * ```
     */
    fun <StateT : State> currentState(clazz: KClass<StateT>): StateT?  = syncQueue.sync {
        store.currentState(clazz)
    }

    /**
     * Shutdowns down CoroutineDispatchers. This is typically used to cleanup resources in
     * containerized environments. This is a non-reversible call; the Store will non-longer
     * process events after this call.
     */
    fun shutdown() {
        syncQueue.stop()
        store.shutdown()
    }
}
