package sovran.kotlin

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SovranJvmTest : Subscriber {

    private val store = Store()

    @Test
    fun testDispatch() = runBlocking {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(1)

        // register some handlers for state changes
        store.subscribe(subscriber = this@SovranJvmTest, stateClazz = MessagesState::class) { state ->
            latch.countDown()
            assertEquals(22, state.unreadCount)
        }

        val action = MessagesUnreadAction(22)
        store.dispatch(action, MessagesState::class)

        assertTrue(latch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testAsyncDispatch() = runBlocking {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(1)

        // register some handlers for state changes
        store.subscribe(subscriber = this@SovranJvmTest, initialState = false, stateClazz = MessagesState::class) { state ->
            latch.countDown()
            assertEquals(666, state.unreadCount)
        }

        val action = MessagesUnreadAsyncAction(drop = false, value = 666)
        store.dispatch(action, MessagesState::class)

        assertTrue(latch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testDroppedAsyncDispatch() = runBlocking {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(1)

        // register some handlers for state changes
        store.subscribe(subscriber = this@SovranJvmTest, initialState = true, stateClazz = MessagesState::class) { state ->
            latch.countDown()
            assertNotEquals(666, state.unreadCount)
        }

        val action = MessagesUnreadAsyncAction(drop = true, value = 666)
        store.dispatch(action, MessagesState::class)

        assertTrue(latch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testUnprovidedStateAsyncDispatch() = runBlocking {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(1)

        // register some handlers for state changes
        store.subscribe(this@SovranJvmTest, NotProvidedState::class, true) { _ ->
            // we should never get here because NotProvidedState isn't what's in the store.
            latch.countDown()
        }

        val action = NotProvidedAsyncAction()
        // this action should get dropped, because there's no matching state for it.
        store.dispatch(action, NotProvidedState::class)

        assertFalse(latch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testUnprovidedStateDispatch() = runBlocking {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(1)

        // register some handlers for state changes
        store.subscribe(this@SovranJvmTest, NotProvidedState::class, true) {
            // we should never get here because NotProvidedState isn't what's in the store.
            latch.countDown()
        }

        val action = NotProvidedAction()
        // this action should get dropped, because there's no matching state for it.
        store.dispatch(action, NotProvidedState::class)

        assertFalse(latch.await(10, TimeUnit.SECONDS))
    }

    @Test
    fun testUnsubscribeForAction() = runBlocking {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(2)

        // register some handlers for state changes
        val identifier = store.subscribe(this@SovranJvmTest, MessagesState::class) {
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
    fun testUnsubscribeForAsyncAction() = runBlocking {
        // register some state
        store.provide(MessagesState())

        val latch = CountDownLatch(2)

        // register some handlers for state changes
        val identifier = store.subscribe(this@SovranJvmTest, MessagesState::class) {
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
}
