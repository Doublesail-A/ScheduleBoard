package com.doublesail.scheduleboard.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/** Timer above the board: drag down to reveal it, drag up to return. */
@Composable
fun BoardPager(state: PagerState, dashboard: @Composable () -> Unit, timer: @Composable () -> Unit) {
    VerticalPager(state, modifier = Modifier.fillMaxSize(), reverseLayout = true, beyondViewportPageCount = 1) { page ->
        if (page == 0) dashboard() else timer()
    }
}

/** The task list owns vertical gestures even at its first/last row. */
@Composable
fun Modifier.independentTaskScroll(): Modifier = nestedScroll(remember {
    object : NestedScrollConnection {
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource) = Offset(0f, available.y)
        override suspend fun onPostFling(consumed: Velocity, available: Velocity) = Velocity(0f, available.y)
    }
})
