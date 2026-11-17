package com.kodraliu.localrock.shared.notify

/**
 * The desktop app has no background service; it only watches robots while its window is open, and
 * then shows alerts inside the app. System notifications are not wired up yet.
 */
private object DesktopRobotNotifier : RobotNotifier {
    override fun post(notification: RobotNotification) = Unit
    override fun showProgress(progress: RobotProgress) = Unit
    override fun clearProgress(duid: String) = Unit
}

actual fun platformRobotNotifier(): RobotNotifier = DesktopRobotNotifier

actual val backgroundAlertsSupported: Boolean = false
