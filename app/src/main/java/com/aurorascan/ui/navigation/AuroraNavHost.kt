package com.aurorascan.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aurorascan.ui.detail.DetailScreen
import com.aurorascan.ui.home.HomeScreen
import com.aurorascan.ui.signatures.ManageSignaturesScreen

object Routes {
    const val HOME = "home"
    const val ARG_DOCUMENT_ID = "documentId"
    const val DOCUMENT = "document/{$ARG_DOCUMENT_ID}"
    const val SIGNATURES = "signatures"
    fun document(documentId: String) = "document/$documentId"
}

@Composable
fun AuroraNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onOpenDocument = { id -> navController.navigate(Routes.document(id)) },
                onOpenSignatures = { navController.navigate(Routes.SIGNATURES) },
            )
        }
        composable(Routes.SIGNATURES) {
            ManageSignaturesScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.DOCUMENT,
            arguments = listOf(navArgument(Routes.ARG_DOCUMENT_ID) { type = NavType.StringType }),
        ) {
            DetailScreen(onBack = { navController.popBackStack() })
        }
    }
}
