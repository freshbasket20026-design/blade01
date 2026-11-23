package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.core.i18n.bladeStrings
import com.example.ui.theme.BladeObsidian
import com.example.ui.theme.BladeRed
import com.example.ui.theme.BladeRedAccent
import com.example.ui.theme.TextWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = bladeStrings()
    val alphaAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(1.06f) }
    val badgeOffsetY = remember { Animatable(24f) }
    val progressAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            alphaAnim.animateTo(1f, animationSpec = tween(550, easing = FastOutSlowInEasing))
        }
        launch {
            scaleAnim.animateTo(1f, animationSpec = tween(1100, easing = FastOutSlowInEasing))
        }
        launch {
            badgeOffsetY.animateTo(0f, animationSpec = tween(650, easing = FastOutSlowInEasing))
        }
        launch {
            progressAnim.animateTo(1f, animationSpec = tween(1650, easing = FastOutSlowInEasing))
        }
        delay(1800)
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BladeObsidian)
    ) {
        // Full-bleed user portrait photo background with gentle zoom-in settle
        Image(
            painter = painterResource(id = R.drawable.splash_portrait_bg),
            contentDescription = "Splash background",
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .scale(scaleAnim.value)
                .alpha(alphaAnim.value)
                .testTag("splash_background_image")
        )

        // Soft radial studio vignette so white outer backdrop blends smoothly into dark edges
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.22f),
                            BladeObsidian.copy(alpha = 0.72f)
                        ),
                        radius = 1350f
                    )
                )
        )

        // Top subtle atmospheric shadow
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            BladeObsidian.copy(alpha = 0.55f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Deep cinematic bottom shadow + warm crimson aura for crisp brand placement
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.52f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.38f to BladeObsidian.copy(alpha = 0.62f),
                            0.72f to BladeObsidian.copy(alpha = 0.90f),
                            1.0f to BladeObsidian
                        )
                    )
                )
        )

        // Top subtle brand pill
        Surface(
            shape = RoundedCornerShape(50),
            color = BladeObsidian.copy(alpha = 0.52f),
            border = androidx.compose.foundation.BorderStroke(1.dp, TextWhite.copy(alpha = 0.22f)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 18.dp)
                .alpha(alphaAnim.value)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BladeRedAccent)
                )
                Text(
                    text = "SHORT VIDEO STUDIO",
                    color = TextWhite.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.6.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // Foreground brand lockup centered cleanly in the lower gradient shadow
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 36.dp)
                .graphicsLayer { translationY = badgeOffsetY.value }
                .alpha(alphaAnim.value)
        ) {
            Surface(
                shape = CircleShape,
                color = BladeObsidian.copy(alpha = 0.92f),
                border = androidx.compose.foundation.BorderStroke(2.dp, BladeRed.copy(alpha = 0.85f)),
                modifier = Modifier
                    .size(80.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = CircleShape,
                        ambientColor = BladeRed,
                        spotColor = BladeRed
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.blade_logo_round),
                    contentDescription = strings.appName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .clip(CircleShape)
                )
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = strings.appName.uppercase(),
                color = TextWhite,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 5.sp,
                    shadow = Shadow(
                        color = BladeRed.copy(alpha = 0.55f),
                        offset = Offset(0f, 3f),
                        blurRadius = 14f
                    )
                ),
                modifier = Modifier.testTag("splash_brand_title")
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "Create • Watch • Share",
                color = TextWhite.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    letterSpacing = 1.2.sp,
                    shadow = Shadow(
                        color = Color.Black.copy(alpha = 0.75f),
                        offset = Offset(0f, 1.5f),
                        blurRadius = 6f
                    )
                )
            )

            Spacer(Modifier.height(22.dp))

            // Sleek animated crimson loading bar
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(TextWhite.copy(alpha = 0.16f))
                    .border(0.5.dp, TextWhite.copy(alpha = 0.1f), RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressAnim.value.coerceIn(0.05f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(listOf(BladeRed, BladeRedAccent))
                        )
                )
            }
        }
    }
}
