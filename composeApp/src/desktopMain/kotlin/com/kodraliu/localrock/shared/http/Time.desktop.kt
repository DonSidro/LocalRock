package com.kodraliu.localrock.shared.http

internal actual fun currentEpochSeconds(): Long = System.currentTimeMillis() / 1000L
