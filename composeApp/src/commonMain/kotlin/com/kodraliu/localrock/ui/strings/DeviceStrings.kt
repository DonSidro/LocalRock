package com.kodraliu.localrock.ui.strings

// Device page wording for the redesign (track B2). verified = official wording (PROPOSAL.md section 3
// or the plugin's English string table, key in the comment), inferred = our guess or a LocalRock-only
// label. {n} marks a number the caller fills in with [fill].
object DeviceStrings {
    const val GETTING_INFO = "Getting Info" // verified Common_Constants_0

    // Top bar.
    const val BACK = "Go back" // inferred
    const val REMOTE_VIEWING = "Remote Viewing" // verified setting_realtime_monitor_switch_title
    const val EDIT_MAP = "Edit Map" // verified map_edit_home_menu_title
    const val SETTINGS = "Settings" // verified Setting_index_21
    const val MORE = "More" // verified smart_scene_more
    const val BATTERY = "Battery" // inferred
    const val BATTERY_CHARGING = "Battery, charging" // inferred

    // Bottom panel tabs and tips.
    const val TAB_FULL = "Full" // verified
    const val TAB_ROOM = "Room" // verified
    const val TAB_ZONE = "Zone" // verified
    const val TAB_ROUTINE = "Routine" // verified
    const val TIP_SELECT_ROOMS = "Select room(s) to clean" // verified
    const val TIP_ROOMS_SELECTED = "{n} cleaning areas selected" // verified
    const val TIP_ADD_ZONE = "Add Zone" // verified
    const val TIP_ZONES_ADDED = "{n} cleaning area(s) added" // verified
    const val EDIT_ROUTINE = "Edit Routine" // verified
    const val NO_ROOMS = "Load the map first to see your rooms." // inferred
    const val DEFAULT_ORDER = "Default cleaning order" // inferred
    const val ZONE_HINT = "Draw up to 5 zones on the map, then start them from the zone screen." // inferred
    const val MAX_ZONES = "Maximum 5 Zones" // verified rubys_main_zone_max_tips

    // Routine tab.
    const val MY_ROUTINES = "My Routines" // verified smart_scene_custom_commands
    const val ROUTINES_INTRO = "Routines saved in the Roborock app and kept by your server. Running one that is already going stops it." // inferred
    const val ROUTINES_EMPTY = "No routines for this robot. Routines are made in the Roborock app; once the server has them they show up here." // inferred
    const val RUN = "Run" // inferred
    const val TRY_AGAIN = "Try again" // inferred

    // Mode chip sheet.
    const val MODE_SMARTPLAN = "SmartPlan" // verified
    const val MODE_GENERAL = "General" // verified
    const val MODE_CUSTOMIZE = "Customize" // verified
    const val MODE_VAC_THEN_MOP = "Vac followed by Mop" // verified
    const val MODE_VAC_AND_MOP = "Vac & Mop" // verified
    const val MODE_VACUUM = "Vacuum" // verified
    const val MODE_MOP = "Mop" // verified
    const val SUCTION_POWER = "Suction Power" // verified
    const val WATER_FLOW = "Water Flow" // verified
    const val CLEANING_COUNT = "Cleaning Count" // verified
    const val CLEAN_COUNT_TIMES = "{n} time(s)" // verified clean_count_times
    const val ROUTE = "Route" // verified
    const val MORE_CLEANING_SETTINGS = "More cleaning settings" // verified
    const val MODE_SWITCH_CONFIRM = "Switching cleaning modes will end the current clean" // verified
    const val TIP_SMART = "Learns and adapts to your space, habits, and preferences to customize the optimal cleaning plan." // verified mode_setting_panel_smart_tip
    const val TIP_VAC_THEN_MOP = "Vacuum and mop separately to improve performance." // verified mode_setting_panel_cleanthenmop_tip
    const val TIP_VAC_AND_MOP = "Vac & Mop for daily clean" // verified mode_setting_panel_cleanmop_tip
    const val TIP_MOP = "Mop only for delicate floors" // inferred (official says "effective once", ours is persistent)
    const val TIP_VACUUM = "Vacuum only" // inferred (same reason)
    const val TIP_CUSTOMIZE = "The robot cleans each room based on the cleaning mode settings." // verified custom_mode_panel_has_custom_data_tip
    const val ROOM_MODES_LOADING = "Reading the room settings from the robot." // inferred
    const val ROOM_MODES_FAILED = "Couldn't read the room settings from the robot. Close this sheet and open it again." // inferred
    const val LOADING_MAP = "Loading map…" // inferred
    const val ROOM_MODES_NO_MAP = "Load the map to set up each room." // inferred
    const val SAVE_ROOM_MODES = "Save room settings" // inferred
    const val ROOM_MODES_SAVED = "Room settings saved" // inferred
    const val OTHER = "Other" // inferred

