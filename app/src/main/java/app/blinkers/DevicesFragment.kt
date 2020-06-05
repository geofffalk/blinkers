package app.blinkers

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.*
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.fragment.app.ListFragment
import androidx.navigation.fragment.findNavController
import java.util.*

class DevicesFragment: ListFragment() {

    private var bluetoothAdapter: BluetoothAdapter? = null
    private val listItems = ArrayList<BluetoothDevice>()
    private var listAdapter: ArrayAdapter<BluetoothDevice>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        setListAdapter(null)
        val header: View =
            requireActivity().layoutInflater.inflate(R.layout.device_list_header, null, false)
        listView.addHeaderView(header, null, false)
        setEmptyText("initializing...")
        (listView.emptyView as TextView).textSize = 18f
        setListAdapter(listAdapter)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_devices, menu)
        if (bluetoothAdapter == null) menu.findItem(R.id.bt_settings).isEnabled = false
    }

    override fun onResume() {
        super.onResume()
        if (bluetoothAdapter == null) setEmptyText("<bluetooth not supported>") else if (!bluetoothAdapter!!.isEnabled) setEmptyText(
            "<bluetooth is disabled>"
        ) else setEmptyText("<no bluetooth devices found>")
        refresh()
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

    fun refresh() {
        listItems.clear()
        if (bluetoothAdapter != null) {
            for (device in bluetoothAdapter!!.bondedDevices) if (device.type != BluetoothDevice.DEVICE_TYPE_LE) listItems.add(
                device
            )
        }
        Collections.sort(listItems,
            Comparator { obj: BluetoothDevice, a: BluetoothDevice ->
                obj.compareTo(
                    a
                )
            }
        )
        listAdapter!!.notifyDataSetChanged()
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