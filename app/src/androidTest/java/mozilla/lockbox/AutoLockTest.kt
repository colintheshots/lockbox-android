package mozilla.lockbox

import android.content.Intent
import androidx.test.rule.ActivityTestRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mozilla.lockbox.action.LifecycleAction
import mozilla.lockbox.action.RouteAction
import mozilla.lockbox.action.Setting
import mozilla.lockbox.flux.Dispatcher
import mozilla.lockbox.robots.itemList
import mozilla.lockbox.support.AutoLockSupport
import mozilla.lockbox.support.LockingSupport
import mozilla.lockbox.view.RootActivity
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@Ignore
class TestLockingSupport() : LockingSupport {
    override var systemTimeElapsed: Long = 0L

    constructor(existing: LockingSupport) : this() {
        systemTimeElapsed = existing.systemTimeElapsed
    }

    fun advance(time: Long = 1L) {
        systemTimeElapsed += time
    }
}

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
@LargeTest
@Ignore
open class AutoLockTest {
    private val navigator = Navigator()
    private val testLockingSupport = TestLockingSupport(AutoLockSupport.shared.lockingSupport)

    @get:Rule
    val activityRule: ActivityTestRule<RootActivity> = ActivityTestRule(RootActivity::class.java)

    @Before
    fun setUp() {
        AutoLockSupport.shared.lockingSupport = testLockingSupport
    }

    @Test
    fun basicLockTest() {
        navigator.gotoItemList()
        Dispatcher.shared.dispatch(LifecycleAction.Background)

        testLockingSupport.advance(Setting.AutoLockTime.FiveMinutes.ms + 1000)

        Dispatcher.shared.dispatch(LifecycleAction.Foreground)
        navigator.blockUntil(RouteAction.LockScreen)
        navigator.checkAtLockScreen()
    }

    @Test
    fun lockSurvivesBackButton() {
        navigator.gotoItemList()
        itemList {
            tapLockNow()
        }

        navigator.checkAtLockScreen()

        navigator.back(false)

        activityRule.launchActivity(Intent(Intent.ACTION_MAIN))
        navigator.blockUntil(RouteAction.LockScreen)
        navigator.checkAtLockScreen()
    }

    @Test
    fun basicDontLockTest() {
        navigator.gotoItemList()
        Dispatcher.shared.dispatch(LifecycleAction.Background)

        testLockingSupport.advance(Setting.AutoLockTime.OneMinute.ms)

        Dispatcher.shared.dispatch(LifecycleAction.Foreground)

        navigator.checkAtItemList()
    }

    @Test
    fun firstTimeLoginFlowInterruptTest() {
        navigator.gotoFxALogin()

        Dispatcher.shared.dispatch(LifecycleAction.Background)

        testLockingSupport.advance(Setting.AutoLockTime.FiveMinutes.ms + 1000)

        Dispatcher.shared.dispatch(LifecycleAction.Foreground)

        navigator.checkAtFxALogin()
    }

    @Test
    fun disconnectAndReLoginFlowInterruptTest() {
        navigator.gotoItemList()

        navigator.disconnectAccount()

        navigator.gotoFxALogin()

        Dispatcher.shared.dispatch(LifecycleAction.Background)

        testLockingSupport.advance(Setting.AutoLockTime.FiveMinutes.ms + 1000)

        Dispatcher.shared.dispatch(LifecycleAction.Foreground)

        navigator.checkAtFxALogin()
    }
}