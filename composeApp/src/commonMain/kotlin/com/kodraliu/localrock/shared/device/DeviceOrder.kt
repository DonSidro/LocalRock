package com.kodraliu.localrock.shared.device

import com.kodraliu.localrock.shared.model.Device

/**
 * The robots in the order My vacuums lists them: by name, ignoring case, with numbers in a name
 * compared as numbers so "Robot 2" comes before "Robot 10". The server returns them in pairing order,
 * which puts a newly paired robot last whatever it is called (#133). Equal names keep a stable order
 * by device id.
 */
internal fun List<Device>.sortedForList(): List<Device> =
    sortedWith { a, b -> naturalCompare(a.name, b.name).takeIf { it != 0 } ?: a.duid.compareTo(b.duid) }

internal fun naturalCompare(a: String, b: String): Int {
    val x = a.trim().lowercase()
    val y = b.trim().lowercase()
    var i = 0
    var j = 0
    while (i < x.length && j < y.length) {
        if (x[i].isDigit() && y[j].isDigit()) {
            val si = i
            val sj = j
            while (i < x.length && x[i].isDigit()) i++
            while (j < y.length && y[j].isDigit()) j++
            val nx = x.substring(si, i).trimStart('0')
            val ny = y.substring(sj, j).trimStart('0')
            if (nx.length != ny.length) return nx.length - ny.length
            val byDigits = nx.compareTo(ny)
            if (byDigits != 0) return byDigits
        } else {
            if (x[i] != y[j]) return x[i].compareTo(y[j])
            i++
            j++
        }
    }
    return (x.length - i) - (y.length - j)
}
