package app.blinkers

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.navArgs
import app.blinkers.data.BluetoothDataSource
import app.blinkers.data.DefaultLedRepo
import app.blinkers.data.Led
import app.blinkers.databinding.ControllerFragBinding
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber

class ControllerFragment : Fragment() {

    private val viewModel by viewModels<ControllerViewModel> { getViewModelFactory() }

    private val args: ControllerFragmentArgs by navArgs()
    private lateinit var items: List<Led>

    private lateinit var listAdapter: LedListAdapter

    private lateinit var viewDataBinding: ControllerFragBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewDataBinding = ControllerFragBinding.inflate(inflater, container, false).apply {
            viewmodel = viewModel
        }

        return viewDataBinding.root
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewDataBinding.lifecycleOwner = this.viewLifecycleOwner
        setupSnackbar()
        setupListAdapter()
    }

    private fun setupSnackbar() {
        view?.setupSnackbar(this, viewModel.snackbarText, Snackbar.LENGTH_SHORT)
//        arguments?.let {
//            viewModel.showEditResultMessage(args.userMessage)
//        }
    }

    private fun setupListAdapter() {
        val viewModel = viewDataBinding.viewmodel
        if (viewModel != null) {
            listAdapter = LedListAdapter(viewModel)
            viewDataBinding.ledList.adapter = listAdapter
        } else {
            Timber.w("ViewModel not initialized when attempting to set up adapter.")
        }
    }

}

fun Fragment.getViewModelFactory(): ViewModelFactory {
    val repository = DefaultLedRepo(BluetoothDataSource())
    return ViewModelFactory(repository, this)
}

fun View.setupSnackbar(
    lifecycleOwner: LifecycleOwner,
    snackbarEvent: LiveData<Event<Int>>,
    timeLength: Int
) {

    snackbarEvent.observe(lifecycleOwner, Observer { event ->
        event.getContentIfNotHandled()?.let {
            showSnackbar(context.getString(it), timeLength)
        }
    })
}

fun View.showSnackbar(snackbarText: String, timeLength: Int) {
    Snackbar.make(this, snackbarText, timeLength).run {
        addCallback(object : Snackbar.Callback() {
            override fun onShown(sb: Snackbar?) {
             //   EspressoIdlingResource.increment()
            }

            override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
             //   EspressoIdlingResource.decrement()
            }
        })
        show()
    }
}
