package unitcounter.mod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 用基础 Box/Row/Column 拼出的简单图标，避免依赖 Canvas 的 DrawScope API
 * （compileOnly 下其签名解析不稳定）。
 */
internal object ModIcons {
    /** 工厂：底部厂房 + 右上烟囱。 */
    @Composable
    fun Factory(color: Color, iconSize: Dp = 20.dp) {
        Box(Modifier.size(iconSize)) {
            Box(Modifier.align(androidx.compose.ui.Alignment.BottomStart)
                .size(iconSize * 0.9f, iconSize * 0.5f)
                .background(color, RoundedCornerShape(2.dp)))
            Box(Modifier.align(androidx.compose.ui.Alignment.TopEnd)
                .size(iconSize * 0.18f, iconSize * 0.45f)
                .background(color))
        }
    }

    /** 单位色块。 */
    @Composable
    fun Unit(color: Color, iconSize: Dp = 12.dp) {
        Box(Modifier.size(iconSize).background(color, RoundedCornerShape(2.dp)))
    }

    /** 资源图标：菱形宝石，用于 HOI4 风格资源条。 */
    @Composable
    fun Resource(color: Color, iconSize: Dp = 12.dp) {
        Box(Modifier.size(iconSize), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Box(
                Modifier
                    .size(iconSize * 0.62f)
                    .rotate(45f)
                    .background(color, RoundedCornerShape(1.dp)),
            )
            Box(
                Modifier
                    .size(iconSize * 0.18f)
                    .background(Color(0xCCFFFFFF), RoundedCornerShape(1.dp)),
            )
        }
    }

    @Composable
    fun Play(color: Color, iconSize: Dp = 16.dp) {
        Box(Modifier.size(iconSize), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Triangle(color, iconSize)
        }
    }

    @Composable
    private fun Triangle(color: Color, iconSize: Dp) {
        // 用三个递减宽度的条形近似三角形
        Column(Modifier.width(iconSize), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            for (i in 1..4) {
                Box(Modifier.width(iconSize * (i / 4f)).height(iconSize / 5f).background(color))
            }
        }
    }

    @Composable
    fun Pause(color: Color, iconSize: Dp = 16.dp) {
        Row(Modifier.size(iconSize), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly) {
            Box(Modifier.width(iconSize * 0.22f).height(iconSize).background(color, RoundedCornerShape(1.dp)))
            Box(Modifier.width(iconSize * 0.22f).height(iconSize).background(color, RoundedCornerShape(1.dp)))
        }
    }

    @Composable
    fun Plus(color: Color, iconSize: Dp = 14.dp) {
        Box(Modifier.size(iconSize)) {
            Box(Modifier.align(androidx.compose.ui.Alignment.Center).width(iconSize).height(iconSize * 0.18f).background(color))
            Box(Modifier.align(androidx.compose.ui.Alignment.Center).width(iconSize * 0.18f).height(iconSize).background(color))
        }
    }

    @Composable
    fun Minus(color: Color, iconSize: Dp = 14.dp) {
        Box(Modifier.size(iconSize), contentAlignment = androidx.compose.ui.Alignment.Center) {
            Box(Modifier.width(iconSize).height(iconSize * 0.18f).background(color))
        }
    }

    @Composable
    fun Close(color: Color, iconSize: Dp = 14.dp) {
        Box(Modifier.size(iconSize)) {
            Box(Modifier.align(androidx.compose.ui.Alignment.Center)
                .size(iconSize * 0.16f, iconSize)
                .rotate(45f)
                .background(color))
            Box(Modifier.align(androidx.compose.ui.Alignment.Center)
                .size(iconSize * 0.16f, iconSize)
                .rotate(-45f)
                .background(color))
        }
    }

    /** 稳定地把字符串映射为颜色，用来区分不同单位类型。 */
    fun colorFor(id: String): Color {
        val hue = (id.hashCode() and 0x7FFFFFFF) % 360
        return Color.hsv(hue.toFloat(), 0.65f, 0.92f)
    }
}
