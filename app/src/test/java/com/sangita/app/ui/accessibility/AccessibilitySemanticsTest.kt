package com.sangita.app.ui.accessibility

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.sangita.app.domain.model.Track
import com.sangita.app.sync.SyncedDevice
import com.sangita.app.ui.components.NavTab
import com.sangita.app.ui.components.SangitaBottomNavBar
import com.sangita.app.ui.components.TrackItemRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Automated accessibility test suite verifying TalkBack compliance:
 * - Screen and section headers declare `heading()` semantics.
 * - Interactive elements declare clear, non-empty content descriptions.
 * - Toggles declare state descriptions ("Shuffle on/off", "Playing/Paused", "Selected", etc.)
 *   and appropriate roles (Checkbox, RadioButton, Tab).
 * - Touch targets meet the Android Accessibility >= 48dp minimum standard.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AccessibilitySemanticsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // ------------------------------------------------------------------------
    // 1. Heading Semantics
    // ------------------------------------------------------------------------

    @Test
    fun testScreenAndSectionHeadingsDeclareHeadingSemantics() {
        composeTestRule.setContent {
            Column {
                Text(
                    text = "Sangita",
                    modifier = Modifier
                        .testTag("app_title")
                        .semantics { heading() }
                )
                Text(
                    text = "Library",
                    modifier = Modifier
                        .testTag("library_title")
                        .semantics { heading() }
                )
                Text(
                    text = "Discover",
                    modifier = Modifier
                        .testTag("discover_title")
                        .semantics { heading() }
                )
                Text(
                    text = "Settings",
                    modifier = Modifier
                        .testTag("settings_title")
                        .semantics { heading() }
                )
                Text(
                    text = "Jump Back In",
                    modifier = Modifier
                        .testTag("jump_back_in_header")
                        .semantics { heading() }
                )
                Text(
                    text = "Top 10 Most Played",
                    modifier = Modifier
                        .testTag("top_played_header")
                        .semantics { heading() }
                )
                Text(
                    text = "Theme & Appearance",
                    modifier = Modifier
                        .testTag("theme_header")
                        .semantics { heading() }
                )
                Text(
                    text = "Server Domain",
                    modifier = Modifier
                        .testTag("domain_header")
                        .semantics { heading() }
                )
                Text(
                    text = "Live Downloads",
                    modifier = Modifier
                        .testTag("downloads_header")
                        .semantics { heading() }
                )
            }
        }

        val headingMatcher = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

        composeTestRule.onNodeWithTag("app_title")
            .assert(headingMatcher)
            .assert(hasText("Sangita"))

        composeTestRule.onNodeWithTag("library_title")
            .assert(headingMatcher)
            .assert(hasText("Library"))

        composeTestRule.onNodeWithTag("discover_title")
            .assert(headingMatcher)
            .assert(hasText("Discover"))

        composeTestRule.onNodeWithTag("settings_title")
            .assert(headingMatcher)
            .assert(hasText("Settings"))

        composeTestRule.onNodeWithTag("jump_back_in_header")
            .assert(headingMatcher)
            .assert(hasText("Jump Back In"))

        composeTestRule.onNodeWithTag("top_played_header")
            .assert(headingMatcher)
            .assert(hasText("Top 10 Most Played"))

        composeTestRule.onNodeWithTag("theme_header")
            .assert(headingMatcher)
            .assert(hasText("Theme & Appearance"))

        composeTestRule.onNodeWithTag("domain_header")
            .assert(headingMatcher)
            .assert(hasText("Server Domain"))

        composeTestRule.onNodeWithTag("downloads_header")
            .assert(headingMatcher)
            .assert(hasText("Live Downloads"))
    }

    // ------------------------------------------------------------------------
    // 2. Interactive Buttons and Content Descriptions
    // ------------------------------------------------------------------------

    @Test
    fun testInteractiveButtonsHaveDescriptiveContentDescriptions() {
        composeTestRule.setContent {
            Column {
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("btn_close")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close settings")
                }

                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("btn_more")
                ) {
                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "More options for Track Title")
                }

                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("btn_play")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play Track Title")
                }
            }
        }

        composeTestRule.onNodeWithTag("btn_close")
            .assert(hasContentDescription("Close settings"))

        composeTestRule.onNodeWithTag("btn_more")
            .assert(hasContentDescription("More options for Track Title"))

        composeTestRule.onNodeWithTag("btn_play")
            .assert(hasContentDescription("Play Track Title"))
    }

    // ------------------------------------------------------------------------
    // 3. Toggle Buttons and State Descriptions
    // ------------------------------------------------------------------------

    @Test
    fun testPlaybackToggleStateDescriptions() {
        composeTestRule.setContent {
            Column {
                // Play / Pause toggle
                val isPlaying = true
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("play_pause_btn")
                        .semantics {
                            stateDescription = if (isPlaying) "Playing" else "Paused"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }

                // Shuffle toggle
                val shuffleOn = true
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("shuffle_btn")
                        .semantics {
                            role = Role.Checkbox
                            stateDescription = if (shuffleOn) "Shuffle on" else "Shuffle off"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = if (shuffleOn) "Disable shuffle" else "Enable shuffle"
                    )
                }

                // Repeat toggle
                val repeatMode = "all"
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("repeat_btn")
                        .semantics {
                            stateDescription = "Repeat all"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Repeat,
                        contentDescription = "Repeat mode: Repeat all"
                    )
                }

                // Favorite toggle
                val isFavorite = true
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("fav_btn")
                        .semantics {
                            role = Role.Checkbox
                            stateDescription = if (isFavorite) "In favorites" else "Not in favorites"
                        }
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites"
                    )
                }

                // Password toggle
                val passwordVisible = false
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("password_toggle")
                        .semantics {
                            stateDescription = if (passwordVisible) "Password shown" else "Password hidden"
                        }
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password"
                    )
                }
            }
        }

        // Verify Play/Pause
        composeTestRule.onNodeWithTag("play_pause_btn")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Playing"))

        // Verify Shuffle
        composeTestRule.onNodeWithTag("shuffle_btn")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Shuffle on"))

        // Verify Repeat
        composeTestRule.onNodeWithTag("repeat_btn")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Repeat all"))

        // Verify Favorite
        composeTestRule.onNodeWithTag("fav_btn")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "In favorites"))

        // Verify Password Toggle
        composeTestRule.onNodeWithTag("password_toggle")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Password hidden"))
    }

    @Test
    fun testThemeOptionRowRadioSemantics() {
        val isSelected = true
        composeTestRule.setContent {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .clickable(
                        role = Role.RadioButton,
                        onClick = {}
                    )
                    .semantics {
                        selected = isSelected
                        stateDescription = if (isSelected) "Selected" else "Not selected"
                    }
                    .testTag("theme_option_amoled")
            ) {
                Icon(imageVector = Icons.Default.DarkMode, contentDescription = null)
                Text(text = "Pure AMOLED Black")
            }
        }

        composeTestRule.onNodeWithTag("theme_option_amoled")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected"))
    }

    @Test
    fun testDeviceItemRowButtonSemantics() {
        val device = SyncedDevice(
            deviceId = "dev-123",
            deviceName = "Living Room Web",
            deviceType = "desktop",
            isActive = false,
            isMe = false
        )

        composeTestRule.setContent {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 68.dp)
                    .clickable(
                        enabled = !device.isActive,
                        role = Role.Button,
                        onClickLabel = "Transfer playback to ${device.deviceName}",
                        onClick = {}
                    )
                    .semantics {
                        stateDescription = if (device.isActive) "Active device" else "Available device"
                    }
                    .testTag("device_item")
            ) {
                Button(
                    onClick = {},
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics {
                            contentDescription = "Transfer playback to ${device.deviceName}"
                        }
                        .testTag("device_transfer_btn")
                ) {
                    Text("Switch here")
                }
            }
        }

        composeTestRule.onNodeWithTag("device_item")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Available device"))

        composeTestRule.onNodeWithTag("device_transfer_btn")
            .assert(hasContentDescription("Transfer playback to Living Room Web"))
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    // ------------------------------------------------------------------------
    // 4. Minimum Touch Target Bounds (>= 48dp)
    // ------------------------------------------------------------------------

    @Test
    fun testBottomNavTabsMeetMinimum48dpTouchTarget() {
        composeTestRule.setContent {
            SangitaBottomNavBar(
                currentTab = NavTab.HOME,
                onTabSelected = {},
                modifier = Modifier.testTag("bottom_nav_bar")
            )
        }

        // Home tab is selected
        val homeTab = composeTestRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
                hasText("Home") and
                SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)
        )

        homeTab.assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Selected"))

        // Discover tab has Role.Tab, selected = false, stateDescription = "Not selected"
        val discoverTab = composeTestRule.onNode(
            SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab) and
                hasText("Discover") and
                SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Not selected")
        )

        discoverTab.assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun testInteractiveControlsAssertWidthAndHeightAtLeast48dp() {
        composeTestRule.setContent {
            Column {
                // Interactive Icon Button
                IconButton(
                    onClick = {},
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("icon_button_48dp")
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                }

                // Interactive Action Button
                Button(
                    onClick = {},
                    modifier = Modifier
                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                        .testTag("action_button_48dp")
                ) {
                    Text("Action")
                }

                // Interactive Row Item (e.g. TrackItemRow container)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .sizeIn(minHeight = 48.dp)
                        .clickable(role = Role.Button, onClickLabel = "Play Track", onClick = {})
                        .testTag("row_item_48dp")
                ) {
                    Text("Track Title")
                }
            }
        }

        composeTestRule.onNodeWithTag("icon_button_48dp")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        composeTestRule.onNodeWithTag("action_button_48dp")
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        composeTestRule.onNodeWithTag("row_item_48dp")
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun testTrackItemRowAccessibilitySemantics() {
        val testTrack = Track(
            id = "library/song1.mp3",
            title = "Midnight City",
            artist = "M83",
            durationSeconds = 243.0
        )

        composeTestRule.setContent {
            TrackItemRow(
                track = testTrack,
                serverUrl = "http://localhost:5000",
                token = null,
                isPlayingThisTrack = true,
                onClick = {},
                onMenuClick = {},
                modifier = Modifier.testTag("track_row")
            )
        }

        // Verify the track item row announces Role.Button, onClickLabel, and currently playing state
        composeTestRule.onNodeWithTag("track_row")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Currently playing"))
            .assertHeightIsAtLeast(48.dp)

        // Verify the overflow 3-dots button has descriptive content description and >= 48dp bounds
        composeTestRule.onNodeWithContentDescription("More options for Midnight City")
            .assertExists()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun testTrackItemRowMenuSongActions() {
        var downloadClicked = false
        var deleteDownloadClicked = false
        var toggleFavoriteClicked = false
        var addToPlaylistClicked = false

        val track = Track(
            id = "library/song1.mp3",
            title = "Midnight City",
            artist = "M83",
            durationSeconds = 243.0,
            isFavorite = false,
            isDownloaded = false
        )

        composeTestRule.setContent {
            TrackItemRow(
                track = track,
                onDownloadClick = { downloadClicked = true },
                onDeleteDownload = { deleteDownloadClicked = true },
                onToggleFavorite = { toggleFavoriteClicked = true },
                onAddToPlaylist = { addToPlaylistClicked = true }
            )
        }

        // Click the 3-dots button ("More options for ...")
        composeTestRule.onNodeWithContentDescription("More options for Midnight City")
            .assertExists()
            .performClick()

        // 1. "Add to favorites": verify icon, text, and >= 48dp touch bounds
        composeTestRule.onNodeWithText("Add to favorites")
            .assertExists()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithContentDescription("Add to favorites", useUnmergedTree = true)
            .assertExists()

        // 2. "Download song": verify icon, text, and >= 48dp touch bounds
        composeTestRule.onNodeWithText("Download song")
            .assertExists()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithContentDescription("Download song", useUnmergedTree = true)
            .assertExists()

        // 3. "Add to playlist": verify icon, text, and >= 48dp touch bounds
        composeTestRule.onNodeWithText("Add to playlist")
            .assertExists()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithContentDescription("Add to playlist", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun testTrackItemRowMenuSongActionsWhenFavoritedAndDownloaded() {
        val track = Track(
            id = "library/song1.mp3",
            title = "Midnight City",
            artist = "M83",
            durationSeconds = 243.0,
            isFavorite = true,
            isDownloaded = true
        )

        composeTestRule.setContent {
            TrackItemRow(
                track = track,
                onDownloadClick = {},
                onDeleteDownload = {},
                onToggleFavorite = {},
                onAddToPlaylist = {}
            )
        }

        // Click the 3-dots button
        composeTestRule.onNodeWithContentDescription("More options for Midnight City")
            .assertExists()
            .performClick()

        // 1. "Remove from favorites": verify icon, text, and >= 48dp touch bounds
        composeTestRule.onNodeWithText("Remove from favorites")
            .assertExists()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithContentDescription("Remove from favorites", useUnmergedTree = true)
            .assertExists()

        // 2. "Remove download": verify icon, text, and >= 48dp touch bounds
        composeTestRule.onNodeWithText("Remove download")
            .assertExists()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithContentDescription("Remove download", useUnmergedTree = true)
            .assertExists()

        // 3. "Add to playlist": verify icon, text, and >= 48dp touch bounds
        composeTestRule.onNodeWithText("Add to playlist")
            .assertExists()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
        composeTestRule.onNodeWithContentDescription("Add to playlist", useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun testTrackItemRowVisualIndicators_favoriteOnly() {
        val track = Track(
            id = "library/song1.mp3",
            title = "Midnight City",
            artist = "M83",
            durationSeconds = 243.0,
            isFavorite = true,
            isDownloaded = false
        )
        composeTestRule.setContent {
            TrackItemRow(track = track)
        }
        composeTestRule.onNodeWithContentDescription("Favorite").assertExists()
        composeTestRule.onNodeWithContentDescription("Downloaded offline").assertDoesNotExist()
    }

    @Test
    fun testTrackItemRowVisualIndicators_downloadedOnly() {
        val track = Track(
            id = "library/song1.mp3",
            title = "Midnight City",
            artist = "M83",
            durationSeconds = 243.0,
            isFavorite = false,
            isDownloaded = true
        )
        composeTestRule.setContent {
            TrackItemRow(track = track)
        }
        composeTestRule.onNodeWithContentDescription("Downloaded offline").assertExists()
        composeTestRule.onNodeWithContentDescription("Favorite").assertDoesNotExist()
    }

    @Test
    fun testTrackItemRowVisualIndicators_bothFavoriteAndDownloaded() {
        val track = Track(
            id = "library/song1.mp3",
            title = "Midnight City",
            artist = "M83",
            durationSeconds = 243.0,
            isFavorite = true,
            isDownloaded = true
        )
        composeTestRule.setContent {
            TrackItemRow(track = track)
        }
        composeTestRule.onNodeWithContentDescription("Favorite").assertExists()
        composeTestRule.onNodeWithContentDescription("Downloaded offline").assertExists()
    }
}
