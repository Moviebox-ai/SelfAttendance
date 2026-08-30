package com.aaryo.selfattendance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// caused BOTH the frosted background AND the content() lambda to be blurred,
// making all text and icons inside the card appear fuzzy/unreadable.
//
//   • Inner background Box: carries blur() + background() — only the tinted
//     white layer is blurred, producing the frosted-glass look.
//   • Outer content Box: no blur — content() renders sharp on top.
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        // Blurred frosted-glass background layer only
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(10.dp)
                .background(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp)
                )
        )
        // Sharp content layer — no blur applied here
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
