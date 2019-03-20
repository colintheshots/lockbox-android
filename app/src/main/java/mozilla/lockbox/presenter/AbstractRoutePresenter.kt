/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package mozilla.lockbox.presenter

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.navigation.NavController
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.addTo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import mozilla.lockbox.R
import mozilla.lockbox.action.DialogAction
import mozilla.lockbox.action.RouteAction
import mozilla.lockbox.extensions.view.AlertDialogHelper
import mozilla.lockbox.extensions.view.AlertState
import mozilla.lockbox.flux.Dispatcher
import mozilla.lockbox.flux.Presenter
import mozilla.lockbox.log
import mozilla.lockbox.store.RouteStore
import mozilla.lockbox.view.DialogFragment

@ExperimentalCoroutinesApi
abstract class AbstractRoutePresenter(
    internal val activity: AppCompatActivity,
    internal val dispatcher: Dispatcher = Dispatcher.shared,
    internal val routeStore: RouteStore = RouteStore.shared
) : Presenter() {

    protected lateinit var navController: NavController

    private val backListener = OnBackPressedCallback {
        dispatcher.dispatch(RouteAction.InternalBack)
        false
    }

    private val navHostFragmentManager: FragmentManager
        get() {
            val fragmentManager = activity.supportFragmentManager
            val navHost = fragmentManager.fragments.last()
            return navHost.childFragmentManager
        }

    private val currentFragment: Fragment
        get() {
            return navHostFragmentManager.fragments.last()
        }

    override fun onPause() {
        super.onPause()
        activity.removeOnBackPressedCallback(backListener)
        compositeDisposable.clear()
    }

    override fun onResume() {
        super.onResume()
        activity.addOnBackPressedCallback(backListener)
        routeStore.routes
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(this::route)
            .addTo(compositeDisposable)
    }

    protected abstract fun route(action: RouteAction)

    protected fun showDialog(destination: DialogAction) {
        AlertDialogHelper.showAlertDialog(activity, destination.viewModel)
            .map { alertState ->
                when (alertState) {
                    AlertState.BUTTON_POSITIVE -> {
                        destination.positiveButtonActionList
                    }
                    AlertState.BUTTON_NEGATIVE -> {
                        destination.negativeButtonActionList
                    }
                }
            }
            .flatMapIterable { listOf(RouteAction.InternalBack) + it }
            .subscribe(dispatcher::dispatch)
            .addTo(compositeDisposable)
    }

    protected fun showDialogFragment(dialogFragment: DialogFragment, destination: RouteAction.DialogFragment) {
        try {
            dialogFragment.setTargetFragment(currentFragment, 0)
            dialogFragment.show(navHostFragmentManager, dialogFragment.javaClass.name)
            dialogFragment.setupDialog(destination.dialogTitle, destination.dialogSubtitle)
        } catch (e: IllegalStateException) {
            log.error("Could not show dialog", e)
        }
    }

    protected fun showUnlockFallback(action: RouteAction.UnlockFallbackDialog) {
        val manager = activity.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val intent = manager.createConfirmDeviceCredentialIntent(
            activity.getString(R.string.unlock_fallback_title),
            activity.getString(R.string.confirm_pattern)
        )
        currentFragment.startActivityForResult(intent, action.requestCode)
    }

    protected fun navigateToFragment(@IdRes destinationId: Int, args: Bundle? = null) {
        val src = navController.currentDestination ?: return
        val srcId = src.id
        if (srcId == destinationId && args == null) {
            // No point in navigating if nothing has changed.
            return
        }

        val transition = findTransitionId(srcId, destinationId) ?: destinationId

        val navOptions = if (transition == destinationId) {
            // Without being able to detect if we're in developer mode,
            // it is too dangerous to RuntimeException.
            val from = activity.resources.getResourceName(srcId)
            val to = activity.resources.getResourceName(destinationId)
            val graphName = activity.resources.getResourceName(navController.graph.id)
            log.error(
                "Cannot route from $from to $to. " +
                    "This is a developer bug, fixable by adding an action to $graphName.xml and/or ${javaClass.simpleName}"
            )
            null
        } else {
            // Get the transition action out of the graph, before we manually clear the back
            // stack, because it causes IllegalArgumentExceptions.
            src.getAction(transition)?.navOptions?.let { navOptions ->
                if (navOptions.shouldLaunchSingleTop()) {
                    while (navController.popBackStack()) {
                        // NOP
                    }
                    routeStore.clearBackStack()
                }
                navOptions
            }
        }

        try {
            navController.navigate(destinationId, args, navOptions)
        } catch (e: Throwable) {
            log.error("This appears to be a bug in navController", e)
            navController.navigate(destinationId, args)
        }
    }

    protected abstract fun findTransitionId(@IdRes from: Int, @IdRes to: Int): Int?

    protected fun openWebsite(url: String) {
        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        activity.startActivity(browserIntent, null)
    }

    protected fun openSetting(settingAction: RouteAction.SystemSetting) {
        val settingIntent = Intent(settingAction.setting.intentAction)
        settingIntent.data = settingAction.setting.data
        activity.startActivity(settingIntent, null)
    }
}