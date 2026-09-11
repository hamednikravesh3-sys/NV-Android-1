package com.smartstorage.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smartstorage.app.data.StorageItem
import com.smartstorage.app.ui.SmartStorageApp

class MainActivity : ComponentActivity() {
    private val viewModel: StorageViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()

            val mediaPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { viewModel.scan() }

            val allFilesLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { viewModel.scan() }

            val usageLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { viewModel.scan() }

            val treeLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenDocumentTree()
            ) { uri ->
                if (uri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                    }
                    getSharedPreferences("storage", MODE_PRIVATE).edit().putString("treeUri", uri.toString()).apply()
                    viewModel.scan()
                }
            }

            val deleteLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartIntentSenderForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) viewModel.scan()
            }

            SmartStorageApp(
                state = state,
                onScan = {
                    val permissions = if (Build.VERSION.SDK_INT >= 33) {
                        buildList {
                            add(Manifest.permission.READ_MEDIA_IMAGES)
                            add(Manifest.permission.READ_MEDIA_VIDEO)
                            add(Manifest.permission.READ_MEDIA_AUDIO)
                            if (Build.VERSION.SDK_INT >= 34) add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                        }.toTypedArray()
                    } else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    mediaPermissionLauncher.launch(permissions)
                },
                onRequestAllFiles = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    allFilesLauncher.launch(intent)
                },
                onRequestUsage = {
                    usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                },
                onChooseFolder = { treeLauncher.launch(null) },
                onToggle = viewModel::toggle,
                onSelectRecommended = viewModel::selectRecommended,
                onClearSelection = viewModel::clearSelection,
                onQuery = viewModel::setQuery,
                onOpenAppStorage = { pkg ->
                    runCatching {
                        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))
                    }
                },
                onDelete = { items -> requestDelete(items, deleteLauncher) }
            )
        }
    }

    private fun requestDelete(
        items: List<StorageItem>,
        launcher: androidx.activity.result.ActivityResultLauncher<IntentSenderRequest>
    ) {
        if (items.isEmpty()) return

        val mediaUris = items.filter { it.uri.scheme == "content" && it.uri.authority == MediaStore.AUTHORITY }
            .map { it.uri }
        val nonMedia = items.filterNot { it.uri in mediaUris }

        nonMedia.forEach { item ->
            runCatching {
                when (item.uri.scheme) {
                    "file" -> item.directPath?.let { java.io.File(it).delete() }
                    "content" -> DocumentsContract.deleteDocument(contentResolver, item.uri)
                }
            }
        }

        if (mediaUris.isNotEmpty()) {
            val request = MediaStore.createDeleteRequest(contentResolver, mediaUris)
            launcher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            viewModel.scan()
        }
    }
}
