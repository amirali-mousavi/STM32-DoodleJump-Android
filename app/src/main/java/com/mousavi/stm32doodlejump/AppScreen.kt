package com.mousavi.stm32doodlejump

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.ExperimentalUnitApi
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@ExperimentalAnimationApi
@ExperimentalUnitApi
@Composable
fun App(
    charList: List<Int>,
    hasError: Boolean,
    errorMessage: String,
    onSwipe: (Int) -> Unit = {},
    onClick: () -> Unit = {}
) {
    var direction by remember { mutableStateOf(-1) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF7E5AB))
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
                        GameScreenCell(
                            imageId = charList[row * 4 + column],
                            modifier = Modifier.fillMaxHeight().weight(1f)
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = false, Modifier.fillMaxSize()) {
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
                }
            }
        }
    }
}

@Composable
fun GameScreenCell(
    imageId: Int,
    modifier: Modifier
) {
    Box(modifier = modifier) {
        if (imageId != -1) {
            Image(
                painter = painterResource(id = imageId),
                contentDescription = "",
            )
        }
    }
}