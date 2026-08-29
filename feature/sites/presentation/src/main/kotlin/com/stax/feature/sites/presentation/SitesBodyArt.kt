package com.stax.feature.sites.presentation

import androidx.compose.ui.geometry.Offset
import com.stax.core.domain.BodyRegion
import com.stax.core.domain.Sublocation

/**
 * The §4.12.4 figure, as SVG path data in one fixed viewport.
 *
 * Everything the body map draws lives here and nowhere else: the silhouette, the muscle groups that
 * make it read as a body rather than a gingerbread outline, and one injection zone per region of
 * §5.8.6. Only the **right** side of the figure is written down — the left is the same data with `x`
 * mirrored about the centre line, which is what keeps the two halves identical and the file half as
 * long (see `SitesBodyMap`).
 *
 * Coordinates are the [VIEWPORT_WIDTH] × [VIEWPORT_HEIGHT] space, not fractions, because path data is
 * far easier to read and to edit in whole units. The renderer scales it to whatever bounds it is
 * given, so the figure is sharp at every size and the dots travel with it.
 *
 * The proportions are the canonical eight-head standing figure — head `4..31`, shoulders two heads
 * across, navel `96`, crotch `126`, knee `176`, sole `240` — which is the whole reason it reads as a
 * person. Move a landmark and the rest has to move with it.
 */
internal object BodyArt {

    const val VIEWPORT_WIDTH = 120f
    const val VIEWPORT_HEIGHT = 248f

    /** Head, neck, one shoulder, one side of the trunk and one leg, closed up the centre line. */
    const val TORSO = "M 60,4 " +
        "C 65,4 69.5,10 69.5,17 " +
        "C 69.5,23.5 67.5,28 64,30.5 " +
        "C 65,33 65,35 65,40 " +
        "C 69.5,41.5 74,43 78,46 " +
        "C 81,49 82.5,55 82.5,62 " +
        "C 82.5,72 81,84 77,95 " +
        "C 79,102 83,109 84,116 " +
        "C 84.5,124 83,132 81.5,140 " +
        "C 79,152 76,164 75,176 " +
        "C 75.5,182 76,186 76,190 " +
        "C 75.5,202 73,216 71,228 " +
        "C 71,232 71.5,235 73.5,237 " +
        "C 75,238.5 74.5,240 72.5,240 " +
        "L 62.5,240 " +
        "C 61.5,240 61,238.5 61.5,236.5 " +
        "C 62,233 63,231 63.5,228 " +
        "C 62.5,214 61.5,200 62,190 " +
        "C 62.5,184 63.5,180 64,176 " +
        "C 63.5,158 62,140 60.5,126 " +
        "L 60,126 Z"

    /**
     * One arm, hanging with the elbow clear of the waist and the fingertips at mid-thigh.
     *
     * Its own closed shape rather than part of [TORSO]: a hanging arm crosses the hip, and traced as
     * one outline that crossing is a self-intersection which fills the gap at the waist solid.
     */
    const val ARM = "M 78,44 " +
        "C 86,45 96,49 97,60 " +
        "C 97.5,72 96.5,84 95.5,94 " +
        "C 94.5,102 93.5,104 93,106 " +
        "C 91.5,116 90,128 89,138 " +
        "C 90,145 90,153 86,156 " +
        "C 82.5,157 81.5,150 82,140 " +
        "C 83,128 84.5,116 85.5,106 " +
        "C 86,96 87,72 86,56 Z"

    /**
     * The muscle groups of the Front tab, drawn a shade off the body and clipped to it.
     *
     * They carry no data — a dose is logged against a site, not a muscle — but they are what turns a
     * silhouette into something the user can find a deltoid on, which is the whole job of the map.
     */
    val FRONT_MUSCLES = listOf(
        "M 78,45 C 86,45 96,52 96.5,62 C 97,71 94,76 89.5,76 C 84,76 79,65 78,52 Z", // deltoid
        "M 61,55 C 68,53 74.5,55 77.5,60 C 79.5,65 76.5,73 71.5,76 C 66.5,78 62,78 61,77 Z", // pectoral
        "M 61,81 C 66,80 70.5,81.5 71.5,85 C 72,88 71.5,90 70.5,90.5 C 67,91.5 61,91.5 61,91 Z",
        "M 61,93.5 C 66,93 70.5,94 71,97 C 71.5,100 71,102 70,102.5 C 66.5,103.5 61,103.5 61,103 Z",
        "M 61,105.5 C 65.5,105 69.5,106 70,109 C 70.5,113 69,117 66.5,118 C 64,119 61,118.5 61,118 Z",
        "M 73,84 C 79,87 81,93 80,101 C 79,107 76,110 73,109 Z", // oblique
        "M 86,80 C 91.5,81 94.5,86 94,95 C 93.5,102 91.5,105 89.5,105 C 87,104.5 85.5,95 86,86 Z", // biceps
        "M 84.5,110 C 89.5,111 92.5,118 91.5,129 C 90.5,135 88.5,138 87,137 C 85,135 83.5,122 84.5,115 Z",
        "M 61.5,129 C 70,127 80,133 80,146 C 80,159 76,169 70,171 C 65,172 61.5,162 61.5,148 Z", // quadriceps
        "M 65,175 C 70,174 74,177 74,181 C 74,185 70,187 66,186 C 63,185 63,177 65,175 Z", // knee
        "M 63.5,187 C 69,187 73,196 73,208 C 73,217 70.5,221 68,221 C 65,220 63.5,206 63.5,196 Z", // shin
    )

