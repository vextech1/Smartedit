package com.vextech.smart_edit.feature.onboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.vextech.smart_edit.R
import kotlin.math.roundToInt

private val DeepTeal = Color(0xFF0A4551)
private val MidTeal = Color(0xFF0F5660)
private val GlowTeal = Color(0xFF2C8F94)
private val Cream = Color(0xFFF4F1C9)
private val ButtonBlue = Color(0xFF5DB9E8)
private val ButtonText = Color(0xFF06324A)

private const val BADGE_WIDTH_DP = 46
private const val BADGE_HEIGHT_DP = 58

private class OnboardingPage(val title: Int, val body: Int)

private val pages = listOf(
    OnboardingPage(R.string.onboarding_p1_title, R.string.onboarding_p1_body),
    OnboardingPage(R.string.onboarding_p2_title, R.string.onboarding_p2_body),
    OnboardingPage(R.string.onboarding_p3_title, R.string.onboarding_p3_body),
)

/**
 * First-launch screen: deep teal with a soft glow, drifting document icons, three swipeable pages and one
 * "Get started" button. Finishing it stores the flag; later launches go straight to the app.
 */
@Composable
fun OnboardingScreen(onComplete: () -> Unit, vm: OnboardingViewModel = hiltViewModel()) {
    LightSystemBarIcons()
    val pagerState = rememberPagerState(pageCount = { pages.size })
    Box(
        Modifier
            .fillMaxSize()
            .background(DeepTeal)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(GlowTeal.copy(alpha = 0.75f), MidTeal, DeepTeal),
                        center = Offset(size.width * OnboardingScatter.GLOW_X, size.height * OnboardingScatter.GLOW_Y),
                        radius = size.width * 1.1f,
                    )
                )
            },
    ) {
        ScatterLayer(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().systemBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1.25f))
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxWidth()) { index -> PageText(pages[index]) }
            Spacer(Modifier.weight(0.7f))
            PageDots(current = pagerState.currentPage, count = pages.size)
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { vm.complete(onComplete) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ButtonBlue, contentColor = ButtonText),
            ) {
                Text(stringResource(R.string.onboarding_get_started), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PageText(page: OnboardingPage) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(page.title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = Cream,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(page.body),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White.copy(alpha = 0.92f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    val description = stringResource(R.string.onboarding_page_indicator, current + 1, count)
    Row(Modifier.semantics { contentDescription = description }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (index == current) Color.White else Color.White.copy(alpha = 0.35f)),
            )
        }
    }
}

@Composable
private fun ScatterLayer(modifier: Modifier) {
    // Honour the system "remove animations" setting.
    val context = LocalContext.current
    val animate = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
    BoxWithConstraints(modifier.clearAndSetSemantics { }) {
        val widthPx = constraints.maxWidth
        val heightPx = constraints.maxHeight
        OnboardingScatter.items.forEachIndexed { index, item -> FloatingDoc(item, index, widthPx, heightPx, animate) }
    }
}

@Composable
private fun FloatingDoc(item: ScatterItem, index: Int, widthPx: Int, heightPx: Int, animate: Boolean) {
    val density = LocalDensity.current
    val badgeWidthPx = with(density) { BADGE_WIDTH_DP.dp.toPx() }
    val badgeHeightPx = with(density) { BADGE_HEIGHT_DP.dp.toPx() }
    val bobPx = with(density) { 6.dp.toPx() }
    val bob: State<Float> = if (animate) {
        rememberInfiniteTransition().animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3200 + (index % 5) * 400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
        )
    } else {
        remember { mutableStateOf(0f) }
    }
    Box(
        Modifier
            .offset { IntOffset((widthPx * item.x - badgeWidthPx / 2f).roundToInt(), (heightPx * item.y - badgeHeightPx / 2f).roundToInt()) }
            .size(BADGE_WIDTH_DP.dp, BADGE_HEIGHT_DP.dp)
            .graphicsLayer {
                rotationZ = item.rotation
                scaleX = item.scale
                scaleY = item.scale
                alpha = item.alpha
                translationY = bob.value * bobPx * item.scale
            },
    ) {
        DocBadge(item.kind)
    }
}

@Composable
private fun DocBadge(kind: DocKind) {
    when (kind) {
        DocKind.IMAGE -> IconBadge(Icons.Rounded.Image, Color(0xFFBFE3F7), Color(0xFF2F7FB0))
        DocKind.FOLDER -> IconBadge(Icons.Rounded.Folder, Color(0xFFF4D98B), Color(0xFFC8921A))
        DocKind.PDF -> LabelBadge("PDF", Color(0xFFE25141))
        DocKind.DOC -> LabelBadge("DOC", Color(0xFF3B78D8))
        DocKind.PPT -> LabelBadge("PPT", Color(0xFFE8833A))
        DocKind.XLS -> LabelBadge("XLS", Color(0xFF2E9D5B))
        DocKind.TXT -> LabelBadge("TXT", Color(0xFF7A8794))
        DocKind.ZIP -> LabelBadge("ZIP", Color(0xFFE0A526))
    }
}

@Composable
private fun LabelBadge(label: String, accent: Color) {
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)).background(Color(0xFFF7FAFA)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(9.dp))
        repeat(3) {
            Box(Modifier.fillMaxWidth(0.6f).height(3.dp).clip(CircleShape).background(Color(0xFFD6DEDE)))
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().background(accent).padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, background: Color, tint: Color) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(9.dp)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
    }
}

/** The screen background is dark in both themes, so status and navigation bar icons must be light here. */
@Composable
private fun LightSystemBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousStatus = controller?.isAppearanceLightStatusBars
        val previousNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = false
        controller?.isAppearanceLightNavigationBars = false
        onDispose {
            if (controller != null) {
                previousStatus?.let { controller.isAppearanceLightStatusBars = it }
                previousNavigation?.let { controller.isAppearanceLightNavigationBars = it }
            }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
