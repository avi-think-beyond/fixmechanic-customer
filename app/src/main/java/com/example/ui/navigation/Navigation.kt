package com.example.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Auth : Screen("auth")
    object Main : Screen("main")
    object VehicleSelect : Screen("vehicle_select")
    object ServiceSelect : Screen("service_select")
    object Location : Screen("location")
    object VehicleDetails : Screen("vehicle_details")
    object RequestSummary : Screen("request_summary")
    object SearchingMechanic : Screen("searching_mechanic")
    object JobTracking : Screen("job_tracking")
    object Chat : Screen("chat/{jobId}") {
        fun createRoute(jobId: String) = "chat/$jobId"
    }
    object JobCompletion : Screen("job_completion")
    object AiDiagnoser : Screen("ai_diagnoser")
}

enum class MainTab(val title: String, val testTag: String) {
    HOME("Home", "tab_home"),
    ACTIVITY("Activity", "tab_activity"),
    PROFILE("Profile", "tab_profile")
}