    /** The Back tab's muscle groups — the half of §4.12.4 the Front tab cannot show. */
    val BACK_MUSCLES = listOf(
        "M 61.5,42 C 69,43.5 77,47 80,52 C 81.5,57 77.5,61 72,63 C 66,64.5 62.5,64.5 61.5,64 Z", // trapezius
        "M 78,45 C 86,45 96,52 96.5,62 C 97,71 94,76 89.5,76 C 84,76 79,65 78,52 Z", // deltoid
        "M 62,68 C 70,71 79,78 80,88 C 80.5,94.5 74.5,98 68.5,98 C 64.5,98 62,95 62,92 Z", // latissimus
        "M 61.5,103 C 66.5,102 71,106 71.5,112 C 72,117 69,121 65,121.5 C 62.5,122 61.5,121 61.5,119 Z",
        "M 86,80 C 91.5,81 94.5,86 94,95 C 93.5,102 91.5,105 89.5,105 C 87,104.5 85.5,95 86,86 Z", // triceps
        "M 61,108 C 70,106 80,113 81,124 C 82,135 76,142 68,142 C 63,142 61,135 61,124 Z", // gluteus
        "M 61.5,146 C 70,145 78,151 78,161 C 78,171 74,177 68,177 C 64,177 61.5,167 61.5,156 Z", // hamstring
        "M 62.5,183 C 69,183 74,192 74,205 C 74,214 71,220 68,220 C 64.5,220 62.5,207 62.5,194 Z", // calf
    )

    /**
     * Where a site is injected, and where its dot goes.
     *
     * The zone is the patch of body a dose actually lands in — a hand's width of abdomen, the outer
     * third of a thigh — so the map answers "where on me" and not only "which of fourteen rows".
     * [center] is the dot: the middle of that patch, and the point the hit test measures from.
     */
    data class Zone(val outline: String, val center: Offset)

    /**
     * The zone a region injects into, narrowed by [sublocation] where §5.8.6 splits one region in two.
     *
     * Every [BodyRegion] is placed, not only the fourteen the seed grows today: a preset added later
     * (§5.8.6 names posterior deltoid and forearm for v1.1) would otherwise land on the navel with no
     * warning at all.
     */
    fun zoneOf(region: BodyRegion, sublocation: Sublocation?): Zone = when (region) {
        BodyRegion.ABDOMEN -> if (sublocation == Sublocation.UPPER) ABDOMEN_UPPER else ABDOMEN_LOWER
        // "Lateral thigh" is the quadriceps' outer sublocation (§5.8.6) — the same muscle, a hand's
        // width further out, which is the only reason the two are drawn apart.
        BodyRegion.QUADRICEPS -> if (sublocation == Sublocation.OUTER) LATERAL_THIGH else THIGH_FRONT
        BodyRegion.THIGH -> THIGH_FRONT
        BodyRegion.GLUTE -> GLUTE
        BodyRegion.HAMSTRING -> HAMSTRING
        BodyRegion.LOWER_BACK -> LOWER_BACK
        BodyRegion.DELT -> DELTOID
        BodyRegion.UPPER_ARM -> UPPER_ARM
        BodyRegion.FOREARM -> FOREARM
    }

    private val ABDOMEN_UPPER = Zone(
        "M 61,83 C 67,82 72,84 73,89 C 74,96 73,102 71,106 C 68,108 61,108 61,107 Z",
        Offset(67f, 95f),
    )
    private val ABDOMEN_LOWER = Zone(
        "M 61,108 C 66,107 71,109 71.5,114 C 72,120 70,124 66.5,125 C 63.5,126 61,125 61,124 Z",
        Offset(66.5f, 116f),
    )
    private val DELTOID = Zone(
        "M 78,46 C 86,46 96,53 96.5,63 C 97,71 94,77 89.5,77 C 84,77 79,66 78,53 Z",
        Offset(88f, 62f),
    )
    private val UPPER_ARM = Zone(
        "M 86,80 C 91.5,81 94.5,86 94,95 C 93.5,102 91.5,105 89.5,105 C 87,104.5 85.5,95 86,86 Z",
        Offset(90f, 93f),
    )
    private val FOREARM = Zone(
        "M 84.5,110 C 89.5,111 92.5,118 91.5,129 C 90.5,135 88.5,138 87,137 C 85,135 83.5,122 84.5,115 Z",
        Offset(88f, 124f),
    )
    private val THIGH_FRONT = Zone(
        "M 61.5,129 C 70,127 80,133 80,146 C 80,159 76,169 70,171 C 65,172 61.5,162 61.5,148 Z",
        Offset(71f, 149f),
    )
    private val LATERAL_THIGH = Zone(
        "M 70,134 C 77,133 81.5,140 81,150 C 80.5,161 76.5,169 72,170 C 68.5,170 67.5,158 68.5,146 Z",
        Offset(75f, 152f),
    )
    private val GLUTE = Zone(
        "M 61,108 C 70,106 80,113 81,124 C 82,135 76,142 68,142 C 63,142 61,135 61,124 Z",
        Offset(71f, 124f),
    )
    private val HAMSTRING = Zone(
        "M 61.5,146 C 70,145 78,151 78,161 C 78,171 74,177 68,177 C 64,177 61.5,167 61.5,156 Z",
        Offset(70f, 161f),
    )
    private val LOWER_BACK = Zone(
        "M 61.5,90 C 66.5,89 71,93 71.5,99 C 72,105 69,109 65,109.5 C 62.5,110 61.5,109 61.5,107 Z",
        Offset(66.5f, 99f),
    )
}
