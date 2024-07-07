import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var messageEditText: EditText
    private lateinit var sendButton: Button
    private lateinit var messagesListView: ListView
    private lateinit var messageAdapter: ArrayAdapter<String>

    private val messages = mutableListOf<String>()
    private var connectedThread: ConnectedThread? = null

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_PERMISSION_BLUETOOTH = 2
        private val MY_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        messageEditText = findViewById(R.id.messageEditText)
        sendButton = findViewById(R.id.sendButton)
        messagesListView = findViewById(R.id.messagesListView)

        messageAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, messages)
        messagesListView.adapter = messageAdapter

        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            finish()
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH), REQUEST_PERMISSION_BLUETOOTH)
        }

        sendButton.setOnClickListener {
            val message = messageEditText.text.toString()
            sendMessage(message)
        }

        AcceptThread().start()
    }

    private fun sendMessage(message: String) {
        connectedThread?.write(message.toByteArray())
        messages.add("Me: $message")
        messageAdapter.notifyDataSetChanged()
        messageEditText.text.clear()
    }

    private inner class AcceptThread : Thread() {
        private val serverSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            bluetoothAdapter.listenUsingInsecureRfcommWithServiceRecord("ChatApp", MY_UUID)
        }

        override fun run() {
            var shouldLoop = true
            while (shouldLoop) {
                val socket: BluetoothSocket? = try {
                    serverSocket?.accept()
                } catch (e: IOException) {
                    shouldLoop = false
                    null
                }
                socket?.also {
                    manageConnectedSocket(it)
                    serverSocket?.close()
                    shouldLoop = false
                }
            }
        }
    }

    private fun manageConnectedSocket(socket: BluetoothSocket) {
        connectedThread = ConnectedThread(socket)
        connectedThread?.start()
    }

    private inner class ConnectedThread(private val socket: BluetoothSocket) : Thread() {
        private val inputStream: InputStream = socket.inputStream
        private val outputStream: OutputStream = socket.outputStream
        private val buffer: ByteArray = ByteArray(1024)

        override fun run() {
            var numBytes: Int
            while (true) {
                numBytes = try {
                    inputStream.read(buffer)
                } catch (e: IOException) {
                    break
                }
                val receivedMessage = String(buffer, 0, numBytes)
                runOnUiThread {
                    messages.add("Other: $receivedMessage")
                    messageAdapter.notifyDataSetChanged()
                }
            }
        }

        fun write(bytes: ByteArray) {
            try {
                outputStream.write(bytes)
            } catch (e: IOException) {
                // Handle the error
            }
        }
    }
}
