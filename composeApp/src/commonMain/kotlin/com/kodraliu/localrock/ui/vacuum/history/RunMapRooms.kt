package com.kodraliu.localrock.ui.vacuum

import com.kodraliu.localrock.shared.vacuum.map.ParsedMap
import com.kodraliu.localrock.shared.vacuum.map.ParsedMapRoom

/** A run map carries bare segment ids; names and room types come from the current rooms, matched by id. */
internal fun ParsedMap.withRoomNames(known: List<ParsedMapRoom>): ParsedMap {
    if (known.isEmpty() || rooms.isEmpty()) return this
    val byId = known.associateBy { it.id }
    return copy(rooms = rooms.map { room -> byId[room.id]?.let { room.copy(name = it.name, tag = it.tag ?: room.tag) } ?: room })
}
