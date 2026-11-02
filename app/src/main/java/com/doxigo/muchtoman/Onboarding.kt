package com.doxigo.muchtoman

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class OnboardingSlide(
    val title: String,
    val subtitle: String,
    val description: String,
    val highlight: String,
    val icon: ImageVector,
    val accentColor: Color,
)

@Composable
fun OnboardingDialog(
    onDismiss: () -> Unit,
) {
    val t = CanvasTheme.tokens
    var step by remember { mutableIntStateOf(0) }

    val slides = remember {
        listOf(
            OnboardingSlide(
                title = "محرمانگی اول-محلی (Local-First)",
                subtitle = "امنیت ۱۰۰٪ آفلاین",
                description = "ژتون بدون نیاز به شماره موبایل، سرور خارجی یا تله‌متری کار می‌کند. تمامی پیامک‌های بانکی، تراکنش‌ها و کارت‌های شما به صورت رمزنگاری‌شده صرفاً روی حافظه همین دستگاه ذخیره می‌شوند.",
                highlight = "اطلاعات مالی شما هرگز از گوشی خارج نمی‌شود",
                icon = Icons.Rounded.Lock,
                accentColor = Color(0xFF10B981),
            ),
            OnboardingSlide(
                title = "سپر ضد تورم و معادل طلا",
                subtitle = "دیده‌بان قدرت خرید",
                description = "ارزش موجودی نقد و کارت‌های بانکی شما به صورت بلادرنگ به معادل گرم طلای ۱۸ عیار تبدیل می‌شود تا اثر واقعی افت ارزش ریال را بسنجید و سبد دارایی خود را هوشمندانه مدیریت کنید.",
                highlight = "رصد نرخ‌های زنده طلا، سکه، دلار و تتر",
                icon = Icons.Rounded.Star,
                accentColor = Color(0xFFE5A93C),
            ),
            OnboardingSlide(
                title = "پیامک‌های بانکی و اقساط",
                subtitle = "مدیریت خودکار تعهدات",
                description = "با فعال‌سازی خوانش پیامک‌ها، تراکنش‌های واریز و برداشت به طور خودکار ثبت می‌شوند و موعد چک‌های صیاد و اقساط وام به موقع به شما هشدار داده خواهند شد.",
                highlight = "پشتیبانی کامل از کارت‌های شبکه شتاب ایران",
                icon = Icons.Rounded.Notifications,
                accentColor = Color(0xFFF26430),
            ),
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(t.ground),
            color = t.ground,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                // Top Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(t.inverse),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "ژ",
                                color = t.onInverse,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                            )
                        }
                        Column {
                            Text(
                                text = "ژتون • Zheton",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = t.ink,
                            )
                            Text(
                                text = "دستیار مالی و قدرت خرید",
                                style = MaterialTheme.typography.labelSmall,
                                color = t.muted,
                            )
                        }
                    }

                    // Step Dots Indicator
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        slides.indices.forEach { idx ->
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(if (idx == step) 22.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(if (idx == step) t.ink else t.track),
                            )
                        }
                    }
                }

                // Middle Content
                AnimatedContent(
                    targetState = step,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(200))
                    },
                    label = "onboarding_step_anim",
                    modifier = Modifier.weight(1f),
                ) { currentStep ->
                    val s = slides[currentStep]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Visual Circle
                        Box(
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(32.dp))
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            s.accentColor.copy(alpha = 0.22f),
                                            s.accentColor.copy(alpha = 0.05f),
                                        )
                                    )
                                )
                                .border(1.dp, s.accentColor.copy(alpha = 0.25f), RoundedCornerShape(32.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = s.icon,
                                contentDescription = null,
                                tint = s.accentColor,
                                modifier = Modifier.size(44.dp),
                            )
                        }

                        Spacer(Modifier.height(24.dp))

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = s.accentColor.copy(alpha = 0.12f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, s.accentColor.copy(alpha = 0.3f)),
                        ) {
                            Text(
                                text = s.subtitle,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = s.accentColor,
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        Text(
                            text = s.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black,
                            color = t.ink,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = s.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = t.inkSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )

                        Spacer(Modifier.height(18.dp))

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = t.card,
                            border = androidx.compose.foundation.BorderStroke(1.dp, t.cardBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = s.highlight,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = t.muted,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                // Bottom Buttons
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (step < slides.lastIndex) {
                        Button(
                            onClick = { step++ },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = t.inverse,
                                contentColor = t.onInverse,
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = "مرحله بعد",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    } else {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = t.inverse,
                                contentColor = t.onInverse,
                            ),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "شروع استفاده از ژتون",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                            }
                        }
                    }

                    if (step > 0) {
                        TextButton(
                            onClick = { step-- },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = "بازگشت",
                                color = t.muted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

