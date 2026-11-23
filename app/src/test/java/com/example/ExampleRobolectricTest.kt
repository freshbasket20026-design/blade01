package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppPreferences
import com.example.data.model.VideoItem
import com.example.data.remote.SupabaseClient
import com.example.data.repository.BladeRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  private fun sampleVideo(
    id: String,
    category: String = "All",
    title: String = id,
    caption: String = "",
    hashtags: List<String> = emptyList(),
    userId: String = "creator_$id",
    likes: Int = 0,
    views: Int = 0
  ) = VideoItem(
    id = id,
    userId = userId,
    videoUrl = "https://cdn.example.com/$id.mp4",
    title = title,
    caption = caption,
    category = category,
    hashtags = hashtags,
    likesCount = likes,
    viewsCount = views
  )

  @Test
  fun `read string from context and verify CameraMlAnalyzer defers heavy work safely`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Blade", appName)

    var faceUpdated = false
    val analyzer = com.example.core.camera.CameraMlAnalyzer(
      isFrontCameraProvider = { true },
      isPreviewStreamingProvider = { false },
      needsFaceTrackingProvider = { true },
      needsSegmentationProvider = { false },
      onFaceUpdated = { faceUpdated = true },
      onLumaUpdated = {},
      onMaskUpdated = {}
    )
    analyzer.close()
    assertEquals(false, faceUpdated)
  }

  @Test
  fun `watch time and interactions prioritize same and similar categories next`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = AppPreferences(context)
    val repo = BladeRepository(prefs, SupabaseClient(prefs))

    val cricketWatched = sampleVideo(
      id = "v_cricket_1",
      category = "Cricket",
      title = "Match highlights",
      userId = "u_c1"
    )
    // Simulate long watch + like + comment + share on Cricket
    repo.recordWatchSignal(cricketWatched, weight = 14) // deep watch time
    repo.recordWatchSignal(cricketWatched, weight = 6)  // like
    repo.recordWatchSignal(cricketWatched, weight = 7)  // comment
    repo.recordWatchSignal(cricketWatched, weight = 8)  // share

    val unseenFoodHighViews = sampleVideo("v_food", category = "Food", views = 9000, likes = 800)
    val unseenCricket = sampleVideo("v_cricket_2", category = "Cricket", views = 5, likes = 0)
    val unseenInferredMusic = sampleVideo("v_music", category = "All", title = "Guitar cover #music", views = 10)
    val unseenGamingRelated = sampleVideo("v_gaming", category = "Gaming", views = 10)

    val ranked = repo.rankUpcomingVideos(
      videos = listOf(unseenFoodHighViews, unseenInferredMusic, unseenGamingRelated, unseenCricket),
      forYou = true
    )

    // Matching Cricket video ranks first, and Cricket-related Gaming beats unrelated lower-affinity clips
    assertEquals("v_cricket_2", ranked.first().id)
    assertTrue(ranked.indexOfFirst { it.id == "v_gaming" } < ranked.indexOfFirst { it.id == "v_music" })
  }

  @Test
  fun `reloading For You shows new videos then cycles older videos one by one without repeating top`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = AppPreferences(context)
    val repo = BladeRepository(prefs, SupabaseClient(prefs))

    val catalog = listOf(
      sampleVideo("v1", category = "Comedy", views = 5000, likes = 300),
      sampleVideo("v2", category = "Music", views = 20, likes = 2),
      sampleVideo("v3", category = "Tech", views = 10, likes = 1)
    )

    val topIds = mutableListOf<String>()
    var previousTop: VideoItem? = null

    repeat(6) { step ->
      val reloadState = if (step == 0) {
        repo.getFeedReloadState()
      } else {
        repo.updateFeedReloadState(
          forYou = true,
          newlySurfacedIds = listOfNotNull(previousTop?.id),
          incrementCycle = true,
          nextOffset = 0
        )
      }
      val ranked = repo.rankVideosWithSignals(
        videos = catalog,
        forYou = true,
        followingIds = emptySet(),
        affinity = prefs.getWatchAffinity(),
        demoteIds = reloadState.lastBatchIds.toSet(),
        reloadCycle = reloadState.reloadCycle,
        currentSeq = reloadState.surfacedSeq
      )
      val currentTop = ranked.first()
      previousTop?.let { prev ->
        assertNotEquals("Reload step $step should not repeat top video ${prev.id}", prev.id, currentTop.id)
      }
      topIds.add(currentTop.id)
      prefs.markVideoSeen(currentTop.id)
      previousTop = currentTop
    }

    // All 3 videos must appear across the first 3 reloads (unseen first), then rotate older ones
    assertEquals(setOf("v1", "v2", "v3"), topIds.take(3).toSet())
    assertEquals(setOf("v1", "v2", "v3"), topIds.drop(3).toSet())
  }

  @Test
  fun `admin gate password and credential popup reject wrong inputs and accept valid admin`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = AppPreferences(context)
    val repo = BladeRepository(prefs, SupabaseClient(prefs))

    assertTrue(repo.verifyAdminGatePassword("").isFailure)
    assertTrue(repo.verifyAdminGatePassword("wrong_pass").isFailure)
    assertTrue(repo.authenticateAdmin("blade_admin", "wrong_pass").isFailure)
    assertTrue(repo.authenticateAdmin("wrong_admin", com.example.data.remote.SupabaseConfig.defaultAdminPassword).isFailure)

    val validPass = com.example.data.remote.SupabaseConfig.defaultAdminPassword
    val validId = com.example.data.remote.SupabaseConfig.defaultAdminUserId
    assertTrue(repo.verifyAdminGatePassword(validPass).isSuccess)
    assertTrue(repo.authenticateAdmin(validId, validPass).isSuccess)
  }

  @Test
  fun `createViewModel manages video-only modes front camera bright beauty AR filters timeline undo redo and draft recovery`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = AppPreferences(context)
    val repo = BladeRepository(prefs, SupabaseClient(prefs))
    val vm = com.example.ui.viewmodel.CreateViewModel(repo)

    // Strictly video-only modes (3m, 60s, 15s) and front camera active by default
    assertEquals(3, com.example.ui.viewmodel.CaptureMode.entries.size)
    assertTrue(vm.uiState.value.useFrontCamera)
    assertTrue(vm.uiState.value.beauty.enabled)
    assertTrue(vm.uiState.value.beautyBrightness > 0f)

    vm.setCaptureMode(com.example.ui.viewmodel.CaptureMode.M3)
    assertEquals(180, vm.uiState.value.captureMode.maxDurationSec)
    vm.setCaptureMode(com.example.ui.viewmodel.CaptureMode.S15)
    assertEquals(15, vm.uiState.value.captureMode.maxDurationSec)

    // 29-filter catalog, favorites, intensity, beauty, AR face, green screen
    assertTrue(vm.filterPresets.size >= 29)
    vm.updateFilter("Golden Hour")
    vm.updateFilterIntensity(0.75f)
    assertEquals("Golden Hour", vm.uiState.value.selectedFilter)
    assertEquals(0.75f, vm.uiState.value.filterIntensity)
    val cm = com.example.core.camera.VideoFilterCatalog.computeColorMatrix("Golden Hour", filterIntensity = 0.75f, autoLowLightLift = 0.2f)
    assertEquals(20, cm.array.size)

    vm.toggleFavoriteFilter("sunset")
    assertTrue("sunset" in vm.uiState.value.favoriteFilterIds)

    vm.setSpeedMultiplier(0.3f)
    assertEquals(0.3f, vm.uiState.value.speedMultiplier)
    vm.setTimerSeconds(10)
    assertEquals(10, vm.uiState.value.timerSeconds)
    vm.updateBeauty { it.copy(skinSmoothing = 0.9f, lipstickIntensity = 0.6f, enabled = false) }
    assertEquals(0f, vm.uiState.value.beauty.activeFactor)
    vm.updateBeauty { it.copy(enabled = true) }
    assertTrue(vm.uiState.value.beauty.activeFactor > 0f)

    // Camera defaults keep ML Kit landmarks OFF until AR/makeup is enabled, preventing camera lag
    assertEquals(false, com.example.core.camera.BeautySettings().needsFaceLandmarks)
    assertEquals(true, vm.uiState.value.beauty.needsFaceLandmarks)

    // Audio format validation & trim clamping for MP3/M4A/AAC/WAV
    assertTrue(com.example.core.camera.AudioImportManager.isSupportedAudio("audio/mpeg", "song.mp3"))
    assertTrue(com.example.core.camera.AudioImportManager.isSupportedAudio(null, "beat.m4a"))
    assertTrue(com.example.core.camera.AudioImportManager.isSupportedAudio("audio/wav", "clip.wav"))
    assertEquals(false, com.example.core.camera.AudioImportManager.isSupportedAudio("image/jpeg", "photo.jpg"))

    val customSong = com.example.data.model.SoundItem(
      id = "local_song",
      title = "Summer Beat",
      artist = "Phone Storage",
      audioUrl = "file:///tmp/summer.mp3",
      durationSeconds = 90
    )
    vm.selectSound(customSong, fullDurationMs = 90_000L, isLocalDevice = true)
    assertTrue(vm.uiState.value.isDeviceAudio)
    assertEquals(90_000L, vm.uiState.value.selectedSoundDurationMs)
    vm.updateSoundTrimRange(12_000L, 42_000L)
    assertEquals(12_000L, vm.uiState.value.soundTrimStartMs)
    assertEquals(42_000L, vm.uiState.value.soundTrimEndMs)
    vm.removeSelectedSound()
    assertEquals(null, vm.uiState.value.selectedSound)
    assertEquals(false, vm.uiState.value.isDeviceAudio)

    vm.setArEffect(com.example.core.camera.ArEffectType.CAT_EARS)
    assertEquals(com.example.core.camera.ArEffectType.CAT_EARS, vm.uiState.value.arEffect)

    val preset = vm.greenScreenPresets.first()
    vm.setGreenScreenPreset(preset)
    assertEquals(preset.id, vm.uiState.value.greenScreenPreset?.id)
    assertTrue(vm.uiState.value.greenScreenEnabled)
    vm.clearGreenScreen()
    assertEquals(null, vm.uiState.value.greenScreenPreset)

    // Timeline split, merge, transform, undo/redo, and draft save/recovery
    val testUri = android.net.Uri.parse("file:///tmp/test_blade.mp4")
    vm.onVideoSelected(testUri, 20_000L)
    assertEquals(com.example.ui.viewmodel.CreateStep.EDIT_MEDIA, vm.uiState.value.step)
    assertEquals(1, vm.uiState.value.segments.size)

    vm.updatePlayheadMs(10_000L)
    vm.splitSegmentAtPlayhead()
    assertEquals(2, vm.uiState.value.segments.size)
    assertTrue(vm.uiState.value.canUndo)

    vm.undo()
    assertEquals(1, vm.uiState.value.segments.size)
    assertTrue(vm.uiState.value.canRedo)

    vm.redo()
    assertEquals(2, vm.uiState.value.segments.size)

    vm.mergeVideoSegment(android.net.Uri.parse("file:///tmp/second.mp4"), 10_000L)
    assertEquals(3, vm.uiState.value.segments.size)

    vm.rotate90Degrees()
    vm.setAspectRatioMode("1:1")
    vm.toggleReversePlayback()
    assertEquals(90, vm.uiState.value.rotationDegrees)
    assertEquals("1:1", vm.uiState.value.aspectRatioMode)
    assertTrue(vm.uiState.value.reversePlayback)

    vm.addOverlay("Hello Blade", isSticker = false, animation = com.example.ui.viewmodel.OverlayAnimation.POP)
    vm.updateTitle("My Clip")
    vm.appendHashtag("dhaka")
    vm.saveProjectDraft()

    // Draft preservation across Edit -> Publish -> Edit -> Camera -> Resume Draft
    vm.proceedToPublish()
    assertEquals(com.example.ui.viewmodel.CreateStep.PUBLISH_MEDIA, vm.uiState.value.step)
    vm.backToEdit()
    assertEquals(com.example.ui.viewmodel.CreateStep.EDIT_MEDIA, vm.uiState.value.step)
    vm.backToCapture()
    assertEquals(com.example.ui.viewmodel.CreateStep.CAPTURE, vm.uiState.value.step)
    assertTrue(vm.uiState.value.hasDraftMedia)
    assertEquals("My Clip", vm.uiState.value.title)
    assertTrue(vm.uiState.value.caption.contains("#dhaka"))
    assertEquals(1, vm.uiState.value.overlays.size)
    vm.resumeDraftEdit()
    assertEquals(com.example.ui.viewmodel.CreateStep.EDIT_MEDIA, vm.uiState.value.step)

    // AI repository gracefully reports failure when unreadable video URI is supplied
    val aiRepo = com.example.data.ai.LocalSampleAiEffectsRepository()
    val aiFail = aiRepo.processVideoAiEffect(
      contentResolver = context.contentResolver,
      videoUri = android.net.Uri.parse("file:///non_existent_clip.mp4"),
      durationMs = 10_000L,
      effectType = com.example.data.ai.AiEffectType.ANIME_CARTOON,
      stylePrompt = "cyber",
      onProgress = { _, _ -> }
    )
    assertTrue(aiFail.isFailure)
  }

  @Test
  fun `adminViewModel tracks verification filters and validates video upload target user`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = AppPreferences(context)
    val repo = BladeRepository(prefs, SupabaseClient(prefs))
    val adminVm = com.example.ui.viewmodel.AdminViewModel(repo)

    adminVm.updateUserFilter(com.example.ui.viewmodel.UserFilter.PENDING)
    assertEquals(com.example.ui.viewmodel.UserFilter.PENDING, adminVm.uiState.value.userFilter)

    adminVm.updateUserFilter(com.example.ui.viewmodel.UserFilter.REJECTED)
    assertEquals(com.example.ui.viewmodel.UserFilter.REJECTED, adminVm.uiState.value.userFilter)

    adminVm.updateUserFilter(com.example.ui.viewmodel.UserFilter.VERIFIED)
    assertEquals(com.example.ui.viewmodel.UserFilter.VERIFIED, adminVm.uiState.value.userFilter)

    // Publishing without a target user surfaces inline uploadError immediately
    adminVm.publishVideoForUser(
      contentResolver = context.contentResolver,
      targetUserId = "",
      targetUsername = "",
      videoUri = null,
      remoteVideoUrl = "https://cdn.example.com/clip.mp4",
      title = "Clip",
      caption = "Test",
      category = "All",
      location = "",
      privacy = "public",
      onPublished = {}
    )
    assertEquals(
      "Select a user account to post this video under.",
      adminVm.uiState.value.uploadError
    )

    adminVm.clearUploadError()
    assertEquals(null, adminVm.uiState.value.uploadError)
  }

  @Test
  fun `inboxViewModel mergeMessages deduplicates clientIds preserves local preview and updates read ticks`() {
    val optimisticImg = com.example.data.model.MessageItem(
      id = "local_cid_img",
      conversationId = "c1",
      senderId = "u1",
      clientId = "cid_img",
      messageType = "image",
      mediaUrl = "content://picker/photo.jpg",
      status = "sending",
      createdAt = "2026-11-19T10:00:00Z",
      localPreviewUri = "content://picker/photo.jpg"
    )
    val failedText = com.example.data.model.MessageItem(
      id = "local_cid_fail",
      conversationId = "c1",
      senderId = "u1",
      clientId = "cid_fail",
      content = "Retry me",
      status = "failed",
      createdAt = "2026-11-19T10:00:05Z"
    )

    val serverInserted = com.example.data.model.MessageItem(
      id = "srv_1",
      conversationId = "c1",
      senderId = "u1",
      clientId = "cid_img",
      messageType = "image",
      mediaUrl = "https://cdn.example.com/photo.jpg",
      status = "sent",
      createdAt = "2026-11-19T10:00:01Z"
    )
    val duplicateServer = serverInserted.copy()

    val mergedAfterSend = com.example.ui.viewmodel.InboxViewModel.mergeMessages(
      current = listOf(optimisticImg, failedText),
      incoming = listOf(serverInserted, duplicateServer)
    )
    // Duplicate client_id & duplicate server row collapsed cleanly; failed message stays queued at bottom
    assertEquals(2, mergedAfterSend.size)
    assertEquals("srv_1", mergedAfterSend[0].id)
    assertEquals("sent", mergedAfterSend[0].resolvedStatus)
    assertEquals("content://picker/photo.jpg", mergedAfterSend[0].localPreviewUri)
    assertEquals("local_cid_fail", mergedAfterSend[1].id)

    // Realtime UPDATE turns Single Tick -> Double Tick -> Read Tick without duplication
    val deliveredUpdate = serverInserted.copy(
      status = "delivered",
      deliveredAt = "2026-11-19T10:00:02Z"
    )
    val readUpdate = deliveredUpdate.copy(
      status = "read",
      isRead = true,
      readAt = "2026-11-19T10:00:03Z"
    )
    val mergedAfterRead = com.example.ui.viewmodel.InboxViewModel.mergeMessages(
      current = mergedAfterSend,
      incoming = listOf(deliveredUpdate, readUpdate)
    )
    assertEquals(2, mergedAfterRead.size)
    assertEquals("read", mergedAfterRead[0].resolvedStatus)

    // Stale poll returning older "delivered" state must never downgrade a "read" tick
    val mergedAfterStalePoll = com.example.ui.viewmodel.InboxViewModel.mergeMessages(
      current = mergedAfterRead,
      incoming = listOf(deliveredUpdate)
    )
    assertEquals(2, mergedAfterStalePoll.size)
    assertEquals("read", mergedAfterStalePoll[0].resolvedStatus)
  }

  @Test
  fun `inboxViewModel mergeMessages coalesces legacy DB realtime row without clientId when send returns`() {
    val optimistic = com.example.data.model.MessageItem(
      id = "local_cid_legacy",
      conversationId = "c1",
      senderId = "u1",
      clientId = "cid_legacy",
      content = "Legacy hello",
      status = "sending",
      createdAt = "2026-11-19T10:00:00Z"
    )
    // Realtime INSERT from a legacy table has no client_id column yet
    val realtimeWithoutClientId = com.example.data.model.MessageItem(
      id = "srv_legacy_1",
      conversationId = "c1",
      senderId = "u1",
      clientId = "",
      content = "Legacy hello",
      status = "sent",
      createdAt = "2026-11-19T10:00:01Z"
    )
    val afterRealtime = com.example.ui.viewmodel.InboxViewModel.mergeMessages(
      current = listOf(optimistic),
      incoming = listOf(realtimeWithoutClientId)
    )
    // Then sendMessageLegacy returns the inserted row with local clientId attached
    val sendResponse = realtimeWithoutClientId.copy(clientId = "cid_legacy")
    val afterSendCompletes = com.example.ui.viewmodel.InboxViewModel.mergeMessages(
      current = afterRealtime,
      incoming = listOf(sendResponse)
    )
    assertEquals(1, afterSendCompletes.size)
    assertEquals("srv_legacy_1", afterSendCompletes[0].id)
    assertEquals("cid_legacy", afterSendCompletes[0].clientId)
  }

  @Test
  fun `createUiState defaults to Original filter and resets cached upload keys cleanly`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = AppPreferences(context)
    val repo = BladeRepository(prefs, SupabaseClient(prefs))
    val vm = com.example.ui.viewmodel.CreateViewModel(repo)

    assertEquals("Original", vm.uiState.value.selectedFilter)
    assertEquals(true, vm.uiState.value.useFrontCamera)
    assertEquals(null, vm.uiState.value.pendingUploadKey)
    assertEquals(null, vm.uiState.value.uploadedVideoUrl)

    val uri = android.net.Uri.parse("file:///tmp/sample_hd.mp4")
    vm.onVideoSelected(uri, 12_000L)
    assertEquals(com.example.ui.viewmodel.CreateStep.EDIT_MEDIA, vm.uiState.value.step)
    vm.proceedToPublish()
    assertEquals(com.example.ui.viewmodel.CreateStep.PUBLISH_MEDIA, vm.uiState.value.step)
    vm.backToEdit()
    assertEquals(null, vm.uiState.value.pendingUploadKey)
    assertEquals(null, vm.uiState.value.uploadedVideoUrl)
  }

  @Test
  fun `feedViewModel prependPublishedVideo prepends uploaded video immediately without duplicates`() {
    val existing = listOf(
      sampleVideo(id = "vid_old_1", title = "Older Clip", category = "Comedy")
    )
    val uploaded = sampleVideo(id = "vid_new_1080p", title = "Fresh Clip", category = "Music")

    val afterPublish = com.example.ui.viewmodel.FeedViewModel.prependPublishedVideo(existing, uploaded)
    val afterDuplicateSync = com.example.ui.viewmodel.FeedViewModel.prependPublishedVideo(afterPublish, uploaded)

    assertEquals(2, afterDuplicateSync.size)
    assertEquals("vid_new_1080p", afterDuplicateSync.first().id)
    assertEquals(1, afterDuplicateSync.count { it.id == "vid_new_1080p" })
  }

  @Test
  fun `authViewModel sanitizes activity cancellation errors and exposes browser OAuth fallback`() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val prefs = AppPreferences(context)
    val repo = BladeRepository(prefs, SupabaseClient(prefs))
    val authVm = com.example.ui.viewmodel.AuthViewModel(repo)

    authVm.onGoogleCredentialError(
      rawMessage = "activity is cancelled by the user.",
      canUseBrowserFallback = true
    )
    assertEquals(false, authVm.uiState.value.isGoogleLoading)
    assertTrue(authVm.uiState.value.offerBrowserGoogleSignIn)
    assertEquals(
      false,
      authVm.uiState.value.errorMessage?.contains("activity is cancelled by the user", ignoreCase = true) == true
    )

    authVm.onGoogleCredentialCancelled(
      fastSystemCancel = true,
      canUseBrowserFallback = true
    )
    assertEquals(null, authVm.uiState.value.errorMessage)
    assertTrue(authVm.uiState.value.offerBrowserGoogleSignIn)
    assertTrue(authVm.uiState.value.infoMessage?.isNotBlank() == true)
  }
}
