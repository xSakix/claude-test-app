package com.example.youtubetranscript

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.youtubetranscript.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TranscriptViewModel by viewModels()
    private val adapter = TranscriptAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.transcriptRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.transcriptRecyclerView.adapter = adapter

        binding.fetchButton.setOnClickListener { triggerFetch() }

        binding.urlEditText.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                triggerFetch()
                true
            } else false
        }

        binding.shareButton.setOnClickListener { shareTranscript() }
        binding.saveButton.setOnClickListener { saveTranscriptToDownloads() }

        viewModel.uiState.observe(this) { state ->
            when (state) {
                is UiState.Idle -> showIdle()
                is UiState.Loading -> showLoading()
                is UiState.Success -> showSuccess(state)
                is UiState.Error -> showError(state.message)
            }
        }
    }

    private fun triggerFetch() {
        val input = binding.urlEditText.text?.toString()?.trim() ?: return
        if (input.isBlank()) {
            binding.urlInputLayout.error = getString(R.string.error_invalid_url)
            return
        }
        binding.urlInputLayout.error = null
        hideKeyboard()
        viewModel.fetchTranscript(input)
    }

    private fun showIdle() {
        binding.progressBar.visibility = View.GONE
        binding.errorText.visibility = View.GONE
        binding.transcriptHeader.visibility = View.GONE
        binding.fetchButton.isEnabled = true
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.errorText.visibility = View.GONE
        binding.transcriptHeader.visibility = View.GONE
        binding.fetchButton.isEnabled = false
        adapter.submitList(emptyList())
    }

    private fun showSuccess(state: UiState.Success) {
        binding.progressBar.visibility = View.GONE
        binding.errorText.visibility = View.GONE
        binding.transcriptHeader.visibility = View.VISIBLE
        binding.fetchButton.isEnabled = true
        binding.videoTitleText.text = state.videoTitle ?: "Transcript"
        adapter.submitList(state.entries)
    }

    private fun showError(message: String) {
        binding.progressBar.visibility = View.GONE
        binding.errorText.visibility = View.VISIBLE
        binding.errorText.text = message
        binding.transcriptHeader.visibility = View.GONE
        binding.fetchButton.isEnabled = true
        adapter.submitList(emptyList())
    }

    private fun shareTranscript() {
        val text = viewModel.getFormattedTranscript() ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.chooser_share)))
    }

    private fun saveTranscriptToDownloads() {
        val text = viewModel.getFormattedTranscript() ?: return
        val fileName = "transcript_${System.currentTimeMillis()}.txt"

        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                File(dir, fileName).writeText(text, Charsets.UTF_8)
                Toast.makeText(this, getString(R.string.transcript_saved), Toast.LENGTH_LONG).show()
                return
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val uri = contentResolver.insert(
                MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                contentValues
            ) ?: throw IOException("MediaStore insert returned null URI")

            contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
            }

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, contentValues, null, null)

            Toast.makeText(this, getString(R.string.transcript_saved), Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
}
