package com.pausiar.openfy.presentation.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Material shape scale: rounded but not bubbly (8 / 12 / 16 / 20 / 28). */
val OpenfyMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Semantic shapes for Openfy components. */
object OpenfyShapes {
    val artworkSmall = RoundedCornerShape(10.dp) // list rows, mini player
    val artwork = RoundedCornerShape(14.dp) // tiles, cards
    val artworkLarge = RoundedCornerShape(20.dp) // hero, player
    val card = RoundedCornerShape(16.dp)
    val cardLarge = RoundedCornerShape(20.dp)
    val field = RoundedCornerShape(14.dp)
    val row = RoundedCornerShape(12.dp)
    val miniPlayer = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = CircleShape
}
