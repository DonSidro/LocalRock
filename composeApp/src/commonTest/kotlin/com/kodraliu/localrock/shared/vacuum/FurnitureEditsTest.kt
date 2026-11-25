package com.kodraliu.localrock.shared.vacuum

import com.kodraliu.localrock.shared.protocol.V1Response
import com.kodraliu.localrock.shared.vacuum.map.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.test.*

/** Synthetic rooms/coordinates only. Wire structure and catalog codes follow the observed contract. */
class FurnitureEditsTest {
    private fun placement(key: Int, type: Int = 48, subtype: Int = 2, x: Int = 4000, y: Int = 4000): FurniturePlacement =
        newFurniture(-1, requireNotNull(furnitureKind(type, subtype)), x, y).copy(key = key)

    private fun record(item: FurniturePlacement, id: Int = item.key, flag: Int = 1): MapFurniture = MapFurniture(
        item.corners.flatMap { p -> listOf(p.x and 255, p.x ushr 8, p.y and 255, p.y ushr 8) } +
            listOf(17, 39, item.type, item.subtype, flag, id, 173),
    )
    private fun map(vararg items: MapFurniture): ParsedMap {
        val header = MutableList(12) { 0 }.also { it[0] = 25; it[2] = 12; it[8] = items.size }
        return ParsedMap(100, 100, ByteArray(10000) { 128.toByte() }, emptyList(), .05f,
            format = MapFormat.LEGACY, furnitureBlocks = listOf(MapFurnitureBlock(header, items.flatMap { it.rawBytes })))
    }
    private fun snapshot(vararg items: MapFurniture) = FurnitureSnapshot(map(*items), FurnitureSupport.SUPPORTED)
    private fun ok() = V1Response(1, JsonArray(listOf(JsonPrimitive("ok"))), null)

    @Test fun catalog_has_all_21_distinct_observed_choices() {
        val codes = listOf(45 to 2, 45 to 1, 43 to 0, 46 to 3, 46 to 2, 46 to 1, 46 to 4, 46 to 5,
            47 to 0, 48 to 2, 48 to 1, 49 to 0, 51 to 0, 50 to 0, 52 to 0, 53 to 0, 54 to 0,
            55 to 0, 56 to 0, 57 to 0, 58 to 0)
        assertEquals(codes, FURNITURE_CATALOG.map { it.type to it.subtype })
        FURNITURE_CATALOG.forEach { assertTrue(validFurniturePlacement(newFurniture(-1, it, 20000, 20000))) }
    }

    @Test fun move_rotate_and_resize_keep_the_same_id_type_and_center() {
        val original = placement(7)
        val rotated = original.rotated(37.0)
        val resized = rotated.resized(1200.0, 800.0)
        assertEquals(7, resized.key)
        assertTrue(validFurniturePlacement(resized))
        assertTrue(abs(resized.widthMm - 1200) < 2)
        assertTrue(abs(resized.depthMm - 800) < 2)
        assertEquals(original.corners.sumOf { it.x }, resized.corners.sumOf { it.x })
        assertEquals(original.corners.sumOf { it.y }, resized.corners.sumOf { it.y })
        assertTrue(resized.contains(4000, 4000))
        assertFalse(resized.contains(6000, 6000))
        assertEquals(resized.corners.map { ParsedMapPoint(it.x + 100, it.y - 50) }, resized.moved(100, -50).corners)
        assertFalse(validFurniturePlacement(resized.moved(-10000, 0)))
        assertFalse(validFurniturePlacement(original.resized(50.0, 300.0)))
        assertFalse(validFurniturePlacement(original.copy(corners = original.corners.reversed().take(3))))
    }

    @Test fun operations_use_stable_ids_and_only_the_changed_records() {
        val one = record(placement(1))
        val seven = record(placement(7, x = 7000))
        val hidden = record(placement(13, 46, 1), flag = 0)
        val unknown = record(placement(19).copy(type = 247, subtype = 9))
        val before = snapshot(one, seven, hidden, unknown)
        val changed = placement(7, x = 7000).moved(100, -50)
        val added = placement(-1, 56, 0, 9000, 9000)
        val plan = furnitureEditPlan(before, listOf(changed, added))
        val data = plan.params()["data"]!!.jsonArray
        assertEquals(3, data.size)
        assertEquals(Json.parseToJsonElement("[1,7,6350,4700,7850,4700,7850,3200,6350,3200,48,2,1]"), data[0])
        assertEquals(-1, data[1].jsonArray[1].jsonPrimitive.int)
        assertEquals(Json.parseToJsonElement("[0,1]"), data[2])
        assertEquals(setOf("data", "map_flag"), plan.params().keys)
        assertEquals(JsonPrimitive(0), plan.params()["map_flag"])
        assertEquals(listOf(1, 7), before.placements.map { it.key })
        assertEquals(listOf(hidden.rawBytes, unknown.rawBytes), before.map.furniture.takeLast(2).map { it.rawBytes })
    }

