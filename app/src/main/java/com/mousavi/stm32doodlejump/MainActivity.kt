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
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.felhr.usbserial.UsbSerialDevice
import com.felhr.usbserial.UsbSerialInterface
import com.mousavi.stm32doodlejump.ui.theme.STM32DoodleJumpTheme
import kotlinx.coroutines.flow.MutableStateFlow


class MainActivity : ComponentActivity() {

    private val errorStateFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val charListStateFlow: MutableStateFlow<List<String>> = MutableStateFlow(emptyList())
    private var errorMessage: String = ""
    private var dataStringStateFlow: MutableStateFlow<String> = MutableStateFlow("")
    private val buffer: MutableList<String> = mutableListOf()
    private var dataPartIndex = 0;

    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

    private lateinit var manager: UsbManager
    private var device: UsbDevice? = null
    private var serial: UsbSerialDevice? = null

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

        if (device != null) {
            // Open a connection to the first available driver.
            val connection = manager.openDevice(device)

            if (connection != null) {
                serial = UsbSerialDevice.createUsbSerialDevice(device, connection)
                serial!!.open()
                serial!!.setBaudRate(230400)
                serial!!.setDataBits(UsbSerialInterface.DATA_BITS_8)
                serial!!.setStopBits(UsbSerialInterface.STOP_BITS_1)
                serial!!.setParity(UsbSerialInterface.PARITY_NONE)
                serial!!.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF)
                serial!!.read {
                    handleReceiveData(it)
                }

            } else {
                errorStateFlow.value = true
                errorMessage = "Connection is null"
            }
        } else {
            errorStateFlow.value = true
            errorMessage = "No Available device"
        }

        setContent {
            STM32DoodleJumpTheme {
                val charList by charListStateFlow.collectAsState()
                val hasError by errorStateFlow.collectAsState()
                val dataString by dataStringStateFlow.collectAsState()
                App(
                    charList,
                    hasError,
                    errorMessage,
                    dataString,
                    onSwipe = {
                        if (it == 0) { // Right
                            serial?.write("control-right----------".toByteArray())
                        } else if (it == 1) { // Left
                            serial?.write("control-left-----------".toByteArray())
                        }
                    },
                    onClick = {
                        serial?.write("control-fire-----------".toByteArray())
                    }
                )
            }
        }
    }

    private fun handleReceiveData(data: ByteArray?) {
        runOnUiThread {
            data?.let {
                dataStringStateFlow.value = String(it).trim()
                val charStrArray = String(it).trim().toCharArray()
                if (charStrArray.size != 41) {
                    errorStateFlow.value = true
                    errorMessage =
                        "DataByteArray size is ${it.size}\nCharArray size is ${charStrArray.size}"
                    return@let
                }
                if (charStrArray.last().digitToInt() != dataPartIndex) {
                    errorStateFlow.value = true
                    errorMessage = "Expect part $dataPartIndex of data, but receive ${charStrArray.last()}"
                    dataPartIndex = 0
                    buffer.clear()
                    return@let
                }

                errorStateFlow.value = false

                if (dataPartIndex == 0)
                    buffer.clear()

                val charList = mutableListOf<String>()
                for (i in 0..37 step 2) {
                    when (charStrArray[i].toString() + charStrArray[i + 1].toString()) {
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

                buffer.addAll(charList)

                if (dataPartIndex == 3) {
                    charListStateFlow.value = charList
                    dataPartIndex = -1
                }

                dataPartIndex++
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
    dataString: String,
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
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "ERROR",
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = dataString,
                        color = Color.Blue,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
