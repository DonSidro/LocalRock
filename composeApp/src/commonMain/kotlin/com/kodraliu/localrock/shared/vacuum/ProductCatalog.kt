package com.kodraliu.localrock.shared.vacuum

/**
 * Maps the technical model string the robot reports (`roborock.vacuum.a101`) to the name Roborock
 * actually sells it under ("Roborock Qrevo Pro"), so the app can show the latter.
 *
 * Ported from local_roborock_server's `product_catalog.json`. A model not in this list is shown as
 * reported rather than hidden, since a newly released robot is still worth identifying by something.
 */
object ProductCatalog {
    private val names: Map<String, String> = mapOf(
        "roborock.vacuum.a08" to "Roborock S6 Pure",
        "roborock.vacuum.a10" to "Roborock S6 MaxV",
        "roborock.vacuum.a101" to "Roborock Qrevo Pro",
        "roborock.vacuum.a104" to "Roborock Qrevo S",
        "roborock.vacuum.a117" to "Roborock Qrevo Master",
        "roborock.vacuum.a123" to "Roborock Qrevo Plus",
        "roborock.vacuum.a125" to "Roborock Q5 Max+",
        "roborock.vacuum.a135" to "Roborock Qrevo Curv",
        "roborock.vacuum.a140" to "Roborock Q5 DuoRoller+",
        "roborock.vacuum.a144" to "Roborock Saros 10R",
        "roborock.vacuum.a147" to "Roborock Saros 10",
        "roborock.vacuum.a15" to "Roborock S7",
        "roborock.vacuum.a156" to "Roborock Qrevo Edge",
        "roborock.vacuum.a159" to "Roborock Qrevo Curv Series",
        "roborock.vacuum.a168" to "Roborock Qrevo L",
        "roborock.vacuum.a170" to "Roborock Qrevo C",
        "roborock.vacuum.a179" to "Roborock Saros Z70",
        "roborock.vacuum.a185" to "Roborock Qrevo CurvX",
        "roborock.vacuum.a187" to "Roborock Qrevo Edge Series",
        "roborock.vacuum.a19" to "Roborock S4 Max",
        "roborock.vacuum.a197" to "Roborock Qrevo EdgeT",
        "roborock.vacuum.a199" to "Roborock QV 35A",
        "roborock.vacuum.a205" to "Roborock QR 598",
        "roborock.vacuum.a21" to "Roborock Qrevo Slim",
        "roborock.vacuum.a245" to "Roborock Qrevo Curv 2 Flow",
        "roborock.vacuum.a246" to "Roborock QX Revo Arc",
        "roborock.vacuum.a27" to "Roborock S7 MaxV",
        "roborock.vacuum.a279" to "Roborock Saros 20 Sonic",
        "roborock.vacuum.a288" to "Roborock Saros 20",
        "roborock.vacuum.a294" to "Roborock Qrevo 2 Pro Series",
        "roborock.vacuum.a296" to "Roborock Qrevo Edge 3 Pro",
        "roborock.vacuum.a298" to "Roborock Qrevo Edge 2",
        "roborock.vacuum.a30" to "Roborock G10",
        "roborock.vacuum.a307" to "Roborock Saros 20 Flow",
        "roborock.vacuum.a326" to "Roborock Qrevo S Pro Series",
        "roborock.vacuum.a34" to "Roborock Q5",
        "roborock.vacuum.a38" to "Roborock Q7 Max",
        "roborock.vacuum.a40" to "Roborock Q7",
        "roborock.vacuum.a51" to "Roborock S8",
        "roborock.vacuum.a65" to "Roborock S7 Max Ultra",
        "roborock.vacuum.a70" to "Roborock S8 Pro Ultra",
        "roborock.vacuum.a72" to "Roborock Q5 Pro",
        "roborock.vacuum.a73" to "Roborock Q8 Max",
        "roborock.vacuum.a75" to "Roborock Q Revo",
        "roborock.vacuum.a87" to "Roborock Qrevo MaxV",
        "roborock.vacuum.a95" to "Roborock S8 Max Ultra",
        "roborock.vacuum.a97" to "Roborock S8 MaxV Ultra",
        "roborock.vacuum.s4" to "Roborock S4",
        "roborock.vacuum.s5e" to "Roborock S5 Max",
        "roborock.vacuum.s6" to "Roborock S6",
        "roborock.vacuum.sc01" to "Roborock Q7 Series",
        "roborock.vacuum.sc05" to "Roborock Q7 Series",
        "roborock.vacuum.ss07" to "Roborock Q10 Series",
        "roborock.vacuum.ss09" to "Roborock Q10 Series",
    )

    /** The name Roborock sells [model] under, or [model] itself when it isn't in the catalog. */
    fun displayName(model: String?): String? = model?.let { names[it] ?: it }
}
