package com.kodraliu.localrock.ui.navigation

import kotlinx.serialization.Serializable

// Top-level destinations
@Serializable data object Splash
@Serializable data object Welcome
@Serializable data object Login
@Serializable data object DeviceList
@Serializable data object AppSettings
@Serializable data object AddVacuum

@Serializable data class VacuumGraph(val duid: String)
@Serializable data object VacuumMain
@Serializable data object VacuumSettings
@Serializable data object VacuumZoneClean
@Serializable data object VacuumSchedule
@Serializable data object VacuumCamera
@Serializable data object VacuumRemote
@Serializable data object VacuumPinGo
@Serializable data object VacuumHistory
