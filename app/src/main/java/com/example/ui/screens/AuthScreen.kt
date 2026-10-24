package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Recycling
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DarkTeal
import com.example.ui.theme.TealPrimary
import com.example.ui.viewmodel.CleanTrackViewModel

@Composable
fun CitizenDetailsScreen(viewModel: CleanTrackViewModel, modifier: Modifier = Modifier) {
    val error by viewModel.authError.collectAsState()
    val busy by viewModel.isAuthenticating.collectAsState()
    var name by rememberSaveable { mutableStateOf("") }
    var mobile by rememberSaveable { mutableStateOf("") }

    Scaffold { padding ->
        Column(
            modifier = modifier.fillMaxSize().padding(padding)
                .background(Brush.verticalGradient(listOf(DarkTeal, TealPrimary)))
                .verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(Icons.Default.Recycling, contentDescription = null,
                tint = Color.White, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(16.dp))
            Text("CleanTrack", style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold, color = Color.White)
            Text("Report garbage in your community", color = Color.White.copy(alpha = 0.85f))
            Spacer(Modifier.height(24.dp))
            Card(shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.padding(24.dp)) {
                    Text("Your details", style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                    Text("Enter your name and mobile number for complaint follow-up.",
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(20.dp))
                    OutlinedTextField(value = name, onValueChange = {
                        name = it.take(100); viewModel.clearAuthError()
                    }, label = { Text("Name") }, singleLine = true, enabled = !busy,
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(value = mobile, onValueChange = {
                        mobile = it.filter { c -> c in '0'..'9' }.take(10)
                        viewModel.clearAuthError()
                    }, label = { Text("Mobile number") }, singleLine = true, enabled = !busy,
                        supportingText = { Text("10 digits, without +91") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(12.dp))
                    Text("No password or OTP is needed for this college demonstration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Use test details during rehearsal. The entered mobile number is not verified.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (error != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { viewModel.continueCitizen(name, mobile) },
                        enabled = !busy, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        Text(if (busy) "Please wait…" else "Continue")
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Your complaints stay linked to this app installation. Keep app data to retain access.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
