package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.CitizenDetailsScreen
import com.example.ui.screens.CitizenHomeScreen
import com.example.ui.theme.CleanTrackTheme
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.CleanTrackViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CleanTrackViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CleanTrackTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CleanTrackApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun CleanTrackApp(viewModel: CleanTrackViewModel) {
    val authState by viewModel.authState.collectAsState()

    when (val state = authState) {
        is AuthState.Unauthenticated -> {
            CitizenDetailsScreen(viewModel = viewModel)
        }
        is AuthState.Authenticated -> {
            CitizenHomeScreen(
                user = state.user,
                viewModel = viewModel
            )
        }
    }
}
