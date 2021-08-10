package com.example.multiple_backstacks_fun

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.fragment.app.FragmentManager
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
    private val backPressedOnStackRoot: (String) -> Unit
) : SavedStateRegistry.SavedStateProvider {
    private var currentStack: String? = null
    // Keeps track of stack root names. FragmentManager.findFragmentByX isn't convenient
    // here because FragmentManager.save/restoreBackStack are asynchronous, and executing
    // pending transactions before querying makes for inconvenient code and less atomic Fragment
    // transactions.
    private val stackRootNames = mutableSetOf<String>()

    init {
        // Wait for registry to be in created state - otherwise it will throw.
        registryOwner.lifecycle.doOnEvent(Lifecycle.Event.ON_CREATE) {
            setUp()
        }
    }

    private fun setUp() {
        registryOwner.savedStateRegistry.registerSavedStateProvider("controller", this)

        // Restore any state across config changes
        val state = registryOwner.savedStateRegistry.consumeRestoredStateForKey("controller")
        if (state != null) {
            currentStack = state.getString("currentStack", null)
            val savedStackRootNames = state.getStringArray("stacks").orEmpty()
            stackRootNames.addAll(savedStackRootNames.toList())
        }

        val backPress = object : OnBackPressedCallback(canHandleBackPress()) {
            override fun handleOnBackPressed() {
                isEnabled = false
                backPressedOnStackRoot(requireNotNull(currentStack))
            }
        }

        onBackPressedDispatcher.addCallback(lifecycleOwner, backPress)

        fragmentManager.addOnBackStackChangedListener {
            backPress.isEnabled = canHandleBackPress()
        }
    }

    private fun canHandleBackPress(): Boolean {
        return fragmentManager.backStackEntryCount == 1
    }

    override fun saveState(): Bundle {
        return Bundle().apply {
            putString("currentStack", currentStack)
            putStringArray("stacks", stackRootNames.toTypedArray())
        }
    }

    fun onStackClicked(name: String, createStack: () -> Unit) {
        val prevStack = currentStack
        currentStack = name
        if (prevStack != null) {
            fragmentManager.saveBackStack(prevStack)
        }
        fragmentManager.restoreBackStack(name)

        if (name !in stackRootNames) {
            stackRootNames += name
            createStack()
        }
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
}