    @Test fun unknown_hidden_duplicate_and_forged_targets_cannot_be_edited() {
        val item = record(placement(7))
        val hidden = record(placement(9), flag = 0)
        val base = snapshot(item, hidden)
        assertFailsWith<MapEditException> { furnitureEditPlan(base, listOf(placement(7), placement(9))) }
        assertFailsWith<MapEditException> { furnitureEditPlan(base, listOf(placement(6))) }
        assertFailsWith<MapEditException> { furnitureEditPlan(base, listOf(placement(7, 45, 1))) }
        assertFailsWith<MapEditException> { furnitureEditPlan(base, listOf(placement(7), placement(7))) }
        assertNotNull(furnitureMapBlocker(map(item, item), 0))
        assertNotNull(furnitureMapBlocker(map(item), 1))
        assertNotNull(furnitureMapBlocker(map(item), null))
        assertNotNull(furnitureMapBlocker(map(item).copy(format = MapFormat.B01), 0))
        assertNotNull(furnitureMapBlocker(map(item).copy(furnitureBlocks = listOf(MapFurnitureBlock(List(8) { 0 }, item.rawBytes))), 0))
    }

    @Test fun readback_checks_add_delete_update_and_preserves_hidden_and_unknown_bytes() {
        val old = record(placement(7))
        val removed = record(placement(3, 43, 0))
        val hidden = record(placement(13), flag = 0)
        val unknown = record(placement(19).copy(type = 247, subtype = 9))
        val before = snapshot(old, removed, hidden, unknown)
        val edited = placement(7).rotated(30.0)
        val added = placement(-1, 58, 0, 9000, 9000)
        val plan = furnitureEditPlan(before, listOf(edited, added))
        val after = map(record(added, id = 3), unknown, record(edited), hidden)
        assertTrue(furnitureEditsConfirmed(plan, after)) // Assigned ID may reuse a deleted ID.
        assertFalse(furnitureEditsConfirmed(plan, before.map))
        assertFalse(furnitureEditsConfirmed(plan, map(record(edited), record(added, id = 14), unknown)))
        assertFalse(furnitureEditsConfirmed(plan, map(record(edited), record(added, id = 14), unknown,
            MapFurniture(hidden.rawBytes.toMutableList().also { it[22] = 174 }))))
        assertFalse(furnitureEditsConfirmed(plan, map(record(edited.moved(1000, 0)), record(added, id = 14), hidden, unknown)))
    }

    @Test fun two_overlapping_additions_require_two_distinct_readback_records() {
        val base = snapshot()
        val a = placement(-1)
        val b = placement(-2).moved(60, 0)
        val plan = furnitureEditPlan(base, listOf(a, b))
        assertTrue(furnitureEditsConfirmed(plan, map(record(a.moved(30, 0), 1), record(a.moved(-30, 0), 2))))
        assertFalse(furnitureEditsConfirmed(plan, map(record(a, 1))))
    }

    @Test fun unchanged_readback_does_not_confirm_a_small_nudge_inside_the_tolerance() {
        val before = snapshot(record(placement(7)))
        val changed = placement(7).moved(50, 0)
        val plan = furnitureEditPlan(before, listOf(changed))
        assertFalse(furnitureEditsConfirmed(plan, before.map))
        assertTrue(furnitureEditsConfirmed(plan, map(record(changed))))
    }

    @Test fun stale_map_aborts_before_any_write_and_no_op_sends_nothing() = runTest {
        val base = snapshot(record(placement(7)))
        var writes = 0
        assertFailsWith<MapEditException> {
            commitFurnitureEdits(base, listOf(placement(7).moved(100, 0)),
                { snapshot(record(placement(7).moved(50, 0))) }, { _, _ -> writes++; ok() })
        }
        assertEquals(0, writes)
        assertEquals(base, commitFurnitureEdits(base, base.placements, { base }, { _, _ -> writes++; ok() }))
        assertEquals(0, writes)
    }

