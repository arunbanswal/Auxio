/*
 * Copyright (c) 2021 Auxio Project
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
 
package org.oxycblt.auxio.search

import android.app.Application
import android.content.Context
import androidx.annotation.IdRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.oxycblt.auxio.R
import org.oxycblt.auxio.music.Album
import org.oxycblt.auxio.music.Artist
import org.oxycblt.auxio.music.Genre
import org.oxycblt.auxio.music.Music
import org.oxycblt.auxio.music.MusicMode
import org.oxycblt.auxio.music.MusicStore
import org.oxycblt.auxio.music.Song
import org.oxycblt.auxio.music.Sort
import org.oxycblt.auxio.settings.Settings
import org.oxycblt.auxio.ui.recycler.Header
import org.oxycblt.auxio.ui.recycler.Item
import org.oxycblt.auxio.util.application
import org.oxycblt.auxio.util.logD
import java.text.Normalizer

/**
 * The [ViewModel] for search functionality.
 * @author OxygenCobalt
 */
class SearchViewModel(application: Application) :
    AndroidViewModel(application), MusicStore.Callback {
    private val musicStore = MusicStore.getInstance()
    private val settings = Settings(application)

    private val _searchResults = MutableStateFlow(listOf<Item>())

    /** Current search results from the last [search] call. */
    val searchResults: StateFlow<List<Item>>
        get() = _searchResults

    val filterMode: MusicMode?
        get() = settings.searchFilterMode

    private var lastQuery: String? = null
    private var currentSearchJob: Job? = null

    init {
        musicStore.addCallback(this)
    }

    /**
     * Use [query] to perform a search of the music library. Will push results to [searchResults].
     */
    fun search(query: String?) {
        lastQuery = query

        currentSearchJob?.cancel()

        val library = musicStore.library
        if (query.isNullOrEmpty() || library == null) {
            logD("No music/query, ignoring search")
            _searchResults.value = listOf()
            return
        }

        logD("Performing search for $query")

        // Searching can be quite expensive, so get on a co-routine
        currentSearchJob = viewModelScope.launch {
            val sort = Sort(Sort.Mode.ByName, true)
            val results = mutableListOf<Item>()

            // Note: a filter mode of null means to not filter at all.

            if (filterMode == null || filterMode == MusicMode.ARTISTS) {
                library.artists.filterArtistsBy(query)?.let { artists ->
                    results.add(Header(R.string.lbl_artists))
                    results.addAll(sort.artists(artists))
                }
            }

            if (filterMode == null || filterMode == MusicMode.ALBUMS) {
                library.albums.filterAlbumsBy(query)?.let { albums ->
                    results.add(Header(R.string.lbl_albums))
                    results.addAll(sort.albums(albums))
                }
            }

            if (filterMode == null || filterMode == MusicMode.GENRES) {
                library.genres.filterGenresBy(query)?.let { genres ->
                    results.add(Header(R.string.lbl_genres))
                    results.addAll(sort.genres(genres))
                }
            }

            if (filterMode == null || filterMode == MusicMode.SONGS) {
                library.songs.filterSongsBy(query)?.let { songs ->
                    results.add(Header(R.string.lbl_songs))
                    results.addAll(sort.songs(songs))
                }
            }

            yield()
            _searchResults.value = results
        }
    }

    /**
     * Update the current filter mode with a menu [id]. New value will be pushed to [filterMode].
     */
    fun updateFilterModeWithId(@IdRes id: Int) {
        val newFilterMode =
            when (id) {
                R.id.option_filter_songs -> MusicMode.SONGS
                R.id.option_filter_albums -> MusicMode.ALBUMS
                R.id.option_filter_artists -> MusicMode.ARTISTS
                R.id.option_filter_genres -> MusicMode.GENRES
                else -> null
            }

        logD("Updating filter mode to $newFilterMode")

        settings.searchFilterMode = newFilterMode

        search(lastQuery)
    }

    private fun List<Song>.filterSongsBy(value: String) =
        baseFilterBy(value) {
            it.rawSortName?.contains(value, ignoreCase = true) == true ||
                it.path.name.contains(value)
        }

    private fun List<Album>.filterAlbumsBy(value: String) =
        baseFilterBy(value) { it.rawSortName?.contains(value, ignoreCase = true) == true }

    private fun List<Artist>.filterArtistsBy(value: String) =
        baseFilterBy(value) { it.rawSortName?.contains(value, ignoreCase = true) == true }

    private fun List<Genre>.filterGenresBy(value: String) = baseFilterBy(value) { false }

    private inline fun <T : Music> List<T>.baseFilterBy(value: String, fallback: (T) -> Boolean) =
        filter {
            // The basic comparison is first by the *normalized* name, as that allows a
            // non-unicode search to match with some unicode characters. In an ideal world, we
            // would just want to leverage CollationKey, but that is not designed for a contains
            // algorithm. If that fails, filter impls have fallback values, primarily around
            // sort tags or file names.
            it.resolveNameNormalized(application).contains(value, ignoreCase = true) ||
                fallback(it)
        }
            .ifEmpty { null }

    private fun Music.resolveNameNormalized(context: Context): String {
        val norm = Normalizer.normalize(resolveName(context), Normalizer.Form.NFKD)
        return NORMALIZATION_SANITIZE_REGEX.replace(norm, "")
    }

    override fun onLibraryChanged(library: MusicStore.Library?) {
        if (library != null) {
            logD("Library changed, re-searching")
            // Make sure our query is up to date with the music library.
            search(lastQuery)
        }
    }

    override fun onCleared() {
        super.onCleared()
        musicStore.removeCallback(this)
    }

    companion object {
        private val NORMALIZATION_SANITIZE_REGEX = Regex("\\p{InCombiningDiacriticalMarks}+")
    }
}
