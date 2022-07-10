package com.mousavi.stm32doodlejump

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.mousavi.stm32doodlejump.ui.theme.STM32DoodleJumpTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.random.Random


class MainActivity : ComponentActivity(),
    SerialInputOutputManager.Listener {

    private var errorStateFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private var charListStateFlow: MutableStateFlow<List<Int>> = MutableStateFlow(emptyList())
    private var errorMessage: String = ""
    private var buffer = ""

    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

    private lateinit var manager: UsbManager
    private var port: UsbSerialPort? = null

    private val usbReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.apply {
                            this@MainActivity.device = this
                            //connectSerial()
                        }
                    } else {
                        Log.d("SEYED", "permission denied for device $device")
                    }
                }
            }
        }
    }

    private lateinit var device: UsbDevice

    @ExperimentalAnimationApi
    @ExperimentalUnitApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val list = mutableListOf<Int>()
        for (i in 0..79) {
            list.add(-1)
        }
        charListStateFlow.value = list

        val permissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        registerReceiver(usbReceiver, filter)


        manager = getSystemService(Context.USB_SERVICE) as UsbManager
        if (manager.deviceList.isNotEmpty()) {
            device = manager.deviceList.values.first()
            manager.requestPermission(device, permissionIntent)
        }

        val availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager)

        if (availableDrivers.size != 0) {
            // Open a connection to the first available driver.
            val driver = availableDrivers[0]
            val connection = manager.openDevice(driver.device)

            if (connection != null) {
                port = driver.ports[0] // Most devices have just one port (port 0)
                port!!.open(connection)
                port!!.setParameters(
                    230400,
                    UsbSerialPort.DATABITS_8,
                    UsbSerialPort.STOPBITS_1,
                    UsbSerialPort.PARITY_NONE
                )
                
                val usbIoManager = SerialInputOutputManager(port, this)
                usbIoManager.start()
            } else {
                errorStateFlow.value = true
                errorMessage = "Connection is null"
            }
        } else {
            errorStateFlow.value = true
            errorMessage = "No Available drivers"
        }

        setContent {
            STM32DoodleJumpTheme {
                val charList by charListStateFlow.collectAsState()
                val hasError by errorStateFlow.collectAsState()
                App(
                    charList,
                    hasError,
                    errorMessage,
                    onSwipe = {
                        if (it == 0) { // Right
                            Log.i("SEYED", "Swipe : RIGHT")
                            port?.write("control-right----------".toByteArray(), 1000)
                        } else if (it == 1) { // Left
                            Log.i("SEYED", "Swipe : LEFT")
                            port?.write("control-left-----------".toByteArray(), 1000)
                        }
                    },
                    onClick = {
                        port?.write("control-fire-----------".toByteArray(), 1000)
                    }
                )
            }
        }
    }

    override fun onNewData(data: ByteArray?) {
        runOnUiThread {
            handleReceivedData(data)
        }
    }

    override fun onRunError(e: Exception?) {
        e?.let {
            it.localizedMessage?.let { localizedMessage ->
                errorStateFlow.value = true
                errorMessage = localizedMessage
            }
        }
    }

    private fun handleReceivedData(data: ByteArray?) {
        data?.let {
            errorStateFlow.value = false
            buffer += String(it)
            if (buffer.length == 160) {
                val charList = mutableListOf<Int>()
                for (i in 0..158 step 2) {
                    when (buffer[i].toString() + buffer[i + 1].toString()) {
                        GameChar.AIR -> charList.add(-1)
                        GameChar.BLACK_HOLE -> charList.add(R.drawable.hole)
                        GameChar.MONSTER -> charList.add(R.drawable.monster)
                        GameChar.NORMAL_STEP -> charList.add(R.drawable.normal_step)
                        GameChar.BROKEN_STEP -> charList.add(R.drawable.broken_step)
                        GameChar.SPRINT_STEP -> charList.add(R.drawable.spring_step)
                        GameChar.BULLET -> charList.add(R.drawable.bullet)
                        GameChar.DOODLER_UP -> charList.add(R.drawable.doodler_up)
                        GameChar.DOODLER_DOWN -> charList.add(R.drawable.doodler_down)
                        else -> charList.add(-1)
                    }
                }
                charListStateFlow.value = charList
                buffer = ""
            }
            if (buffer.length > 160) {
                errorStateFlow.value = true
                errorMessage = "Buffer size is more than 160"
            }
        }
    }

}
