package com.example.multiple_backstacks_fun

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.fragment.app.commit
import androidx.fragment.app.replace
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewModelScope
import com.example.multiple_backstacks_fun.databinding.MainActivityBinding
import com.example.multiple_backstacks_fun.databinding.MyFragmentBinding
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive

class MainActivity : AppCompatActivity(), MyFragment.AddToFragmentStack {
    private lateinit var controller: MultipleBackStacksController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = MainActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tabs.forEach { tab ->
            val button = tab.toButton(this)
            binding.bottomBar.addView(button)
        }

        fun findPrimaryTab(): View {
            val index = tabs.indexOfFirst { it.isStartDestination }
            return binding.bottomBar.children.toList()[index]
        }

        val primaryTabName = tabs.first { it.isStartDestination }.name

        val backPressedOnStackRoot: (String) -> Unit = { name ->
            when (name) {
                primaryTabName -> finish()
                else -> findPrimaryTab().performClick()
            }
        }

        controller = MultipleBackStacksController(
            registryOwner = this,
            fragmentManager = supportFragmentManager,
            onBackPressedDispatcher = onBackPressedDispatcher,
            lifecycleOwner = this,
            backPressedOnStackRoot = backPressedOnStackRoot
        )

        if (savedInstanceState == null) {
            findPrimaryTab().performClick()
        }
    }

    private fun Tab.toButton(context: Context): Button {
        return Button(context).apply {
            text = name
            setOnClickListener {
                controller.onStackClicked(name) {
                    addFragment(name, 1)
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        }
    }

    private fun addFragment(name: String, count: Int) {
        supportFragmentManager.commit {
            setReorderingAllowed(true)
            replace<MyFragment>(
                containerViewId = R.id.container,
                tag = name,
                args = MyFragment.args(name, count)
            )
            addToBackStack(name)
        }
    }

    override fun addToFragmentStack(name: String, currentCount: Int) {
        addFragment(name, currentCount + 1)
    }

    private data class Tab(
        val name: String,
        val isStartDestination: Boolean
    )

    companion object {
        private val tabs = listOf(
            Tab(name = "first", isStartDestination = true),
            Tab(name = "second", isStartDestination = false),
            Tab(name = "third", isStartDestination = false),
        )
    }
}

class MyFragment : Fragment(R.layout.my_fragment) {
    private val name get() = requireArguments().getString("name")!!
    private val count get() = requireArguments().getInt("count")
    private lateinit var addToFragmentStack: AddToFragmentStack
    private val viewModel: TickerViewModel by viewModels()

    interface AddToFragmentStack {
        fun addToFragmentStack(name: String, currentCount: Int)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        addToFragmentStack = context as AddToFragmentStack
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = MyFragmentBinding.bind(view)
        binding.name.text = name
        binding.count.text = count.toString()

        binding.addToFragmentStack.setOnClickListener {
            addToFragmentStack.addToFragmentStack(name, count)
        }

        viewModel.tick
            .onEach { tock ->
                binding.ticker.text = tock.toString()
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    companion object {
        fun args(name: String, count: Int): Bundle {
            return bundleOf(
                "name" to name,
                "count" to count
            )
        }
    }
}

class TickerViewModel : ViewModel() {
    val tick = flow {
        var counter = 0
        while (currentCoroutineContext().isActive) {
            emit(counter++)
            delay(100L)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = 0
    )
}
