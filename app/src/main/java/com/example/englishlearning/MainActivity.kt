package com.example.englishlearning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.englishlearning.ui.AppScreen
import com.example.englishlearning.ui.LearningSetupViewModel
import com.example.englishlearning.ui.TodayPlanViewModel
import com.example.englishlearning.ui.WordCardViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            AppScreen(
                viewModel = hiltViewModel(),
                learningSetupViewModel = hiltViewModel<LearningSetupViewModel>(),
                todayPlanViewModel = hiltViewModel<TodayPlanViewModel>(),
                wordCardViewModel = hiltViewModel<WordCardViewModel>(),
            )
        }
    }
}
