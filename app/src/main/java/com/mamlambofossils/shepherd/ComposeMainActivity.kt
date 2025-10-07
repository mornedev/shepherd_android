package com.mamlambofossils.shepherd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

class ComposeMainActivity : ComponentActivity() {

    private val supabase by lazy {
        createSupabaseClient(
            supabaseUrl = "https://umvlwoplsdunsvhdqzta.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVtdmx3b3Bsc2R1bnN2aGRxenRhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTg4NzExOTIsImV4cCI6MjA3NDQ0NzE5Mn0.yrJSp1UPBDyNBvumFJddB3DHG8P1oj_CsNaX-lk6wu4"
        ) {
            install(Auth) {
                host = "auth"
                scheme = "legacyshepherd"
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
            }
        }
    }

    fun getApiBaseUrl(): String = BuildConfig.API_BASE_URL

    suspend fun getAccessToken(): String? = supabase.auth.currentSessionOrNull()?.accessToken

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
            LaunchedEffect(Unit) {
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
        android.util.Log.d("ActivityLifecycle", "onDestroy called")
    }
}

// Decode and resize an image from a Uri so the longest side is <= maxSizePx.
// Returns a JPEG-compressed byte array.
private fun resizeImageToMax(resolver: ContentResolver, uri: Uri, maxSizePx: Int): ByteArray {
    // 1) Bounds decode to get dimensions
    val optsBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, optsBounds) }
    val srcW = optsBounds.outWidth
    val srcH = optsBounds.outHeight
    if (srcW <= 0 || srcH <= 0) {
        // Fallback: just stream through
        return resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    }

    // 2) Compute inSampleSize to get close to target with less memory
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

    // 3) Scale precisely to max dimension if still larger than max
    val w = decoded.width
    val h = decoded.height
    val scale = if (w >= h) maxSizePx.toFloat() / w.toFloat() else maxSizePx.toFloat() / h.toFloat()
    val finalBitmap = if (maxOf(w, h) > maxSizePx) {
        val newW = (w * scale).toInt().coerceAtLeast(1)
        val newH = (h * scale).toInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(decoded, newW, newH, true)
    } else {
        decoded
    }

    // 4) Compress to JPEG
    val out = ByteArrayOutputStream()
    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
    if (finalBitmap !== decoded) decoded.recycle()
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
                        return@withContext ItemData(id, title, description, imageUrl, audioUrl, status)
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
    deleteAudio: Boolean
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

    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        newImageUri = uri
    }

    LaunchedEffect(itemId) {
        isLoading = true
        val details = fetchItemDetails(activity, itemId)
        if (details != null) {
            title = details.title
            description = details.description ?: ""
            currentImageUrl = details.imageUrl
            currentAudioUrl = details.audioUrl
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Edit Item", style = MaterialTheme.typography.headlineMedium)

        if (isLoading) {
            Text("Loading...")
            return@Column
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier.padding(top = 16.dp)
        )
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Description") },
            modifier = Modifier.padding(top = 16.dp)
        )

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
                ) {
                    val modelData: Any? = newImageUri ?: currentImageUrl
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(modelData)
                            .crossfade(true)
                            .build(),
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
                        deleteAudio = deleteAudio
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
                containerColor = MaterialTheme.colorScheme.secondary
            )
        ) { Text("Cancel") }

        Button(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.padding(top = 16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) { Text("Delete Item") }
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
    MaterialTheme {
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
                        onItemClick = { itemId -> navController.navigate("item/edit/$itemId") }
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
                        collectionIdProvider = collectionIdProvider
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
            }
        }
    }
}

