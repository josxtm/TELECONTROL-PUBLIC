package io.github.josxtm.mangossh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Native, readable PTY output. No WebView is required to see text from the router.
 * Commands continue to use the secured input controls in TerminalPage.
 * Keep a bounded raw snapshot in the ViewModel and redraw from that snapshot,
 * instead of decoding arbitrary byte boundaries as separate UTF-8 strings.
 */
@Composable
fun TerminalView(
    output: Flow<ByteArray>,
    snapshot: () -> ByteArray,
    epoch: Int,
    fontSize: Int,
    enabled: Boolean,
    onInput: (ByteArray) -> Unit,
    onResize: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    fallbackText: String = "",
    onStatus: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val currentSnapshot by rememberUpdatedState(snapshot)
    val currentStatus by rememberUpdatedState(onStatus)
    var terminalText by remember { mutableStateOf("") }
    val scroll = rememberScrollState()

    LaunchedEffect(output, epoch) {
        terminalText = TerminalText.format(currentSnapshot())
        currentStatus("Terminal de texto lista · salida SSH visible")
        output.collect {
            terminalText = TerminalText.format(currentSnapshot())
        }
    }
    LaunchedEffect(terminalText) { scroll.scrollTo(scroll.maxValue) }

    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                manager.setPrimaryClip(ClipData.newPlainText("MangoSSH", terminalText))
            }) { Text("Copiar terminal") }
            Text("SSH · UTF-8 · PTY", style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 16.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF121418))) {
            SelectionContainer {
                Text(
                    terminalText.ifEmpty {
                        if (enabled) "Shell conectada · esperando respuesta del Mango…"
                        else "Modo diagnóstico · activa Administrador para abrir la terminal SSH."
                    },
                    modifier = Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp),
                    color = Color(0xFFF4F5F7),
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSize.coerceIn(12, 24).sp,
                    lineHeight = (fontSize.coerceIn(12, 24) * 1.35f).sp,
                    softWrap = true
                )
            }
        }
    }
}

/** Removes ANSI screen controls for a stable readable transcript.
 * This view is designed for diagnostic commands, not full-screen editors (vim/top).
 */
object TerminalText {
    private val ansi = Regex("\\u001B(?:\\[[0-?]*[ -/]*[@-~]|\\][^\\u0007]*(?:\\u0007|\\u001B\\\\)|\\([A-Za-z0-9])")
    fun format(bytes: ByteArray): String {
        val source = bytes.toString(Charsets.UTF_8)
        val cleaned = ansi.replace(source, "")
        val out = StringBuilder(cleaned.length)
        for (char in cleaned) {
            when (char) {
                '\r' -> { // Preserve overwritten prompts and normal CRLF without stacking characters.
                    if (out.isNotEmpty() && out.last() != '\n') out.append('\n')
                }
                '\b' -> { if (out.isNotEmpty() && out.last() != '\n') out.deleteCharAt(out.lastIndex) }
                '\u0000' -> Unit
                else -> if (char == '\n' && out.isNotEmpty() && out.last() == '\n') out.append(char)
                        else out.append(char)
            }
        }
        return out.toString().takeLast(65536)
    }
}
