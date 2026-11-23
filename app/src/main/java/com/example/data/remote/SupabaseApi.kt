package com.example.data.remote

import com.example.data.model.AdItem
import com.example.data.model.AppSettingRecord
import com.example.data.model.AuthSession
import com.example.data.model.AuthUserDto
import com.example.data.model.BlockUserRequest
import com.example.data.model.BlockedUserRecord
import com.example.data.model.CommentItem
import com.example.data.model.ConversationItem
import com.example.data.model.CreateCommentRequest
import com.example.data.model.CreateConversationRequest
import com.example.data.model.CreateMessageLegacyRequest
import com.example.data.model.CreateMessageRequest
import com.example.data.model.CreateReportRequest
import com.example.data.model.CreateVideoLegacyRequest
import com.example.data.model.CreateVideoRequest
import com.example.data.model.FollowerRecord
import com.example.data.model.GiftItem
import com.example.data.model.HashtagItem
import com.example.data.model.IdTokenRequest
import com.example.data.model.LikeRecord
import com.example.data.model.LiveStreamItem
import com.example.data.model.MessageItem
import com.example.data.model.NotificationItem
import com.example.data.model.PkceTokenRequest
import com.example.data.model.RecoverPasswordRequest
import com.example.data.model.RefreshTokenRequest
import com.example.data.model.ReportItem
import com.example.data.model.SavedRecord
import com.example.data.model.SignInPasswordRequest
import com.example.data.model.SignUpRequest
import com.example.data.model.SoundItem
import com.example.data.model.TransactionItem
import com.example.data.model.UserProfile
import com.example.data.model.UserProfileUpsert
import com.example.data.model.VideoItem
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Query

interface SupabaseAuthApi {
    @POST("auth/v1/signup")
    suspend fun signUp(@Body body: SignUpRequest): Response<AuthSession>

    @POST("auth/v1/token?grant_type=password")
    suspend fun signInWithPassword(@Body body: SignInPasswordRequest): Response<AuthSession>

    @POST("auth/v1/token?grant_type=refresh_token")
    suspend fun refreshSession(@Body body: RefreshTokenRequest): Response<AuthSession>

    @POST("auth/v1/token?grant_type=id_token")
    suspend fun signInWithIdToken(@Body body: IdTokenRequest): Response<AuthSession>

    @POST("auth/v1/token?grant_type=pkce")
    suspend fun exchangePkceCode(@Body body: PkceTokenRequest): Response<AuthSession>

    @GET("auth/v1/user")
    suspend fun getAuthUser(@Header("Authorization") bearerToken: String): Response<AuthUserDto>

    @POST("auth/v1/recover")
    suspend fun recoverPassword(@Body body: RecoverPasswordRequest): Response<Unit>

    @POST("auth/v1/logout")
    suspend fun signOut(): Response<Unit>
}

interface SupabaseRestApi {

    // --- USERS ---
    @GET("rest/v1/users")
    suspend fun getUserById(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
    ): List<UserProfile>

    @GET("rest/v1/users")
    suspend fun getUsersByEmail(
        @Query("email") emailFilter: String,
        @Query("select") select: String = "id,email",
        @Query("limit") limit: Int = 1
    ): List<UserProfile>

    @GET("rest/v1/users")
    suspend fun getUsersByIds(
        @Query("id") idInFilter: String,
        @Query("select") select: String = "*"
    ): List<UserProfile>

    @GET("rest/v1/users")
    suspend fun getUsers(
        @Query("select") select: String = "*",
        @Query("order") order: String = "followers_count.desc",
        @Query("limit") limit: Int = 500,
        @Query("offset") offset: Int = 0
    ): List<UserProfile>

    @GET("rest/v1/users")
    suspend fun searchUsers(
        @Query("or") orFilter: String,
        @Query("select") select: String = "*",
        @Query("limit") limit: Int = 50
    ): List<UserProfile>

    @Headers("Prefer: resolution=merge-duplicates,return=representation")
    @POST("rest/v1/users")
    suspend fun upsertUserProfile(@Body body: UserProfileUpsert): List<UserProfile>

    @Headers("Prefer: return=representation")
    @PATCH("rest/v1/users")
    suspend fun updateUserProfile(
        @Query("id") idFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): List<UserProfile>

    @DELETE("rest/v1/users")
    suspend fun deleteUserProfile(@Query("id") idFilter: String): Response<Unit>

    // --- VIDEOS ---
    @GET("rest/v1/videos")
    suspend fun getVideos(
        @Query("select") select: String = "*,users(*)",
        @Query("privacy") privacy: String? = "eq.public",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): List<VideoItem>

