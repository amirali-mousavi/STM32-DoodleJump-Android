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


class MainActivity : ComponentActivity(),
    SerialInputOutputManager.Listener {

    private var errorStateFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private var charListStateFlow: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
    private var errorMessage: String = ""

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

        val list = mutableListOf<String>()
        for (i in 0..19) {
            list.add("-")
            list.add("-")
            list.add("-")
            list.add("-")
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
                    115200,
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
                    port?.readEndpoint?.maxPacketSize ?: -1,
                    onSwipe = {
                        if (it == 0) { // Right
                            port?.write("control-right----------".toByteArray(), 1000)
                        } else if (it == 1) { // Left
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
        data?.let {
            val charStrArray = String(it).trim().toCharArray()
            if (charStrArray.size != 160) {
                errorStateFlow.value = true
                errorMessage = "DataByteArray size is ${it.size}\nCharArray size is ${charStrArray.size}"
                return
            }
            errorStateFlow.value = false
            val charList = mutableListOf<String>()
            for (i in 0..157 step 2) {
                val charStr = charStrArray[i].toString() + charStrArray[i + 1].toString()
                when (charStr) {
                    GameChar.AIR -> charList.add("Air")
                    GameChar.BLACK_HOLE -> charList.add("Hole")
                    GameChar.MONSTER -> charList.add("Monster")
                    GameChar.NORMAL_STEP -> charList.add("NormalStep")
                    GameChar.BROKEN_STEP -> charList.add("BrokenStep")
                    GameChar.SPRINT_STEP -> charList.add("SprintStep")
                    GameChar.BULLET -> charList.add("Bullet")
                    GameChar.DOODLER_UP -> charList.add("DoodlerUp")
                    GameChar.DOODLER_DOWN -> charList.add("DoodlerDown")
                    else -> charList.add("UNKNOWN")
                }
            }
            charListStateFlow.value = charList
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

}

@ExperimentalAnimationApi
@ExperimentalUnitApi
@Composable
fun App(
    charList: List<String>,
    hasError: Boolean,
    errorMessage: String,
    bufferSize: Int = 0,
    onSwipe: (Int) -> Unit = {},
    onClick: () -> Unit = {}
) {
    var direction by remember { mutableStateOf(-1) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consumeAllChanges()
                        val (x, y) = dragAmount
                        when {
                            x > 0 -> {
                                // Right
                                direction = 0
                            }
                            x < 0 -> {
                                // Left
                                direction = 1
                            }
                        }
                    },
                    onDragEnd = {
                        when (direction) {
                            0 -> {
                                onSwipe(direction)
                            }
                            1 -> {
                                onSwipe(direction)
                            }
                        }
                    }
                )
            }
            .clickable {
                onClick()
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            for (row in 0..19) {
                Row(Modifier.weight(1f)) {
                    for (column in 0..3) {
                        Text(
                            text = charList[row * 4 + column],
                            Modifier.weight(1f),
                            fontSize = TextUnit(2f, TextUnitType.Em),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = hasError, Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x9A000000))
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text = "ERROR",
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(30.dp))

                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(30.dp))

                    Text(
                        text = bufferSize.toString(),
                        color = Color.Blue,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@ExperimentalAnimationApi
@ExperimentalUnitApi
@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    STM32DoodleJumpTheme {
        App(mutableListOf(), false, "")
    }
}