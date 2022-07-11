package com.mousavi.stm32doodlejump

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.lifecycle.lifecycleScope
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import com.mousavi.stm32doodlejump.ui.theme.STM32DoodleJumpTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.util.*


class MainActivity : ComponentActivity(),
    SerialInputOutputManager.Listener {

    private val charListStateFlow: MutableStateFlow<List<Int>> = MutableStateFlow(emptyList())
    private val loseStateFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private val errorStateFlow: MutableStateFlow<Boolean> = MutableStateFlow(false)
    private var errorMessage: String = ""
    private var gameScore: Int = 0
    private var gameDifficulty = 0
    private var buffer: String = ""
    private var bufferSize: Int = 168
    private var receiveDataType: ReceiveDataType = ReceiveDataType.GAME_SCREEN

    private val ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION"

    private lateinit var manager: UsbManager
    private lateinit var device: UsbDevice
    private var port: UsbSerialPort? = null

    private lateinit var sharedPref: SharedPreferences

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

        sharedPref = getSharedPreferences("doodler-data-save", MODE_PRIVATE)

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

                sendTime()
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
                val isLost by loseStateFlow.collectAsState()
                AppScreen(
                    charList,
                    hasError,
                    errorMessage,
                    isLost,
                    gameScore,
                    gameDifficulty,
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
                        Log.i("SEYED", "CLICKED")
                    }
                )
            }
        }
    }

    private fun sendTime() {
        lifecycleScope.launch(Dispatchers.IO) {
            while (true) {
                port?.let {
                    val calendar = Calendar.getInstance()
                    val year = calendar.get(Calendar.YEAR) % 2000
                    val month = if (calendar.get(Calendar.MONTH) + 1 > 9) calendar.get(Calendar.MONTH) + 1 else "0${calendar.get(Calendar.MONTH) + 1}"
                    val day = if (calendar.get(Calendar.DAY_OF_MONTH) > 9) calendar.get(Calendar.DAY_OF_MONTH) else "0${calendar.get(Calendar.DAY_OF_MONTH)}"
                    val hour = if (calendar.get(Calendar.HOUR_OF_DAY) > 9) calendar.get(Calendar.HOUR_OF_DAY) else "0${calendar.get(Calendar.HOUR_OF_DAY)}"
                    val min = if (calendar.get(Calendar.MINUTE) > 9) calendar.get(Calendar.MINUTE) else "0${calendar.get(Calendar.MINUTE)}"
                    val sec = if (calendar.get(Calendar.SECOND) > 9) calendar.get(Calendar.SECOND) else "0${calendar.get(Calendar.SECOND)}"
                    val dataStr = "$year/$month/$day-$hour:$min:$sec"
                    it.write("clock-$dataStr".toByteArray(), 1000)
                }
                delay(30000)
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

            val dataStr = String(it)
            if (buffer.isEmpty()) {
                when (dataStr[0].digitToInt()) {
                    0 -> {
                        bufferSize = 166
                        receiveDataType = ReceiveDataType.GAME_SCREEN
                    }
                    1 -> {
                        bufferSize = 416
                        receiveDataType = ReceiveDataType.SAVE
                    }
                    2 -> {
                        bufferSize = 13
                        receiveDataType = ReceiveDataType.LOAD
                    }
                }
            }

            buffer += dataStr

            if (buffer.length == bufferSize) {
                if (receiveDataType == ReceiveDataType.GAME_SCREEN) {
                    updateGameScreen()
                }
                else if (receiveDataType == ReceiveDataType.SAVE) {
                    saveData()
                }
                else if (receiveDataType == ReceiveDataType.LOAD) {
                    loadData()
                }

                buffer = ""
            }

            if (buffer.length > bufferSize) {
                errorStateFlow.value = true
                errorMessage = "Buffer size is more than $bufferSize"
            }
        }
    }

    private fun updateGameScreen() {
        val charList = mutableListOf<Int>()
        for (i in 1..159 step 2) {
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
                GameChar.DIZZY_DOODLER_UP -> charList.add(R.drawable.dizzy_doodler_up)
                else -> charList.add(-1)
            }
        }

        gameScore = buffer.substring(161, 164).toInt()
        gameDifficulty = buffer[164].digitToInt()

        if (buffer[165].digitToInt() == 1) { // Lose
            charList.clear()
            for (i in 0..79) charList.add(-1)
            loseStateFlow.value = true
        }
        else
            loseStateFlow.value = false

        charListStateFlow.value = charList
    }

    private fun saveData() {
        sharedPref
            .edit()
            .putString("data", buffer)
            .apply()
    }

    private fun loadData() {
        if (sharedPref.contains("data")) {
            val dataStr = sharedPref.getString("data", "")
            lifecycleScope.launch(Dispatchers.IO) {
                port?.write("load-approve-----------".toByteArray(), 1000)
                delay(10)
                port?.write("loading-$dataStr".toByteArray(), 1000)
            }
        }
    }
}
