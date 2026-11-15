package com.kodraliu.localrock.ui.vacuum


expect fun formatCleanRecordTime(epochSeconds: Long): String

/** The device zone's offset from UTC at [epochSeconds], for stamps formatted in common code. */
expect fun localUtcOffsetSeconds(epochSeconds: Long): Int
