package com.sshautoforward.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sshautoforward.ui.dashboard.DashboardScreen
import com.sshautoforward.ui.hosts.AddEditHostScreen
import com.sshautoforward.ui.hosts.HostListScreen

object Routes {
    const val HOST_LIST = "hosts"
    const val ADD_HOST = "hosts/add"
    const val EDIT_HOST = "hosts/{hostId}"
    const val DASHBOARD = "hosts/{hostId}/dashboard"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOST_LIST) {
        composable(Routes.HOST_LIST) {
            HostListScreen(
                onAddHost = { navController.navigate(Routes.ADD_HOST) },
                onEditHost = { hostId -> navController.navigate("hosts/$hostId") },
                onConnect = { hostId -> navController.navigate("hosts/$hostId/dashboard") },
            )
        }

        composable(Routes.ADD_HOST) {
            AddEditHostScreen(
                hostId = null,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.EDIT_HOST,
            arguments = listOf(navArgument("hostId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val hostId = backStackEntry.arguments?.getLong("hostId") ?: return@composable
            AddEditHostScreen(
                hostId = hostId,
                onNavigateBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.DASHBOARD,
            arguments = listOf(navArgument("hostId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val hostId = backStackEntry.arguments?.getLong("hostId") ?: return@composable
            DashboardScreen(
                hostId = hostId,
                onNavigateBack = { navController.popBackStack() },
            )
        }
    }
}
