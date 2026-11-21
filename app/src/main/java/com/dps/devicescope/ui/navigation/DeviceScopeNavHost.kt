package com.dps.devicescope.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dps.devicescope.core.DeviceContainer
import com.dps.devicescope.data.model.ThemeMode
import com.dps.devicescope.ui.screens.battery.BatteryScreen
import com.dps.devicescope.ui.screens.biometric.BiometricScreen
import com.dps.devicescope.ui.screens.camera.CameraScreen
import com.dps.devicescope.ui.screens.cellular.CellularScreen
import com.dps.devicescope.ui.screens.connectivity.ConnectivityScreen
import com.dps.devicescope.ui.screens.dashboard.DashboardScreen
import com.dps.devicescope.ui.screens.display.DisplayScreen
import com.dps.devicescope.ui.screens.location.LocationScreen
import com.dps.devicescope.ui.screens.microphone.MicrophoneScreen
import com.dps.devicescope.ui.screens.phone.PhoneScreen
import com.dps.devicescope.ui.screens.report.ReportScreen
import com.dps.devicescope.ui.screens.sensors.SensorsScreen
import com.dps.devicescope.ui.screens.system.SystemScreen

@Composable
fun DeviceScopeNavHost(
    container: DeviceContainer,
    themeMode: ThemeMode,
    onToggleTheme: () -> Unit
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.DASHBOARD
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                container = container,
                themeMode = themeMode,
                onToggleTheme = onToggleTheme,
                onNavigate = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            )
        }
        composable(Routes.SYSTEM) {
            SystemScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.BATTERY) {
            BatteryScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.DISPLAY) {
            DisplayScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.CONNECTIVITY) {
            ConnectivityScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.CAMERA) {
            CameraScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.CELLULAR) {
            CellularScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.LOCATION) {
            LocationScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.SENSORS) {
            SensorsScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.MICROPHONE) {
            MicrophoneScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.PHONE) {
            PhoneScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.BIOMETRIC) {
            BiometricScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.REPORT) {
            ReportScreen(container = container, onBack = { navController.popBackStack() })
        }
    }
}
