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

    /** Impostato dalla modalità live: riceve i tasti e dice se li ha usati. */
    var keyListener: ((Int) -> Boolean)? = null

    /** Impostato dalla schermata Impostazioni per "imparare" i tasti del pedale. */
    var learnListener: ((Int) -> Unit)? = null

    private val consumed = HashSet<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(applicationContext)
        setContent { DrumChartTheme { App() } }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)

        learnListener?.let { l ->
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) l(code)
            return true
        }
        val l = keyListener
        if (l != null) {
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount > 0) { if (code in consumed) return true }
                    else if (l(code)) { consumed += code; return true }
                }
                KeyEvent.ACTION_UP -> if (consumed.remove(code)) return true
            }
        }
        return super.dispatchKeyEvent(event)
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
    data object Settings : Screen
}

class Nav(private val stack: MutableList<Screen>) {
    fun go(s: Screen) { stack.add(s) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun replace(s: Screen) { stack[stack.lastIndex] = s }
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
        Screen.Settings -> SettingsScreen(nav)
    }
}
