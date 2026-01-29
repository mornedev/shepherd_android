package com.mamlambofossils.legacyretriever

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import coil.util.DebugLogger
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.ContentResolver
import android.content.ContentValues
import android.media.MediaRecorder
import android.provider.MediaStore
import android.os.ParcelFileDescriptor
import android.Manifest
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import android.content.ClipboardManager
import android.content.ClipData
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.ExternalAuthAction
import io.github.jan.supabase.gotrue.SessionStatus
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.handleDeeplinks
import io.github.jan.supabase.gotrue.providers.Google
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme

// Brand colors
object BrandColors {
    val Orange = Color(0xFFF27907)
    val Green = Color(0xFF4CAF50)
}

// Custom color scheme with brand orange as primary
private val LegacyRetrieverLightColorScheme = lightColorScheme(
    primary = BrandColors.Orange,
    onPrimary = Color.Black,
    primaryContainer = BrandColors.Orange,
    onPrimaryContainer = Color.Black
)

class ComposeMainActivity : ComponentActivity() {

    // User plan cache with 1-hour expiration
    private var cachedUserPlan: UserPlanData? = null
    private var planCacheTimestamp: Long = 0
    private val PLAN_CACHE_DURATION_MS = 60 * 60 * 1000L // 1 hour in milliseconds

    // Billing manager for Google Play subscriptions
    internal val billingManager by lazy {
        BillingManager(
            context = this,
            purchaseTokenCallback = object : PurchaseTokenCallback {
                override suspend fun getApiBaseUrl(): String = this@ComposeMainActivity.getApiBaseUrl()
                override suspend fun getAccessToken(): String? = this@ComposeMainActivity.getAccessToken()
            }
        )
    }

