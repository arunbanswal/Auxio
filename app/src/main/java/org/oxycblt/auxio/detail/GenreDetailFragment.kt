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
 
package org.oxycblt.auxio.detail

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.transition.MaterialSharedAxis
import org.oxycblt.auxio.MainFragmentDirections
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.FragmentDetailBinding
import org.oxycblt.auxio.detail.recycler.DetailAdapter
import org.oxycblt.auxio.detail.recycler.GenreDetailAdapter
import org.oxycblt.auxio.music.Album
import org.oxycblt.auxio.music.Artist
import org.oxycblt.auxio.music.Genre
import org.oxycblt.auxio.music.Music
import org.oxycblt.auxio.music.MusicMode
import org.oxycblt.auxio.music.MusicParent
import org.oxycblt.auxio.music.Song
import org.oxycblt.auxio.music.Sort
import org.oxycblt.auxio.music.picker.PickerMode
import org.oxycblt.auxio.settings.Settings
import org.oxycblt.auxio.ui.MainNavigationAction
import org.oxycblt.auxio.ui.fragment.MenuFragment
import org.oxycblt.auxio.ui.recycler.Item
import org.oxycblt.auxio.util.collect
import org.oxycblt.auxio.util.collectImmediately
import org.oxycblt.auxio.util.context
import org.oxycblt.auxio.util.logD
import org.oxycblt.auxio.util.showToast
import org.oxycblt.auxio.util.unlikelyToBeNull

/**
 * A fragment that shows information for a particular [Genre].
 * @author OxygenCobalt
 */
class GenreDetailFragment :
    MenuFragment<FragmentDetailBinding>(), Toolbar.OnMenuItemClickListener, DetailAdapter.Listener {
    private val detailModel: DetailViewModel by activityViewModels()

    private val args: GenreDetailFragmentArgs by navArgs()
    private val detailAdapter = GenreDetailAdapter(this)
    private val settings: Settings by lifecycleObject { binding -> Settings(binding.context) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
        exitTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        reenterTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateBinding(inflater: LayoutInflater) = FragmentDetailBinding.inflate(inflater)

    override fun onBindingCreated(binding: FragmentDetailBinding, savedInstanceState: Bundle?) {
        detailModel.setGenreUid(args.genreUid)

        binding.detailToolbar.apply {
            inflateMenu(R.menu.menu_genre_artist_detail)
            setNavigationOnClickListener { findNavController().navigateUp() }
            setOnMenuItemClickListener(this@GenreDetailFragment)
        }

        binding.detailRecycler.adapter = detailAdapter

        // --- VIEWMODEL SETUP ---

        collectImmediately(detailModel.currentGenre, ::handleItemChange)
        collectImmediately(detailModel.genreData, detailAdapter::submitList)
        collectImmediately(
            playbackModel.song,
            playbackModel.parent,
            playbackModel.isPlaying,
            ::updatePlayback
        )
        collect(navModel.exploreNavigationItem, ::handleNavigation)
    }

    override fun onDestroyBinding(binding: FragmentDetailBinding) {
        super.onDestroyBinding(binding)
        binding.detailToolbar.setOnMenuItemClickListener(null)
        binding.detailRecycler.adapter = null
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_play_next -> {
                playbackModel.playNext(unlikelyToBeNull(detailModel.currentGenre.value))
                requireContext().showToast(R.string.lng_queue_added)
                true
            }
            R.id.action_queue_add -> {
                playbackModel.addToQueue(unlikelyToBeNull(detailModel.currentGenre.value))
                requireContext().showToast(R.string.lng_queue_added)
                true
            }
            else -> false
        }
    }

    override fun onItemClick(item: Item) {
        check(item is Song) { "Unexpected datatype: ${item::class.simpleName}" }
        when (settings.detailPlaybackMode) {
            null -> playbackModel.playFromGenre(item, unlikelyToBeNull(detailModel.currentGenre.value))
            MusicMode.SONGS -> playbackModel.playFromAll(item)
            MusicMode.ALBUMS -> playbackModel.playFromAlbum(item)
            MusicMode.ARTISTS -> {
                if (item.artists.size == 1) {
                    playbackModel.playFromArtist(item, item.artists[0])
                } else {
                    navModel.mainNavigateTo(
                        MainNavigationAction.Directions(
                            MainFragmentDirections.actionPickArtist(item.uid, PickerMode.PLAY)
                        )
                    )
                }
            }
            else -> error("Unexpected playback mode: ${settings.detailPlaybackMode}")
        }
    }

    override fun onOpenMenu(item: Item, anchor: View) {
        check(item is Song) { "Unexpected datatype: ${item::class.simpleName}" }
        musicMenu(anchor, R.menu.menu_song_actions, item)
    }

    override fun onPlayParent() {
        playbackModel.play(unlikelyToBeNull(detailModel.currentGenre.value))
    }

    override fun onShuffleParent() {
        playbackModel.shuffle(unlikelyToBeNull(detailModel.currentGenre.value))
    }

    override fun onShowSortMenu(anchor: View) {
        menu(anchor, R.menu.menu_genre_sort) {
            val sort = detailModel.genreSort
            unlikelyToBeNull(menu.findItem(sort.mode.itemId)).isChecked = true
            unlikelyToBeNull(menu.findItem(R.id.option_sort_asc)).isChecked = sort.isAscending
            setOnMenuItemClickListener { item ->
                item.isChecked = !item.isChecked
                detailModel.genreSort =
                    if (item.itemId == R.id.option_sort_asc) {
                        sort.withAscending(item.isChecked)
                    } else {
                        sort.withMode(unlikelyToBeNull(Sort.Mode.fromItemId(item.itemId)))
                    }
                true
            }
        }
    }

    private fun handleItemChange(genre: Genre?) {
        if (genre == null) {
            findNavController().navigateUp()
            return
        }

        requireBinding().detailToolbar.title = genre.resolveName(requireContext())
    }

    private fun handleNavigation(item: Music?) {
        when (item) {
            is Song -> {
                logD("Navigating to another song")
                findNavController()
                    .navigate(GenreDetailFragmentDirections.actionShowAlbum(item.album.uid))
            }
            is Album -> {
                logD("Navigating to another album")
                findNavController()
                    .navigate(GenreDetailFragmentDirections.actionShowAlbum(item.uid))
            }
            is Artist -> {
                logD("Navigating to another artist")
                findNavController()
                    .navigate(GenreDetailFragmentDirections.actionShowArtist(item.uid))
            }
            is Genre -> {
                navModel.finishExploreNavigation()
            }
            null -> {}
        }
    }

    private fun updatePlayback(song: Song?, parent: MusicParent?, isPlaying: Boolean) {
        if (parent is Genre && parent == unlikelyToBeNull(detailModel.currentGenre.value)) {
            detailAdapter.updateIndicator(song, isPlaying)
        } else {
            // Ignore song playback not from the genre
            detailAdapter.updateIndicator(null, isPlaying)
        }
    }
}
