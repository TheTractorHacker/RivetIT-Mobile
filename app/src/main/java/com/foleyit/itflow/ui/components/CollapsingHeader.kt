package com.foleyit.itflow.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * A header (search, filters, tabs) that slides away as the list below scrolls down and comes straight back when it
 * scrolls up, so lists get the whole screen while you read and the controls are one flick away. The slide is done in the
 * layout phase, so it costs no recomposition per frame.
 */
@Composable
fun CollapsingHeaderColumn(
    modifier: Modifier = Modifier,
    header: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = remember { HeaderSlide() }
    Column(modifier.nestedScroll(state.connection)) {
        Column(
            Modifier
                .clipToBounds()
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(constraints)
                    state.height = placeable.height.toFloat()
                    val shift = state.offset.coerceIn(-state.height, 0f).roundToInt()
                    layout(placeable.width, (placeable.height + shift).coerceAtLeast(0)) { placeable.place(0, shift) }
                },
            content = header,
        )
        content()
    }
}

private class HeaderSlide {
    var height by mutableFloatStateOf(0f)
    var offset by mutableFloatStateOf(0f)

    /** Moves the header with the finger before the list scrolls, and takes only the part of the drag it uses. */
    val connection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val next = (offset + available.y).coerceIn(-height, 0f)
            val used = next - offset
            offset = next
            return Offset(0f, used)
        }
    }
}
