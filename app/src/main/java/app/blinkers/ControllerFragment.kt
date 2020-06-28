package app.blinkers

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat.format
import android.view.*
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import app.blinkers.data.source.DefaultDeviceCommunicator
import app.blinkers.databinding.ControllerFragBinding
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext

class ControllerFragment : Fragment(), CoroutineScope {

    private var btSocket: BluetoothSocket? = null;
    private val viewModel by viewModels<ControllerViewModel> { getViewModelFactory() }

    private val args: ControllerFragmentArgs by navArgs()

    private lateinit var viewDataBinding: ControllerFragBinding

    private enum class Connected {
        False, Pending, True
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    private var deviceAddress: String? = null

    private var initialStart = true
    private var connected = Connected.False

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
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
        return viewDataBinding.root
    }


    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewDataBinding.lifecycleOwner = this.viewLifecycleOwner
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_controller, menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.export_device_data -> {
                exportEEGDatabaseToCSVFile()
                true
            }
            R.id.export_emotion -> {
                exportEmotionDatabaseToCSVFile()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun exportEmotionDatabaseToCSVFile() {
        launch {
            val csvFile = File(context?.filesDir, "emotional_data.csv")
            csvFile.createNewFile()
            val emotionData =
                withContext(Dispatchers.IO) { viewModel.getEmotionData() }
            csvWriter().open(csvFile, append = false) {
                writeRow(listOf("Date", "Timestamp", "Valence", "Arousal", "Dominance"))
                        emotionData?.forEach {
                        writeRow(listOf(format("EEE, d MMM HH:mm:ss", it.timestamp), it.timestamp, it.valence, it.arousal, it.dominance))
                }
            }
            val intent = goToFileIntent(requireContext(), csvFile)
            startActivity(intent)
        }
    }

    private fun exportEEGDatabaseToCSVFile() {
        launch {
            val csvFile = File(context?.filesDir, "eeg_data.csv")
            csvFile.createNewFile()
            val deviceData =
                withContext(Dispatchers.IO) { viewModel.getDeviceData() }
            csvWriter().open(csvFile, append = false) {
                writeRow(listOf("Date", "Timestamp", "Signal strength", "Delta", "Theta", "lowAlpha", "highAlpha", "lowBeta", "highBeta", "lowGamma", "highGamma"))
                deviceData?.forEach {
                    it.eegSnapshot?.let { eeg ->
                    writeRow(listOf(format("EEE, d MMM HH:mm:ss", it.timestamp), it.timestamp, eeg.signalStrength, eeg.delta, eeg.theta, eeg.lowAlpha, eeg.highAlpha, eeg.lowBeta, eeg.highBeta, eeg.lowGamma, eeg.highGamma))
                    }
                }
            }
            val intent = goToFileIntent(requireContext(), csvFile)
            startActivity(intent)
        }
    }

    private fun goToFileIntent(context: Context, file: File): Intent {
        val intent = Intent(Intent.ACTION_VIEW)
        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = context.contentResolver.getType(contentUri)
        intent.setDataAndType(contentUri, mimeType)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        return intent
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

    /*
     * Serial + UI
     */
    private fun connect() {
        try {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            connected = Connected.Pending
            DefaultDeviceCommunicator.connect(this@ControllerFragment.requireContext(), device)
            connected = Connected.True
        } catch (e: Exception) {
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
}

fun Fragment.getViewModelFactory(): ViewModelFactory {
    val repository = (requireContext().applicationContext as BlinkersApp).blinkersRepository
    return ViewModelFactory(repository, this)
}
