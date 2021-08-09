package com.example.multiple_backstacks_fun

import android.content.Context
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
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

        controller = MultipleBackStacksController(
            registryOwner = this,
            fragmentManager = supportFragmentManager,
            onBackPressedDispatcher = onBackPressedDispatcher,
            lifecycleOwner = this,
            primaryTab = primaryTab
        )

        binding.first.setOnClickListener {
            controller.onTabClicked(primaryTab, isStartDestination = true)
        }
        binding.second.setOnClickListener {
            controller.onTabClicked("second", isStartDestination = false)
        }
        binding.third.setOnClickListener {
            controller.onTabClicked("third", isStartDestination = false)
        }

        if (savedInstanceState == null) {
            binding.first.performClick()
        }
    }

    override fun addToFragmentStack(currentStack: Int) {
        controller.addToCurrentFragmentStack(currentStack + 1)
    }

    companion object {
        private const val primaryTab = "first"
    }
}

class MyFragment : Fragment(R.layout.my_fragment) {
    private val tab get() = requireArguments().getString("tab")
    private val stack get() = requireArguments().getInt("stack")
    private lateinit var addToFragmentStack: AddToFragmentStack
    private val viewModel: TickerViewModel by viewModels()

    interface AddToFragmentStack {
        fun addToFragmentStack(currentStack: Int)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        addToFragmentStack = context as AddToFragmentStack
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val binding = MyFragmentBinding.bind(view)
        binding.tabPosition.text = tab
        binding.stackPosition.text = stack.toString()

        binding.addToFragmentStack.setOnClickListener {
            addToFragmentStack.addToFragmentStack(stack)
        }

        viewModel.tick
            .onEach { tock ->
                binding.ticker.text = tock.toString()
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    companion object {
        fun args(tab: String, stack: Int): Bundle {
            return bundleOf(
                "tab" to tab,
                "stack" to stack
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
