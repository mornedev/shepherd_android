package com.mamlambofossils.shepherd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.ContentResolver
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
        setContent {
            val nav = rememberNavController()
            var collectionName by remember { mutableStateOf<String?>(null) }
            var collectionId by remember { mutableStateOf<String?>(null) }
            AppNav(
                navController = nav,
                onSignIn = {
                    // Start Google sign-in
                    lifecycleScope.launch {
                        supabase.auth.signInWith(Google)
                    }
                },
                collectionName = collectionName,
                collectionIdProvider = { collectionId }
            )

            // Observe session and navigate
            LaunchedEffect(Unit) {
                supabase.auth.sessionStatus.collect { status ->
                    if (status is SessionStatus.Authenticated) {
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
                                                    } else {
                                                        withContext(Dispatchers.Main) {
                                                            Toast.makeText(this@ComposeMainActivity, "Failed to create default collection: $createCode", Toast.LENGTH_LONG).show()
                                                        }
                                                        collectionName = null
                                                        collectionId = null
                                                    }
                                                } finally {
                                                    createConn.disconnect()
                                                }
                                            }
                                        } else {
                                            collectionName = null
                                            collectionId = null
                                            withContext(Dispatchers.Main) {
                                                Toast.makeText(this@ComposeMainActivity, "Failed to load collections: $code", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    } finally {
                                        conn.disconnect()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("FetchCollections", "Exception: ${e.message}")
                            collectionName = null
                            collectionId = null
                            Toast.makeText(this@ComposeMainActivity, "Error loading collections", Toast.LENGTH_LONG).show()
                        }
                        if (nav.currentDestination?.route?.startsWith("welcome/") != true) {
                            nav.navigate("welcome/$first") {
                                popUpTo("login") { inclusive = true }
                            }
                        }
                    } else {
                        if (nav.currentDestination?.route != "login") {
                            nav.navigate("login") {
                                popUpTo(0)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        supabase.handleDeeplinks(intent)
    }
}

@Composable
private fun AppNav(
    navController: NavHostController,
    onSignIn: () -> Unit,
    collectionName: String?,
    collectionIdProvider: () -> String?
) {
    MaterialTheme {
        Scaffold { padding ->
            NavHost(navController = navController, startDestination = "login", modifier = Modifier.padding(padding)) {
                composable("login") { LoginScreen(onSignIn) }
                composable("welcome/{first}") { backStack ->
                    val first = backStack.arguments?.getString("first") ?: "there"
                    WelcomeScreen(
                        firstName = first,
                        collectionName = collectionName,
                        onAddItem = { navController.navigate("item/new") }
                    )
                }
                composable("item/new") {
                    ItemFormScreen(
                        onSave = { title, imageUri ->
                            // navigate back on save success from inside ItemFormScreen via callback
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
private fun LoginScreen(onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Sign in to continue")
        Button(onClick = onSignIn, modifier = Modifier.padding(top = 16.dp)) {
            Text("Sign in with Google")
        }
    }
}

@Composable
private fun WelcomeScreen(firstName: String, collectionName: String?, onAddItem: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Welcome $firstName", style = MaterialTheme.typography.headlineMedium)
        Text(text = "Collection: ${collectionName ?: "None"}", modifier = Modifier.padding(top = 24.dp))
        Button(onClick = onAddItem, modifier = Modifier.padding(top = 16.dp)) {
            Text("Add Item")
        }
    }
}

@Composable
private fun ItemFormScreen(
    onSave: (String, Uri?) -> Unit,
    onCancel: () -> Unit,
    collectionIdProvider: () -> String?
) {
    val activity = LocalContext.current as ComposeMainActivity
    var title by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<Uri?>(null) }
    val pickImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        imageUri = uri
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "New Item", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("Title") },
            modifier = Modifier
                .padding(top = 16.dp)
        )
        Button(
            onClick = { pickImageLauncher.launch("image/*") },
            modifier = Modifier.padding(top = 16.dp)
        ) { Text(if (imageUri != null) "Change Photo" else "Pick Photo") }
        Button(
            onClick = {
                activity.lifecycleScope.launch {
                    val token = activity.getAccessToken()
                    if (token.isNullOrEmpty()) {
                        android.util.Log.w("ItemForm", "Save blocked: Not authenticated")
                        Toast.makeText(activity, "Not authenticated", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    if (title.isBlank()) {
                        android.util.Log.w("ItemForm", "Save blocked: Title is blank")
                        Toast.makeText(activity, "Title is required", Toast.LENGTH_SHORT).show()
                        return@launch
                    }
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
        Button(onClick = onCancel, modifier = Modifier.padding(top = 8.dp)) { Text("Cancel") }
    }
}
data class PostItemResult(val success: Boolean, val errorMessage: String?)

suspend private fun postItemMultipart(
    resolver: ContentResolver,
    apiBase: String,
    token: String,
    title: String,
    imageUri: Uri?,
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
                    val mime = resolver.getType(uri) ?: "image/jpeg"
                    writeString(twoHyphens + boundary + lineEnd)
                    writeString("Content-Disposition: form-data; name=\"image\"; filename=\"$name\"" + lineEnd)
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
