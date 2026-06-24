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
import com.shaqsid.smart.feature.devicelist.DeviceListScreen
import com.shaqsid.smart.feature.devicelist.DeviceListViewModel
import com.shaqsid.smart.ui.theme.SmartDeviceTheme

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

    NavHost(navController = navController, startDestination = "device_list") {
        composable("device_list") {
            val viewModel: DeviceListViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return DeviceListViewModel(appContainer.deviceUseCases) as T
                    }
                }
            )
            DeviceListScreen(
                viewModel = viewModel,
                onAddDeviceClick = { navController.navigate("add_device") }
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
