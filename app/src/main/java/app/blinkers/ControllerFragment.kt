package app.blinkers

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.*
import android.os.Bundle
import android.os.IBinder
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
import app.blinkers.data.BluetoothDataSource
import app.blinkers.data.DefaultLedRepo
import app.blinkers.data.Led
import app.blinkers.databinding.ControllerFragBinding
import com.google.android.material.snackbar.Snackbar
import timber.log.Timber
import java.util.*

class ControllerFragment : Fragment(), ServiceConnection, SerialListener {

    private val viewModel by viewModels<ControllerViewModel> { getViewModelFactory() }

    private val args: ControllerFragmentArgs by navArgs()
    private lateinit var items: List<Led>

    private lateinit var listAdapter: LedListAdapter

    private lateinit var viewDataBinding: ControllerFragBinding

    private enum class Connected {
        False, Pending, True
    }

    private var deviceAddress: String? = null
    private var newline = "\r\n"

    private lateinit var receiveText: TextView

    private var socket: SerialSocket? = null
    private var service: SerialService? = null
    private var initialStart = true
    private var connected = Connected.False

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        deviceAddress = requireArguments().getString("device")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        viewDataBinding = ControllerFragBinding.inflate(inflater, container, false).apply {
            viewmodel = viewModel
        }

        receiveText =
            viewDataBinding.root.findViewById(R.id.receive_text) // TextView performance decreases with number of spans

        receiveText.setTextColor(resources.getColor(R.color.colorRecieveText)) // set as default color to reduce number of spans

        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance())
        val sendText = viewDataBinding.root.findViewById<TextView>(R.id.send_text)
        val sendBtn = viewDataBinding.root.findViewById<View>(R.id.send_btn)
        sendBtn.setOnClickListener { v: View? ->
            send(
                sendText.text.toString()
            )
        }

        return viewDataBinding.root
    }

    override fun onStart() {
        super.onStart()
        if (service != null) service!!.attach(this) else requireActivity().startService(
            Intent(
                activity,
                SerialService::class.java
            )
        ) // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewDataBinding.lifecycleOwner = this.viewLifecycleOwner
        setupSnackbar()
        setupListAdapter()
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

    override fun onStop() {
        if (service != null && !requireActivity().isChangingConfigurations) service!!.detach()
        super.onStop()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        requireActivity().bindService(
            Intent(activity, SerialService::class.java),
            this,
            Context.BIND_AUTO_CREATE
        )

    }
    override fun onResume() {
        super.onResume()
        if (initialStart && service != null) {
            initialStart = false
            requireActivity().runOnUiThread { connect() }
        }
    }
    override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
        service = (binder as SerialService.SerialBinder).getService()
        if (initialStart && isResumed) {
            initialStart = false
            requireActivity().runOnUiThread { connect() }
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        service = null
    }

    override fun onDetach() {
        try {
            requireActivity().unbindService(this)
        } catch (ignored: java.lang.Exception) {
        }
        super.onDetach()
    }


    override fun onDestroy() {
        if (connected != Connected.False) disconnect()
        requireActivity().stopService(Intent(activity, SerialService::class.java))
        super.onDestroy()
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
            socket = SerialSocket()
            service!!.connect(this, "Connected to $deviceName")
            socket!!.connect(context, service, device)
        } catch (e: Exception) {
            onSerialConnectError(e)
        }
    }

    private fun disconnect() {
        connected = Connected.False
        service!!.disconnect()
        socket!!.disconnect()
        socket = null
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
            socket!!.write(data)
        } catch (e: Exception) {
            onSerialIoError(e)
        }
    }

    private fun receive(data: ByteArray) {
        receiveText.append(String(data))
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

    /*
     * SerialListener
     */
    override fun onSerialConnect() {
        status("connected")
        connected = Connected.True
    }

    override fun onSerialConnectError(e: Exception) {
        status("connection failed: " + e.message)
        disconnect()
    }

    override fun onSerialRead(data: ByteArray) {
        receive(data)
    }

    override fun onSerialIoError(e: Exception) {
        status("connection lost: " + e.message)
        disconnect()
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
