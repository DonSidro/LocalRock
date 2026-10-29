# Furniture on the map

The Furniture layer and editor use all 21 S7 MaxV picker choices: two beds, TV stand,
three sofas, two chaise sofas, dining set, square and coffee tables, shoe rack,
wardrobe, nightstand, two litter boxes, pet cage, pet bed, pet bowls, floor mirror,
and cat tower. The vector illustrations use a uniform gray fill with transparent detail cutouts.
Saved four-corner footprints control position, rotation and dimensions.

Open **Edit map → Furniture**, using the tile below the zone tools. Save or discard
any pending zone edits first. The furniture editor loads a fresh map. Add an item
from the catalog, then tap it or choose it from **Items**. Its padded, dashed
selection box has four controls: top left deletes, top right rotates, bottom left
moves, and bottom right independently adjusts width and height. Drag the controls
to place the item; dragging the item itself also moves it. The padding is visual
only and does not change the saved furniture footprint. Pinch to zoom or drag empty
map space to pan. VoiceOver and TalkBack expose precise movement, rotation and size
steps through the corner controls' accessibility actions.

The selected item's **Done** button finishes placement. **Save** writes the changes,
checks the returned map and returns to Edit map with a confirmation. **Back** asks
before discarding unsaved edits; **Done** in the toolbar returns to Edit map when
there are no pending changes. A failed or unconfirmed save keeps the editor open
and requires a reload before another save.
Small-screen controls scroll without covering the whole map. Starting dimensions
are convenient LocalRock sizes based on observed saved footprints; they are not
verified OEM defaults or minimum/maximum limits.

Furniture editing supports the legacy 23-byte furniture layout with
stable object IDs and one saved map (map flag 0). It requires a finished cleaning
task and a loaded saved map. Unknown record layouts block editing. Hidden,
unknown-type, or malformed furniture stays untouched and is never included in
an edit payload. Unknown visible types keep a neutral footprint.

Saves use the observed `start_edit_map` / `save_furnitures` / `end_edit_map`
sequence. The payload contains only add/update/delete operations, using byte 21
as the persistent ID rather than a list index. Fresh status, map-list and map
reads precede each save. A changed base map aborts the write. Session cleanup is
attempted even if saving is cancelled or fails. Lost acknowledgements never
cause an automatic write retry; the editor requires an explicit reload before
another save. Success requires fresh map readback including preservation of
untouched and hidden records. Restriction sets and room definitions are not
rewritten by furniture editing.

