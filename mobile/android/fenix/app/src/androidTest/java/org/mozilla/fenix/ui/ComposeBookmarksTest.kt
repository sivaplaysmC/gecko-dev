/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.fenix.ui

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import androidx.test.uiautomator.UiDevice
import kotlinx.coroutines.runBlocking
import mozilla.appservices.places.BookmarkRoot
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mozilla.fenix.R
import org.mozilla.fenix.customannotations.SmokeTest
import org.mozilla.fenix.ext.bookmarkStorage
import org.mozilla.fenix.helpers.AndroidAssetDispatcher
import org.mozilla.fenix.helpers.HomeActivityIntentTestRule
import org.mozilla.fenix.helpers.RecyclerViewIdlingResource
import org.mozilla.fenix.helpers.RetryTestRule
import org.mozilla.fenix.helpers.TestAssetHelper
import org.mozilla.fenix.helpers.TestHelper.clickSnackbarButton
import org.mozilla.fenix.helpers.TestHelper.exitMenu
import org.mozilla.fenix.helpers.TestHelper.longTapSelectItem
import org.mozilla.fenix.helpers.TestHelper.registerAndCleanupIdlingResources
import org.mozilla.fenix.ui.robots.bookmarksMenu
import org.mozilla.fenix.ui.robots.browserScreen
import org.mozilla.fenix.ui.robots.homeScreen
import org.mozilla.fenix.ui.robots.multipleSelectionToolbar
import org.mozilla.fenix.ui.robots.navigationToolbar

/**
 *  Tests for verifying basic functionality of bookmarks
 */
class ComposeBookmarksTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var mDevice: UiDevice
    private val bookmarksFolderName = "New Folder"
    private val testBookmark = object {
        var title: String = "Bookmark title"
        var url: String = "https://www.example.com"
    }

    @get:Rule(order = 0)
    val activityTestRule =
        AndroidComposeTestRule(
            HomeActivityIntentTestRule.withDefaultSettingsOverrides(
                tabsTrayRewriteEnabled = true,
            ),
        ) { it.activity }

    @Rule(order = 1)
    @JvmField
    val retryTestRule = RetryTestRule(3)

    @Before
    fun setUp() {
        mDevice = UiDevice.getInstance(getInstrumentation())
        mockWebServer = MockWebServer().apply {
            dispatcher = AndroidAssetDispatcher()
            start()
        }
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        // Clearing all bookmarks data after each test to avoid overlapping data
        val bookmarksStorage = activityTestRule.activity?.bookmarkStorage
        runBlocking {
            val bookmarks = bookmarksStorage?.getTree(BookmarkRoot.Mobile.id)?.children
            bookmarks?.forEach { bookmarksStorage.deleteNode(it.guid) }
        }
    }

    @Test
    fun verifyEmptyBookmarksMenuTest() {
        homeScreen {
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 1),
            ) {
                verifyBookmarksMenuView()
                verifyAddFolderButton()
                verifyCloseButton()
                verifyBookmarkTitle("Desktop Bookmarks")
                selectFolder("Desktop Bookmarks")
                verifyFolderTitle("Bookmarks Menu")
                verifyFolderTitle("Bookmarks Toolbar")
                verifyFolderTitle("Other Bookmarks")
                verifySyncSignInButton()
            }
        }.clickSingInToSyncButton {
            verifyTurnOnSyncToolbarTitle()
        }
    }

    @Test
    fun cancelCreateBookmarkFolderTest() {
        homeScreen {
        }.openThreeDotMenu {
        }.openBookmarks {
            clickAddFolderButton()
            addNewFolderName(bookmarksFolderName)
            navigateUp()
            verifyKeyboardHidden()
            verifyBookmarkFolderIsNotCreated(bookmarksFolderName)
        }
    }

    @Test
    fun cancelingChangesInEditModeAreNotSavedTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        navigationToolbar {
        }.enterURLAndEnterToBrowser(defaultWebPage.url) {
        }.openThreeDotMenu {
        }.bookmarkPage {
            clickSnackbarButton("EDIT")
        }
        bookmarksMenu {
            verifyEditBookmarksView()
            changeBookmarkTitle(testBookmark.title)
            changeBookmarkUrl(testBookmark.url)
        }.closeEditBookmarkSection {
        }
        browserScreen {
        }.openThreeDotMenu {
        }.openBookmarks {
            verifyBookmarkTitle(defaultWebPage.title)
            verifyBookmarkedURL(defaultWebPage.url.toString())
        }
    }

    @SmokeTest
    @Test
    fun editBookmarksNameAndUrlTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openThreeDotMenu {
        }.editBookmarkPage {
            verifyEditBookmarksView()
            changeBookmarkTitle(testBookmark.title)
            changeBookmarkUrl(testBookmark.url)
            saveEditBookmark()
        }
        browserScreen {
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 2),
            ) {}
            verifyBookmarkTitle(testBookmark.title)
            verifyBookmarkedURL(testBookmark.url)
        }.openBookmarkWithTitle(testBookmark.title) {
            verifyUrl("example.com")
        }
    }

    @Test
    fun copyBookmarkURLTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 2),
            ) {}
        }.openThreeDotMenu(defaultWebPage.title) {
        }.clickCopy {
            verifyCopySnackBarText()
            navigateUp()
        }

        navigationToolbar {
        }.clickUrlbar {
            clickClearButton()
            longClickToolbar()
            clickPasteText()
            verifyTypedToolbarText(defaultWebPage.url.toString())
        }
    }

    @Test
    fun threeDotMenuShareBookmarkTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 2),
            ) {}
        }.openThreeDotMenu(defaultWebPage.title) {
        }.clickShare {
            verifyShareOverlay()
            verifyShareBookmarkFavicon()
            verifyShareBookmarkTitle()
            verifyShareBookmarkUrl()
        }
    }

    @Test
    fun openBookmarkInNewTabTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 2),
            ) {}
        }.openThreeDotMenu(defaultWebPage.title) {
        }.clickOpenInNewTab(activityTestRule) {
            verifyTabTrayIsOpen()
            verifyNormalBrowsingButtonIsSelected()
        }
    }

    @Test
    fun verifyOpenAllInNewTabsOptionTest() {
        val webPages = listOf(
            TestAssetHelper.getGenericAsset(mockWebServer, 1),
            TestAssetHelper.getGenericAsset(mockWebServer, 2),
            TestAssetHelper.getGenericAsset(mockWebServer, 3),
            TestAssetHelper.getGenericAsset(mockWebServer, 4),
        )

        homeScreen {
        }.openThreeDotMenu {
        }.openBookmarks {
            createFolder("root")
            createFolder("sub", "root")
            createFolder("empty", "root")
        }.closeMenu {
        }

        browserScreen {
            createBookmark(webPages[0].url)
            createBookmark(webPages[1].url, "root")
            createBookmark(webPages[2].url, "root")
            createBookmark(webPages[3].url, "sub")
        }.openComposeTabDrawer(activityTestRule) {
            closeTab()
        }

        browserScreen {
        }.openThreeDotMenu {
        }.openBookmarks {
        }.openThreeDotMenu("root") {
        }.clickOpenAllInTabs(activityTestRule) {
            verifyTabTrayIsOpen()
            verifyNormalBrowsingButtonIsSelected()

            verifyExistingOpenTabs("Test_Page_2", "Test_Page_3", "Test_Page_4")

            // Bookmark that is not under the root folder should not be opened
            verifyNoExistingOpenTabs("Test_Page_1")
        }
    }

    @Test
    fun verifyOpenAllInPrivateTabsTest() {
        val webPages = listOf(
            TestAssetHelper.getGenericAsset(mockWebServer, 1),
            TestAssetHelper.getGenericAsset(mockWebServer, 2),
        )

        homeScreen {
        }.openThreeDotMenu {
        }.openBookmarks {
            createFolder("root")
            createFolder("sub", "root")
            createFolder("empty", "root")
        }.closeMenu {
        }

        browserScreen {
            createBookmark(webPages[0].url, "root")
            createBookmark(webPages[1].url, "sub")
        }.openComposeTabDrawer(activityTestRule) {
            closeTab()
        }

        browserScreen {
        }.openThreeDotMenu {
        }.openBookmarks {
        }.openThreeDotMenu("root") {
        }.clickOpenAllInPrivateTabs(activityTestRule) {
            verifyTabTrayIsOpen()
            verifyPrivateBrowsingButtonIsSelected()

            verifyExistingOpenTabs("Test_Page_1", "Test_Page_2")
        }
    }

    @Test
    fun openBookmarkInPrivateTabTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 2),
            ) {}
        }.openThreeDotMenu(defaultWebPage.title) {
        }.clickOpenInPrivateTab(activityTestRule) {
            verifyTabTrayIsOpen()
            verifyPrivateBrowsingButtonIsSelected()
        }
    }

    @Test
    fun deleteBookmarkTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 2),
            ) {}
        }.openThreeDotMenu(defaultWebPage.title) {
        }.clickDelete {
            verifyUndoDeleteSnackBarButton()
            clickUndoDeleteButton()
            verifySnackBarHidden()
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 2),
            ) {
                verifyBookmarkedURL(defaultWebPage.url.toString())
            }
        }.openThreeDotMenu(defaultWebPage.title) {
        }.clickDelete {
            verifyBookmarkIsDeleted(defaultWebPage.title)
        }
    }

    @Test
    fun bookmarksMultiSelectionToolbarItemsTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 2),
            ) {
                longTapSelectItem(defaultWebPage.url)
            }
        }

        multipleSelectionToolbar {
            verifyMultiSelectionCheckmark(defaultWebPage.url)
            verifyMultiSelectionCounter()
            verifyShareBookmarksButton()
            verifyCloseToolbarButton()
        }.closeToolbarReturnToBookmarks {
            verifyBookmarksMenuView()
        }
    }

    @SmokeTest
    @Test
    fun openMultipleSelectedBookmarksInANewTabTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openComposeTabDrawer(activityTestRule) {
            closeTab()
        }

        homeScreen {
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 2),
            ) {
                longTapSelectItem(defaultWebPage.url)
                openActionBarOverflowOrOptionsMenu(activityTestRule.activity)
            }
        }

        multipleSelectionToolbar {
        }.clickOpenNewTab(activityTestRule) {
            verifyTabTrayIsOpen()
            verifyNormalBrowsingButtonIsSelected()
            verifyNormalTabsList()
        }
    }

    @Test
    fun openMultipleSelectedBookmarksInPrivateTabTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 2),
            ) {
                longTapSelectItem(defaultWebPage.url)
                openActionBarOverflowOrOptionsMenu(activityTestRule.activity)
            }
        }

        multipleSelectionToolbar {
        }.clickOpenPrivateTab(activityTestRule) {
            verifyPrivateBrowsingButtonIsSelected()
            verifyPrivateTabsList()
        }
    }

    @SmokeTest
    @Test
    fun deleteMultipleSelectedBookmarksTest() {
        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)
        val secondWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 2)

        browserScreen {
            createBookmark(firstWebPage.url)
            createBookmark(secondWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 3),
            ) {
                longTapSelectItem(firstWebPage.url)
                longTapSelectItem(secondWebPage.url)
            }
            openActionBarOverflowOrOptionsMenu(activityTestRule.activity)
        }

        multipleSelectionToolbar {
            clickMultiSelectionDelete()
        }

        bookmarksMenu {
            verifyDeleteMultipleBookmarksSnackBar()
            clickUndoDeleteButton()
            verifyBookmarkedURL(firstWebPage.url.toString())
            verifyBookmarkedURL(secondWebPage.url.toString())
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 3),
            ) {
                longTapSelectItem(firstWebPage.url)
                longTapSelectItem(secondWebPage.url)
            }
            openActionBarOverflowOrOptionsMenu(activityTestRule.activity)
        }

        multipleSelectionToolbar {
            clickMultiSelectionDelete()
        }

        bookmarksMenu {
            verifyDeleteMultipleBookmarksSnackBar()
        }
    }

    @Test
    fun multipleSelectionShareButtonTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 2),
            ) {
                longTapSelectItem(defaultWebPage.url)
            }
        }

        multipleSelectionToolbar {
            clickShareBookmarksButton()
            verifyShareOverlay()
            verifyShareTabFavicon()
            verifyShareTabTitle()
            verifyShareTabUrl()
        }
    }

    @Test
    fun createBookmarkFolderTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 2),
            ) {}
        }.openThreeDotMenu(defaultWebPage.title) {
        }.clickEdit {
            clickParentFolderSelector()
            clickAddNewFolderButtonFromSelectFolderView()
            addNewFolderName(bookmarksFolderName)
            saveNewFolder()
            navigateUp()
            saveEditBookmark()
            selectFolder(bookmarksFolderName)
            verifyBookmarkedURL(defaultWebPage.url.toString())
        }
    }

    @Test
    fun navigateBookmarksFoldersTest() {
        homeScreen {
        }.openThreeDotMenu {
        }.openBookmarks {
            createFolder("1")
            getInstrumentation().waitForIdleSync()
            waitForBookmarksFolderContentToExist("Bookmarks", "1")
            selectFolder("1")
            verifyCurrentFolderTitle("1")
            createFolder("2")
            getInstrumentation().waitForIdleSync()
            waitForBookmarksFolderContentToExist("1", "2")
            selectFolder("2")
            verifyCurrentFolderTitle("2")
            navigateUp()
            waitForBookmarksFolderContentToExist("1", "2")
            verifyCurrentFolderTitle("1")
            mDevice.pressBack()
            verifyBookmarksMenuView()
        }
    }

    @Test
    fun cantSelectDesktopFoldersTest() {
        homeScreen {
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list)),
            ) {
                longTapDesktopFolder("Desktop Bookmarks")
                verifySelectDefaultFolderSnackBarText()
            }
        }
    }

    @Test
    fun deleteBookmarkInEditModeTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
            registerAndCleanupIdlingResources(
                RecyclerViewIdlingResource(activityTestRule.activity.findViewById(R.id.bookmark_list), 2),
            ) {}
        }.openThreeDotMenu(defaultWebPage.title) {
        }.clickEdit {
            clickDeleteInEditModeButton()
            cancelDeletion()
            clickDeleteInEditModeButton()
            confirmDeletion()
            verifyDeleteSnackBarText()
            verifyBookmarkIsDeleted("Test_Page_1")
        }
    }

    @Test
    fun verifySearchBookmarksViewTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
        }.clickSearchButton {
            verifySearchView()
            verifySearchToolbar(true)
            verifySearchSelectorButton()
            verifySearchEngineIcon("Bookmarks")
            verifySearchBarPlaceholder("Search bookmarks")
            verifySearchBarPosition(true)
            tapOutsideToDismissSearchBar()
            verifySearchToolbar(false)
        }
        bookmarksMenu {
        }.goBackToBrowserScreen {
        }.openThreeDotMenu {
        }.openSettings {
        }.openCustomizeSubMenu {
            clickTopToolbarToggle()
        }

        exitMenu()

        browserScreen {
        }.openThreeDotMenu {
        }.openBookmarks {
        }.clickSearchButton {
            verifySearchToolbar(true)
            verifySearchEngineIcon("Bookmarks")
            verifySearchBarPosition(false)
            pressBack()
            verifySearchToolbar(false)
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/1715712
    @Test
    fun verifySearchForBookmarkedItemsTest() {
        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)
        val secondWebPage = TestAssetHelper.getHTMLControlsFormAsset(mockWebServer)

        homeScreen {
        }.openThreeDotMenu {
        }.openBookmarks {
            createFolder(bookmarksFolderName)
        }

        exitMenu()

        browserScreen {
            createBookmark(firstWebPage.url, bookmarksFolderName)
            createBookmark(secondWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
        }.clickSearchButton {
            // Search for a valid term
            typeSearch(firstWebPage.title)
            verifySearchEngineSuggestionResults(activityTestRule, firstWebPage.url.toString(), searchTerm = firstWebPage.title)
            verifySuggestionsAreNotDisplayed(activityTestRule, secondWebPage.url.toString())
            // Search for invalid term
            typeSearch("Android")
            verifySuggestionsAreNotDisplayed(activityTestRule, firstWebPage.url.toString())
            verifySuggestionsAreNotDisplayed(activityTestRule, secondWebPage.url.toString())
        }
    }

    @Test
    fun verifyVoiceSearchInBookmarksTest() {
        val defaultWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(defaultWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
        }.clickSearchButton {
            verifySearchToolbar(true)
            verifySearchEngineIcon("Bookmarks")
            startVoiceSearch()
        }
    }

    // TestRail link: https://testrail.stage.mozaws.net/index.php?/cases/view/1715714
    @Test
    fun verifyDeletedBookmarksAreNotDisplayedAsSearchResultsTest() {
        val firstWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 1)
        val secondWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 2)
        val thirdWebPage = TestAssetHelper.getGenericAsset(mockWebServer, 3)

        browserScreen {
            createBookmark(firstWebPage.url)
            createBookmark(secondWebPage.url)
            createBookmark(thirdWebPage.url)
        }.openThreeDotMenu {
        }.openBookmarks {
        }.openThreeDotMenu(firstWebPage.title) {
        }.clickDelete {
            verifyBookmarkIsDeleted(firstWebPage.title)
        }.openThreeDotMenu(secondWebPage.title) {
        }.clickDelete {
            verifyBookmarkIsDeleted(secondWebPage.title)
        }.clickSearchButton {
            // Search for a valid term
            typeSearch("generic")
            verifySuggestionsAreNotDisplayed(activityTestRule, firstWebPage.url.toString())
            verifySuggestionsAreNotDisplayed(activityTestRule, secondWebPage.url.toString())
            verifySearchEngineSuggestionResults(activityTestRule, thirdWebPage.url.toString(), searchTerm = "generic")
            pressBack()
        }
        bookmarksMenu {
        }.openThreeDotMenu(thirdWebPage.title) {
        }.clickDelete {
            verifyBookmarkIsDeleted(thirdWebPage.title)
        }.clickSearchButton {
            // Search for a valid term
            typeSearch("generic")
            verifySuggestionsAreNotDisplayed(activityTestRule, thirdWebPage.url.toString())
        }
    }

    // Verifies that deleting a Bookmarks folder also removes the item from inside it.
    @SmokeTest
    @Test
    fun deleteBookmarkFoldersTest() {
        val website = TestAssetHelper.getGenericAsset(mockWebServer, 1)

        browserScreen {
            createBookmark(website.url)
        }.openThreeDotMenu {
        }.openBookmarks {
            verifyBookmarkTitle("Test_Page_1")
            createFolder("My Folder")
            verifyFolderTitle("My Folder")
        }.openThreeDotMenu("Test_Page_1") {
        }.clickEdit {
            clickParentFolderSelector()
            selectFolder("My Folder")
            navigateUp()
            saveEditBookmark()
            createFolder("My Folder 2")
            verifyFolderTitle("My Folder 2")
        }.openThreeDotMenu("My Folder 2") {
        }.clickEdit {
            clickParentFolderSelector()
            selectFolder("My Folder")
            navigateUp()
            saveEditBookmark()
        }.openThreeDotMenu("My Folder") {
        }.clickDelete {
            cancelFolderDeletion()
            verifyFolderTitle("My Folder")
        }.openThreeDotMenu("My Folder") {
        }.clickDelete {
            confirmDeletion()
            verifyDeleteSnackBarText()
            clickUndoDeleteButton()
            verifyFolderTitle("My Folder")
        }.openThreeDotMenu("My Folder") {
        }.clickDelete {
            confirmDeletion()
            verifyDeleteSnackBarText()
            verifyBookmarkIsDeleted("My Folder")
            verifyBookmarkIsDeleted("My Folder 2")
            verifyBookmarkIsDeleted("Test_Page_1")
            navigateUp()
        }
    }
}
