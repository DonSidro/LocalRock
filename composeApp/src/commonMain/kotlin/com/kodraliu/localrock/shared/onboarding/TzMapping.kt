package com.kodraliu.localrock.shared.onboarding


private val IANA_TO_POSIX: Map<String, String> = mapOf(
    "America/New_York" to "EST5EDT,M3.2.0,M11.1.0",
    "America/Chicago" to "CST6CDT,M3.2.0,M11.1.0",
    "America/Denver" to "MST7MDT,M3.2.0,M11.1.0",
    "America/Los_Angeles" to "PST8PDT,M3.2.0,M11.1.0",
    "America/Phoenix" to "MST7",
    "America/Anchorage" to "AKST9AKDT,M3.2.0,M11.1.0",
    "Pacific/Honolulu" to "HST10",
    "America/Toronto" to "EST5EDT,M3.2.0,M11.1.0",
    "America/Vancouver" to "PST8PDT,M3.2.0,M11.1.0",
    "America/Winnipeg" to "CST6CDT,M3.2.0,M11.1.0",
    "America/Edmonton" to "MST7MDT,M3.2.0,M11.1.0",
    "Europe/London" to "GMT0BST,M3.5.0/1,M10.5.0",
    "Europe/Berlin" to "CET-1CEST,M3.5.0,M10.5.0/3",
    "Europe/Paris" to "CET-1CEST,M3.5.0,M10.5.0/3",
    "Europe/Amsterdam" to "CET-1CEST,M3.5.0,M10.5.0/3",
    "Asia/Shanghai" to "CST-8",
    "Asia/Tokyo" to "JST-9",
    "Asia/Kolkata" to "IST-5:30",
    "Australia/Sydney" to "AEST-10AEDT,M10.1.0,M4.1.0/3",
    "Australia/Melbourne" to "AEST-10AEDT,M10.1.0,M4.1.0/3",
    "Australia/Perth" to "AWST-8",
)

private val IANA_TO_COUNTRY: Map<String, String> = mapOf(
    "America/New_York" to "us",
    "America/Chicago" to "us",
    "America/Denver" to "us",
    "America/Los_Angeles" to "us",
    "America/Phoenix" to "us",
    "America/Anchorage" to "us",
    "Pacific/Honolulu" to "us",
    "America/Toronto" to "us",
    "America/Vancouver" to "us",
    "America/Winnipeg" to "us",
    "America/Edmonton" to "us",
    "Europe/London" to "gb",
    "Europe/Berlin" to "de",
    "Europe/Paris" to "fr",
    "Europe/Amsterdam" to "nl",
    "Asia/Shanghai" to "cn",
    "Asia/Tokyo" to "jp",
    "Asia/Kolkata" to "in",
    "Australia/Sydney" to "au",
    "Australia/Melbourne" to "au",
    "Australia/Perth" to "au",
)

const val DEFAULT_IANA_TZ: String = "America/New_York"
const val DEFAULT_POSIX_TZ: String = "EST5EDT,M3.2.0,M11.1.0"
const val DEFAULT_COUNTRY_DOMAIN: String = "us"

fun posixTzFromIana(iana: String): String =
    IANA_TO_POSIX[iana.trim()] ?: DEFAULT_POSIX_TZ

fun countryDomainFromIana(iana: String): String =
    IANA_TO_COUNTRY[iana.trim()] ?: DEFAULT_COUNTRY_DOMAIN

fun supportedIanaTimezones(): List<String> = IANA_TO_POSIX.keys.toList()


fun sanitizeStackServer(baseUrl: String): String {
    val trimmed = baseUrl.trim()
    if (trimmed.isEmpty()) throw IllegalArgumentException("Server URL is required")
    val afterScheme = if ("://" in trimmed) trimmed.substringAfter("://") else trimmed
    var host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    if (host.isEmpty()) throw IllegalArgumentException("Server URL has no host: $baseUrl")
    if (host.startsWith("api-", ignoreCase = true)) host = host.substring(4)
    return "$host/"
}
