package com.example.multiple_backstacks_fun

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner

class MultipleBackStacksController(
    private val registryOwner: SavedStateRegistryOwner,
    private val fragmentManager: FragmentManager,
    private val onBackPressedDispatcher: OnBackPressedDispatcher,
    private val lifecycleOwner: LifecycleOwner,
    private val primaryTab: String
) : SavedStateRegistry.SavedStateProvider {
    private var currentTab: String = primaryTab
    // Keeps track of added tab root Fragment names. FragmentManager.findFragmentByX isn't convenient
    // here because FragmentManager.save/restoreBackStack are asynchronous, and executing
    // pending transactions before querying makes for inconvenient code and less atomic Fragment
    // transactions.
    private val addedTabRoots = mutableSetOf<String>()

    init {
        registryOwner.lifecycle.doOnEvent(Lifecycle.Event.ON_CREATE) {
            setUp()
        }
    }

    private fun setUp() {
        registryOwner.savedStateRegistry.registerSavedStateProvider("controller", this)
        val state = registryOwner.savedStateRegistry.consumeRestoredStateForKey("controller")
        if (state != null) {
            currentTab = state.getString("currentTab", primaryTab)
            val savedTabRoots = state.getStringArray("addedTabRoots").orEmpty()
            addedTabRoots.addAll(savedTabRoots.toList())
        }

        // Navigates to primary tab when a non-primary tab is backed out of.
        val backPress = object : OnBackPressedCallback(canHandleBackPress()) {
            override fun handleOnBackPressed() {
                onTabClicked(primaryTab, isStartDestination = true)
            }
        }

        onBackPressedDispatcher.addCallback(lifecycleOwner, backPress)

        fragmentManager.addOnBackStackChangedListener {
            backPress.isEnabled = canHandleBackPress()
        }
    }

    override fun saveState(): Bundle {
        return Bundle().apply {
            putString("currentTab", currentTab)
            putStringArray("addedTabRoots", addedTabRoots.toTypedArray())
        }
    }

    // Determines whether a back press should navigate to the primary tab. Non-primary tabs
    // should keep their root tab in the back stack if they exist.
    private fun canHandleBackPress(): Boolean {
        return fragmentManager.backStackEntryCount == 1 && currentTab != primaryTab
    }

    fun onTabClicked(tab: String, isStartDestination: Boolean) {
        val prevTab = currentTab
        currentTab = tab
        fragmentManager.saveBackStack(prevTab)
        fragmentManager.restoreBackStack(tab)

        if (tab !in addedTabRoots) {
            addedTabRoots += tab
            addFragment(tab, 1, addToBackStack = !isStartDestination)
        }
    }

    private fun addFragment(tab: String, stack: Int, addToBackStack: Boolean = true) {
        fragmentManager.commit {
            setReorderingAllowed(true)
            replace<MyFragment>(
                containerViewId = R.id.container,
                tag = tab,
                args = MyFragment.args(tab, stack)
            )
            if (addToBackStack) {
                addToBackStack(tab)
            }
        }
    }

    fun addToCurrentFragmentStack(stack: Int) {
        addFragment(currentTab, stack)
    }

    private fun Lifecycle.doOnEvent(which: Lifecycle.Event, block: () -> Unit) {
        val observer = object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (which != event) return
                source.lifecycle.removeObserver(this)
                block()
            }
        }

        addObserver(observer)
    }

    companion object {
        private const val primaryTab = "first"
    }
}