    // Suction, water and route option labels.
    const val FAN_OFF = "Off" // verified setting_switch_off
    const val FAN_QUIET = "Quiet" // verified Common_Protocol_0
    const val FAN_BALANCED = "Balanced" // verified Common_Protocol_1
    const val FAN_TURBO = "Turbo" // verified Common_Protocol_2
    const val FAN_MAX = "Max" // verified Common_Protocol_3
    const val WATER_OFF = "Off" // verified setting_switch_off
    const val WATER_LOW = "Low" // verified water_box_small
    const val WATER_MEDIUM = "Medium" // verified water_box_middle
    const val WATER_HIGH = "High" // verified water_box_big
    const val ROUTE_STANDARD = "Standard" // verified clean_efficiency_standard
    const val ROUTE_DEEP = "Deep" // verified clean_efficiency_meticulous
    const val ROUTE_DEEP_PLUS = "Deep+" // verified mop_method_careful_slow
    const val ROUTE_FAST = "Fast" // verified clean_route_fast_mode_title
    const val WATER_FLOW_SLIGHT = "Slight" // verified mode_setting_panel_custom_water_box_7
    const val WATER_FLOW_LOW = "Low" // verified mode_setting_panel_custom_water_box_6
    const val WATER_FLOW_GENTLE = "Gentle" // verified mode_setting_panel_custom_water_box_5
    const val WATER_FLOW_MEDIUM = "Medium" // verified mode_setting_panel_custom_water_box_4
    const val WATER_FLOW_MODERATE = "Moderate" // verified mode_setting_panel_custom_water_box_3
    const val WATER_FLOW_HIGH = "High" // verified mode_setting_panel_custom_water_box_2
    const val WATER_FLOW_EXTREME = "Extreme" // verified mode_setting_panel_custom_water_box_1
    const val WATER_FLOW_WARNING = "Water marks & robot slipping" // verified super_water_mode_foot_tip

    // Start button.
    const val START_CLEAN = "Clean" // verified
    const val START_PAUSE = "Pause" // verified
    const val START_RESUME = "Resume" // verified
    const val MAPPING = "Mapping" // verified
    const val END_CLEAN = "End cleaning" // inferred
    const val CLEANING_STARTED = "Cleaning started" // inferred
    const val CLEANING_RESUMED = "Cleaning resumed" // inferred
    const val RETURNING_TOAST = "Returning to dock" // inferred
    const val DOCK_WORKING_NO_PAUSE = "The dock is working, please pause it after it finishes current task." // verified
    const val WATER_CUSTOM = "Custom" // verified
    const val WATER_CUSTOM_CONFIRM = "Confirm water flow" // inferred
    const val PET_SEARCH_STARTED = "Looking for your pet" // inferred
    const val PET_SEARCH_CONFIRM = "The robot leaves its dock and patrols the home with its camera. It can only start while docked and not cleaning."

    // Dock button and Dock Control sheet.
    const val DOCK = "Dock" // verified
    const val RETURN_TO_DOCK = "Return to the dock" // verified home_new_control_start_charge
    const val BACK_TO_DOCK = "Back to Dock" // verified home_new_control_back_charge
    const val STOP_RETURNING = "Stop returning to dock" // verified robot_cmd_stop_back_washing
    const val CONFIRM_STOP_RETURN_RESUME = "The robot will stop returning to the dock and resume the current cleaning task." // verified dock_click_confirm_tip1
    const val CONFIRM_STOP_TASK_RETURN = "The robot will stop its current task and return to the dock." // verified dock_click_confirm_tip2
    const val CONFIRM_STOP_MAPPING_RETURN = "The robot will stop mapping and return to the dock. The unfinished map will not be saved automatically." // verified dock_click_confirm_tip3
    const val CONFIRM_STOP_CRUISE_RETURN = "The robot will stop cruising and return to the dock." // verified dock_click_confirm_tip4
    const val CONFIRM_END_CLEAN_RETURN = "Sending the robot back to the dock will end the current clean." // verified home_dialog_begin_new_back_charge
    const val MIDWAY_EMPTY = "Halfway docking for emptying" // verified homecontrol_midway_start_collect
    const val MIDWAY_WASH = "Wash Mop Halfway" // verified terminate_current_task_then_wash
    const val DOCK_CONTROL = "Dock Control" // verified
    const val CLEANING_MODE = "Cleaning Mode" // verified smart_scene_clean_mode_setting_item_title
    const val MAPS = "Maps" // inferred
    const val MANAGE_SAVED_MAPS = "Manage saved maps" // inferred
    const val DOCK_SETTINGS = "Dock Settings" // verified robot_setting_self_cleaning_title
    const val CLEAN_WATER_TANK = "Clean Water Tank" // verified
    const val DIRTY_WATER_TANK = "Dirty Water Tank" // verified
    const val DUST_BAG = "Dust Bag" // verified dust_collection_life5
    const val OUT_OF_WATER = "Out of water" // verified dock_info_clear_water_box_exception1
    const val TANK_FULL = "Full" // verified dock_info_dust_bag_exception
    const val NOT_INSTALLED = "Not installed" // verified dock_info_item_gone_exception
    const val NORMAL = "Normal" // verified all_supplies_ok_tip
    const val LATCH_OPEN = "Latch open" // inferred
    const val DUCT_BLOCKED = "Duct blocked" // inferred
    const val UNKNOWN = "Unknown" // inferred
    const val EMPTY = "Empty" // verified
    const val STOP_EMPTYING = "Stop Emptying" // verified
    const val WASH = "Wash" // verified
    const val STOP = "Stop" // verified
    const val DRY = "Dry" // verified
    const val STOP_DRYING = "Stop Drying" // verified
    const val DRYING_MINUTES = "Drying the mop, estimated finish after: {n} minute(s)" // verified home_sepcial_info_airdry_finish_time_minute
    const val DRYING = "Drying" // inferred

