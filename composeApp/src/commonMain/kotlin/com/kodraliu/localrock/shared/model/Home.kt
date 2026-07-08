package com.kodraliu.localrock.shared.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Home(
    val id: Long,
    val name: String,
    val rooms: List<Room> = emptyList(),
    val devices: List<Device> = emptyList(),
    val receivedDevices: List<Device> = emptyList(),
    val products: List<Product> = emptyList(),
    @SerialName("received_devices") val receivedDevicesSnake: List<Device> = emptyList(),
)

@Serializable
data class Room(
    val id: Long,
    val name: String,
)


@Serializable
data class Device(
    val duid: String,
    val name: String,
    val localKey: String? = null,
    val productId: String? = null,
    val online: Boolean = false,
    val deviceStatus: Map<String, Int>? = null,
    val featureSet: String? = null,
    val newFeatureSet: String? = null,
    val share: Boolean? = null,
    val extra: String? = null,
    val sn: String? = null,
    val pv: String? = null,
    val fv: String? = null,
    val activeTime: Long? = null,
    val timeZoneId: String? = null,
    val silentOtaSwitch: Boolean? = null,
    val f: Boolean? = null,
    val createTime: Long? = null,
)

@Serializable
data class Product(
    val id: String,
    val name: String? = null,
    val model: String? = null,
    val category: String? = null,
    val code: String? = null,
    val iconUrl: String? = null,
    val capability: Int? = null,
    val schema: List<DeviceSchema> = emptyList(),
)

@Serializable
data class DeviceSchema(
    val id: Int,
    val name: String? = null,
    val code: String? = null,
    val mode: String? = null,
    val type: String? = null,
    @SerialName("product_property") val productProperty: String? = null,
    val property: String? = null,
    val desc: String? = null,
)