    @GET("rest/v1/videos")
    suspend fun getVideosByUser(
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "*,users(*)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): List<VideoItem>

    @GET("rest/v1/videos")
    suspend fun getVideosByFollowing(
        @Query("user_id") userIdInFilter: String,
        @Query("select") select: String = "*,users(*)",
        @Query("privacy") privacy: String = "eq.public",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 30,
        @Query("offset") offset: Int = 0
    ): List<VideoItem>

    @GET("rest/v1/videos")
    suspend fun searchVideos(
        @Query("or") orFilter: String? = null,
        @Query("category") categoryFilter: String? = null,
        @Query("hashtags") hashtagFilter: String? = null,
        @Query("sound_id") soundIdFilter: String? = null,
        @Query("select") select: String = "*,users(*)",
        @Query("privacy") privacy: String = "eq.public",
        @Query("order") order: String = "views_count.desc",
        @Query("limit") limit: Int = 40
    ): List<VideoItem>

    @GET("rest/v1/videos")
    suspend fun getVideoByUrl(
        @Query("video_url") videoUrlFilter: String,
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "*,users(*)",
        @Query("limit") limit: Int = 1
    ): List<VideoItem>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/videos")
    suspend fun createVideo(@Body body: CreateVideoRequest): List<VideoItem>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/videos")
    suspend fun createVideoLegacy(@Body body: CreateVideoLegacyRequest): List<VideoItem>

    @Headers("Prefer: return=representation")
    @DELETE("rest/v1/videos")
    suspend fun deleteVideo(@Query("id") idFilter: String): Response<List<VideoItem>>

    @PATCH("rest/v1/videos")
    suspend fun updateVideo(
        @Query("id") idFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    // --- LIKES & SAVES & FOLLOWS ---
    @GET("rest/v1/likes")
    suspend fun getLikesForUser(
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "video_id,user_id"
    ): List<LikeRecord>

    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/likes")
    suspend fun createLike(@Body body: LikeRecord): Response<Unit>

    @DELETE("rest/v1/likes")
    suspend fun deleteLike(
        @Query("video_id") videoIdFilter: String,
        @Query("user_id") userIdFilter: String
    ): Response<Unit>

    @GET("rest/v1/saved_videos")
    suspend fun getSavedForUser(
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "video_id,user_id"
    ): List<SavedRecord>

    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/saved_videos")
    suspend fun saveVideo(@Body body: SavedRecord): Response<Unit>

    @DELETE("rest/v1/saved_videos")
    suspend fun unsaveVideo(
        @Query("video_id") videoIdFilter: String,
        @Query("user_id") userIdFilter: String
    ): Response<Unit>

    @GET("rest/v1/videos")
    suspend fun getVideosByIds(
        @Query("id") idInFilter: String,
        @Query("select") select: String = "*,users(*)"
    ): List<VideoItem>

    @GET("rest/v1/followers")
    suspend fun getFollowingIds(
        @Query("follower_id") followerIdFilter: String,
        @Query("select") select: String = "follower_id,following_id"
    ): List<FollowerRecord>

    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/followers")
    suspend fun createFollow(@Body body: FollowerRecord): Response<Unit>

    @DELETE("rest/v1/followers")
    suspend fun deleteFollow(
        @Query("follower_id") followerIdFilter: String,
        @Query("following_id") followingIdFilter: String
    ): Response<Unit>

    @GET("rest/v1/followers")
    suspend fun getFollowerIds(
        @Query("following_id") followingIdFilter: String,
        @Query("select") select: String = "follower_id,following_id"
    ): List<FollowerRecord>

    // --- COMMENTS ---
    @GET("rest/v1/comments")
    suspend fun getComments(
        @Query("video_id") videoIdFilter: String,
        @Query("select") select: String = "*,users(*)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 100
    ): List<CommentItem>

    @GET("rest/v1/comments")
    suspend fun getAllComments(
        @Query("select") select: String = "*,users(*)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 300
    ): List<CommentItem>

    @DELETE("rest/v1/comments")
    suspend fun deleteComment(@Query("id") idFilter: String): Response<Unit>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/comments")
    suspend fun postComment(@Body body: CreateCommentRequest): List<CommentItem>

    @PATCH("rest/v1/videos")
    suspend fun updateVideoCounts(
        @Query("id") idFilter: String,
        @Body updates: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    // --- HASHTAGS & SOUNDS ---
    @GET("rest/v1/hashtags")
    suspend fun getHashtags(
        @Query("select") select: String = "*",
        @Query("order") order: String = "views_count.desc",
        @Query("limit") limit: Int = 25
    ): List<HashtagItem>

    @Headers("Prefer: resolution=merge-duplicates")
    @POST("rest/v1/hashtags")
    suspend fun upsertHashtag(@Body body: Map<String, @JvmSuppressWildcards Any>): Response<Unit>

    @GET("rest/v1/sounds")
    suspend fun getSounds(
        @Query("select") select: String = "*",
        @Query("order") order: String = "usage_count.desc",
        @Query("limit") limit: Int = 30
    ): List<SoundItem>

    // --- NOTIFICATIONS ---
    @GET("rest/v1/notifications")
    suspend fun getNotifications(
        @Query("user_id") userIdFilter: String,
        @Query("select") select: String = "*,users!notifications_actor_id_fkey(*)",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 50
    ): List<NotificationItem>

    @PATCH("rest/v1/notifications")
    suspend fun markAllNotificationsRead(
        @Query("user_id") userIdFilter: String,
        @Body body: Map<String, Boolean> = mapOf("is_read" to true)
    ): Response<Unit>

    // --- CONVERSATIONS & MESSAGES ---
    @GET("rest/v1/conversations")
    suspend fun getConversations(
        @Query("or") participantOrFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "updated_at.desc"
    ): List<ConversationItem>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/conversations")
    suspend fun createConversation(@Body body: CreateConversationRequest): List<ConversationItem>

    @PATCH("rest/v1/conversations")
    suspend fun updateConversationLastMessage(
        @Query("id") idFilter: String,
        @Body body: Map<String, String>
    ): Response<Unit>

    @GET("rest/v1/messages")
    suspend fun getMessages(
        @Query("conversation_id") conversationIdFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.asc",
        @Query("limit") limit: Int = 200
    ): List<MessageItem>

    @GET("rest/v1/messages")
    suspend fun getMessageByClientId(
        @Query("conversation_id") conversationIdFilter: String,
        @Query("client_id") clientIdFilter: String,
        @Query("select") select: String = "*",
        @Query("limit") limit: Int = 1
    ): List<MessageItem>

    @GET("rest/v1/messages")
    suspend fun getUnreadMessagesForRecipient(
        @Query("sender_id") notSenderFilter: String,
        @Query("is_read") readFilter: String = "eq.false",
        @Query("select") select: String = "id,conversation_id,sender_id,is_read"
    ): List<MessageItem>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/messages")
    suspend fun sendMessage(@Body body: CreateMessageRequest): List<MessageItem>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/messages")
    suspend fun sendMessageLegacy(@Body body: CreateMessageLegacyRequest): List<MessageItem>

    @PATCH("rest/v1/messages")
    suspend fun updateMessagesStatus(
        @Query("conversation_id") conversationIdFilter: String,
        @Query("sender_id") notSenderFilter: String,
        @Query("status") statusFilter: String? = null,
        @Query("is_read") readFilter: String? = null,
        @Body body: Map<String, @JvmSuppressWildcards Any>
    ): Response<Unit>

    // --- MONETIZATION & GIFTS ---
    @GET("rest/v1/gifts")
    suspend fun getGifts(
        @Query("select") select: String = "*",
        @Query("order") order: String = "coin_cost.asc"
    ): List<GiftItem>

    @GET("rest/v1/transactions")
    suspend fun getTransactions(
        @Query("or") participantOrFilter: String,
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 40
    ): List<TransactionItem>

    // --- ADS, LIVE STREAMS, REPORTS, BLOCKS ---
    @GET("rest/v1/advertisements")
    suspend fun getActiveAds(
        @Query("is_active") activeFilter: String = "eq.true",
        @Query("select") select: String = "*"
    ): List<AdItem>

    @GET("rest/v1/live_streams")
    suspend fun getLiveStreams(
        @Query("is_live") liveFilter: String = "eq.true",
        @Query("select") select: String = "*,users(*)"
    ): List<LiveStreamItem>

    @GET("rest/v1/reports")
    suspend fun getReports(
        @Query("select") select: String = "*",
        @Query("order") order: String = "created_at.desc",
        @Query("limit") limit: Int = 200
    ): List<ReportItem>

    @Headers("Prefer: return=representation")
    @POST("rest/v1/reports")
    suspend fun createReport(@Body body: CreateReportRequest): List<ReportItem>

    @PATCH("rest/v1/reports")
    suspend fun updateReportStatus(
        @Query("id") idFilter: String,
        @Body body: Map<String, String>
    ): Response<Unit>

    @DELETE("rest/v1/reports")
    suspend fun deleteReport(@Query("id") idFilter: String): Response<Unit>

    @GET("rest/v1/app_settings")
    suspend fun getAppSettings(
        @Query("select") select: String = "*"
    ): List<AppSettingRecord>

    @Headers("Prefer: resolution=merge-duplicates")
    @POST("rest/v1/app_settings")
    suspend fun upsertAppSetting(@Body body: AppSettingRecord): Response<Unit>

    @GET("rest/v1/blocked_users")
    suspend fun getBlockedUsers(
        @Query("blocker_id") blockerIdFilter: String,
        @Query("select") select: String = "blocker_id,blocked_id"
    ): List<BlockedUserRecord>

    @Headers("Prefer: resolution=ignore-duplicates")
    @POST("rest/v1/blocked_users")
    suspend fun blockUser(@Body body: BlockUserRequest): Response<Unit>

    // --- RPC FUNCTIONS ---
    @POST("rest/v1/rpc/increment_video_views")
    suspend fun incrementVideoViews(@Body body: Map<String, String>): Response<Unit>

    @POST("rest/v1/rpc/increment_video_shares")
    suspend fun incrementVideoShares(@Body body: Map<String, String>): Response<Unit>

    @POST("rest/v1/rpc/toggle_video_like")
    suspend fun toggleVideoLike(@Body body: Map<String, String>): Boolean

    @POST("rest/v1/rpc/toggle_follow_user")
    suspend fun toggleFollowUser(@Body body: Map<String, String>): Boolean

    @POST("rest/v1/rpc/send_virtual_gift")
    suspend fun sendVirtualGift(@Body body: Map<String, @JvmSuppressWildcards Any>): Int

    @POST("rest/v1/rpc/claim_reward_coins")
    suspend fun claimRewardCoins(@Body body: Map<String, @JvmSuppressWildcards Any>): Int

    @POST("rest/v1/rpc/request_user_verification")
    suspend fun requestUserVerification(): Response<Unit>

    @POST("rest/v1/rpc/mark_conversation_delivered")
    suspend fun markConversationDelivered(@Body body: Map<String, String>): Response<Unit>

    @POST("rest/v1/rpc/mark_inbox_delivered")
    suspend fun markInboxDelivered(): Response<Unit>

    @POST("rest/v1/rpc/mark_conversation_read")
    suspend fun markConversationRead(@Body body: Map<String, String>): Response<Unit>

    // --- ADMIN RPC FUNCTIONS ---
    @POST("rest/v1/rpc/admin_verify_gate_password")
    suspend fun adminVerifyGatePassword(@Body body: Map<String, String>): Boolean

    @POST("rest/v1/rpc/admin_verify_credentials")
    suspend fun adminVerifyCredentials(@Body body: Map<String, String>): Boolean

    @POST("rest/v1/rpc/admin_update_credentials")
    suspend fun adminUpdateCredentials(@Body body: Map<String, String>): Boolean

    @POST("rest/v1/rpc/admin_update_user")
    suspend fun adminUpdateUser(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Unit>

    @POST("rest/v1/rpc/admin_delete_user")
    suspend fun adminDeleteUser(@Body body: Map<String, String>): Response<Unit>

    @POST("rest/v1/rpc/admin_create_video")
    suspend fun adminCreateVideo(@Body body: Map<String, @JvmSuppressWildcards Any?>): List<VideoItem>

    @POST("rest/v1/rpc/admin_update_video")
    suspend fun adminUpdateVideo(@Body body: Map<String, @JvmSuppressWildcards Any?>): Response<Unit>

    @POST("rest/v1/rpc/admin_delete_video")
    suspend fun adminDeleteVideo(@Body body: Map<String, String>): Response<Unit>

    @POST("rest/v1/rpc/admin_delete_comment")
    suspend fun adminDeleteComment(@Body body: Map<String, String>): Response<Unit>

    @POST("rest/v1/rpc/admin_resolve_report")
    suspend fun adminResolveReport(@Body body: Map<String, String>): Response<Unit>

    @POST("rest/v1/rpc/admin_delete_report")
    suspend fun adminDeleteReport(@Body body: Map<String, String>): Response<Unit>

    @POST("rest/v1/rpc/admin_upsert_app_setting")
    suspend fun adminUpsertAppSetting(@Body body: Map<String, String>): Response<Unit>
}