@Composable
private fun LoginScreen(onSignIn: () -> Unit, isAuthenticating: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isAuthenticating) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Signing in...")
        } else {
            Text(text = "Sign in to continue")
            Button(onClick = onSignIn, modifier = Modifier.padding(top = 16.dp)) {
                Text("Sign in with Google")
            }
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
    onItemClick: (String) -> Unit
) {
    val activity = LocalContext.current as ComposeMainActivity
    var items by remember { mutableStateOf<List<ItemData>>(emptyList()) }
    var collections by remember { mutableStateOf<List<CollectionData>>(emptyList()) }
    var isLoadingItems by remember { mutableStateOf(true) }
    var isLoadingCollections by remember { mutableStateOf(true) }
    var selectedTabIndex by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoadingItems = true
        items = fetchLatestItems(activity)
        isLoadingItems = false
    }

    LaunchedEffect(Unit) {
        isLoadingCollections = true
        collections = fetchCollections(activity)
        isLoadingCollections = false
    }

    val pullRefreshState = rememberPullToRefreshState()

    if (pullRefreshState.isRefreshing) {
        LaunchedEffect(true) {
            if (selectedTabIndex == 0) {
                isLoadingItems = true
                items = fetchLatestItems(activity)
                isLoadingItems = false
            } else {
                isLoadingCollections = true
                collections = fetchCollections(activity)
                isLoadingCollections = false
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
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Welcome, $firstName",
                        style = MaterialTheme.typography.titleLarge
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onlyOneCollection) {
                        Button(onClick = {
                            scope.launch {
                                val token = activity.getAccessToken()
                                val cid = collectionIdProvider()
                                if (token.isNullOrEmpty() || cid.isNullOrEmpty()) {
                                    Toast.makeText(activity, "No collection to share", Toast.LENGTH_SHORT).show()
                                    return@launch
                                }
                                withContext(Dispatchers.IO) {
                                    val url = URL(activity.getApiBaseUrl().trimEnd('/') + "/collections/" + cid + "/publish")
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
                                                    val clipboard = activity.getSystemService(ClipboardManager::class.java)
                                                    clipboard?.setPrimaryClip(ClipData.newPlainText("Collection Link", shareUrl))
                                                    Toast.makeText(activity, "Share link copied", Toast.LENGTH_SHORT).show()
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
                                }
                            }
                        }) { Text("Share") }
                    }
                    Button(onClick = onAddItem) { Text("Add Item") }
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
                    text = { Text("Recent Items") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Collections") }
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
                        Text(
                            text = "You have not loaded any items yet",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 24.dp)
                        )
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
                        CollectionsList(collections = collections)
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
}

@Composable
private fun ItemFormScreen(
    onSave: (String, Uri?) -> Unit,
    onCancel: () -> Unit,
    collectionIdProvider: () -> String?
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // In-app CameraX overlay
        if (showCamera) {
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            val context = androidx.compose.ui.platform.LocalContext.current
            val mainExecutor = androidx.core.content.ContextCompat.getMainExecutor(context)
            androidx.compose.ui.viewinterop.AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                            val imgCapture = androidx.camera.core.ImageCapture.Builder()
                                .setCaptureMode(androidx.camera.core.ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { showCamera = false }) { Text("Cancel") }
                Button(onClick = {
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
                }) { Text("Capture") }
            }
        }

        if (!showCamera) {
            Text(text = "Load new item", style = MaterialTheme.typography.headlineMedium)
            // Title field removed. Title defaults to "processing" and is not user-editable.
            Row(
                modifier = Modifier.padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = {
                    val hasCam = androidx.core.content.ContextCompat.checkSelfPermission(
                        activity, android.Manifest.permission.CAMERA
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (hasCam) {
                        showCamera = true
                    } else {
                        requestCameraPermission.launch(android.Manifest.permission.CAMERA)
                    }
                }) {
                    Text("Take Photo")
                }
                Button(onClick = { pickImageLauncher.launch("image/*") }) {
                    Text(if (imageUri != null) "Change Photo" else "Pick Photo")
                }
            }
        }
        
        // Image preview
        if (!showCamera && imageUri != null) {
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
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Selected photo preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
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
        
        // Audio recording section (in-app MediaRecorder)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
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
            if (audioUri != null) {
                Text(text = if (isRecording) "Recording..." else "Audio attached", style = MaterialTheme.typography.bodySmall)
                IconButton(onClick = { audioUri = null }) {
                    Icon(imageVector = Icons.Filled.Close, contentDescription = "Remove audio")
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
        
        Button(
            onClick = {
                activity.lifecycleScope.launch {
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
                    val collectionId = collectionIdProvider()
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
                }
            },
            modifier = Modifier.padding(top = 24.dp)
        ) { Text("Save Item") }
        Button(
            onClick = onCancel,
            modifier = Modifier.padding(top = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error
            )
        ) { Text("Cancel") }
    }
}
data class PostItemResult(val success: Boolean, val errorMessage: String?)

data class ItemData(
    val id: String,
    val title: String,
    val description: String?,
    val imageUrl: String?,
    val audioUrl: String?,
    val status: String?
)

data class CollectionData(
    val id: String,
    val name: String
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
                    val items = arr?.take(6)?.mapNotNull { element ->
                        val obj = element.jsonObject
                        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                        val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: "Untitled"
                        val description = obj["description"]?.jsonPrimitive?.contentOrNull
                        val imageUrl = obj["image_url"]?.jsonPrimitive?.contentOrNull
                        val audioUrl = obj["audio_url"]?.jsonPrimitive?.contentOrNull
                        val status = obj["status"]?.jsonPrimitive?.contentOrNull
                        android.util.Log.d("FetchItems", "Item: id=$id, title=$title, status=$status, imageUrl=$imageUrl, audioUrl=$audioUrl")
                        ItemData(id, title, description, imageUrl, audioUrl, status)
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
                        CollectionData(id, name)
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

@Composable
private fun ItemsGrid(items: List<ItemData>, onItemClick: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(items) { item ->
            ItemThumbnail(item = item, onClick = { onItemClick(item.id) })
        }
    }
}

@Composable
private fun CollectionsList(collections: List<CollectionData>) {
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
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = collection.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
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
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.imageUrl)
                        .crossfade(true)
                        .listener(
                            onError = { _, result ->
                                android.util.Log.e("ItemThumbnail", "Failed to load image: ${item.imageUrl}, error: ${result.throwable.message}")
                            },
                            onSuccess = { _, _ ->
                                android.util.Log.d("ItemThumbnail", "Successfully loaded image: ${item.imageUrl}")
                            }
                        )
                        .build(),
                    contentDescription = item.title,
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
