package com.doublesail.scheduleboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@Composable
fun DisplayRestOverlay(onPeek: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)
        .semantics { contentDescription = "上课休屏，点击临时查看两分钟" }
        .clickable(onClickLabel = "临时查看两分钟", onClick = onPeek))
}
