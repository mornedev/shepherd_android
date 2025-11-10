package com.mamlambofossils.legacyhound

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mamlambofossils.legacyhound.databinding.FragmentSecondBinding
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AlertDialog
import android.widget.EditText
import android.widget.Toast
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * A simple [Fragment] subclass as the second destination in the navigation.
 */
class SecondFragment : Fragment() {

    private var _binding: FragmentSecondBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentSecondBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Show welcome message with first name if provided
        val firstName = arguments?.getString("firstName") ?: "there"
        binding.textviewSecond.text = "Welcome $firstName"

        binding.buttonCreateCollection.setOnClickListener {
            showCreateCollectionDialog()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun showCreateCollectionDialog() {
        val ctx = requireContext()
        val input = EditText(ctx).apply { hint = "Collection name" }
        AlertDialog.Builder(ctx)
            .setTitle("New Collection")
            .setView(input)
            .setPositiveButton("Create") { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    createCollection(name)
                } else {
                    Toast.makeText(ctx, "Name is required", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createCollection(name: String, description: String? = null) {
        val act = activity as? MainActivity ?: return
        lifecycleScope.launch {
            val token = act.getAccessToken()
            if (token.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Not authenticated", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val baseUrl = act.getApiBaseUrl()
            val url = URL("$baseUrl/collections")
            val payload = if (description != null)
                "{" + "\"name\":\"${name.replace("\"", "\\\"")}\",\"description\":\"${description.replace("\"", "\\\"")}\"}" else
                "{" + "\"name\":\"${name.replace("\"", "\\\"")}\"}"

            val result = withContext(Dispatchers.IO) {
                var conn: HttpURLConnection? = null
                try {
                    conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Authorization", "Bearer $token")
                        doOutput = true
                    }
                    conn.outputStream.use { os ->
                        os.write(payload.toByteArray(Charsets.UTF_8))
                    }
                    val code = conn.responseCode
                    val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                    val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
                    code to body
                } catch (e: Exception) {
                    -1 to (e.message ?: "Unknown error")
                } finally {
                    conn?.disconnect()
                }
            }

            if (result.first == 201) {
                Toast.makeText(requireContext(), "Collection created", Toast.LENGTH_SHORT).show()
            } else {
                Log.e("CreateCollection", "HTTP ${result.first} body=${result.second}")
                Toast.makeText(requireContext(), "Failed: ${result.second}", Toast.LENGTH_LONG).show()
            }
        }
    }
}