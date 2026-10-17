package ankhsquirrel.homescreener.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

/** Adaptive icon layers are 108dp with a 72dp visible area, hence the 1.5x zoom. */
private const val ADAPTIVE_ZOOM = 108f / 72f

/** Previews a generated adaptive icon roughly the way a circular-mask launcher shows it. */
@Composable
fun ShortcutIcon(icon: Bitmap?, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (icon != null) {
            val image = remember(icon) { icon.asImageBitmap() }
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = ADAPTIVE_ZOOM
                        scaleY = ADAPTIVE_ZOOM
                    },
            )
        }
    }
}
