package app.blinkers

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothAdapter.LeScanCallback
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.ListFragment
import androidx.navigation.fragment.findNavController
import timber.log.Timber
import java.util.*

class DevicesFragment: ListFragment() {

    companion object {
        /**
         * Request code for location permission request.
         *
         * @see .onRequestPermissionsResult
         */
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val listItems = ArrayList<BluetoothDevice>()
    private lateinit var listAdapter: ArrayAdapter<BluetoothDevice>
    private var isScanning = false
    private lateinit var handler: Handler

    private val REQUEST_ENABLE_BT = 1

    // Stops scanning after 10 seconds.
    private val SCAN_PERIOD: Long = 20000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handler = Handler()
        setHasOptionsMenu(true)
        if (requireActivity().packageManager
                .hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        ) bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        listAdapter = object : ArrayAdapter<BluetoothDevice>(requireActivity(), 0, listItems) {
            override fun getView(
                position: Int,
                view: View?,
                parent: ViewGroup
            ): View {
                var view = view
                val device = listItems[position]
                if (view == null) view = activity!!.layoutInflater
                    .inflate(R.layout.device_list_item, parent, false)
                val text1 = view!!.findViewById<TextView>(R.id.text1)
                val text2 = view.findViewById<TextView>(R.id.text2)
                text1.text = device.name
                text2.text = device.address
                return view
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        setListAdapter(null)
        val header: View =
            requireActivity().layoutInflater.inflate(R.layout.device_list_header, null, false)
        listView.addHeaderView(header, null, false)
        setEmptyText("initializing...")
        (listView.emptyView as TextView).textSize = 18f
        setListAdapter(listAdapter)

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            // Permissio n to access the location is missing. Show rationale and request permission
            PermissionUtils.requestPermission(
                requireActivity() as AppCompatActivity, LOCATION_PERMISSION_REQUEST_CODE,
                Manifest.permission.ACCESS_FINE_LOCATION, true
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != LOCATION_PERMISSION_REQUEST_CODE) {
            return
        }
        if (PermissionUtils.isPermissionGranted(
                permissions,
                grantResults,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        ) {
            refresh()
        } else {
            // Permission was denied. Display an error message
            // [START_EXCLUDE]
            // Display the missing permission error dialog when the fragments resume.
            // permissionDenied = true
            // [END_EXCLUDE]
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_devices, menu)
        if (bluetoothAdapter == null) menu.findItem(R.id.bt_settings).isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        if (bluetoothAdapter == null) setEmptyText("<bluetooth not supported>") else if (!bluetoothAdapter!!.isEnabled) { setEmptyText(
            "<bluetooth is disabled>")
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(
                enableBtIntent,
                REQUEST_ENABLE_BT
            )} else setEmptyText("<no bluetooth devices found>")
        refresh()
    }

    override fun onPause() {
        super.onPause()
        scanLeDevice(false)
        listAdapter.clear()
    }

    private fun scanLeDevice(enable: Boolean) {

        if (enable) {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed({
                isScanning = false
                bluetoothAdapter?.stopLeScan(mLeScanCallback)
            }, SCAN_PERIOD)
            isScanning = true
            bluetoothAdapter?.startLeScan(mLeScanCallback)
        } else {
            isScanning = false
            bluetoothAdapter?.stopLeScan(mLeScanCallback)
        }
    }

    // Device scan callback.
    private val mLeScanCallback =
        LeScanCallback { device : BluetoothDevice, _, _ ->
            Timber.d("DEVICE FOUND")
            requireActivity().runOnUiThread(Runnable {
                var deviceAlreadyExists = false
                for (i in 0 until listAdapter.count) {
                    if (listAdapter.getItem(i)?.name.equals(device.name)) {
                        deviceAlreadyExists = true
                        break
                    }
                }
                if (!deviceAlreadyExists) {
                    listAdapter.add(device)
                    listAdapter.notifyDataSetChanged()
                }
            })
        }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        return if (id == R.id.bt_settings) {
            val intent = Intent()
            intent.action = Settings.ACTION_BLUETOOTH_SETTINGS
            startActivity(intent)
            true
        } else {
            super.onOptionsItemSelected(item)
        }
    }

override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        // User chose not to enable Bluetooth.
        if (requestCode == REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
           // finish()
            return
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    fun refresh() {
        listItems.clear()
        if (bluetoothAdapter != null) {
            for (device in bluetoothAdapter!!.bondedDevices) if (device.type != BluetoothDevice.DEVICE_TYPE_LE) listItems.add(
                device
            )
        }
        Collections.sort(listItems,
            Comparator { obj: BluetoothDevice, a: BluetoothDevice ->
                obj.compareTo(a) }
        )
        listAdapter!!.notifyDataSetChanged()
        scanLeDevice(true)
    }

    override fun onListItemClick(l: ListView, v: View, position: Int, id: Long) {
        val device = listItems[position - 1]
        val args = Bundle()
        args.putString("device", device.address)
        findNavController().navigate(R.id.action_devicesFragment_to_controllerFragment, args)
    }

    /**
     * sort by name, then address. sort named devices first
     */
operator fun BluetoothDevice.compareTo(a: BluetoothDevice): Int {
        val aValid = a.name != null && !a.name.isEmpty()
        val bValid = name != null && !name.isEmpty()
        if (aValid && bValid) {
            val ret = a.name.compareTo(name)
            return if (ret != 0) ret else a.address.compareTo(address)
        }
        if (aValid) return -1
        return if (bValid) +1 else a.address.compareTo(address)
    }
}