    @Test fun unknown_or_unsupported_capability_never_starts_an_edit_session() = runTest {
        val base = snapshot(record(placement(7)))
        val edited = listOf(placement(7).moved(100, 0))
        for (support in listOf(FurnitureSupport.UNKNOWN, FurnitureSupport.UNSUPPORTED)) {
            var reads = 0
            val calls = mutableListOf<String>()
            assertFailsWith<MapEditException> {
                commitFurnitureEdits(base.copy(support = support), edited,
                    { reads++; base }, { method, _ -> calls += method; ok() })
            }
            assertEquals(0, reads)
            assertTrue(calls.isEmpty())
            // A supported draft cannot authorize a write after the fresh check loses support.
            assertFailsWith<MapEditException> {
                commitFurnitureEdits(base, edited,
                    { reads++; base.copy(support = support) }, { method, _ -> calls += method; ok() })
            }
            assertEquals(1, reads)
            assertTrue(calls.isEmpty())
        }
    }

    @Test fun a_failed_capability_or_map_read_cannot_use_the_supported_draft_to_write() = runTest {
        val base = snapshot(record(placement(7)))
        val calls = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            commitFurnitureEdits(base, listOf(placement(7).moved(100, 0)),
                { throw IllegalStateException("robot unavailable") }, { method, _ -> calls += method; ok() })
        }
        assertTrue(calls.isEmpty())
    }

    @Test fun save_is_bracketed_once_and_waits_for_a_fresh_confirming_map() = runTest {
        val base = snapshot(record(placement(7)))
        val edited = placement(7).moved(100, 0)
        val saved = snapshot(record(edited))
        val calls = mutableListOf<String>(); var reads = 0
        val result = commitFurnitureEdits(base, listOf(edited),
            { reads++; if (reads < 3) base else saved }, { method, _ -> calls += method; ok() }, readbackDelay = {})
        assertEquals(saved, result)
        assertEquals(listOf("start_edit_map", "save_furnitures", "end_edit_map"), calls)
        assertEquals(3, reads)
    }

    @Test fun ambiguous_save_and_cancellation_close_the_session_without_repeating_the_write() = runTest {
        for (failure in listOf<Throwable>(IllegalStateException("timeout"), CancellationException("cancel"))) {
            val base = snapshot(record(placement(7))); val calls = mutableListOf<String>()
            try {
                commitFurnitureEdits(base, listOf(placement(7).moved(100, 0)), { base }, { method, _ ->
                    calls += method
                    if (method == "save_furnitures") throw failure
                    ok()
                })
                fail("Expected the save failure")
            } catch (e: Throwable) { assertSame(failure, e) }
            assertEquals(listOf("start_edit_map", "save_furnitures", "end_edit_map"), calls)
        }
    }

    @Test fun malformed_ack_and_rpc_error_never_report_success() = runTest {
        val base = snapshot(record(placement(7)))
        for (result in listOf<JsonElement?>(null, JsonPrimitive("ok"), JsonArray(listOf(JsonPrimitive("OK"))), JsonArray(emptyList()))) {
            val calls = mutableListOf<String>()
            assertFailsWith<MapEditException> {
                commitFurnitureEdits(base, listOf(placement(7).moved(100, 0)), { base }, { method, _ ->
                    calls += method
                    if (method == "save_furnitures") V1Response(1, result, null) else ok()
                })
            }
            assertEquals(listOf("start_edit_map", "save_furnitures", "end_edit_map"), calls)
        }
        assertFailsWith<RobotCommandException> {
            commitFurnitureEdits(base, listOf(placement(7).moved(100, 0)), { base }, { _, _ ->
                V1Response(1, ok().result, JsonObject(mapOf("code" to JsonPrimitive(-1))))
            })
        }
    }

    @Test fun missing_readback_does_not_succeed_with_cached_data_or_retry_the_save() = runTest {
        val base = snapshot(record(placement(7)))
        var writes = 0; var reads = 0
        assertFailsWith<MapEditException> {
            commitFurnitureEdits(base, listOf(placement(7).moved(100, 0)),
                { reads++; if (reads == 1) base else throw IllegalStateException("map unavailable") },
                { method, _ -> if (method == "save_furnitures") writes++; ok() }, readbackDelay = {})
        }
        assertEquals(1, writes)
        assertEquals(5, reads)
    }

    @Test fun a_lost_start_ack_gets_cleanup_but_an_explicit_refusal_does_not_send_a_save() = runTest {
        val base = snapshot(record(placement(7)))
        for (refused in listOf(false, true)) {
            val calls = mutableListOf<String>()
            assertFailsWith<Exception> {
                commitFurnitureEdits(base, listOf(placement(7).moved(100, 0)), { base }, { method, _ ->
                    calls += method
                    if (method == "start_edit_map") {
                        if (!refused) throw IllegalStateException("lost acknowledgement")
                        V1Response(1, null, JsonObject(mapOf("code" to JsonPrimitive(-1))))
                    } else ok()
                })
            }
            assertEquals(if (refused) listOf("start_edit_map") else listOf("start_edit_map", "end_edit_map"), calls)
        }
    }
}