    // Dialog buttons.
    const val OK = "OK" // verified goto_setting_on
    const val CANCEL = "Cancel" // verified Main_MainPage_11

    // Status line words (status-and-enums.md 3.2 and 3.4).
    const val STATUS_CHARGED = "Charged" // verified
    const val STATUS_CHARGING = "Charging" // verified
    const val STATUS_CHARGING_ERROR = "Charging Error" // verified Common_Constants_10
    const val STATUS_CHARGE_PENDING = "Charge Pending" // verified robot_status_wait_charge
    const val STATUS_CLEANING = "Cleaning" // verified Common_Constants_1
    const val STATUS_SLEEPING = "Sleeping" // verified
    const val STATUS_READY = "Ready" // verified
    const val STATUS_PAUSED = "Paused" // verified
    const val STATUS_SPOT_CLEAN = "Spot Clean" // verified
    const val STATUS_ERROR = "Error" // verified
    const val STATUS_REMOTE = "Remote Controlling" // verified
    const val STATUS_GOING_TO_TARGET = "Going to the target point" // verified
    const val STATUS_GOING_TO_WASH = "Going to wash the mop" // verified
    const val STATUS_DOCKING_TO_WASH = "Docking for mop washing" // verified backdock_substate_washing_mop
    const val STATUS_DOCKING_TO_SETUP_MOP = "Docking to reinstall mop cloth mounts" // verified backdock_substate_setup_mop
    const val STATUS_DOCKING_TO_DETACH_MOP = "Docking to detach mop cloth mounts" // verified backdock_substate_teardown_mop
    const val STATUS_DOCKING_TO_EMPTY = "Docking for emptying" // verified backdock_substate_collecting_dust
    const val STATUS_MAPPING = "Mapping" // verified
    const val STATUS_IN_CALL = "In call…" // verified be_on_the_phone
    const val STATUS_SAVING_MAP = "Saving Map" // verified
    const val STATUS_SCANNING = "scanning" // verified robot_status_egg_attack
    const val STATUS_REINSTALLING_MOP = "Reinstalling mop cloth mounts" // verified
    const val STATUS_DETACHING_MOP = "Detaching mop cloth mounts" // verified
    const val STATUS_SORTING = "Sorting" // verified
    const val STATUS_VACUUMING = "Vacuuming..." // verified status_clean_only
    const val STATUS_MOPPING = "Mopping..." // verified status_mop_only
    const val STATUS_VAC_AND_MOPPING = "Vacuuming & Mopping..." // verified status_clean_mop
    const val STATUS_FULL_CLEANING = "Full Cleaning" // verified
    const val STATUS_ROOM_CLEANING = "Room Cleaning" // verified
    const val STATUS_ZONE_CLEANING = "Zone cleaning" // verified
    const val STATUS_MAPPING_TASK = "Mapping Task" // verified core_task_build_map
    const val STATUS_RETURNING = "Returning to Dock" // verified
    const val STATUS_WASHING_MOP = "Washing the mop" // verified
    const val STATUS_EMPTYING = "Emptying" // verified
    const val STATUS_ON_CRUISE = "On cruise" // verified
    const val STATUS_SEARCHING_PETS = "Searching for pets" // verified
    const val STATUS_UPDATING = "Updating" // verified
    const val STATUS_OFFLINE = "Offline" // verified

    // Status card (compact detail under the map).
    const val AREA_SO_FAR = "{area} so far" // inferred
    const val AREA_LAST_CLEAN = "{area} last clean" // inferred
    const val MINUTES = "{n} min" // inferred
    const val NO_CLEAN_YET = "No cleaning recorded yet" // inferred
    const val HISTORY = "History" // verified Setting_History_index_2

    // Top bar overflow extras.
    const val SEARCH_FOR_PET = "Search for the Pet" // verified pet_environment_one_tap_find_pet
    const val FIND_MY_PET = "Find my pet" // inferred

    /** Puts [n] into a `{n}` template. */
    fun fill(template: String, n: Any): String = template.replace("{n}", n.toString())
}
