package com.shaqsid.smart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shaqsid.smart.feature.adddevice.AddDeviceScreen
import com.shaqsid.smart.feature.adddevice.AddDeviceViewModel
import com.shaqsid.smart.feature.auth.LoginScreen
import com.shaqsid.smart.feature.auth.LoginViewModel
import com.shaqsid.smart.feature.auth.RegisterScreen
import com.shaqsid.smart.feature.auth.RegisterViewModel
import com.shaqsid.smart.feature.camera.CameraScreen
import com.shaqsid.smart.feature.devicedetail.DeviceDetailScreen
import com.shaqsid.smart.feature.devicedetail.DeviceDetailViewModel
import com.shaqsid.smart.feature.devicelist.DeviceListScreen
import com.shaqsid.smart.feature.devicelist.DeviceListViewModel
import com.shaqsid.smart.ui.theme.SmartDeviceTheme
import androidx.navigation.NavType
import androidx.navigation.navArgument

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val appContainer = (application as SmartApp).container

        setContent {
            SmartDeviceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SmartAppNavigation(appContainer)
                }
            }
        }
    }
}

@Composable
fun SmartAppNavigation(appContainer: AppContainer) {
    val navController = rememberNavController()
    val startDestination = if (appContainer.authUseCases.isLoggedIn()) "device_list" else "login"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            val viewModel: LoginViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return LoginViewModel(appContainer.authUseCases) as T
                    }
                }
            )
            LoginScreen(
                viewModel = viewModel,
                onLoginSuccess = {
                    navController.navigate("device_list") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateToRegister = { navController.navigate("register") }
            )
        }
        composable("register") {
            val viewModel: RegisterViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return RegisterViewModel(appContainer.authUseCases) as T
                    }
                }
            )
            RegisterScreen(
                viewModel = viewModel,
                onRegisterSuccess = {
                    navController.navigate("device_list") {
                        popUpTo("login") { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("device_list") {
            val viewModel: DeviceListViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return DeviceListViewModel(appContainer.deviceUseCases, appContainer.authUseCases) as T
                    }
                }
            )
            DeviceListScreen(
                viewModel = viewModel,
                onAddDeviceClick = { navController.navigate("add_device") },
                onDeviceClick = { device ->
                    val route = if (device.isCamera) "camera/${device.id}" else "device_detail/${device.id}"
                    navController.navigate(route)
                },
                onLoggedOut = {
                    navController.navigate("login") {
                        popUpTo("device_list") { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = "device_detail/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            val viewModel: DeviceDetailViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return DeviceDetailViewModel(appContainer.deviceUseCases, deviceId) as T
                    }
                }
            )
            DeviceDetailScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "camera/{deviceId}",
            arguments = listOf(navArgument("deviceId") { type = NavType.StringType })
        ) { backStackEntry ->
            val deviceId = backStackEntry.arguments?.getString("deviceId") ?: ""
            val viewModel: DeviceDetailViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return DeviceDetailViewModel(appContainer.deviceUseCases, deviceId) as T
                    }
                }
            )
            CameraScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("add_device") {
            val viewModel: AddDeviceViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return AddDeviceViewModel(appContainer.deviceUseCases) as T
                    }
                }
            )
            AddDeviceScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
