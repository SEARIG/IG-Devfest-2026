package com.agroai.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.remember
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.viewmodel.compose.viewModel
import com.agroai.app.ui.AgroAiApp
import com.agroai.app.ui.AppViewModel
import com.agroai.app.ui.AppViewModelFactory
import com.agroai.app.ui.Stage2ViewModel
import com.agroai.app.ui.Stage2ViewModelFactory
import com.agroai.app.ui.Stage3ViewModel
import com.agroai.app.ui.Stage3ViewModelFactory
import com.agroai.app.ui.theme.AgroAiTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as AgroAiApplication).container
        setContent {
            AgroAiTheme {
                Surface(Modifier.fillMaxSize()) {
                    val factory = remember { AppViewModelFactory(container) }
                    val appViewModel: AppViewModel = viewModel(factory = factory)
                    val stage2Factory = remember { Stage2ViewModelFactory(container) }
                    val stage2ViewModel: Stage2ViewModel = viewModel(factory = stage2Factory)
                    val stage3Factory = remember { Stage3ViewModelFactory(container) }
                    val stage3ViewModel: Stage3ViewModel = viewModel(factory = stage3Factory)
                    AgroAiApp(appViewModel, stage2ViewModel, stage3ViewModel)
                }
            }
        }
    }
}
