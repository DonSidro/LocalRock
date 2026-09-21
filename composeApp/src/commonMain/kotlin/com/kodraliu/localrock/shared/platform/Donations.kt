package com.kodraliu.localrock.shared.platform

/**
 * Whether this build may show a link asking for a donation.
 *
 * Android only. App Store review treats an external donation link in a free app as a way around
 * in-app purchase (guideline 3.2.2), so the iOS build must contain no donation UI and no donation
 * URL at all — hence an expect/actual split rather than a runtime check, so the string never ends
 * up in the iOS binary.
 */
expect val donationsEnabled: Boolean

/** Where [donationsEnabled] builds send people. Empty on platforms that must not link out. */
expect val donationUrl: String
