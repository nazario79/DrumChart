package com.nazario.drumchart

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.Color

class MainActivity : ComponentActivity() {

    /** Impostato dalla modalità live per ricevere i tasti del pedale Bluetooth. */
    var keyListener: ((Int) -> Boolean)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(applicationContext)
        setContent { DrumChartTheme { App() } }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val l = keyListener
        if (l != null && event.keyCode in PEDAL_KEYS) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) l(event.keyCode)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        val NEXT_KEYS = setOf(
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_NEXT,
        )
        val PREV_KEYS = setOf(
            KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        )
        val PEDAL_KEYS = NEXT_KEYS + PREV_KEYS
    }
}

val Amber = Color(0xFFFFB300)

@Composable
fun DrumChartTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Amber,
            onPrimary = Color.Black,
            secondary = Color(0xFFFFE082),
            background = Color(0xFF121212),
            surface = Color(0xFF121212),
        )
    ) {
        Surface(color = MaterialTheme.colorScheme.background) { content() }
    }
}

sealed interface Screen {
    data object Home : Screen
    data class EditSong(val id: String) : Screen
    data class EditSet(val id: String) : Screen
    data class Live(val setlistId: String?, val songId: String?) : Screen
}

class Nav(private val stack: MutableList<Screen>) {
    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
}

@Composable
fun App() {
    val stack = remember { mutableStateListOf<Screen>(Screen.Home) }
    val nav = remember { Nav(stack) }
    val tab = rememberSaveable { mutableIntStateOf(0) }

    BackHandler(enabled = stack.size > 1) { nav.back() }

    when (val cur = stack.last()) {
        Screen.Home -> HomeScreen(nav, tab)
        is Screen.EditSong -> SongEditor(cur.id, nav)
        is Screen.EditSet -> SetlistEditor(cur.id, nav)
        is Screen.Live -> LiveScreen(cur.setlistId, cur.songId, nav)
    }
}