    // Configure Coil ImageLoader with aggressive caching
    internal val imageLoader by lazy {
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.30) // Use 30% of app's available memory for better caching
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(200 * 1024 * 1024) // 200 MB disk cache for more images
                    .build()
            }
            .respectCacheHeaders(false) // Ignore server cache headers, cache everything
            .crossfade(true) // Enable crossfade by default
            .build()
    }

    private val supabase by lazy {
        createSupabaseClient(
            supabaseUrl = "https://api.legacyhound.app",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVtdmx3b3Bsc2R1bnN2aGRxenRhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTg4NzExOTIsImV4cCI6MjA3NDQ0NzE5Mn0.yrJSp1UPBDyNBvumFJddB3DHG8P1oj_CsNaX-lk6wu4"
        ) {
            install(Auth) {
                host = "auth"
                scheme = "legacyretriever"
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
                // Enable auto-refresh (session persistence is enabled by default in supabase-kt)
                autoSaveToStorage = true    // saves session locally
                autoLoadFromStorage = true  // loads session on startup
            }
        }
    }

    fun getApiBaseUrl(): String = BuildConfig.API_BASE_URL

    suspend fun getAccessToken(): String? = supabase.auth.currentSessionOrNull()?.accessToken

    // Get cached user plan or fetch if expired
    suspend fun getCachedUserPlan(): UserPlanData? {
        val currentTime = System.currentTimeMillis()
        
        // Check if cache is valid (within 1 hour)
        if (cachedUserPlan != null && (currentTime - planCacheTimestamp) < PLAN_CACHE_DURATION_MS) {
            android.util.Log.d("UserPlanCache", "Returning cached plan: ${cachedUserPlan?.planId}")
            return cachedUserPlan
        }
        
        // Cache expired or doesn't exist, fetch new data
        android.util.Log.d("UserPlanCache", "Cache expired or empty, fetching new plan")
        val freshPlan = fetchUserPlan(this)
        if (freshPlan != null) {
            cachedUserPlan = freshPlan
            planCacheTimestamp = currentTime
            android.util.Log.d("UserPlanCache", "Cached new plan: ${freshPlan.planId}")
        }
        return freshPlan
    }
    
    // Clear the plan cache (useful when user changes subscription)
    fun clearPlanCache() {
        cachedUserPlan = null
        planCacheTimestamp = 0
        android.util.Log.d("UserPlanCache", "Plan cache cleared")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Initialize billing manager
        billingManager.initialize()
        // Handle OAuth/OTP deep links from Supabase
        android.util.Log.d("AuthFlow", "onCreate called with intent: ${intent?.data}")
        lifecycleScope.launch {
            supabase.handleDeeplinks(intent)
        }
        setContent {
            val nav = rememberNavController()
            var collectionName by rememberSaveable { mutableStateOf<String?>(null) }
            var collectionId by rememberSaveable { mutableStateOf<String?>(null) }
            var onlyOneCollection by rememberSaveable { mutableStateOf(false) }
            var isAuthenticating by rememberSaveable { mutableStateOf(false) }
            var hasCompletedInitialAuth by rememberSaveable { mutableStateOf(false) }
            AppNav(
                navController = nav,
                onSignIn = {
                    // Start Google sign-in
                    lifecycleScope.launch {
                        supabase.auth.signInWith(Google)
                    }
                },
                collectionName = collectionName,
                collectionIdProvider = { collectionId },
                onlyOneCollection = onlyOneCollection,
                isAuthenticating = isAuthenticating
            )

            // Observe session and navigate
            LaunchedEffect(nav) {
                // Wait for NavController to be ready
                kotlinx.coroutines.delay(100)
                supabase.auth.sessionStatus.collect { status ->
                    android.util.Log.d("AuthFlow", "Session status changed: $status, hasCompletedInitialAuth=$hasCompletedInitialAuth")
                    if (status is SessionStatus.Authenticated && !hasCompletedInitialAuth) {
                        isAuthenticating = true
                        val user = supabase.auth.currentUserOrNull()
                        val meta = user?.userMetadata
                        val fullName = meta?.get("name")?.jsonPrimitive?.contentOrNull
                            ?: meta?.get("full_name")?.jsonPrimitive?.contentOrNull
                            ?: meta?.get("given_name")?.jsonPrimitive?.contentOrNull
                        val first = (fullName?.substringBefore(' ') ?: "there")
                        // Fetch user's first collection name
                        try {
                            val token = getAccessToken()
                            if (!token.isNullOrEmpty()) {
                                withContext(Dispatchers.IO) {
                                    val url = URL(getApiBaseUrl().trimEnd('/') + "/collections")
                                    val conn = (url.openConnection() as HttpURLConnection).apply {
                                        requestMethod = "GET"
                                        setRequestProperty("Authorization", "Bearer $token")
                                        setRequestProperty("Accept", "application/json")
                                        connectTimeout = 10000
                                        readTimeout = 15000
                                    }
                                    try {
                                        val code = conn.responseCode
                                        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                                            ?.bufferedReader()?.use { it.readText() } ?: ""
                                        android.util.Log.d("FetchCollections", "HTTP $code body=$body")
                                        if (code in 200..299) {
                                            val arr = try { Json.parseToJsonElement(body).jsonArray } catch (_: Exception) { null }
                                            val firstObj = arr?.firstOrNull()?.jsonObject
                                            if (firstObj != null) {
                                                collectionName = firstObj["name"]?.jsonPrimitive?.contentOrNull
                                                collectionId = firstObj["id"]?.jsonPrimitive?.contentOrNull
                                                onlyOneCollection = (arr?.size == 1)
                                            } else {
                                                // No collections found; auto-create a default one
                                                val createUrl = URL(getApiBaseUrl().trimEnd('/') + "/collections")
                                                val createConn = (createUrl.openConnection() as HttpURLConnection).apply {
                                                    requestMethod = "POST"
                                                    setRequestProperty("Authorization", "Bearer $token")
                                                    setRequestProperty("Content-Type", "application/json")
                                                    setRequestProperty("Accept", "application/json")
                                                    doOutput = true
                                                }
                                                try {
                                                    val payload = "{\"name\":\"My Collection\"}"
                                                    createConn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                                                    val createCode = createConn.responseCode
                                                    val createBody = (if (createCode in 200..299) createConn.inputStream else createConn.errorStream)
                                                        ?.bufferedReader()?.use { it.readText() } ?: ""
                                                    android.util.Log.d("CreateCollection", "HTTP $createCode body=$createBody")
                                                    if (createCode in 200..299) {
                                                        val obj = try { Json.parseToJsonElement(createBody).jsonObject } catch (_: Exception) { null }
                                                        collectionName = obj?.get("name")?.jsonPrimitive?.contentOrNull
                                                        collectionId = obj?.get("id")?.jsonPrimitive?.contentOrNull
                                                        onlyOneCollection = true
                                                    } else {
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(this@ComposeMainActivity, "Failed to create default collection: $createCode", Toast.LENGTH_LONG).show()
                                                        }
                                                        collectionName = null
                                                        collectionId = null
                                                        onlyOneCollection = false
                                                    }
                                                } finally {
                                                    createConn.disconnect()
                                                }
                                            }
                                        } else {
                                            collectionName = null
                                            collectionId = null
                                            onlyOneCollection = false
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(this@ComposeMainActivity, "Failed to load collections: $code", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } finally {
                                        conn.disconnect()
                                    }
                                }
                                // Fetch user's plan and log it
                                withContext(Dispatchers.IO) {
                                    val planUrl = URL(getApiBaseUrl().trimEnd('/') + "/user/plan")
                                    val planConn = (planUrl.openConnection() as HttpURLConnection).apply {
                                        requestMethod = "GET"
                                        setRequestProperty("Authorization", "Bearer $token")
                                        setRequestProperty("Accept", "application/json")
                                        connectTimeout = 10000
                                        readTimeout = 15000
                                    }
                                    try {
                                        val planCode = planConn.responseCode
                                        val planBody = (if (planCode in 200..299) planConn.inputStream else planConn.errorStream)
                                            ?.bufferedReader()?.use { it.readText() } ?: ""
                                        android.util.Log.d("UserPlan", "HTTP $planCode body=$planBody")
                                        if (planCode in 200..299) {
                                            val obj = try { Json.parseToJsonElement(planBody).jsonObject } catch (_: Exception) { null }
                                            val planId = obj?.get("plan_id")?.jsonPrimitive?.contentOrNull
                                            val numItems = obj?.get("number_items")?.jsonPrimitive?.content?.toIntOrNull()
                                            android.util.Log.i("UserPlan", "plan_id=$planId number_items=$numItems")
                                        }
                                    } finally {
                                        planConn.disconnect()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("FetchCollections", "Exception: ${e.message}")
                            collectionName = null
                            collectionId = null
                            Toast.makeText(this@ComposeMainActivity, "Error loading collections", Toast.LENGTH_LONG).show()
                        }
                        // Only navigate to welcome if we're on the login screen
                        // Don't navigate if we're already on welcome, addItem, or editItem screens
                        val currentRoute = nav.currentDestination?.route
                        if (currentRoute == "login") {
                            nav.navigate("welcome/$first") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                        hasCompletedInitialAuth = true
                        isAuthenticating = false
                    } else {
                        // Only route to login before initial auth is completed.
                        // After initial auth, ignore transient NotAuthenticated emissions to avoid unintended navigation.
                        if (!hasCompletedInitialAuth) {
                            hasCompletedInitialAuth = false
                            isAuthenticating = false
                            if (nav.currentDestination?.route != "login") {
                                nav.navigate("login") {
                                    popUpTo(0)
                                }
                            }
                        } else {
                            // Already past initial auth; do not auto-navigate on status changes.
                            isAuthenticating = false
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        android.util.Log.d("AuthFlow", "onNewIntent called with: ${intent.data}")
        supabase.handleDeeplinks(intent)
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("ActivityLifecycle", "onResume called")
    }

    override fun onPause() {
        super.onPause()
        android.util.Log.d("ActivityLifecycle", "onPause called")
    }

    override fun onStop() {
        super.onStop()
        android.util.Log.d("ActivityLifecycle", "onStop called")
    }

    override fun onDestroy() {
        super.onDestroy()
        billingManager.destroy()
        android.util.Log.d("ActivityLifecycle", "onDestroy called")
    }
}

// Decode and resize an image from a Uri so the longest side is <= maxSizePx.
// Returns a JPEG-compressed byte array with correct orientation applied.
private fun resizeImageToMax(resolver: ContentResolver, uri: Uri, maxSizePx: Int): ByteArray {
    // 1) Read EXIF orientation
    var exifOrientation = androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
    try {
        resolver.openInputStream(uri)?.use { inputStream ->
            val exif = androidx.exifinterface.media.ExifInterface(inputStream)
            exifOrientation = exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )
        }
    } catch (e: Exception) {
        android.util.Log.w("resizeImageToMax", "Failed to read EXIF orientation", e)
    }

    // 2) Bounds decode to get dimensions
    val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, optsBounds) }
    val srcW = optsBounds.outWidth
    val srcH = optsBounds.outHeight
    if (srcW <= 0 || srcH <= 0) {
        // Fallback: just stream through
        return resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    }

    // 3) Compute inSampleSize to get close to target with less memory
    var inSample = 1
    val maxSrc = maxOf(srcW, srcH)
    if (maxSrc > maxSizePx) {
        var halfW = srcW / 2
        var halfH = srcH / 2
        while ((halfW / inSample) >= maxSizePx || (halfH / inSample) >= maxSizePx) {
            inSample *= 2
        }
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = inSample }
    val decoded: Bitmap? = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    if (decoded == null) {
        return resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    }

    // 4) Apply EXIF orientation transformation
    val rotatedBitmap = when (exifOrientation) {
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> {
            val matrix = android.graphics.Matrix().apply { postRotate(90f) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> {
            val matrix = android.graphics.Matrix().apply { postRotate(180f) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }
        androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> {
            val matrix = android.graphics.Matrix().apply { postRotate(270f) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }
        androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
            val matrix = android.graphics.Matrix().apply { postScale(-1f, 1f) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }
        androidx.exifinterface.media.ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
            val matrix = android.graphics.Matrix().apply { postScale(1f, -1f) }
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        }
        else -> decoded
    }
    if (rotatedBitmap !== decoded) decoded.recycle()

    // 5) Scale precisely to max dimension if still larger than max
    val w = rotatedBitmap.width
    val h = rotatedBitmap.height
    val scale = if (w >= h) maxSizePx.toFloat() / w.toFloat() else maxSizePx.toFloat() / h.toFloat()
    val finalBitmap = if (maxOf(w, h) > maxSizePx) {
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(rotatedBitmap, newW, newH, true)
    } else {
        rotatedBitmap
    }

    // 6) Compress to JPEG
    val out = ByteArrayOutputStream()
    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
    if (finalBitmap !== rotatedBitmap) rotatedBitmap.recycle()
    return out.toByteArray()
}

// Fetch a single item's details (id, title, description, image_url, audio_url)
suspend private fun fetchItemDetails(activity: ComposeMainActivity, itemId: String): ItemData? {
    return withContext(Dispatchers.IO) {
        try {
            val token = activity.getAccessToken()
            if (token.isNullOrEmpty()) return@withContext null
            val url = URL(activity.getApiBaseUrl().trimEnd('/') + "/items/" + itemId)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 15000
            }
            try {
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    val obj = try { Json.parseToJsonElement(body).jsonObject } catch (_: Exception) { null }
                    if (obj != null) {
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
                        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
                        val description = obj["description"]?.jsonPrimitive?.contentOrNull
                        val imageUrl = obj["image_url"]?.jsonPrimitive?.contentOrNull
                        val audioUrl = obj["audio_url"]?.jsonPrimitive?.contentOrNull
                        val status = obj["status"]?.jsonPrimitive?.contentOrNull
                        val collectionId = obj["collection_id"]?.jsonPrimitive?.contentOrNull
                        return@withContext ItemData(id, title, description, imageUrl, audioUrl, status, collectionId)
                    }
                } else {
                    android.util.Log.e("FetchItem", "HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
            null
        } catch (e: Exception) {
            android.util.Log.e("FetchItem", "Exception: ${e.message}", e)
            null
        }
    }
}

// Delete an item
suspend private fun deleteItem(
    apiBase: String,
    token: String,
    itemId: String
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(apiBase.trimEnd('/') + "/items/" + itemId)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 10000
                readTimeout = 15000
            }
            try {
                val code = conn.responseCode
                if (code in 200..299) {
                    return@withContext true
                } else {
                    val body = (conn.errorStream ?: conn.inputStream)?.bufferedReader()?.use { it.readText() } ?: ""
                    android.util.Log.e("DeleteItem", "HTTP $code body=$body")
                    return@withContext false
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("DeleteItem", "Exception: ${e.message}", e)
            return@withContext false
        }
    }
}

// Update an item via multipart PUT, optionally including a new image
suspend private fun putItemMultipart(
    resolver: ContentResolver,
    apiBase: String,
    token: String,
    itemId: String,
    title: String?,
    description: String?,
    newImageUri: Uri?,
    newAudioUri: Uri?,
    deleteAudio: Boolean,
    collectionId: String?
): PostItemResult {
    return withContext(Dispatchers.IO) {
        val boundary = "----ShepherdBoundary" + System.currentTimeMillis()
        val lineEnd = "\r\n"
        val twoHyphens = "--"
        val url = URL(apiBase.trimEnd('/') + "/items/" + itemId)
        var conn: HttpURLConnection? = null
        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "PUT"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 30000
            }
            conn.outputStream.use { os ->
                fun writeString(s: String) = os.write(s.toByteArray(Charsets.UTF_8))
                fun writeField(name: String, value: String) {
                    writeString(twoHyphens + boundary + lineEnd)
                    writeString("Content-Disposition: form-data; name=\"$name\"" + lineEnd + lineEnd)
                    writeString(value + lineEnd)
                }

                if (title != null) writeField("title", title)
                if (description != null) writeField("description", description)
                if (collectionId != null) writeField("collection_id", collectionId)
                if (deleteAudio) writeField("delete_audio", "true")

                newImageUri?.let { uri ->
                    // Resize image so the longest side is 2400px and upload as JPEG
                    val resized = resizeImageToMax(resolver, uri, 2400)
                    val name = "image.jpg"
                    val mime = "image/jpeg"
                    writeString(twoHyphens + boundary + lineEnd)
                    writeString("Content-Disposition: form-data; name=\"image\"; filename=\"$name\"" + lineEnd)
                    writeString("Content-Type: $mime$lineEnd$lineEnd")
                    os.write(resized)
                    writeString(lineEnd)
                }

                newAudioUri?.let { uri ->
                    val name = "recording.m4a"
                    val mime = resolver.getType(uri) ?: "audio/m4a"
                    writeString(twoHyphens + boundary + lineEnd)
                    writeString("Content-Disposition: form-data; name=\"audio\"; filename=\"$name\"" + lineEnd)
                    writeString("Content-Type: $mime$lineEnd$lineEnd")
                    resolver.openInputStream(uri)?.use { it.copyTo(os) }
                    writeString(lineEnd)
                }

                writeString(twoHyphens + boundary + twoHyphens + lineEnd)
            }
            val code = conn.responseCode
            if (code in 200..299) PostItemResult(true, null) else {
                val body = (conn.errorStream ?: conn.inputStream)?.bufferedReader()?.use { it.readText() } ?: ""
                android.util.Log.e("PutItem", "HTTP $code body=$body")
                PostItemResult(false, "HTTP $code: $body")
            }
        } catch (e: Exception) {
            android.util.Log.e("PutItem", "Exception during upload", e)
            PostItemResult(false, e.message ?: "Exception during upload")
        } finally {
            conn?.disconnect()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemEditScreen(
    itemId: String,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    collectionIdProvider: () -> String?
) {
    val activity = LocalContext.current as ComposeMainActivity
    var isLoading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var currentImageUrl by remember { mutableStateOf<String?>(null) }
    var currentAudioUrl by remember { mutableStateOf<String?>(null) }
    var deleteAudio by remember { mutableStateOf(false) }
    var newImageUri by remember { mutableStateOf<Uri?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var selectedCollectionId by remember { mutableStateOf<String?>(null) }
    var collections by remember { mutableStateOf<List<CollectionData>>(emptyList()) }
    var collectionDropdownExpanded by remember { mutableStateOf(false) }
    var showFullScreenImage by remember { mutableStateOf(false) }

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        newImageUri = uri
    }

    LaunchedEffect(itemId) {
        isLoading = true
        // Fetch collections and item details in parallel to improve loading performance
        val collectionsDeferred = async { fetchCollections(activity) }
        val detailsDeferred = async { fetchItemDetails(activity, itemId) }
        
        collections = collectionsDeferred.await()
        val details = detailsDeferred.await()
        
        if (details != null) {
            title = details.title
            description = details.description ?: ""
            currentImageUrl = details.imageUrl
            currentAudioUrl = details.audioUrl
            selectedCollectionId = details.collectionId
        }
        isLoading = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Main form content (rendered first, at bottom layer)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.Top
        ) {
        // Header with back button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.TextButton(onClick = onCancel) {
                Text("< Back")
            }
            Text(
                text = "Edit Item",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        if (isLoading) {
            Text("Loading...", modifier = Modifier.padding(top = 16.dp))
            return@Column
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        )

        // Collection dropdown
        ExposedDropdownMenuBox(
            expanded = collectionDropdownExpanded,
            onExpandedChange = { collectionDropdownExpanded = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            OutlinedTextField(
                value = collections.find { it.id == selectedCollectionId }?.name ?: "Select Collection",
                onValueChange = {},
                readOnly = true,
                label = { Text("Collection") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = collectionDropdownExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = collectionDropdownExpanded,
                onDismissRequest = { collectionDropdownExpanded = false }
            ) {
                collections.forEach { collection ->
                    DropdownMenuItem(
                        text = { Text(collection.name) },
                        onClick = {
                            selectedCollectionId = collection.id
                            collectionDropdownExpanded = false
                        }
                    )
                }
            }
        }

        Button(
            onClick = { pickImageLauncher.launch("image/*") },
            modifier = Modifier.padding(top = 16.dp)
        ) { Text(if (newImageUri != null) "Change Photo" else "Pick New Photo") }

        if (newImageUri != null || !currentImageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clickable { showFullScreenImage = true }
                ) {
                    val modelData: Any? = newImageUri ?: currentImageUrl
                    val activity = LocalContext.current as ComposeMainActivity
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(modelData)
                            .crossfade(true)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .build(),
                        imageLoader = activity.imageLoader,
                        contentDescription = "Item image",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                if (newImageUri != null) {
                    IconButton(
                        onClick = { newImageUri = null },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove selected photo",
                            tint = Color.White,
                            modifier = Modifier
                                .size(32.dp)
                                .padding(4.dp)
                        )
                    }
                }
            }
        }

        // Audio player section with waveform
        if (!currentAudioUrl.isNullOrBlank()) {
            Text(
                text = "Recording",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
            
            val context = LocalContext.current
            // Rebuild player when audioUrl changes
            val exoPlayer = remember(currentAudioUrl) {
                ExoPlayer.Builder(context).build().apply {
                    try {
                        setMediaItem(MediaItem.fromUri(currentAudioUrl!!))
                        prepare()
                    } catch (e: Exception) {
                        android.util.Log.e("AudioPreview", "Failed to prepare player", e)
                    }
                }
            }
            
            DisposableEffect(Unit) {
                onDispose {
                    exoPlayer.release()
                }
            }
            
            AudioWaveformPlayer(
                exoPlayer = exoPlayer,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(120.dp)
            )
        }

        Button(
            onClick = {
                activity.lifecycleScope.launch {
                    val token = activity.getAccessToken()
                    if (token.isNullOrEmpty()) {
                        Toast.makeText(activity, "Not authenticated", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    if (title.isBlank()) {
                        Toast.makeText(activity, "Title is required", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    val result = putItemMultipart(
                        resolver = activity.contentResolver,
                        apiBase = activity.getApiBaseUrl(),
                        token = token,
                        itemId = itemId,
                        title = title,
                        description = description,
                        newImageUri = newImageUri,
                        newAudioUri = null,
                        deleteAudio = deleteAudio,
                        collectionId = selectedCollectionId
                    )
                    if (result.success) {
                        Toast.makeText(activity, "Item updated", Toast.LENGTH_SHORT).show()
                        onSave()
                    } else {
                        Toast.makeText(activity, "Failed to update: ${result.errorMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.padding(top = 24.dp)
        ) { Text("Save Changes") }

        Button(
            onClick = onCancel,
            modifier = Modifier.padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = Color.White
            )
        ) { Text("Cancel") }

        Button(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = Color.White
            )
        ) { Text("Delete Item") }
        }
        
        // Full-screen image overlay (rendered last, on top layer)
        if (showFullScreenImage && (newImageUri != null || !currentImageUrl.isNullOrBlank())) {
            var scale by remember { mutableStateOf(1f) }
            var offsetX by remember { mutableStateOf(0f) }
            var offsetY by remember { mutableStateOf(0f) }
            
            // Get screen dimensions for bounds calculation
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val screenWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) { 
                configuration.screenWidthDp.dp.toPx() 
            }
            val screenHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 
                configuration.screenHeightDp.dp.toPx() 
            }
            
            val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 5f)
                
                // Calculate the maximum allowed offset based on scale
                // When scale = 1, no panning allowed (maxOffset = 0)
                // When scale > 1, allow panning up to the scaled image bounds
                val maxOffsetX = (screenWidthPx * (scale - 1f)) / 2f
                val maxOffsetY = (screenHeightPx * (scale - 1f)) / 2f
                
                // Apply offset change and constrain to bounds
                offsetX = (offsetX + offsetChange.x).coerceIn(-maxOffsetX, maxOffsetX)
                offsetY = (offsetY + offsetChange.y).coerceIn(-maxOffsetY, maxOffsetY)
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                val modelData: Any? = newImageUri ?: currentImageUrl
                val activity = LocalContext.current as ComposeMainActivity
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(modelData)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    imageLoader = activity.imageLoader,
                    contentDescription = "Full screen item image",
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                        .transformable(state = state),
                    contentScale = ContentScale.Fit
                )
                // Close button
                IconButton(
                    onClick = { 
                        showFullScreenImage = false
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close full screen",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Item") },
            text = { Text("Are you sure you want to delete this item? This action cannot be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteDialog = false
                        activity.lifecycleScope.launch {
                            val token = activity.getAccessToken()
                            if (token.isNullOrEmpty()) {
                                Toast.makeText(activity, "Not authenticated", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            val success = deleteItem(
                                apiBase = activity.getApiBaseUrl(),
                                token = token,
                                itemId = itemId
                            )
                            if (success) {
                                Toast.makeText(activity, "Item deleted", Toast.LENGTH_SHORT).show()
                                onSave() // Navigate back
                            } else {
                                Toast.makeText(activity, "Failed to delete item", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}


@Composable
private fun AppNav(
    navController: NavHostController,
    onSignIn: () -> Unit,
    collectionName: String?,
    collectionIdProvider: () -> String?,
    onlyOneCollection: Boolean,
    isAuthenticating: Boolean
) {
    MaterialTheme(
        colorScheme = LegacyRetrieverLightColorScheme
    ) {
        Scaffold { padding ->
            NavHost(navController = navController, startDestination = "login", modifier = Modifier.padding(padding)) {
                composable("login") { LoginScreen(onSignIn, isAuthenticating) }
                composable("welcome/{first}") { backStack ->
                    val first = backStack.arguments?.getString("first") ?: "there"
                    WelcomeScreen(
                        firstName = first,
                        collectionName = collectionName,
                        onlyOneCollection = onlyOneCollection,
                        collectionIdProvider = collectionIdProvider,
                        onAddItem = { navController.navigate("item/new") },
                        onItemClick = { itemId -> navController.navigate("item/edit/$itemId") },
                        onCollectionClick = { collectionId -> navController.navigate("collection/$collectionId") },
                        onHelp = { navController.navigate("help") },
                        onSettings = { navController.navigate("settings") },
                        onUpgradePlan = { navController.navigate("upgrade-plan") }
                    )
                }
                composable("collection/{collectionId}") { backStack ->
                    val collectionId = backStack.arguments?.getString("collectionId") ?: ""
                    CollectionGalleryScreen(
                        collectionId = collectionId,
                        onBack = { navController.popBackStack() },
                        onItemClick = { itemId -> navController.navigate("item/edit/$itemId") },
                        onAddItem = { navController.navigate("item/new/$collectionId") }
                    )
                }
                composable("item/new") {
                    ItemFormScreen(
                        onSave = { title, imageUri ->
                            // navigate back on save success from inside ItemFormScreen via callback
                            android.util.Log.d("Navigation", "onSave called - popping back stack")
                            navController.popBackStack()
                        },
                        onCancel = { 
                            android.util.Log.d("Navigation", "onCancel called - popping back stack")
                            navController.popBackStack()
                        },
                        collectionIdProvider = collectionIdProvider,
                        passedCollectionId = null
                    )
                }
                composable("item/new/{collectionId}") { backStack ->
                    val collectionId = backStack.arguments?.getString("collectionId")
                    ItemFormScreen(
                        onSave = { title, imageUri ->
                            android.util.Log.d("Navigation", "onSave called - popping back stack")
                            navController.popBackStack()
                        },
                        onCancel = { 
                            android.util.Log.d("Navigation", "onCancel called - popping back stack")
                            navController.popBackStack()
                        },
                        collectionIdProvider = collectionIdProvider,
                        passedCollectionId = collectionId
                    )
                }
                composable("item/edit/{itemId}") { backStack ->
                    val itemId = backStack.arguments?.getString("itemId") ?: ""
                    ItemEditScreen(
                        itemId = itemId,
                        onSave = {
                            navController.popBackStack()
                        },
                        onCancel = { navController.popBackStack() },
                        collectionIdProvider = collectionIdProvider
                    )
                }
                composable("help") {
                    HelpScreen(onBack = { navController.popBackStack() })
                }
                composable("settings") {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onUpgradePlan = { navController.navigate("upgrade-plan") }
                    )
                }
                composable("upgrade-plan") {
                    UpgradePlanScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, onUpgradePlan: () -> Unit) {
    val activity = LocalContext.current as ComposeMainActivity
    val scope = rememberCoroutineScope()
    var showConfirmDialog by remember { mutableStateOf(false) }
    var isRevoking by remember { mutableStateOf(false) }
    var userPlan by remember { mutableStateOf<UserPlanData?>(null) }
    var isLoadingPlan by remember { mutableStateOf(true) }
    
    // Fetch user plan on screen load (with caching)
    LaunchedEffect(Unit) {
        userPlan = activity.getCachedUserPlan()
        isLoadingPlan = false
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.TextButton(onClick = onBack) {
                Text("< Back")
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Settings content
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Plan section
            Text(
                text = "Plan",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    if (isLoadingPlan) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                    } else if (userPlan != null) {
                        Text(
                            text = "Current Plan: ${userPlan!!.planName.replaceFirstChar { it.uppercase() }}",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Items: ${userPlan!!.totalItems} / ${userPlan!!.numberItemsLimit}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onUpgradePlan,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = BrandColors.Orange
                            )
                        ) {
                            Text(
                                text = if (userPlan!!.planId == "free") "Upgrade Plan" else "Manage Plan"
                            )
                        }
                    } else {
                        Text(
                            text = "Unable to load plan information",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Sharing section
            Text(
                text = "Sharing",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "Revoke All Collection Shares",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This will invalidate all share links for your collections. Anyone with existing links will no longer be able to access them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showConfirmDialog = true },
                        enabled = !isRevoking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        if (isRevoking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onError
                            )
                        } else {
                            Text("Revoke All Shares")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Version ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
    }
    
    // Confirmation dialog
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text("Revoke All Shares?") },
            text = { 
                Text("Are you sure you want to revoke all collection share links? This action cannot be undone and all existing share links will stop working immediately.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showConfirmDialog = false
                        isRevoking = true
                        scope.launch {
                            val success = revokeAllShares(activity)
                            isRevoking = false
                            if (success) {
                                Toast.makeText(activity, "All shares revoked successfully", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(activity, "Failed to revoke shares", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Revoke All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

data class SubscriptionTier(
    val id: String,
    val name: String,
    val price: String,
    val itemLimit: String,
    val features: List<String>,
    val isPopular: Boolean = false,
    val isCurrent: Boolean = false
)

@Composable
private fun UpgradePlanScreen(onBack: () -> Unit) {
    val activity = LocalContext.current as ComposeMainActivity
    val scope = rememberCoroutineScope()
    var currentPlan by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var subscriptionProducts by remember { mutableStateOf<List<SubscriptionProduct>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val activePurchases by activity.billingManager.activePurchases.collectAsState()
    val billingState by activity.billingManager.billingState.collectAsState()
    
    // Fetch current plan and products
    LaunchedEffect(Unit) {
        val planData = activity.getCachedUserPlan()
        currentPlan = planData?.planId
        
        // Query subscription products from Google Play
        subscriptionProducts = activity.billingManager.querySubscriptionProducts()
        isLoading = false
    }
    
    // Handle billing state changes
    LaunchedEffect(billingState) {
        when (val state = billingState) {
            is BillingState.PurchaseSuccess -> {
                Toast.makeText(
                    activity,
                    "Subscription activated successfully!",
                    Toast.LENGTH_LONG
                ).show()
                // Clear plan cache to force refresh
                activity.clearPlanCache()
                // Refresh plan data
                currentPlan = activity.getCachedUserPlan()?.planId
            }
            is BillingState.Error -> {
                if (state.message != "Purchase canceled") {
                    errorMessage = state.message
                }
            }
            else -> {}
        }
    }
    
    // Map product IDs to tier info
    fun getTierInfo(productId: String): Triple<String, String, List<String>> {
        return when (productId) {
            BillingManager.PRODUCT_STARTER -> Triple(
                "50 items",
                "Starter",
                listOf(
                    "Up to 50 items",
                    "High-quality photo storage",
                    "5 photos per item",
                    "30 second audio recordings",
                    "5 collections"
                )
            )
            BillingManager.PRODUCT_STANDARD -> Triple(
                "200 items",
                "Standard",
                listOf(
                    "Up to 200 items",
                    "High-quality photo storage",
                    "45 second audio recordings",
                    "20 collections",
                    "Priority support"
                )
            )
            BillingManager.PRODUCT_PREMIUM -> Triple(
                "Unlimited items",
                "Premium",
                listOf(
                    "2000 items",
                    "High-quality photo storage",
                    "1 minute audio recordings",
                    "Unlimited collections",
                    "Share collections",
                    "Priority support",
                    "Advanced organization",
                    "Export & backup tools",
                    "Premium support"
                )
            )
            else -> Triple("Unknown", "Unknown", emptyList())
        }
    }
    
    val subscriptionTiers = buildList {
        // Always add Free tier
        add(
            SubscriptionTier(
                id = "free",
                name = "Free",
                price = "$0",
                itemLimit = "15 items",
                features = listOf(
                    "Up to 15 items",
                    "Standard resolution photo storage",
                    "1 photo per item",
                    "15 second audio recordings",
                    "1 collection"
                ),
                isCurrent = currentPlan == "free" && activePurchases.isEmpty()
            )
        )
        
        // Add products from Google Play
        subscriptionProducts.forEach { product ->
            val (itemLimit, tierName, features) = getTierInfo(product.productId)
            add(
                SubscriptionTier(
                    id = product.productId,
                    name = tierName,
                    price = product.price,
                    itemLimit = itemLimit,
                    features = features,
                    isPopular = product.productId == BillingManager.PRODUCT_STANDARD,
                    isCurrent = activePurchases.contains(product.productId)
                )
            )
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.TextButton(onClick = onBack) {
                Text("< Back")
            }
            Text(
                text = "Choose Your Plan",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Select the plan that best fits your needs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            // Error message if any
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Subscription cards
            subscriptionTiers.forEach { tier ->
                SubscriptionCard(
                    tier = tier,
                    onSelect = {
                        if (tier.id == "free") {
                            Toast.makeText(
                                activity,
                                "You're already on the free plan",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // Find the product and launch purchase flow
                            val product = subscriptionProducts.find { it.productId == tier.id }
                            if (product != null) {
                                activity.billingManager.launchPurchaseFlow(activity, product)
                            } else {
                                Toast.makeText(
                                    activity,
                                    "Product not available",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Additional info
        Text(
            text = "All plans include:",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "• Secure cloud storage\n• Audio transcription\n• Collection sharing\n• Cross-device sync\n• Cancel anytime",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SubscriptionCard(
    tier: SubscriptionTier,
    onSelect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (tier.isPopular) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (tier.isPopular) 8.dp else 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Header with name and popular badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tier.name,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (tier.isPopular) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
                if (tier.isPopular) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = BrandColors.Orange
                        )
                    ) {
                        Text(
                            text = "POPULAR",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Price
            Row(
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = tier.price,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (tier.isPopular) 
                        MaterialTheme.colorScheme.onPrimaryContainer 
                    else 
                        MaterialTheme.colorScheme.onSurface
                )
                if (tier.price != "$0") {
                    Spacer(modifier = Modifier.width(4.dp))
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = tier.itemLimit,
                style = MaterialTheme.typography.bodyMedium,
                color = if (tier.isPopular) 
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Features list
            tier.features.forEach { feature ->
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = BrandColors.Green,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (tier.isPopular) 
                            MaterialTheme.colorScheme.onPrimaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Select button
            Button(
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
                enabled = !tier.isCurrent,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (tier.isPopular) 
                        BrandColors.Orange 
                    else 
                        MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = if (tier.isCurrent) "Current Plan" else "Select ${tier.name}",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun LoginScreen(onSignIn: () -> Unit, isAuthenticating: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BrandColors.Orange),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isAuthenticating) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "LegacyRetriever Logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Your legacy, for generations",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Signing in...")
        } else {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "LegacyRetriever Logo",
                modifier = Modifier.size(120.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Your legacy, for generations",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "LegacyRetriever",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sign in to continue",
                color = Color.White
            )
            Button(
                onClick = onSignIn,
                modifier = Modifier
                    .padding(top = 16.dp)
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandColors.Green,
                    contentColor = Color.White
                )
            ) {
                Text("Click here to sign in with Google")
            }
        }
    }
}

@Composable
private fun HelpScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Header with back button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.TextButton(onClick = onBack) {
                Text("< Back")
            }
            Text(
                text = "Help & Guide",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Welcome message
        Text(
            text = "Welcome! LegacyRetriever helps you save the stories behind your most treasured items—and keep your voice with them forever.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // How it works section
        Text(
            text = "How it works (3 quick steps)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        HelpStep(
            number = "1",
            title = "Upload a photo",
            description = "Tap Add item. Use your camera or choose from your gallery."
        )

        HelpStep(
            number = "2",
            title = "Record your story",
            description = "Tap Record and speak naturally (30–120s is perfect). We will transcribe the audio into the item description, you can edit the description later."
        )

        HelpStep(
            number = "3",
            title = "Share with loved ones",
            description = "Add items to a Collection, then tap Share. Send this link to your loved ones. They'll hear your voice as they browse and they can download the gallery to keep forever, ensuring your legacy stays safe."
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Tips section
        Text(
            text = "Tips for great results",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = "• Quiet space: reduce background noise and hold the phone 15–20 cm from your mouth.\n" +
                    "• Prompts to try: Where did it come from? Who gave it to you? Why does it matter? What is the value? Any funny memories?\n" +
                    "• One item, one story: shorter, focused clips are easier to enjoy.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Organize section
        Text(
            text = "Organize",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = "• Use Collections (e.g., \"Family Heirlooms\", \"Travel\", \"Childhood\").",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Sharing & privacy section
        Text(
            text = "Sharing & privacy",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = "• Private by default. Only you can see new items until you share.\n" +
                    "• Links can be revoked anytime in Share → Manage access.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Common questions section
        Text(
            text = "Common questions",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = "Can I edit a story? Yes, open the item from the Collection view. Replace the photo or edit the description.\n\n" +
                    "Download a backup? Go to the share link and download the zip file, your loved ones can do the same.\n\n" +
                    "Auto-captions are created for each recording; edit them under the Description field of the item.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Need help section
        Text(
            text = "Need help?",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Text(
            text = "Check Profile → Help & Support or email help@legacyretriever.app.\n\n" +
                    "We're honored to help you preserve your stories.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )
    }
}

@Composable
private fun HelpStep(number: String, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        Card(
            modifier = Modifier.size(40.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WelcomeScreen(
    firstName: String,
    collectionName: String?,
    onlyOneCollection: Boolean,
    collectionIdProvider: () -> String?,
    onAddItem: () -> Unit,
    onItemClick: (String) -> Unit,
    onCollectionClick: (String) -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
    onUpgradePlan: () -> Unit
) {
    val activity = LocalContext.current as ComposeMainActivity
    var items by remember { mutableStateOf<List<ItemData>>(emptyList()) }
    var collections by remember { mutableStateOf<List<CollectionData>>(emptyList()) }
    var userPlan by remember { mutableStateOf<UserPlanData?>(null) }
    var isLoadingItems by remember { mutableStateOf(true) }
    var isLoadingCollections by remember { mutableStateOf(true) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    var showAddCollectionDialog by remember { mutableStateOf(false) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var itemsRefreshTrigger by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    // Observe lifecycle to refresh items when app resumes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                android.util.Log.d("WelcomeScreen", "App resumed, triggering items refresh")
                // Increment trigger to force refresh via LaunchedEffect
                itemsRefreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(itemsRefreshTrigger) {
        isLoadingItems = true
        items = fetchLatestItems(activity)
        isLoadingItems = false
        android.util.Log.d("WelcomeScreen", "Items loaded: ${items.size}")
        
        // Fetch user plan data only on first load (when trigger is 0)
        if (itemsRefreshTrigger == 0) {
            userPlan = activity.getCachedUserPlan()
        }
    }

    LaunchedEffect(refreshTrigger) {
        isLoadingCollections = true
        collections = fetchCollections(activity)
        android.util.Log.d("WelcomeScreen", "Collections loaded: ${collections.size}")
        isLoadingCollections = false
    }

    // Reload collections when switching to Collections tab
    LaunchedEffect(selectedTabIndex) {
        if (selectedTabIndex == 1) {
            isLoadingCollections = true
            collections = fetchCollections(activity)
            android.util.Log.d("WelcomeScreen", "Collections reloaded on tab switch: ${collections.size}")
            isLoadingCollections = false
        }
    }

    val pullRefreshState = rememberPullToRefreshState()

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(Unit) {
            android.util.Log.d("WelcomeScreen", "Pull to refresh triggered")
            if (selectedTabIndex == 0) {
                isLoadingItems = true
                items = fetchLatestItems(activity)
                isLoadingItems = false
                android.util.Log.d("WelcomeScreen", "Items refreshed via pull: ${items.size}")
            } else {
                isLoadingCollections = true
                collections = fetchCollections(activity)
                isLoadingCollections = false
                android.util.Log.d("WelcomeScreen", "Collections refreshed via pull: ${collections.size}")
            }
            pullRefreshState.endRefresh()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullRefreshState.nestedScrollConnection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(PaddingValues(16.dp)),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    IconButton(onClick = onHelp) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Help",
                            tint = Color.Black
                        )
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = Color.Black
                        )
                    }
                }
                
                // Check if user has reached plan limit
                val planLimitReached = userPlan?.let { 
                    it.totalItems >= it.numberItemsLimit 
                } ?: false
                
                Button(onClick = {
                    if (selectedTabIndex == 0) {
                        if (planLimitReached) {
                            onUpgradePlan()
                        } else {
                            onAddItem()
                        }
                    } else {
                        showAddCollectionDialog = true
                    }
                }) {
                    Text(
                        if (selectedTabIndex == 0) {
                            if (planLimitReached) "Plan limit reached" else "Add Item"
                        } else {
                            "Add Collection"
                        }
                    )
                }
            }

            // Tabs
            TabRow(
                selectedTabIndex = selectedTabIndex,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Recent Items", color = Color.Black) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Collections", color = Color.Black) }
                )
            }

            // Tab content
            when (selectedTabIndex) {
                0 -> {
                    if (isLoadingItems && items.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (items.isNotEmpty()) {
                        ItemsGrid(items = items, onItemClick = onItemClick)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "You have not loaded any items yet",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(bottom = 36.dp)
                            )
                            Text(
                                text = "Start your legacy, it only takes 30 seconds!",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(bottom = 24.dp)
                            )
                            Button(onClick = onAddItem) {
                                Text("Load your first item")
                            }
                        }
                    }
                }
                1 -> {
                    if (isLoadingCollections && collections.isEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 24.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (collections.isNotEmpty()) {
                        CollectionsList(
                            collections = collections, 
                            onCollectionClick = onCollectionClick,
                            onDelete = { refreshTrigger++ }
                        )
                    } else {
                        Text(
                            text = "No collections found",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 24.dp)
                        )
                    }
                }
            }
        }
        PullToRefreshContainer(
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
    
    // Add Collection Dialog
    if (showAddCollectionDialog) {
        AddCollectionDialog(
            onDismiss = { showAddCollectionDialog = false },
            onSave = { collectionName ->
                scope.launch {
                    val success = createCollection(activity, collectionName)
                    if (success) {
                        Toast.makeText(activity, "Collection created successfully", Toast.LENGTH_SHORT).show()
                        isLoadingCollections = true
                        collections = fetchCollections(activity)
                        isLoadingCollections = false
                        showAddCollectionDialog = false
                    } else {
                        Toast.makeText(activity, "Failed to create collection", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
private fun AddCollectionDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var collectionName by remember { mutableStateOf("") }
    
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Collection") },
        text = {
            Column {
                Text("Enter a name for the new collection:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = collectionName,
                    onValueChange = { collectionName = it },
                    label = { Text("Collection Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (collectionName.isNotBlank()) {
                        onSave(collectionName.trim())
                    }
                },
                enabled = collectionName.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(androidx.media3.common.util.UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ItemFormScreen(
    onSave: (String, Uri?) -> Unit,
    onCancel: () -> Unit,
    collectionIdProvider: () -> String?,
    passedCollectionId: String? = null
) {
    val activity = LocalContext.current as ComposeMainActivity
    var title by rememberSaveable { mutableStateOf("processing") }
    var imageUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var audioUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var isRecording by rememberSaveable { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputPfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var showCamera by rememberSaveable { mutableStateOf(false) }
    var imageCapture by remember { mutableStateOf<androidx.camera.core.ImageCapture?>(null) }
    var amplitudes by remember { mutableStateOf(listOf<Float>()) }
    var recordingTimeSeconds by remember { mutableStateOf(0) }
    var userPlan by remember { mutableStateOf<UserPlanData?>(null) }
    var collections by remember { mutableStateOf<List<CollectionData>>(emptyList()) }
    var selectedCollectionId by rememberSaveable { mutableStateOf(passedCollectionId ?: collectionIdProvider()) }
    var isLoadingCollections by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Fetch user plan to determine recording time limit (with caching)
    LaunchedEffect(Unit) {
        userPlan = activity.getCachedUserPlan()
        isLoadingCollections = true
        collections = fetchCollections(activity)
        isLoadingCollections = false
        // Set initial selection if not already set
        if (selectedCollectionId == null && collections.isNotEmpty()) {
            selectedCollectionId = collections.first().id
        }
    }
    
    // Determine max recording time based on plan (25s for free, 60s for others)
    val maxRecordingSeconds = if (userPlan?.planId == "5019a2e2-bf60-451f-ad5b-5066c4065dd5") 25 else 60
    
    android.util.Log.d("ItemFormScreen", "Composing ItemFormScreen")
    
    DisposableEffect(Unit) {
        android.util.Log.d("ItemFormScreen", "ItemFormScreen entered composition")
        // Keep screen on while creating a new item to avoid the system stopping surfaces on screen-off
        val window = activity.window
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            android.util.Log.d("ItemFormScreen", "ItemFormScreen leaving composition")
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        imageUri = uri
    }
    // Camera permission for CameraX
    val requestCameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showCamera = true
        } else {
            Toast.makeText(activity, "Camera permission is required to take photos", Toast.LENGTH_LONG).show()
        }
    }
    // Permission launcher for RECORD_AUDIO
    val requestRecordPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            android.util.Log.d("AudioRecord", "RECORD_AUDIO permission granted by user")
            Toast.makeText(activity, "Permission granted. Tap Record to start.", Toast.LENGTH_SHORT).show()
        } else {
            android.util.Log.w("AudioRecord", "RECORD_AUDIO permission denied by user")
            Toast.makeText(activity, "Microphone permission is required to record audio.", Toast.LENGTH_LONG).show()
        }
    }

    // Start recording using MediaRecorder to a MediaStore Uri
    fun startRecording() {
        if (isRecording) return
        try {
            val hasPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (!hasPermission) {
                requestRecordPermission.launch(Manifest.permission.RECORD_AUDIO)
                return
            }

            // Create a MediaStore entry for the recording
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "recording_" + System.currentTimeMillis() + ".m4a")
                put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
                // Let the system place it under Music/ by default; RELATIVE_PATH optional but nicer on API 29+
                // put(MediaStore.MediaColumns.RELATIVE_PATH, "Music/Shepherd")
            }
            val uri = activity.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, contentValues)
            if (uri == null) {
                Toast.makeText(activity, "Failed to create audio file", Toast.LENGTH_LONG).show()
                return
            }
            val pfd = activity.contentResolver.openFileDescriptor(uri, "w")
            if (pfd == null) {
                Toast.makeText(activity, "Failed to open audio file", Toast.LENGTH_LONG).show()
                return
            }

            // Use context-based constructor to avoid deprecation warning
            val recorder = MediaRecorder(activity)
            mediaRecorder = recorder
            outputPfd = pfd
            audioUri = uri // keep the content Uri for upload later

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128_000)
            recorder.setAudioSamplingRate(44_100)
            recorder.setOutputFile(pfd.fileDescriptor)
            recorder.prepare()
            recorder.start()
            isRecording = true
            amplitudes = listOf() // Reset amplitudes
            recordingTimeSeconds = 0 // Reset timer
            
            // Start amplitude polling and timer
            scope.launch {
                while (isRecording) {
                    try {
                        val amplitude = recorder.maxAmplitude
                        val normalizedAmplitude = (amplitude / 32767f).coerceIn(0f, 1f)
                        amplitudes = (amplitudes + normalizedAmplitude).takeLast(50) // Keep last 50 samples
                    } catch (e: Exception) {
                        android.util.Log.e("AudioRecord", "Error reading amplitude", e)
                    }
                    kotlinx.coroutines.delay(50) // Poll every 50ms
                }
            }
            
            // Start recording timer
            scope.launch {
                while (isRecording && recordingTimeSeconds < maxRecordingSeconds) {
                    kotlinx.coroutines.delay(1000)
                    if (isRecording) {
                        recordingTimeSeconds++
                        // Auto-stop when time limit reached
                        if (recordingTimeSeconds >= maxRecordingSeconds) {
                            // Stop recording inline
                            try {
                                mediaRecorder?.apply {
                                    stop()
                                    release()
                                }
                                android.util.Log.d("AudioRecord", "Recording auto-stopped at time limit")
                            } catch (e: Exception) {
                                android.util.Log.e("AudioRecord", "Error stopping recording", e)
                            } finally {
                                try { outputPfd?.close() } catch (_: Exception) {}
                                outputPfd = null
                                mediaRecorder = null
                                isRecording = false
                            }
                            Toast.makeText(activity, "Maximum recording time reached", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            
            android.util.Log.d("AudioRecord", "Recording started: $uri")
        } catch (e: Exception) {
            android.util.Log.e("AudioRecord", "Failed to start recording", e)
            Toast.makeText(activity, "Failed to start recording: ${e.message}", Toast.LENGTH_LONG).show()
            // Clean up
            try { mediaRecorder?.release() } catch (_: Exception) {}
            mediaRecorder = null
            try { outputPfd?.close() } catch (_: Exception) {}
            outputPfd = null
            isRecording = false
        }
    }

    fun stopRecording() {
        if (!isRecording) return
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            android.util.Log.d("AudioRecord", "Recording stopped: $audioUri")
        } catch (e: Exception) {
            android.util.Log.e("AudioRecord", "Error stopping recording", e)
        } finally {
            try { outputPfd?.close() } catch (_: Exception) {}
            outputPfd = null
            mediaRecorder = null
            isRecording = false
        }
    }

    // Prevent accidental back navigation
    BackHandler {
        android.util.Log.d("ItemForm", "Back pressed - showing confirmation")
        // For now, just call onCancel which will pop the back stack
        // You could add a confirmation dialog here if needed
        if (isRecording) {
            stopRecording()
        }
        onCancel()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // In-app CameraX full-screen overlay
        if (showCamera) {
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            val context = androidx.compose.ui.platform.LocalContext.current
            val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(context)
            
            Box(modifier = Modifier.fillMaxSize()) {
                // Camera preview takes full screen
                androidx.compose.ui.viewinterop.AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = androidx.camera.view.PreviewView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                        }
                        val cameraProviderFuture = androidx.camera.lifecycle.ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            try {
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = androidx.camera.core.Preview.Builder().build().apply {
                                    setSurfaceProvider(previewView.surfaceProvider)
                                }
                                // Force portrait orientation since app is locked to portrait mode
                                val imgCapture = androidx.camera.core.ImageCapture.Builder()
                                    .setCaptureMode(androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                    .setTargetRotation(android.view.Surface.ROTATION_0)
                                    .build()
                                imageCapture = imgCapture
                                val selector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA
                                cameraProvider.unbindAll()
                                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imgCapture)
                            } catch (e: Exception) {
                                android.util.Log.e("CameraX", "Binding failed", e)
                                Toast.makeText(context, "Camera error", Toast.LENGTH_SHORT).show()
                                showCamera = false
                            }
                        }, mainExecutor)
                        previewView
                    }
                )
                
                // Buttons overlaid at bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                ) {
                    Button(
                        onClick = {
                            val resolver = activity.contentResolver
                            val name = "photo_" + System.currentTimeMillis() + ".jpg"
                            val values = android.content.ContentValues().apply {
                                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name)
                                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            }
                            val options = androidx.camera.core.ImageCapture.OutputFileOptions.Builder(
                                resolver,
                                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                values
                            ).build()
                            val capture = imageCapture
                            if (capture == null) {
                                Toast.makeText(activity, "Camera not ready", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            capture.takePicture(options, mainExecutor, object: androidx.camera.core.ImageCapture.OnImageSavedCallback {
                                override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                                    android.util.Log.e("CameraX", "Capture error", exception)
                                    Toast.makeText(activity, "Failed to capture photo", Toast.LENGTH_SHORT).show()
                                }
                                override fun onImageSaved(outputFileResults: androidx.camera.core.ImageCapture.OutputFileResults) {
                                    val savedUri = outputFileResults.savedUri
                                    imageUri = savedUri
                                    showCamera = false
                                    android.util.Log.d("CameraX", "Saved image: $savedUri")
                                }
                            })
                        },
                        modifier = Modifier.padding(horizontal = 32.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColors.Green,
                            contentColor = Color.White
                        )
                    ) { Text("Capture") }
                    Button(
                        onClick = { showCamera = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        )
                    ) { Text("Cancel") }
                }
            }
        }
        
        // Main form content
        if (!showCamera) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with back button and title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    androidx.compose.material3.TextButton(onClick = onCancel) {
                        Text("< Back", color = Color.Black)
                    }
                    Text(
                        text = "Add New Item",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.Black
                    )
                    // Spacer to balance the layout
                    Spacer(modifier = Modifier.width(48.dp))
                }
                
                // Step 1: Photo
                Text(
                    text = "Step 1: Add Photo",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val hasCam = androidx.core.content.ContextCompat.checkSelfPermission(
                                activity, android.Manifest.permission.CAMERA
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (hasCam) {
                                showCamera = true
                            } else {
                                requestCameraPermission.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Take Photo")
                    }
                    Button(
                        onClick = { pickImageLauncher.launch("image/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (imageUri != null) "Change Photo" else "Pick Photo")
                    }
                }
                
                // Image preview
                if (imageUri != null) {
                    Box(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .fillMaxWidth()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                        ) {
                            val activity = LocalContext.current as ComposeMainActivity
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(imageUri)
                                    .crossfade(true)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .build(),
                                imageLoader = activity.imageLoader,
                                contentDescription = "Item image",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                        // Remove button
                        IconButton(
                            onClick = { imageUri = null },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Remove photo",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(32.dp)
                                    .padding(4.dp)
                            )
                        }
                    }
                }
                
                // Step 2: Audio
                Text(
                    text = "Step 2: Record Audio Description",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(onClick = {
                        if (isRecording) stopRecording() else startRecording()
                    }) {
                        Text(
                            when {
                                isRecording -> "Stop Recording"
                                audioUri != null -> "Re-record Audio"
                                else -> "Record Audio"
                            }
                        )
                    }
                    if (audioUri != null && !isRecording) {
                        Text(text = "Audio attached", style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { audioUri = null }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Remove audio")
                        }
                    }
                }

                // Waveform visualization during recording
                if (isRecording) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val barWidth = 4.dp.toPx()
                                val barSpacing = 2.dp.toPx()
                                val totalBarWidth = barWidth + barSpacing
                                val maxBars = (size.width / totalBarWidth).toInt()
                                val displayAmplitudes = amplitudes.takeLast(maxBars)
                                
                                displayAmplitudes.forEachIndexed { index, amplitude ->
                                    val barHeight = (amplitude * size.height * 0.8f).coerceAtLeast(4.dp.toPx())
                                    val x = size.width - (displayAmplitudes.size - index) * totalBarWidth
                                    val y = (size.height - barHeight) / 2
                                    
                                    drawRect(
                                        color = androidx.compose.ui.graphics.Color(0xFF2196F3),
                                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                                    )
                                }
                            }
                            // Recording timer overlay
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val remainingSeconds = maxRecordingSeconds - recordingTimeSeconds
                                Text(
                                    text = String.format("%d:%02d", remainingSeconds / 60, remainingSeconds % 60),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = if (remainingSeconds <= 5) Color.Red else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.background(
                                        color = Color.White.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(8.dp)
                                    ).padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                                Text(
                                    text = "remaining",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.background(
                                        color = Color.White.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(4.dp)
                                    ).padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Audio preview (ExoPlayer) when an audio file is attached and not currently recording
                if (audioUri != null && !isRecording) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp)
                    ) {
                        val context = LocalContext.current
                        // Rebuild player when audioUri changes
                        val exoPlayer = remember(audioUri) {
                            ExoPlayer.Builder(context).build().apply {
                                try {
                                    setMediaItem(MediaItem.fromUri(audioUri!!))
                                    prepare()
                                } catch (e: Exception) {
                                    android.util.Log.e("AudioPreview", "Failed to prepare player", e)
                                }
                            }
                        }
                        DisposableEffect(exoPlayer) {
                            onDispose {
                                try { exoPlayer.release() } catch (_: Exception) {}
                            }
                        }
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    useController = true
                                    player = exoPlayer
                                    controllerAutoShow = true
                                    setShowRewindButton(true)
                                    setShowFastForwardButton(true)
                                }
                            },
                            update = { view -> view.player = exoPlayer },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // Step 3: Select Collection
                Text(
                    text = "Step 3: Select Collection",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Black,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp, bottom = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Start
                )
                
                if (isLoadingCollections) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                } else if (collections.isEmpty()) {
                    Text(
                        text = "No collections available. Please create one first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    var expanded by remember { mutableStateOf(false) }
                    val selectedCollection = collections.find { it.id == selectedCollectionId }
                    
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedCollection?.name ?: "Select a collection",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Collection") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            collections.forEach { collection ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(
                                                text = collection.name,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                text = "${collection.numberItems} items",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedCollectionId = collection.id
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                // Show spinner while saving, otherwise show buttons
                if (isSaving) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = BrandColors.Green
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Saving item...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            activity.lifecycleScope.launch {
                                isSaving = true
                                try {
                                    if (isRecording) {
                                        stopRecording()
                                    }
                                    val token = activity.getAccessToken()
                                    if (token.isNullOrEmpty()) {
                                        android.util.Log.w("ItemForm", "Save blocked: Not authenticated")
                                        Toast.makeText(activity, "Not authenticated", Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    // Title defaults to "processing"; no validation required.
                                    val collectionId = selectedCollectionId
                                    if (collectionId.isNullOrEmpty()) {
                                        android.util.Log.w("ItemForm", "Save blocked: No collectionId available")
                                        Toast.makeText(activity, "No collection available. Please create one on the server.", Toast.LENGTH_LONG).show()
                                        return@launch
                                    }
                                    val result = postItemMultipart(
                                        resolver = activity.contentResolver,
                                        apiBase = activity.getApiBaseUrl(),
                                        token = token,
                                        title = title,
                                        imageUri = imageUri,
                                        audioUri = audioUri,
                                        collectionId = collectionId
                                    )
                                    if (result.success) {
                                        Toast.makeText(activity, "Item saved", Toast.LENGTH_SHORT).show()
                                        onSave(title, imageUri)
                                    } else {
                                        android.util.Log.e("PostItem", "Save failed: ${result.errorMessage ?: "unknown error"}")
                                        Toast.makeText(activity, "Failed to save: ${result.errorMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                                    }
                                } finally {
                                    isSaving = false
                                }
                            }
                        },
                        modifier = Modifier.padding(top = 24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandColors.Green,
                            contentColor = Color.White
                        )
                    ) { Text("Save Item") }
                    Button(
                        onClick = onCancel,
                        modifier = Modifier.padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        )
                    ) { Text("Cancel") }
                }
            }
        }
    }
}
data class PostItemResult(val success: Boolean, val errorMessage: String?)

data class ItemData(
    val id: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val audioUrl: String?,
    val status: String?,
    val collectionId: String?
)

data class CollectionData(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val numberItems: Int
)

data class UserPlanData(
    val planId: String,
    val planName: String,
    val numberItemsLimit: Int,
    val totalItems: Int
)

suspend private fun fetchLatestItems(activity: ComposeMainActivity): List<ItemData> {
    return withContext(Dispatchers.IO) {
        try {
            val token = activity.getAccessToken()
            if (token.isNullOrEmpty()) {
                android.util.Log.w("FetchItems", "No token available")
                return@withContext emptyList()
            }
            val url = URL(activity.getApiBaseUrl().trimEnd('/') + "/items")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 15000
            }
            try {
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    android.util.Log.d("FetchItems", "Response body: $body")
                    val arr = try { Json.parseToJsonElement(body).jsonArray } catch (_: Exception) { null }
                    val items = arr?.take(12)?.mapNotNull { element ->
                        val obj = element.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
                        val description = obj["description"]?.jsonPrimitive?.contentOrNull
                        val imageUrl = obj["image_url"]?.jsonPrimitive?.contentOrNull
                        val audioUrl = obj["audio_url"]?.jsonPrimitive?.contentOrNull
                        val status = obj["status"]?.jsonPrimitive?.contentOrNull
                        val collectionId = obj["collection_id"]?.jsonPrimitive?.contentOrNull
                        android.util.Log.d("FetchItems", "Item: id=$id, title=$title, status=$status, imageUrl=$imageUrl, audioUrl=$audioUrl")
                        ItemData(id, title, description, imageUrl, audioUrl, status, collectionId)
                    } ?: emptyList()
                    android.util.Log.d("FetchItems", "Loaded ${items.size} items")
                    items
                } else {
                    android.util.Log.e("FetchItems", "HTTP $code")
                    emptyList()
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("FetchItems", "Exception: ${e.message}", e)
            emptyList()
        }
    }
}

suspend private fun fetchCollections(activity: ComposeMainActivity): List<CollectionData> {
    return withContext(Dispatchers.IO) {
        try {
            val token = activity.getAccessToken()
            if (token.isNullOrEmpty()) {
                android.util.Log.w("FetchCollections", "No token available")
                return@withContext emptyList()
            }
            val url = URL(activity.getApiBaseUrl().trimEnd('/') + "/collections")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 15000
            }
            try {
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    android.util.Log.d("FetchCollections", "Response body: $body")
                    val arr = try { Json.parseToJsonElement(body).jsonArray } catch (_: Exception) { null }
                    val collections = arr?.mapNotNull { element ->
                        val obj = element.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "Untitled Collection"
                        val thumbnailUrl = obj["thumbnail_url"]?.jsonPrimitive?.contentOrNull
                        val numberItems = obj["number_items"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                        CollectionData(id, name, thumbnailUrl, numberItems)
                    } ?: emptyList()
                    android.util.Log.d("FetchCollections", "Loaded ${collections.size} collections")
                    collections
                } else {
                    android.util.Log.e("FetchCollections", "HTTP $code")
                    emptyList()
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("FetchCollections", "Exception: ${e.message}", e)
            emptyList()
        }
    }
}

suspend private fun deleteCollection(activity: ComposeMainActivity, collectionId: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val token = activity.getAccessToken()
            if (token.isNullOrEmpty()) {
                android.util.Log.w("DeleteCollection", "No token available")
                return@withContext false
            }
            val url = URL(activity.getApiBaseUrl().trimEnd('/') + "/collections/$collectionId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 10000
                readTimeout = 15000
            }
            try {
                val code = conn.responseCode
                if (code == 204) {
                    android.util.Log.d("DeleteCollection", "Collection deleted successfully")
                    return@withContext true
                } else {
                    val body = (conn.errorStream ?: conn.inputStream)?.bufferedReader()?.use { it.readText() } ?: ""
                    android.util.Log.e("DeleteCollection", "HTTP $code: $body")
                    return@withContext false
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("DeleteCollection", "Exception: ${e.message}", e)
            return@withContext false
        }
    }
}

suspend private fun fetchUserPlan(activity: ComposeMainActivity): UserPlanData? {
    return withContext(Dispatchers.IO) {
        try {
            val token = activity.getAccessToken()
            if (token.isNullOrEmpty()) {
                android.util.Log.w("FetchUserPlan", "No token available")
                return@withContext null
            }
            val url = URL(activity.getApiBaseUrl().trimEnd('/') + "/user/plan")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 15000
            }
            try {
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    android.util.Log.d("FetchUserPlan", "Response body: $body")
                    val obj = try { Json.parseToJsonElement(body).jsonObject } catch (_: Exception) { null }
                    if (obj != null) {
                        val planId = obj["plan_id"]?.jsonPrimitive?.contentOrNull ?: ""
                        val planName = obj["plan_name"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
                        val numberItemsLimit = obj["number_items_limit"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                        val totalItems = obj["total_items"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                        android.util.Log.d("FetchUserPlan", "Plan: name=$planName, limit=$numberItemsLimit, total=$totalItems")
                        UserPlanData(planId, planName, numberItemsLimit, totalItems)
                    } else {
                        null
                    }
                } else {
                    android.util.Log.e("FetchUserPlan", "HTTP $code")
                    null
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("FetchUserPlan", "Exception: ${e.message}", e)
            null
        }
    }
}

suspend private fun revokeAllShares(activity: ComposeMainActivity): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val token = activity.getAccessToken()
            if (token.isNullOrEmpty()) {
                android.util.Log.w("RevokeAllShares", "No token available")
                return@withContext false
            }
            val url = URL(activity.getApiBaseUrl().trimEnd('/') + "/collections/revoke-all-shares")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 15000
            }
            try {
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                android.util.Log.d("RevokeAllShares", "HTTP $code body=$body")
                code in 200..299
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("RevokeAllShares", "Exception: ${e.message}", e)
            false
        }
    }
}

suspend private fun createCollection(activity: ComposeMainActivity, collectionName: String): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val token = activity.getAccessToken()
            if (token.isNullOrEmpty()) {
                android.util.Log.w("CreateCollection", "No token available")
                return@withContext false
            }
            val url = URL(activity.getApiBaseUrl().trimEnd('/') + "/collections")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 15000
            }
            try {
                val payload = "{\"name\":\"${collectionName.replace("\"", "\\\\\"")}\"}"
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: ""
                android.util.Log.d("CreateCollection", "HTTP $code body=$body")
                code in 200..299
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("CreateCollection", "Exception: ${e.message}", e)
            false
        }
    }
}

suspend private fun fetchCollectionItems(activity: ComposeMainActivity, collectionId: String): List<ItemData> {
    return withContext(Dispatchers.IO) {
        try {
            val token = activity.getAccessToken()
            if (token.isNullOrEmpty()) {
                android.util.Log.w("FetchCollectionItems", "No token available")
                return@withContext emptyList()
            }
            val url = URL(activity.getApiBaseUrl().trimEnd('/') + "/items?collection_id=$collectionId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 10000
                readTimeout = 15000
            }
            try {
                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    android.util.Log.d("FetchCollectionItems", "Response body: $body")
                    val arr = try { Json.parseToJsonElement(body).jsonArray } catch (_: Exception) { null }
                    val items = arr?.mapNotNull { element ->
                        val obj = element.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
                        val description = obj["description"]?.jsonPrimitive?.contentOrNull
                        val imageUrl = obj["image_url"]?.jsonPrimitive?.contentOrNull
                        val audioUrl = obj["audio_url"]?.jsonPrimitive?.contentOrNull
                        val status = obj["status"]?.jsonPrimitive?.contentOrNull
                        val collectionId = obj["collection_id"]?.jsonPrimitive?.contentOrNull
                        ItemData(id, title, description, imageUrl, audioUrl, status, collectionId)
                    } ?: emptyList()
                    android.util.Log.d("FetchCollectionItems", "Loaded ${items.size} items for collection $collectionId")
                    items
                } else {
                    android.util.Log.e("FetchCollectionItems", "HTTP $code")
                    emptyList()
                }
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("FetchCollectionItems", "Exception: ${e.message}", e)
            emptyList()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CollectionGalleryScreen(
    collectionId: String,
    onBack: () -> Unit,
    onItemClick: (String) -> Unit,
    onAddItem: () -> Unit
) {
    val activity = LocalContext.current as ComposeMainActivity
    var items by remember { mutableStateOf<List<ItemData>>(emptyList()) }
    var collectionName by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isEditingName by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(collectionId) {
        isLoading = true
        // Fetch collection details
        withContext(Dispatchers.IO) {
            try {
                val token = activity.getAccessToken()
                if (!token.isNullOrEmpty()) {
                    val url = URL(activity.getApiBaseUrl().trimEnd('/') + "/collections/$collectionId")
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        setRequestProperty("Authorization", "Bearer $token")
                        setRequestProperty("Accept", "application/json")
                        connectTimeout = 10000
                        readTimeout = 15000
                    }
                    try {
                        val code = conn.responseCode
                        if (code in 200..299) {
                            val body = conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                            val obj = try { Json.parseToJsonElement(body).jsonObject } catch (_: Exception) { null }
                            collectionName = obj?.get("name")?.jsonPrimitive?.contentOrNull
                        }
                    } finally {
                        conn.disconnect()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CollectionGallery", "Failed to fetch collection: ${e.message}", e)
            }
        }
        // Fetch items
        items = fetchCollectionItems(activity, collectionId)
        isLoading = false
    }

    val pullRefreshState = rememberPullToRefreshState()

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            isLoading = true
            items = fetchCollectionItems(activity, collectionId)
            isLoading = false
            pullRefreshState.endRefresh()
        }
    }

    BackHandler { onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(pullRefreshState.nestedScrollConnection)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header with back button
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.TextButton(onClick = onBack) {
                    Text("< Back")
                }
                if (isEditingName) {
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge
                    )
                    // Green check to save
                    IconButton(onClick = {
                        // Save the edited name
                        if (editedName.isNotBlank()) {
                            activity.lifecycleScope.launch {
                                val token = activity.getAccessToken()
                                if (!token.isNullOrEmpty()) {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            val url = URL(activity.getApiBaseUrl().trimEnd('/') + "/collections/$collectionId")
                                            val conn = (url.openConnection() as HttpURLConnection).apply {
                                                requestMethod = "PUT"
                                                setRequestProperty("Authorization", "Bearer $token")
                                                setRequestProperty("Content-Type", "application/json")
                                                setRequestProperty("Accept", "application/json")
                                                doOutput = true
                                            }
                                            try {
                                                val payload = "{\"name\":\"${editedName.replace("\"", "\\\\\"")}\"}"
                                                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                                                val code = conn.responseCode
                                                if (code in 200..299) {
                                                    withContext(Dispatchers.Main) {
                                                        collectionName = editedName
                                                        isEditingName = false
                                                        Toast.makeText(activity, "Collection name updated", Toast.LENGTH_SHORT).show()
                                                    }
                                                } else {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(activity, "Failed to update name", Toast.LENGTH_SHORT).show()
                                                    }
                                                }
                                            } finally {
                                                conn.disconnect()
                                            }
                                        } catch (e: Exception) {
                                            android.util.Log.e("CollectionGallery", "Failed to update name: ${e.message}", e)
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(activity, "Error updating name", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Save",
                            tint = Color.Green
                        )
                    }
                    // Red cross to cancel
                    IconButton(onClick = {
                        isEditingName = false
                        editedName = collectionName ?: ""
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Cancel",
                            tint = Color.Red
                        )
                    }
                } else {
                    Text(
                        text = collectionName ?: "Collection",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        editedName = collectionName ?: ""
                        isEditingName = true
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit name"
                        )
                    }
                }
            }

            // Share and Add Item buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(onClick = onAddItem) {
                    Text("Add Item")
                }
                // Show "Delete Collection" if empty, otherwise "Share Collection"
                if (items.isEmpty() && !isLoading) {
                    Button(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.Red,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Delete Collection")
                    }
                } else {
                    Button(onClick = {
                        activity.lifecycleScope.launch {
                            val token = activity.getAccessToken()
                            if (token.isNullOrEmpty()) {
                                Toast.makeText(activity, "Not authenticated", Toast.LENGTH_SHORT).show()
                                return@launch
                            }
                            withContext(Dispatchers.IO) {
                                try {
                                    val url = URL(activity.getApiBaseUrl().trimEnd('/') + "/collections/$collectionId/publish")
                                    val conn = (url.openConnection() as HttpURLConnection).apply {
                                        requestMethod = "POST"
                                        setRequestProperty("Authorization", "Bearer $token")
                                        setRequestProperty("Accept", "application/json")
                                        doOutput = true
                                        connectTimeout = 10000
                                        readTimeout = 15000
                                    }
                                    try {
                                        val code = conn.responseCode
                                        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                                            ?.bufferedReader()?.use { it.readText() } ?: ""
                                        android.util.Log.d("PublishCollection", "HTTP $code body=$body")
                                        if (code in 200..299) {
                                            val obj = try { Json.parseToJsonElement(body).jsonObject } catch (_: Exception) { null }
                                            val shareUrl = obj?.get("url")?.jsonPrimitive?.contentOrNull
                                            withContext(Dispatchers.Main) {
                                                if (!shareUrl.isNullOrBlank()) {
                                                    // Open Android share sheet with friendly message
                                                    val shareText = "View my LegacyRetriever.app collection at:\n\n$shareUrl"
                                                    val shareIntent = android.content.Intent().apply {
                                                        action = android.content.Intent.ACTION_SEND
                                                        putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                                                        type = "text/plain"
                                                    }
                                                    val chooser = android.content.Intent.createChooser(shareIntent, "Share Collection")
                                                    activity.startActivity(chooser)
                                                } else {
                                                    Toast.makeText(activity, "Failed to get link", Toast.LENGTH_LONG).show()
                                                }
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(activity, "Failed to publish ($code)", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("PublishCollection", "Exception: ${e.message}", e)
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(activity, "Error publishing link", Toast.LENGTH_LONG).show()
                                        }
                                    } finally {
                                        conn.disconnect()
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("PublishCollection", "Exception: ${e.message}", e)
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(activity, "Error publishing link", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        }
                    }) {
                        Text("Share Collection")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Gallery grid with 2 columns
            if (isLoading && items.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (items.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(items) { item ->
                        ItemThumbnail(item = item, onClick = { onItemClick(item.id) })
                    }
                }
            } else {
                Text(
                    text = "No items in this collection yet",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        }
        PullToRefreshContainer(
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
    
    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Collection?") },
            text = { 
                Text("Are you sure you want to delete \"${collectionName ?: "this collection"}\"? This action cannot be undone.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        showDeleteDialog = false
                        activity.lifecycleScope.launch {
                            val success = deleteCollection(activity, collectionId)
                            if (success) {
                                Toast.makeText(activity, "Collection deleted", Toast.LENGTH_SHORT).show()
                                onBack() // Navigate back after deletion
                            } else {
                                Toast.makeText(activity, "Failed to delete collection", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ItemsGrid(items: List<ItemData>, onItemClick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(600.dp)
    ) {
        items(items) { item ->
            ItemThumbnail(item = item, onClick = { onItemClick(item.id) })
        }
    }
}

@Composable
private fun CollectionsList(
    collections: List<CollectionData>, 
    onCollectionClick: (String) -> Unit,
    onDelete: () -> Unit = {}
) {
    val activity = LocalContext.current as ComposeMainActivity
    val scope = rememberCoroutineScope()
    var collectionToDelete by remember { mutableStateOf<CollectionData?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        collections.forEach { collection ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clickable { onCollectionClick(collection.id) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Thumbnail
                    Card(
                        modifier = Modifier
                            .size(60.dp)
                    ) {
                        if (collection.thumbnailUrl != null) {
                            val activity = LocalContext.current as ComposeMainActivity
                            // Extract filename from thumbnail URL to use as cache key
                            // This ensures that when an item moves to a different collection, 
                            // the old thumbnail is not cached incorrectly
                            val filename = collection.thumbnailUrl?.let { url ->
                                // Extract the filename from the path (before query parameters)
                                url.substringAfterLast("/").substringBefore("?").substringBeforeLast(".")
                            } ?: collection.id
                            val cacheKey = "collection_thumb_${filename}"
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(collection.thumbnailUrl)
                                    .crossfade(true)
                                    .memoryCacheKey(cacheKey) // Use filename as cache key
                                    .diskCacheKey(cacheKey) // Use filename for disk cache
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .listener(
                                        onError = { _, result ->
                                            android.util.Log.e("CollectionThumbnail", "Failed to load thumbnail: ${collection.thumbnailUrl}, error: ${result.throwable.message}")
                                        },
                                        onSuccess = { _, result ->
                                            android.util.Log.d("CollectionThumbnail", "Successfully loaded thumbnail from ${result.dataSource}: ${collection.thumbnailUrl}")
                                        }
                                    )
                                    .build(),
                                imageLoader = activity.imageLoader,
                                contentDescription = "${collection.name} thumbnail",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = collection.name.take(1).uppercase(),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = collection.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "[${collection.numberItems}]",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    
    // Confirmation dialog
    if (collectionToDelete != null) {
        AlertDialog(
            onDismissRequest = { collectionToDelete = null },
            title = { Text("Delete Collection?") },
            text = { 
                Text("Are you sure you want to delete \"${collectionToDelete!!.name}\"? This action cannot be undone.")
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        val collection = collectionToDelete!!
                        collectionToDelete = null
                        isDeleting = true
                        scope.launch {
                            val success = deleteCollection(activity, collection.id)
                            isDeleting = false
                            if (success) {
                                Toast.makeText(activity, "Collection deleted", Toast.LENGTH_SHORT).show()
                                onDelete() // Trigger refresh
                            } else {
                                Toast.makeText(activity, "Failed to delete collection", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isDeleting
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    } else {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { collectionToDelete = null },
                    enabled = !isDeleting
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ItemThumbnail(item: ItemData, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        val cardModifier = if (item.status == "WAITING TRANSCRIPTION") {
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clickable { onClick() }
        }
        Card(
            modifier = cardModifier
        ) {
            if (item.imageUrl != null && item.imageUrl.isNotBlank()) {
                val activity = LocalContext.current as ComposeMainActivity
                // Use item ID as cache key since signed URLs change with each request
                val cacheKey = "item_${item.id}"
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.imageUrl)
                        .crossfade(true)
                        .memoryCacheKey(cacheKey) // Use stable item ID as cache key
                        .diskCacheKey(cacheKey) // Use stable item ID for disk cache
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .listener(
                            onError = { _, result ->
                                android.util.Log.e("ItemThumbnail", "Failed to load image: ${item.imageUrl}, error: ${result.throwable.message}")
                            },
                            onSuccess = { _, result ->
                                android.util.Log.d("ItemThumbnail", "Successfully loaded image from ${result.dataSource}: ${item.imageUrl}")
                            }
                        )
                        .build(),
                    imageLoader = activity.imageLoader,
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = androidx.compose.ui.graphics.painter.ColorPainter(Color.LightGray)
                )
            } else {
                // Placeholder for items without images
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "No Image", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun AudioWaveformPlayer(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    
    // Update playback state
    LaunchedEffect(exoPlayer) {
        while (true) {
            isPlaying = exoPlayer.isPlaying
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(1L)
            kotlinx.coroutines.delay(50) // Update every 50ms for smooth animation
        }
    }
    
    // Generate waveform bars (simulated peaks)
    val waveformBars = remember {
        List(60) { i ->
            // Create varied heights for visual interest
            (0.3f + (kotlin.math.sin(i * 0.5).toFloat() * 0.4f + 0.4f) * kotlin.random.Random.nextFloat()).coerceIn(0.2f, 1f)
        }
    }
    
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Waveform visualization
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable {
                        // Seek on tap
                        // Calculate position based on tap
                    },
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                
                waveformBars.forEachIndexed { index, height ->
                    val barProgress = index.toFloat() / waveformBars.size
                    val isPassed = barProgress <= progress
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(height)
                            .background(
                                color = if (isPassed) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                },
                                shape = RoundedCornerShape(2.dp)
                            )
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time display
                Text(
                    text = formatTime(currentPosition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Play/Pause button
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    }
                ) {
                    if (isPlaying) {
                        // Pause icon (two vertical bars)
                        Canvas(modifier = Modifier.size(32.dp)) {
                            val barWidth = size.width * 0.25f
                            val barHeight = size.height * 0.7f
                            val spacing = size.width * 0.2f
                            val topOffset = (size.height - barHeight) / 2
                            
                            drawRect(
                                color = androidx.compose.ui.graphics.Color(0xFF6200EE),
                                topLeft = androidx.compose.ui.geometry.Offset(spacing, topOffset),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                            )
                            drawRect(
                                color = androidx.compose.ui.graphics.Color(0xFF6200EE),
                                topLeft = androidx.compose.ui.geometry.Offset(size.width - spacing - barWidth, topOffset),
                                size = androidx.compose.ui.geometry.Size(barWidth, barHeight)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                // Duration display
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun formatTime(millis: Long): String {
    val seconds = (millis / 1000) % 60
    val minutes = (millis / 1000) / 60
    return String.format("%d:%02d", minutes, seconds)
}

suspend private fun postItemMultipart(
    resolver: ContentResolver,
    apiBase: String,
    token: String,
    title: String,
    imageUri: Uri?,
    audioUri: Uri?,
    collectionId: String
): PostItemResult {
    return withContext(Dispatchers.IO) {
        // Use an RFC-compliant boundary (token chars only: no spaces or special chars like { } $)
        val boundary = "----ShepherdBoundary" + System.currentTimeMillis()
        val lineEnd = "\r\n"
        val twoHyphens = "--"
        val url = URL(apiBase.trimEnd('/') + "/items")
        var conn: HttpURLConnection? = null
        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                doOutput = true
                connectTimeout = 15000
                readTimeout = 30000
            }
            conn.outputStream.use { os ->
                fun writeString(s: String) = os.write(s.toByteArray(Charsets.UTF_8))
                // title field
                writeString(twoHyphens + boundary + lineEnd)
                writeString("Content-Disposition: form-data; name=\"title\"" + lineEnd + lineEnd)
                writeString(title + lineEnd)
                // collection_id field
                writeString(twoHyphens + boundary + lineEnd)
                writeString("Content-Disposition: form-data; name=\"collection_id\"" + lineEnd + lineEnd)
                writeString(collectionId + lineEnd)

                // optional image
                imageUri?.let { uri ->
                    val name = "image.jpg"
                    val mime = "image/jpeg"
                    // Resize before upload
                    val resized = resizeImageToMax(resolver, uri, 2400)
                    writeString(twoHyphens + boundary + lineEnd)
                    writeString("Content-Disposition: form-data; name=\"image\"; filename=\"$name\"" + lineEnd)
                    writeString("Content-Type: $mime$lineEnd$lineEnd")
                    os.write(resized)
                    writeString(lineEnd)
                }

                // optional audio
                audioUri?.let { uri ->
                    val name = "recording.m4a"
                    val mime = resolver.getType(uri) ?: "audio/mp4"
                    writeString(twoHyphens + boundary + lineEnd)
                    writeString("Content-Disposition: form-data; name=\"audio\"; filename=\"$name\"" + lineEnd)
                    writeString("Content-Type: $mime$lineEnd$lineEnd")
                    resolver.openInputStream(uri)?.use { it.copyTo(os) }
                    writeString(lineEnd)
                }

                // end boundary
                writeString(twoHyphens + boundary + twoHyphens + lineEnd)
            }
            val code = conn.responseCode
            if (code in 200..299) PostItemResult(true, null) else {
                val body = (conn.errorStream ?: conn.inputStream)?.bufferedReader()?.use { it.readText() } ?: ""
                android.util.Log.e("PostItem", "HTTP $code body=$body")
                PostItemResult(false, "HTTP $code: $body")
            }
        } catch (e: Exception) {
            android.util.Log.e("PostItem", "Exception during upload", e)
            PostItemResult(false, e.message ?: "Exception during upload")
        } finally {
            conn?.disconnect()
        }
    }
}
