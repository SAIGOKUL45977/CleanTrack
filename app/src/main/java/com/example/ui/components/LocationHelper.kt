package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState

data class CapturedLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun AutoLocationCapture(
    currentLocation: CapturedLocation?,
    onLocationCaptured: (CapturedLocation) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locationPermissionState = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    var isFetching by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }

    fun captureGpsNow() {
        locationError = null
        if (!locationPermissionState.status.isGranted) {
            locationPermissionState.launchPermissionRequest()
            locationError = "Allow precise location, then tap Capture again."
            return
        }
        isFetching = true
        try {
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc: Location? ->
                    isFetching = false
                    if (loc == null) {
                        locationError = "No GPS fix. Enable Location, move outdoors, and retry."
                    } else {
                        onLocationCaptured(CapturedLocation(loc.latitude, loc.longitude,
                            "Lat: %.6f°, Lng: %.6f° (device GPS)".format(java.util.Locale.US, loc.latitude, loc.longitude)))
                    }
                }
                .addOnFailureListener {
                    isFetching = false
                    locationError = "GPS capture failed. Enable Location and retry on a physical phone."
                }
        } catch (_: Exception) {
            isFetching = false
            locationError = "Location permission or Google Play services is unavailable. Retry on a supported phone."
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "GPS",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GPS Location Capture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
            locationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (currentLocation != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Captured",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "GPS Coordinates Captured",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = currentLocation.address,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { captureGpsNow() }) {
                        Icon(
                            Icons.Default.MyLocation,
                            contentDescription = "Recapture GPS",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                Button(
                    onClick = { captureGpsNow() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isFetching,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (isFetching) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Getting device location…")
                    } else {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auto-Capture GPS Location (One Tap)")
                    }
                }
            }
        }
    }
}
