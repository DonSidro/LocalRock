package com.kodraliu.localrock

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform