package app.blinkers

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.DialogInterface
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.method.ScrollingMovementMethod
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.navigation.fragment.navArgs
import app.blinkers.data.*
import app.blinkers.data.source.*
import app.blinkers.data.source.local.BlinkerDao
import app.blinkers.databinding.ControllerFragBinding
import com.github.mikephil.charting.charts.LineChart
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.controller_frag.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.*
import kotlin.coroutines.CoroutineContext

class ControllerFragment : Fragment(), CoroutineScope {

    private var btSocket: BluetoothSocket? = null;
    private val viewModel by viewModels<ControllerViewModel> { getViewModelFactory() }

    private val args: ControllerFragmentArgs by navArgs()
    private lateinit var items: List<Led>

    private lateinit var chart: LineChart

    private lateinit var viewDataBinding: ControllerFragBinding

    private enum class Connected {
        False, Pending, True
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    private var deviceAddress: String? = null
    private var newline = "\r\n"

    private lateinit var receiveText: TextView
    private var brainRawData: String = ""
    private var currentlyReadingBrainData: Boolean = false

    //  private var socket: SerialSocket? = null
    private var initialStart = true
    private var connected = Connected.False
    private var startTime = System.currentTimeMillis();

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceAddress = requireArguments().getString("device")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        val view = inflater.inflate(R.layout.controller_frag, container, false)

        viewModel.serialConnectEvent.observe(viewLifecycleOwner, Observer {
            if (it) {
                connected = Connected.True
                status("Connected")
            }
        })

        viewModel.serialConnectErrorEvent.observe(viewLifecycleOwner, Observer {event ->
            event.getContentIfNotHandled()?.let {
                status("connection failed: $it")
                disconnect()
            }
        })

        viewModel.serialReadEvent.observe(viewLifecycleOwner, Observer {event ->
            event.getContentIfNotHandled()?.let {
                // receive(it)
            }
        })

        viewModel.serialIOErrorEvent.observe(viewLifecycleOwner, Observer {event ->
            event.getContentIfNotHandled()?.let {
                status("connection lost: $it")
                disconnect()
            }
        })

        viewModel.observeBrainWaves.observe(viewLifecycleOwner, Observer {
            if (it is Result.Success) receive(it.data)
            else if (it is Result.Error) receiveText.append("${it.exception}\n")
        })

        viewModel.observeLed.observe(viewLifecycleOwner, Observer {
            if (it is Result.Success) {
                view.ledStatus.text = if (it.data == 1) "Led is ON" else "Led is OFF"
            } else if (it is Result.Error) {
                view.ledStatus.text = it.exception.message
            }
        })

        viewModel.observeConnectionStatus.observe(viewLifecycleOwner, Observer {
            if (it is Result.Success) {
                status(it.data)
            } else if (it is Result.Error) {
                status(it.exception.message!!)
            }
        })

        startTime = System.currentTimeMillis()

        receiveText =
            view.findViewById(R.id.receive_text) // TextView performance decreases with number of spans

        receiveText.setTextColor(resources.getColor(R.color.colorRecieveText)) // set as default color to reduce number of spans

        receiveText.movementMethod = ScrollingMovementMethod.getInstance()
        val sendText = view.findViewById<TextView>(R.id.send_text)
        val sendBtn = view.findViewById<View>(R.id.send_btn)
        sendBtn.setOnClickListener { v: View? ->
            send(
                sendText.text.toString()
            )
        }

        view.ledSwitch.setOnCheckedChangeListener { _, isChecked ->
            viewModel.switchLed(isChecked)
        }

        return view
    }

    private var thread: Thread? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        ///  viewDataBinding.lifecycleOwner = this.viewLifecycleOwner
        setupSnackbar()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_terminal, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        return if (id == R.id.clear) {
            receiveText.text = ""
            true
        } else if (id == R.id.newline) {
            val newlineNames =
                resources.getStringArray(R.array.newline_names)
            val newlineValues =
                resources.getStringArray(R.array.newline_values)
            val pos = Arrays.asList(*newlineValues).indexOf(newline)
            val builder =
                AlertDialog.Builder(activity)
            builder.setTitle("Newline")
            builder.setSingleChoiceItems(
                newlineNames,
                pos
            ) { dialog: DialogInterface, item1: Int ->
                newline = newlineValues[item1]
                dialog.dismiss()
            }
            builder.create().show()
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

    override fun onResume() {
        super.onResume()
        if (initialStart) {
            initialStart = false
             connect()
        }
    }

    override fun onDestroy() {
      //  if (connected != Connected.False) disconnect()
        super.onDestroy()
    }

    private fun setupSnackbar() {
        view?.setupSnackbar(this, viewModel.snackbarText, Snackbar.LENGTH_SHORT)
//        arguments?.let {
//            viewModel.showEditResultMessage(args.userMessage)
//        }
    }


    /*
     * Serial + UI
     */
    private fun connect() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            val deviceName =
                if (device.name != null) device.name else device.address
            status("connecting...")
            connected = Connected.Pending
            DefaultDeviceCommunicator.connect(this@ControllerFragment.requireContext(), device)
            connected = Connected.True
            //   service!!.connect("Connected to $deviceName")
            //    socket!!.connect(context, viewModel, device)
        } catch (e: Exception) {
            status("connection failed: ${e.message}")
            disconnect()
        }
    }

    private fun disconnect() {
        connected = Connected.False
        btSocket?.let {
            it.close()
            //  it.disconnect()
            btSocket = null
        }
    }

    private fun send(str: String) {
        if (connected != Connected.True) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val spn = SpannableStringBuilder(
                """
                    $str
                    
                    """.trimIndent()
            )
            spn.setSpan(
                ForegroundColorSpan(resources.getColor(R.color.colorSendText)),
                0,
                spn.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            receiveText .append(spn)
            val data = (str + newline).toByteArray()
            viewModel.switchLed(data.contentEquals("1".toByteArray()))
        } catch (e: Exception) {
            status("connection lost: ${e.message}")
            disconnect()
        }
    }

    private fun receive(data: EEGSnapshot) {
//        with(data) {
//            setBrainData(listOf(
//                signalStrength,
//                delta,
//                theta,
//                lowAlpha,
//                highAlpha,
//                lowBeta,
//                highBeta,
//                lowGamma,
//                highGamma
//            ))
//        }

        receiveText.append("${data}\n")

    }

    private fun status(str: String) {
        val spn = SpannableStringBuilder(
            """
                $str
                
                """.trimIndent()
        )
        spn.setSpan(
            ForegroundColorSpan(resources.getColor(R.color.colorStatusText)),
            0,
            spn.length,
            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        receiveText.append(spn)
    }

}

fun Fragment.getViewModelFactory(): ViewModelFactory {
    val repository = (requireContext().applicationContext as BlinkersApp).blinkersRepository
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
