package com.example.englishlearning.launch

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.englishlearning.MainActivity
import com.example.englishlearning.R
import kotlinx.coroutines.delay

// 宿主矩阵守卫（D-5 推广，BiometricHostActivityContractTest）：Manifest 声明的每个生产 Activity
// 都必须是 FragmentActivity 的后代。本类今天只是闪屏并转发 MainActivity，但一旦有屏幕被挂到这里，
// `context as? FragmentActivity`（BiometricPrompt 宿主）在 ComponentActivity 下会静默为 null。
// FragmentActivity 是 ComponentActivity 子类，installSplashScreen/setContent 行为不变。
class BrandLaunchActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            val gradient = Brush.linearGradient(
                listOf(Color(0xFFA8F3C8), Color(0xFF5DDFB4), Color(0xFF35B9B5)),
            )
            Box(
                modifier = Modifier.fillMaxSize().background(gradient),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_learning_mark),
                    contentDescription = null,
                    modifier = Modifier.size(180.dp),
                )
            }
            LaunchedEffect(Unit) {
                delay(1200)
                startActivity(Intent(this@BrandLaunchActivity, MainActivity::class.java))
                finish()
            }
        }
    }
}
