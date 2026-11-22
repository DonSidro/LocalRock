package com.kodraliu.localrock.ui.strings

/** One command on the official Voice Commands page: a heading and the phrases that trigger it. */
data class VoiceCommand(val title: String, val phrases: List<String>)

data class VoiceCommandGroup(val title: String, val commands: List<VoiceCommand>)

// The plugin's English voice command list for non-Beryl robots, in its order; misspellings ("vaccum") are the plugin's own.
object VoiceCommandStrings {
    val GROUPS: List<VoiceCommandGroup> = listOf(
        VoiceCommandGroup(
            "Clean", // verified
            listOf(
                VoiceCommand("Start cleaning", listOf("Start cleaning")), // verified
                VoiceCommand("Vac & Mop", listOf("Vacuum and mop")), // verified
                VoiceCommand("Vacuum", listOf("just vacuum", "vacuum only")), // verified
                VoiceCommand("Mop", listOf("just mop", "mop only")), // verified
                VoiceCommand("clean here", listOf("clean here")), // verified
                VoiceCommand("skip here", listOf("don't clean here", "skip here")), // verified
                VoiceCommand("start mapping", listOf("create a map", "start mapping")), // verified
            ),
        ),
        VoiceCommandGroup(
            "Dock Control", // verified
            listOf(
                VoiceCommand("go back to dock", listOf("go back to dock", "charge")), // verified
                VoiceCommand("empty the dustbin", listOf("empty the dustbin")), // verified
                VoiceCommand("wash the mop", listOf("wash the mop")), // verified
                VoiceCommand("dry the mop", listOf("dry the mop")), // verified
                VoiceCommand("stop drying", listOf("stop drying")), // verified
            ),
        ),
        VoiceCommandGroup(
            "Pause and resume", // verified
            listOf(
                VoiceCommand("pause", listOf("pause")), // verified
                VoiceCommand("resume", listOf("resume")), // verified
            ),
        ),
        VoiceCommandGroup(
            "Robot Settings", // verified
            listOf(
                VoiceCommand("increase vacuum intensity", listOf("increase vacuum intensity", "vaccum harder")), // verified, plugin has a trailing space on the first phrase
                VoiceCommand("reduce vacuum intensity", listOf("reduce vacuum intensity", "vaccum softer")), // verified
                VoiceCommand("increase mopping intensity", listOf("increase mopping intensity", "mop harder")), // verified
                VoiceCommand("reduce mopping intensity", listOf("reduce mopping intensity", "mop softer")), // verified
                VoiceCommand("volume up", listOf("volume up", "speak louder")), // verified
                VoiceCommand("volume down", listOf("volume down", "speak softer")), // verified
            ),
        ),
        VoiceCommandGroup(
            "Room Cleaning", // verified
            listOf(
                VoiceCommand("Bedroom", listOf("clean the bedroom")), // verified
                VoiceCommand("Living room", listOf("clean the living room")), // verified
                VoiceCommand("Dining room", listOf("clean the dining room")), // verified
                VoiceCommand("kitchen", listOf("clean the kitchen")), // verified
                VoiceCommand("Bathroom", listOf("clean the bathroom")), // verified
                VoiceCommand("Sunroom", listOf("clean the sunroom")), // verified
                VoiceCommand("Master bedroom", listOf("clean the master bedroom")), // verified
                VoiceCommand("Guest bedroom", listOf("clean the guest bedroom")), // verified
                VoiceCommand("Corridor", listOf("clean the corridor")), // verified
                VoiceCommand("Hall", listOf("clean the hall", "clean the lobby")), // verified
                VoiceCommand("Study", listOf("clean the study")), // verified
            ),
        ),
    )

    /** Every phrase, as the official page pools them for its example chips. */
    val ALL_PHRASES: List<String> get() = GROUPS.flatMap { g -> g.commands.flatMap { it.phrases } }
}
