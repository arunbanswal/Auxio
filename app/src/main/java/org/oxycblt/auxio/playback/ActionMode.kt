/*
 * Copyright (c) 2022 Auxio Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.playback

import org.oxycblt.auxio.IntegerTable

/** Represents custom actions available in certain areas of the playback UI. */
enum class ActionMode {
    NEXT,
    REPEAT,
    SHUFFLE;

    val intCode: Int
        get() = when (this) {
            NEXT -> IntegerTable.ACTION_MODE_NEXT
            REPEAT -> IntegerTable.ACTION_MODE_REPEAT
            SHUFFLE -> IntegerTable.ACTION_MODE_SHUFFLE
        }

    companion object {
        /** Convert an int [code] into an instance, or null if it isn't valid. */
        fun fromIntCode(code: Int) =
            when (code) {
                IntegerTable.ACTION_MODE_NEXT -> NEXT
                IntegerTable.ACTION_MODE_REPEAT -> REPEAT
                IntegerTable.ACTION_MODE_SHUFFLE -> SHUFFLE
                else -> null
            }
    }
}
