package com.kodraliu.localrock.ui.strings

// Manage Maps wording for the redesign (track B3). verified = official wording (PROPOSAL.md section 5),
// inferred = our guess or a LocalRock-only label.
object MapStrings {
    const val MANAGE_MAPS = "Manage Maps" // verified
    const val MAP_DETAILS = "Map Details" // verified
    const val BACKUP = "Backup" // verified
    const val RESTORE = "Restore" // verified
    const val ROTATE_90 = "90°" // verified
    const val ROTATE_MAP = "Rotate map" // inferred
    const val RENAME_MAP = "Rename map" // inferred
    const val MORE = "More" // inferred
    const val MAPS = "Maps" // verified
    const val NEEDS_SAVED_MAP = "Map tools need a saved map. Let the robot finish mapping and save the map first." // inferred
    const val MAP_NOT_LOADED = "Map not loaded" // inferred
    const val CLOSE = "Close" // inferred
    const val LOAD = "Load" // inferred
    const val LOADED = "Loaded" // inferred

    // Tool grid, in official order.
    const val TOOL_DIVIDE = "Divide" // verified
    const val TOOL_COMBINE = "Combine" // verified
    const val TOOL_REMOVE = "Remove" // verified
    const val TOOL_NAME_ROOM = "Name the Room" // verified
    const val TOOL_NO_GO = "No-Go Zones" // verified
    const val TOOL_THRESHOLD = "Threshold" // verified
    const val TOOL_FLOOR_TYPE = "Floor Type" // verified
    const val TOOL_CARPET = "Carpet" // verified
    const val TOOL_FURNITURE = "Furniture" // verified
    const val TOOL_SEQUENCE = "Sequence" // verified

    // Tool flows.
    const val EDIT_ROOMS = "Edit Rooms" // verified
    const val DIVIDE = "Divide" // verified
    const val ROOM_LIMIT_REACHED = "Room limit reached" // verified
    const val DIVIDE_DRAW_TIP = "Draw a line in the selected room. " // verified
    const val NAME_TIP = "Select an area to name" // verified
    const val ROOM_TYPE_INFO = "In some modes, your robot automatically adjusts cleaning preferences based on the room type." // verified
    const val CUSTOM_NAME = "Custom name" // inferred
    const val RENAME = "Rename" // inferred
    const val NO_GO_INFO = "No-Go Zones/Invisible Wall: Prevent the robot from entering a certain area when cleaning." // verified
    const val THRESHOLD_INFO = "The robot moves through door thresholds and steps without cleaning." // verified
    const val FLOOR_SELECT_TIP = "Select a room to set the floor type" // verified
    const val FLOOR_DEFAULT = "Default" // inferred
    const val FLOOR_WOOD_HORIZONTAL = "Wood Horizontal" // verified
    const val FLOOR_WOOD_VERTICAL = "Wood Vertical" // verified
    const val CARPET_ADD_TIP = "Tap on an existing carpet to edit or delete it." // verified
    const val REMOVE_TITLE = "Remove" // verified
    const val REMOVE_LIMIT = "The maximum number of removal areas has been reached." // verified
    const val REMOVE_TOO_CLOSE = "The removal area is too close to the station, please adjust." // verified
    const val REMOVE_INFO = "Removed areas are hidden from the map. The robot keeps them in its own map." // inferred
    const val REMOVE_SAVED = "Saved. The robot's map shows the removal areas." // inferred
    const val DIVIDE_TIP = "Select a room to divide." // verified
    const val MERGE = "Merge" // verified
    const val COMBINE_TIP = "Please select adjacent areas to merge." // verified
    const val REMOVE_TIP = "Select removal area" // verified
    const val SELECT_ROOM_TYPE = "Select a room type" // verified
    const val AREA_MANAGEMENT = "Cleaning Area Management" // verified
    const val INVISIBLE_WALL = "Invisible Wall" // verified
    const val NO_GO_ZONE = "No-Go Zone" // verified
    const val NO_MOP_ZONE = "No-Mop Zone" // verified
    const val DOOR_THRESHOLD = "Door Threshold" // verified
    const val DOOR_STEPS = "Door Steps" // verified
    const val FLOOR_TYPE_TIP = "Select room(s) to set floor type" // verified
    const val FLOOR_TILE = "Tile" // verified
    const val FLOOR_WOOD = "Wood" // verified
    const val FLOOR_OTHERS = "Others" // verified
    const val CARPET_TIP = "Tap on a carpet to edit" // verified
    const val ADD_CARPET = "Add Carpet" // verified
    const val RECTANGULAR_CARPET = "Rectangular Carpet" // verified
    const val ROUND_CARPET = "Round Carpet" // verified
    const val EDIT_FURNITURE = "Edit Furniture" // verified
    const val SET_SEQUENCE = "Set Cleaning Sequence" // verified
    const val SAVE_PROMPT = "Save?" // verified
    const val NO = "No" // verified
    const val SAVE = "Save" // verified

    // Map Details toggles.
    const val DETAIL_ROOM_NAME = "Room Name" // verified
    const val DETAIL_FLOOR_TYPE = "Floor Type" // verified
    const val DETAIL_FURNITURE = "Furniture" // verified
    const val DETAIL_OBSTACLE = "Obstacle" // verified
    const val DETAIL_HOME_LAYOUT = "Home Layout" // verified
    const val DETAIL_FLOOR_PLAN_EXTRA = "Floor plan (LocalRock)" // inferred

    // Multi-map and finished-run map.
    const val SWITCH_MAP = "Switch Map" // verified
    const val CREATE_NEW_MAP = "Create New Map" // verified
    const val MAP_CREATING_GUIDE = "Map Creating Guide" // verified
    const val SAVE_AS_NEW_MAP = "Save as new map" // verified
    const val UNSAVED_MAP_FOUND = "Unsaved map found. Tap to review." // verified
    const val CLEANING_ROUTE = "Cleaning Route" // verified
    const val DIRT_MAP = "Dirt Map" // verified
    const val DIRT_LEVEL = "Dirt Level" // verified
}
