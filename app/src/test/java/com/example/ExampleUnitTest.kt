package com.example

import com.example.core.moderation.ModerationResult
import com.example.core.moderation.VideoContentModerator
import com.example.data.model.MessageItem
import com.example.data.model.UserProfile
import com.example.data.model.VideoItem
import com.example.data.remote.SupabaseConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun moshiKotlinAdapter_parsesSnakeCaseModels() {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val adapter = moshi.adapter(VideoItem::class.java)
    val json = """
      {
        "id": "vid_1",
        "user_id": "usr_1",
        "video_url": "https://cdn.example.com/v.mp4",
        "title": "Test Blade",
        "likes_count": 42
      }
    """.trimIndent()
    val parsed = adapter.fromJson(json)
    assertEquals("vid_1", parsed?.id)
    assertEquals("usr_1", parsed?.userId)
    assertEquals(42, parsed?.likesCount)
  }

  @Test
  fun messageItem_parsesMediaFieldsAndResolvesDeliveryReadTicks() {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val adapter = moshi.adapter(MessageItem::class.java)

    val sentVoice = adapter.fromJson("""
      {
        "id": "m1",
        "conversation_id": "c1",
        "sender_id": "u1",
        "client_id": "cid_1",
        "message_type": "voice",
        "media_url": "https://cdn.example.com/voice.m4a",
        "media_duration_ms": 6400,
        "status": "sent",
        "is_read": false
      }
    """.trimIndent())!!
    assertTrue(sentVoice.isVoiceMessage)
    assertEquals(6400L, sentVoice.mediaDurationMs)
    assertEquals("sent", sentVoice.resolvedStatus)

    val deliveredImage = adapter.fromJson("""
      {
        "id": "m2",
        "conversation_id": "c1",
        "sender_id": "u1",
        "message_type": "image",
        "media_url": "https://cdn.example.com/photo.jpg",
        "status": "delivered",
        "delivered_at": "2026-11-19T10:00:00Z",
        "is_read": false
      }
    """.trimIndent())!!
    assertTrue(deliveredImage.isImageMessage)
    assertEquals("delivered", deliveredImage.resolvedStatus)

    val readByTimestamp = MessageItem(
      id = "m3",
      status = "delivered",
      isRead = false,
      readAt = "2026-11-19T10:01:00Z"
    )
    assertEquals("read", readByTimestamp.resolvedStatus)
    assertEquals("sending", MessageItem(id = "local_1", status = "sending").resolvedStatus)
    assertEquals("failed", MessageItem(id = "local_2", status = "failed").resolvedStatus)
  }

  @Test
  fun messageItem_legacySchemaRoundTrip_decodesTextImageAndVoice() {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val adapter = moshi.adapter(MessageItem::class.java)

    val legacyText = adapter.fromJson("""
      {
        "id": "m_leg_1",
        "conversation_id": "c1",
        "sender_id": "u1",
        "content": "Hello legacy!",
        "is_read": false,
        "created_at": "2026-11-19T10:00:00Z"
      }
    """.trimIndent())!!.normalized()
    assertEquals("text", legacyText.messageType)
    assertEquals("Hello legacy!", legacyText.content)
    assertEquals("sent", legacyText.resolvedStatus)

    val encodedImageWithCaption = MessageItem.encodeLegacyContent(
      messageType = "image",
      content = "Look at this!",
      mediaUrl = "https://cdn.example.com/pic.jpg",
      mediaDurationMs = 0L
    )
    val decodedImageWithCaption = MessageItem(
      id = "m_leg_2",
      content = encodedImageWithCaption,
      isRead = true
    ).normalized()
    assertTrue(decodedImageWithCaption.isImageMessage)
    assertEquals("https://cdn.example.com/pic.jpg", decodedImageWithCaption.mediaUrl)
    assertEquals("Look at this!", decodedImageWithCaption.content)
    assertEquals("read", decodedImageWithCaption.resolvedStatus)

    val encodedImageOnly = MessageItem.encodeLegacyContent(
      messageType = "image",
      content = "",
      mediaUrl = "https://cdn.example.com/only.png",
      mediaDurationMs = 0L
    )
    val decodedImageOnly = MessageItem(id = "m_leg_3", content = encodedImageOnly).normalized()
    assertTrue(decodedImageOnly.isImageMessage)
    assertEquals("https://cdn.example.com/only.png", decodedImageOnly.mediaUrl)
    assertEquals("", decodedImageOnly.content)

    val encodedVoice = MessageItem.encodeLegacyContent(
      messageType = "voice",
      content = "",
      mediaUrl = "https://cdn.example.com/voice.m4a",
      mediaDurationMs = 4850L
    )
    val decodedVoice = MessageItem(id = "m_leg_4", content = encodedVoice).normalized()
    assertTrue(decodedVoice.isVoiceMessage)
    assertEquals("https://cdn.example.com/voice.m4a", decodedVoice.mediaUrl)
    assertEquals(4850L, decodedVoice.mediaDurationMs)
    assertEquals("", decodedVoice.content)
  }

  @Test
  fun userProfile_resolvedVerificationStatus_handlesAllStatesAndMoshiParsing() {
    val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    val adapter = moshi.adapter(UserProfile::class.java)

    val verifiedJson = """
      {
        "id": "u1",
        "username": "creator1",
        "is_verified": true,
        "verification_status": "verified"
      }
    """.trimIndent()
    val verifiedUser = adapter.fromJson(verifiedJson)!!
    assertEquals("verified", verifiedUser.resolvedVerificationStatus)

    val rejectedJson = """
      {
        "id": "u2",
        "username": "creator2",
        "is_verified": false,
        "verification_status": "REJECTED"
      }
    """.trimIndent()
    val rejectedUser = adapter.fromJson(rejectedJson)!!
    assertEquals("rejected", rejectedUser.resolvedVerificationStatus)

    val legacyPendingUser = UserProfile(id = "u3", username = "creator3", isVerified = false, verificationStatus = null)
    assertEquals("pending", legacyPendingUser.resolvedVerificationStatus)

    val verifiedOverridesRejected = UserProfile(id = "u4", username = "creator4", isVerified = true, verificationStatus = "rejected")
    assertEquals("verified", verifiedOverridesRejected.resolvedVerificationStatus)
  }

  @Test
  fun normalizeSupabaseUrl_stripsSubpathsAndQuotes() {
    assertEquals(
      "https://abcdefghij.supabase.co",
      SupabaseConfig.normalizeSupabaseUrl("\"https://abcdefghij.supabase.co/rest/v1/\"")
    )
    assertEquals(
      "https://abcdefghij.supabase.co",
      SupabaseConfig.normalizeSupabaseUrl("abcdefghij.supabase.co/")
    )
  }

  @Test
  fun normalizeSupabaseUrl_convertsDashboardUrlToProjectHost() {
    assertEquals(
      "https://myprojref123.supabase.co",
      SupabaseConfig.normalizeSupabaseUrl("https://supabase.com/dashboard/project/myprojref123/editor")
    )
  }

  @Test
  fun videoContentModerator_allowsNormalVideoMetadata() {
    val result = VideoContentModerator.checkMetadataSafety(
      title = "Sunset Cricket Match in Dhaka",
      caption = "Great classic pass and goal! #cricket #dhaka #analysis",
      location = "Dhaka, Bangladesh",
      fileName = "VID_20261116_cricket.mp4"
    )
    assertTrue(result is ModerationResult.Safe)
  }

  @Test
  fun videoContentModerator_blocksExplicitPornMetadataAndFilename() {
    val blockedCaption = VideoContentModerator.checkMetadataSafety(
      title = "Check this out",
      caption = "Watch hot #xxx #porn video now",
      location = ""
    )
    assertTrue(blockedCaption is ModerationResult.Blocked)

    val blockedFile = VideoContentModerator.checkMetadataSafety(
      title = "My video",
      caption = "Fun clip",
      location = "",
      fileName = "leaked_sextape_18+.mp4"
    )
    assertTrue(blockedFile is ModerationResult.Blocked)
  }
}
