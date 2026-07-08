package com.kodraliu.localrock.shared.demo

import com.kodraliu.localrock.shared.model.Device
import com.kodraliu.localrock.shared.model.Home
import com.kodraliu.localrock.shared.model.Product
import com.kodraliu.localrock.shared.model.RegionInfo
import com.kodraliu.localrock.shared.model.Room
import com.kodraliu.localrock.shared.model.Rriot
import com.kodraliu.localrock.shared.model.UserData
import com.kodraliu.localrock.shared.vacuum.CleanSummary
import com.kodraliu.localrock.shared.vacuum.ConsumableStatus
import com.kodraliu.localrock.shared.vacuum.VacuumStatus

/**
 * Credentials that put the app into offline **demo mode** on the sign-in screen, so app-store
 * reviewers (and anyone without a self-hosted server) can explore the full interface with
 * fabricated data. Entering these — or tapping the "Explore demo" button — calls
 * [com.kodraliu.localrock.shared.auth.AuthRepository.enterDemo]. No network or MQTT traffic
 * occurs in demo mode.
 */
const val DEMO_EMAIL: String = "demo@localrock.app"
const val DEMO_CODE: String = "000000"

fun isDemoCredentials(email: String, code: String): Boolean =
    email.trim().equals(DEMO_EMAIL, ignoreCase = true) && code.trim() == DEMO_CODE

/**
 * Static, plausible-looking data used to populate the UI while in demo mode. Nothing here is
 * real: the identifiers, keys and tokens are placeholders that are never sent anywhere.
 */
object DemoData {

    // rriot.r.m must be a parseable MQTT URL (see deriveMqttCreds) even though it's never dialed.
    val userData: UserData = UserData(
        uid = 0L,
        token = "demo-token",
        rruid = "demo-rruid",
        nickname = "Demo user",
        rriot = Rriot(
            u = "demo-user",
            s = "demo-session",
            h = "demo-hawk-key",
            k = "demo-rriot-key",
            r = RegionInfo(
                r = "US",
                a = "https://demo.invalid",
                m = "ssl://demo.invalid:8883",
                l = "https://demo.invalid",
            ),
        ),
    )

    private val products: List<Product> = listOf(
        Product(
            id = "demo-prod-s8",
            name = "S8 Pro Ultra",
            model = "roborock.vacuum.a70",
            category = "robot.vacuum.cleaner",
        ),
        Product(
            id = "demo-prod-q7",
            name = "Q7 Max+",
            model = "roborock.vacuum.a38",
            category = "robot.vacuum.cleaner",
        ),
    )

    private val rooms: List<Room> = listOf(
        Room(id = 1L, name = "Living Room"),
        Room(id = 2L, name = "Kitchen"),
        Room(id = 3L, name = "Bedroom"),
        Room(id = 4L, name = "Bathroom"),
        Room(id = 5L, name = "Office"),
    )

    private val devices: List<Device> = listOf(
        Device(
            duid = "demo-vacuum-0001",
            name = "Downstairs",
            localKey = "demolocalkey0001",
            productId = "demo-prod-s8",
            online = true,
            fv = "02.36.00",
            sn = "DEMO0001",
        ),
        Device(
            duid = "demo-vacuum-0002",
            name = "Upstairs",
            localKey = "demolocalkey0002",
            productId = "demo-prod-q7",
            online = true,
            fv = "04.03.94",
            sn = "DEMO0002",
        ),
    )

    val home: Home = Home(
        id = 1L,
        name = "Demo Home",
        rooms = rooms,
        devices = devices,
        products = products,
    )

    /** A healthy, fully-charged robot sitting on its dock. */
    val status: VacuumStatus = VacuumStatus(
        battery = 100,
        state = 8,               // CHARGING
        fanPower = 102,          // BALANCED
        cleanArea = 32_000_000L, // mm² (~32 m²)
        cleanTime = 3_600L,      // seconds
        errorCode = 0,
        waterBoxCustomMode = 202,
        chargeStatus = 1,
        dryStatus = 0,
    )

    val consumable: ConsumableStatus = ConsumableStatus(
        mainBrushWorkTime = 55L * 3600,
        sideBrushWorkTime = 30L * 3600,
        filterWorkTime = 40L * 3600,
        sensorDirtyTime = 8L * 3600,
    )

    val cleanSummary: CleanSummary = CleanSummary(
        totalTimeSec = 356_400L,      // ~99 hours
        totalAreaMm2 = 1_240_000_000L,
        totalCount = 148,
    )
}
