package com.kodraliu.localrock.shared.http

import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970

internal actual fun currentEpochSeconds(): Long = NSDate().timeIntervalSince1970.toLong()
