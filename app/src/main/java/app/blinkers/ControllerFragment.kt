package app.blinkers

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Color
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
import app.blinkers.databinding.ControllerFragBinding
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.Legend.LegendForm
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.components.YAxis.AxisDependency
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.*
import kotlin.coroutines.CoroutineContext

class ControllerFragment : Fragment(), CoroutineScope {

    private lateinit var setBrainData: (List<Float>) -> Unit
    private var socket: BluetoothSocket? = null;
    private val viewModel by viewModels<ControllerViewModel> { getViewModelFactory() }

    private val args: ControllerFragmentArgs by navArgs()
    private lateinit var items: List<Led>

    private lateinit var listAdapter: LedListAdapter

    private lateinit var chart: LineChart

    private lateinit var viewDataBinding: ControllerFragBinding

    private enum class Connected {
        False, Pending, True
    }

    private val BLUETOOTH_SPP =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

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
        viewDataBinding = ControllerFragBinding.inflate(inflater, container, false).apply {
            viewmodel = viewModel
        }

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

        initChart();

        return viewDataBinding.root
    }

    private fun initChart() {

        chart =  viewDataBinding.root.findViewById(R.id.chart1)

        // enable description text
        chart.getDescription().setEnabled(true)

        // enable touch gestures
        chart.setTouchEnabled(false)

        // enable scaling and dragging
        chart.setDragEnabled(true)
        chart.setScaleEnabled(true)
        chart.setDrawGridBackground(false)

        // if disabled, scaling can be done on x- and y-axis separately
        chart.setPinchZoom(true)

        // set an alternative background color
        chart.setBackgroundColor(Color.LTGRAY)

        val data = LineData()
        data.setValueTextColor(Color.WHITE)

        // add empty data
        chart.data = data

        val labels = listOf("Signal strength", "Delta", "Theta", "Low Alpha", "High Alpha", "Low Beta", "High Beta", "Low Gamma", "High Gamma")

        labels.forEach {
            chart.data.addDataSet(createSet(it))
        }

        setBrainData = { readings ->
            val timeGoneInSeconds = ( System.currentTimeMillis() - startTime ) / 1000F
            readings.mapIndexed { index, dataEntry -> chart.data.addEntry(Entry(timeGoneInSeconds, dataEntry), index) }

            chart.data.notifyDataChanged()

            // let the chart know it's data has changed
            chart.notifyDataSetChanged()

            // limit the number of visible entries
            chart.setVisibleXRangeMaximum(120f)
            // chart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            chart.moveViewToX(data.entryCount.toFloat())
        }



        // get the legend (only possible after setting data)
        val l: Legend = chart.legend

        // modify the legend ...
        l.form = LegendForm.LINE
        l.textColor = Color.WHITE

        val xl: XAxis = chart.xAxis
        xl.textColor = Color.WHITE
        xl.setDrawGridLines(false)
        xl.setAvoidFirstLastClipping(true)
        xl.isEnabled = true

        val leftAxis: YAxis = chart.axisLeft
        leftAxis.textColor = Color.WHITE
        leftAxis.axisMaximum = 100f
        leftAxis.axisMinimum = 0f
        leftAxis.setDrawGridLines(true)

        val rightAxis: YAxis = chart.axisRight
        rightAxis.isEnabled = false
    }

    private fun addEntry() {
        val data = chart.data
        if (data != null) {
            var set = data.getDataSetByIndex(0)
            // set.addEntry(...); // can be called as well
            if (set == null) {
                set = createSet("blah")
                data.addDataSet(set)
            }
            data.addEntry(
                Entry(
                    set.entryCount.toFloat(),
                    (Math.random() * 40).toFloat() + 30f
                ), 0
            )
            data.notifyDataChanged()

            // let the chart know it's data has changed
            chart.notifyDataSetChanged()

            // limit the number of visible entries
            chart.setVisibleXRangeMaximum(120f)
            // chart.setVisibleYRange(30, AxisDependency.LEFT);

            // move to the latest entry
            chart.moveViewToX(data.entryCount.toFloat())

            // this automatically refreshes the chart (calls invalidate())
            // chart.moveViewTo(data.getXValCount()-7, 55f,
            // AxisDependency.LEFT);
        }
    }

    private fun createSet(label: String, yVals: List<Entry>? = null, color: Int = ColorTemplate.getHoloBlue()): LineDataSet {
        val set = LineDataSet(yVals, label)
        set.axisDependency = AxisDependency.LEFT
        set.color = ColorTemplate.getHoloBlue()
        set.setCircleColor(Color.WHITE)
        set.lineWidth = 2f
        set.circleRadius = 4f
        set.fillAlpha = 65
        set.fillColor = ColorTemplate.getHoloBlue()
        set.highLightColor = Color.rgb(244, 117, 117)
        set.valueTextColor = Color.WHITE
        set.valueTextSize = 9f
        set.setDrawValues(false)
        return set
    }

    private var thread: Thread? = null

    private fun feedMultiple() {
        if (thread != null) thread!!.interrupt()
        val runnable = Runnable { addEntry() }
        thread = Thread(Runnable {
            for (i in 0..999) {

                // Don't generate garbage runnables inside the loop.
                activity?.runOnUiThread(runnable)
                try {
                    Thread.sleep(25)
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        })
        thread!!.start()
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

    override fun onResume() {
        super.onResume()
        if (initialStart) {
            initialStart = false
            requireActivity().runOnUiThread { connect() }
        }
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
            launch {
                try {
                    socket = device.createRfcommSocketToServiceRecord(BLUETOOTH_SPP)
                    socket?.apply {
                        connect()
                        viewModel.setRepo(BluetoothSocketRepo(this))
                        connected = Connected.True

                        activity?.runOnUiThread {
                            viewModel.readData.observe(viewLifecycleOwner, Observer {
                                if (it is Result.Success) receive(it.data)
                            })
                            viewModel.connect("Connected to $deviceName", device)
                            startTime = System.currentTimeMillis()
                        }
                    }
                } catch (e: Exception) {
                    Timber.d("EXCEPTION CONNECTING $e")
                    socket?.close()
                }
            }


         //   service!!.connect("Connected to $deviceName")
         //    socket!!.connect(context, viewModel, device)
        } catch (e: Exception) {
            status("connection failed: ${e.message}")
            disconnect()
        }
    }

    private fun disconnect() {
        connected = Connected.False
        socket?.let {
            it.close()
          //  it.disconnect()
            socket = null
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
            viewModel.writeData(data)
        } catch (e: Exception) {
            status("connection lost: ${e.message}")
            disconnect()
        }
    }

    private fun receive(data: BrainWaves) {
        with(data) {
            setBrainData(listOf(
                signalStrength,
                delta,
                theta,
                lowAlpha,
                highAlpha,
                lowBeta,
                highBeta,
                lowGamma,
                highGamma
            ))
        }

        receiveText.append("${data}\n")

//        when {
//            currentData.contains("!") -> {
//                brainRawData = currentData
//            }
//            currentData.contains("~") -> {
//                brainRawData += currentData
//
//                receiveText.append("$brainRawData\n")
//
//                val trimmedData = brainRawData.substringAfter("!").substringBefore("~");
//                val values = trimmedData.split(",").map {
//                    it.toInt() / 10000
//                }.filter { it > 0 }
//                setBrainData(values)
//                Timber.d("Received data is $brainRawData")
//                brainRawData = ""
//            }
//            else -> {
//                brainRawData += currentData
//            }
//        }
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
