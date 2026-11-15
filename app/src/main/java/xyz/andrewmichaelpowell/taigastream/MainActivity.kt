//  Taiga Stream (Android)
//  github.com/andrewmichaelpowell

package xyz.andrewmichaelpowell.taigastream

import android.Manifest
import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.media3.cast.Cast
import androidx.media3.common.util.UnstableApi
import xyz.andrewmichaelpowell.taigastream.playback.PlaybackService
import xyz.andrewmichaelpowell.taigastream.ui.MainScreen
import xyz.andrewmichaelpowell.taigastream.ui.theme.TaigaStreamTheme

class MainActivity : ComponentActivity() {
    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { Cast.getSingletonInstance(this).initialize() }
        enableEdgeToEdge()
        handleVoiceRequest(intent)

        setContent {
            TaigaStreamTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val permissionLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.RequestPermission(),
                        ) {}
                        LaunchedEffect(Unit) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                    MainScreen(modifier = Modifier.safeDrawingPadding())
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleVoiceRequest(intent)
    }

    // "Play … on Taiga Stream" from Google Assistant on the phone.
    private fun handleVoiceRequest(intent: Intent?) {
        if (intent?.action != MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH) return
        PlaybackService.playFromSearch(this, intent.getStringExtra(SearchManager.QUERY))
    }
}
