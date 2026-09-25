package com.example.englishlearning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.englishlearning.ui.AiProfileSettingsViewModel
import com.example.englishlearning.ui.AppScreen
import com.example.englishlearning.ui.ArticleReadingViewModel
import com.example.englishlearning.ui.CheckInViewModel
import com.example.englishlearning.ui.LearningSetupViewModel
import com.example.englishlearning.ui.ReadingAccessViewModel
import com.example.englishlearning.ui.TodayPlanViewModel
import com.example.englishlearning.ui.WordCardViewModel
import com.example.englishlearning.ui.WorksheetViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.example.englishlearning.language.domain.PronunciationProvider

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var pronunciationProvider: PronunciationProvider
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            AppScreen(
                viewModel = hiltViewModel(),
                learningSetupViewModel = hiltViewModel<LearningSetupViewModel>(),
                todayPlanViewModel = hiltViewModel<TodayPlanViewModel>(),
                wordCardViewModel = hiltViewModel<WordCardViewModel>(),
                worksheetViewModel = hiltViewModel<WorksheetViewModel>(),
                readingAccessViewModel = hiltViewModel<ReadingAccessViewModel>(),
                articleReadingViewModel = hiltViewModel<ArticleReadingViewModel>(),
                aiProfileViewModel = hiltViewModel<AiProfileSettingsViewModel>(),
                checkInViewModel = hiltViewModel<CheckInViewModel>(),
                pronunciationProvider = pronunciationProvider,
            )
        }
    }
}
