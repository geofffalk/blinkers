package app.blinkers

import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateFormat.format
import android.view.*
import androidx.core.content.ContextCompat.getSystemService
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.navArgs
import app.blinkers.data.Analysis
import app.blinkers.data.source.DefaultDeviceCommunicator
import app.blinkers.databinding.ControllerFragBinding
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import kotlinx.android.synthetic.main.controller_frag.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.apache.commons.math3.linear.BlockRealMatrix
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation
import java.io.File
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

        viewDataBinding.root.keepScreenOn = true

         arrayOf(
            viewDataBinding.root.phase0picker,
            viewDataBinding.root.phase1picker,
            viewDataBinding.root.phase2picker,
            viewDataBinding.root.phase3picker
        ).forEach {
             it.maxValue = 6
             it.minValue = 0
             it.displayedValues = (0..60 step 10).toList().map { num -> "$num s" }.toTypedArray()
         }

        with(viewDataBinding.root.repeatPicker) {
            maxValue = 59
            minValue = 0
            displayedValues = (0..59).toMutableList().map { num -> if (num == 0) "NO REPEAT" else if (num == 1) "1 min" else "$num mins"}.toTypedArray()
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
            R.id.export_analysis -> {
                exportAnalysisToCSVFile()
                true
            }
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

    private fun exportAnalysisToCSVFile() {
        launch {
            val csvFile = File(context?.filesDir, "analysis_data.csv")
            csvFile.createNewFile()
            val analysis =
                withContext(Dispatchers.IO) { viewModel.getAnalysis() }

            analysis?.let {

                val analysisMatrix: RealMatrix = analysis.toRealMatrix()

                csvWriter().open(csvFile, append = false) {
                    writeRow(
                        listOf(
                            "Date",
                            "Timestamp",
                            "Valence",
                            "Arousal",
                            "Dominance",
                            "Signal strength",
                            "Delta",
                            "Theta",
                            "lowAlpha",
                            "highAlpha",
                            "lowBeta",
                            "highBeta",
                            "lowGamma",
                            "highGamma"
                        )
                    )

                    analysisMatrix.data.iterator().forEach {
                        writeRow(listOf(
                            format("EEE, d MMM HH:mm:ss", it[0].toLong()), it[0].toLong(), it[1], it[2], it[3], it[4], it[5], it[6], it[7], it[8], it[9], it[10], it[11], it[12]))
                    }

                    val pearsons = PearsonsCorrelation(analysisMatrix)
                    val correlations = (5..12).map {
                        pearsons.correlation(analysisMatrix.getColumn(1), analysisMatrix.getColumn(it))
                    }

                    writeRow(emptyList())
                    writeRow("Stats analysis")
                    writeRow(emptyList())
                    writeRow(
                        listOf(
                            "",
                            "",
                            "",
                            "",
                            "",
                            "Valence Correlations: ",
                            correlations[0],
                            correlations[1],
                            correlations[2],
                            correlations[3],
                            correlations[4],
                            correlations[5],
                            correlations[6],
                            correlations[7]
                        )
                    )
                    writeRow(emptyList())
                    writeRow("CORRELATION MATRIX")
                    writeRow(emptyList())

                    pearsons.correlationMatrix.data.forEach {
                        writeRow(it.toList())
                    }

                    writeRow(emptyList())
                    writeRow("CORRELATION P VALUES")
                    writeRow(emptyList())

                    pearsons.correlationPValues.data.forEach {
                        writeRow(it.toList())
                    }

                }
                val intent = goToFileIntent(requireContext(), csvFile)
                startActivity(intent)
            }
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
//            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
//            val device = bluetoothAdapter.getRemoteDevice(deviceAddress)
            connected = Connected.Pending
            deviceAddress?.let  {
                DefaultDeviceCommunicator.connect(
                    this@ControllerFragment.requireContext(),
                    it
                )
            }
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

private fun List<Analysis>.toRealMatrix(): RealMatrix {
    val matrix = mutableListOf<DoubleArray>()
    if (isEmpty()) return BlockRealMatrix(0, 0)

    for (index in indices) {
        matrix.add(this[index].toDoubleArray())
    }

    return BlockRealMatrix(matrix.toTypedArray())
}

fun Fragment.getViewModelFactory(): ViewModelFactory {
    val repository = (requireContext().applicationContext as BlinkersApp).blinkersRepository
    return ViewModelFactory(repository, this)
}
