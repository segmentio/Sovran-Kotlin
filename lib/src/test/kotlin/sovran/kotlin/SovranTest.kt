/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package sovran.kotlin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.*

class SovranTest : Subscriber {
    private val store = Store()

    @Before
    fun setup() {
//        store.reset()
    }

    @Test
    fun exampleCase() {

    }

    @Test
    fun testProvide() {
        store.provide(MessagesState())
        assertEquals(1, store.states.size)

        store.provide(UserState())
        assertEquals(2, store.states.size)
    }

    @Test
    fun testDoubleSubscribe() {
        // register some state
        store.provide(MessagesState())
        store.provide(UserState())

        // register some handlers for state changes
        val id1 = store.subscribe(this, MessagesState::class) { state ->
            print("unreadCount = $state.unreadCount")
        }

        // subscribe self to UserState twice.
        store.subscribe(this, UserState::class) { state ->
            print("username = $state.username")
        }

        // this should add a second listener for UserState
        val id3 = store.subscribe(this, UserState::class) { state ->
            print("username2 = $state.username")
        }

        // we should have 3 subscriptions.  2 for UserState, one for MessagesState.
        assertEquals(3, store.subscriptions.size)
        // we should have id1 + 2 = id3,
        // since the subscription ID has been increased twice since id1
        assertEquals(id1 + 2, id3)
    }

    @Test
    fun testDoubleProvide() {
        // register some state
        store.provide(MessagesState())
        store.provide(UserState())

        // this should do nothing since UserState has already been provided.
        // in use, this will assert in DEBUG mode, outside of tests.
        store.provide(UserState())

        assertEquals(2, store.states.size)
    }

    @Test
    fun testDispatch() {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(1)

        // register some handlers for state changes
        store.subscribe(subscriber = this, stateClazz = MessagesState::class) { state ->
            latch.countDown()
            assertEquals(22, state.unreadCount)
        }

        val action = MessagesUnreadAction(22)
        store.dispatch(action, MessagesState::class)

        assertTrue(latch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testAsyncDispatch() {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(1)

        // register some handlers for state changes
        store.subscribe(subscriber = this, initialState = false, stateClazz = MessagesState::class) { state ->
            latch.countDown()
            assertEquals(666, state.unreadCount)
        }

        val action = MessagesUnreadAsyncAction(drop = false, value = 666)
        store.dispatch(action, MessagesState::class)

        assertTrue(latch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testDroppedAsyncDispatch() {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(1)

        // register some handlers for state changes
        store.subscribe(subscriber = this, initialState = true, stateClazz = MessagesState::class) { state ->
            latch.countDown()
            assertNotEquals(666, state.unreadCount)
        }

        val action = MessagesUnreadAsyncAction(drop = true, value = 666)
        store.dispatch(action, MessagesState::class)

        assertTrue(latch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testUnprovidedStateAsyncDispatch() {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(1)

        // register some handlers for state changes
        store.subscribe(this, NotProvidedState::class, true) { _ ->
            // we should never get here because NotProvidedState isn't what's in the store.
            latch.countDown()
        }

        val action = NotProvidedAsyncAction()
        // this action should get dropped, because there's no matching state for it.
        store.dispatch(action, NotProvidedState::class)

        assertFalse(latch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testUnprovidedStateDispatch() {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(1)

        // register some handlers for state changes
        store.subscribe(this, NotProvidedState::class, true) {
            // we should never get here because NotProvidedState isn't what's in the store.
            latch.countDown()
        }

        val action = NotProvidedAction()
        // this action should get dropped, because there's no matching state for it.
        store.dispatch(action, NotProvidedState::class)

        assertFalse(latch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testUnsubscribeForAction() {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(2)

        // register some handlers for state changes
        val identifier = store.subscribe(this, MessagesState::class) {
            // now we got hit by the action, verify the expected value.
            assertEquals(22, it.unreadCount)
            // if this gets hit twice, we'll get an error about multiple calls to the trigger.
            latch.countDown()
        }

        val action = MessagesUnreadAction(22)
        store.dispatch(action, MessagesState::class)

        val subscriptionCount = store.subscriptions.size
        store.unsubscribe(identifier)

        // now the subscriptions should not have the one being unsubscribed
        assertFalse(store.subscriptions.any{ it.subscriptionID == identifier })
        // and the size should reduced only by 1
        assertEquals(subscriptionCount - 1, store.subscriptions.size)

        // this should be ignored since we've unsubscribed.
        val nextAction = MessagesUnreadAction(11)
        store.dispatch(nextAction, MessagesState::class)

        assertFalse(latch.await(5, TimeUnit.SECONDS))
        assertEquals(1, latch.count)
    }

    @Test
    fun testUnsubscribeForAsyncAction() {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(2)

        // register some handlers for state changes
        val identifier = store.subscribe(this, MessagesState::class) {
            // now we got hit by the action, verify the expected value.
            assertEquals(666, it.unreadCount)
            latch.countDown()
        }

        val action = MessagesUnreadAsyncAction(drop = false, value = 666)
        store.dispatch(action, MessagesState::class)

        runBlocking {
            delay(2000L)
        }

        store.unsubscribe(identifier)

        val nextAction = MessagesUnreadAsyncAction(drop = false, value = 10)
        store.dispatch(nextAction, MessagesState::class)

        assertFalse(latch.await(5, TimeUnit.SECONDS))
        assertEquals(1, latch.count)
    }

    @Test
    fun testAlternateSubscriptionSyntax() {
        // this is more of a syntax test.  we don't really care if it's
        // a success, just that it builds.
        // register some state

        store.provide(MessagesState())

        // register some handlers for state changes
        store.subscribe(this, MessagesState::class) {
            print("hello")
        }

        val action = MessagesUnreadAction(22)
        store.dispatch(action, MessagesState::class)
    }

    @Test
    fun testSubscriptionIDIncrement() {
        val handler: Handler<MessagesState> = { _ ->
            print("booya")
        }
        val s1 = Store.Subscription(this, handler, MessagesState::class, Dispatchers.Default)
        val s2 = Store.Subscription(this, handler, MessagesState::class, Dispatchers.Default)
        val s3 = Store.Subscription(this, handler, MessagesState::class, Dispatchers.Default)

        assertTrue(s2.subscriptionID > s1.subscriptionID)
        assertTrue(s3.subscriptionID > s2.subscriptionID)
    }

    @Test
    fun testGetCurrentState() {
        val state = MessagesState(unreadCount = 1, outgoingCount = 2, messages = emptyList(), outgoing = emptyList())
        store.provide(state)

        val messageState = store.currentState(MessagesState::class)
        assertEquals(1, messageState?.unreadCount)
    }

}
