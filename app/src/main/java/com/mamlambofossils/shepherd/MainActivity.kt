package com.mamlambofossils.shepherd

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import com.mamlambofossils.shepherd.databinding.ActivityMainBinding
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.ExternalAuthAction
import io.github.jan.supabase.gotrue.handleDeeplinks
import io.github.jan.supabase.gotrue.providers.Google
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import io.github.jan.supabase.gotrue.SessionStatus
import kotlinx.coroutines.flow.collect

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private var hasNavigatedAfterLogin = false

    // Supabase client configured for Android OAuth deep links
    private val supabase by lazy {
        createSupabaseClient(
            supabaseUrl = "https://umvlwoplsdunsvhdqzta.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InVtdmx3b3Bsc2R1bnN2aGRxenRhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTg4NzExOTIsImV4cCI6MjA3NDQ0NzE5Mn0.yrJSp1UPBDyNBvumFJddB3DHG8P1oj_CsNaX-lk6wu4"
        ) {
            install(Auth) {
                // Must match the intent-filter in AndroidManifest.xml
                host = "auth"
                scheme = "legacyshepherd"
                defaultExternalAuthAction = ExternalAuthAction.CustomTabs()
            }
        }
    }

    // API base URL from BuildConfig (configured via Gradle property or environment)
    fun getApiBaseUrl(): String = BuildConfig.API_BASE_URL

    // Retrieve the current access token for Authorization: Bearer
    suspend fun getAccessToken(): String? {
        // Prefer current session's access token
        val session = supabase.auth.currentSessionOrNull()
        return session?.accessToken
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Handle OAuth/OTP deep links from Supabase
        supabase.handleDeeplinks(intent)
        // After handling deep link, try navigate to Welcome if logged in
        navigateToWelcomeIfLoggedIn()

        // Observe session changes to navigate as soon as we're authenticated
        lifecycleScope.launch {
            supabase.auth.sessionStatus.collect { status ->
                if (status is SessionStatus.Authenticated) {
                    if (!hasNavigatedAfterLogin) {
                        hasNavigatedAfterLogin = true
                        navigateToWelcomeIfLoggedIn()
                    }
                    // hide FAB when logged in
                    binding.fab.hide()
                } else {
                    // reset flag and show FAB when not authenticated
                    hasNavigatedAfterLogin = false
                    binding.fab.show()
                }
            }
        }

        // Click to sign in with Google via Supabase
        binding.fab.setOnClickListener { view ->
            // Launch Google OAuth. On success, a deep link will be delivered to this Activity.
            lifecycleScope.launch {
                supabase.auth.signInWith(io.github.jan.supabase.gotrue.providers.Google)
            }
            Snackbar.make(view, "Launching Google sign-in...", Snackbar.LENGTH_SHORT)
                .setAnchorView(R.id.fab).show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_settings -> true
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        supabase.handleDeeplinks(intent)
        // After new intent handled (OAuth callback), try navigate
        navigateToWelcomeIfLoggedIn()
    }

    override fun onResume() {
        super.onResume()
        // When coming back from Custom Tabs, ensure we navigate if already authenticated
        navigateToWelcomeIfLoggedIn()
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    fun startGoogleSignIn() {
        lifecycleScope.launch {
            supabase.auth.signInWith(io.github.jan.supabase.gotrue.providers.Google)
        }
    }

    private fun navigateToWelcomeIfLoggedIn() {
        lifecycleScope.launch {
            val user = supabase.auth.currentUserOrNull()
            if (user != null) {
                val meta = user.userMetadata
                val fullName = meta?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: meta?.get("full_name")?.jsonPrimitive?.contentOrNull
                    ?: meta?.get("given_name")?.jsonPrimitive?.contentOrNull
                val firstName = (fullName?.substringBefore(" ") ?: "there")

                val navController = findNavController(R.id.nav_host_fragment_content_main)
                // Avoid re-navigating if we're already on the destination
                if (navController.currentDestination?.id != R.id.SecondFragment) {
                    val args = android.os.Bundle().apply { putString("firstName", firstName) }
                    navController.navigate(R.id.SecondFragment, args)
                }
                // Hide the sign-in FAB once logged in
                binding.fab.hide()
            }
        }
    }
}