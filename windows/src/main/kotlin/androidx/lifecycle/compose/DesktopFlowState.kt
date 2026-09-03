package androidx.lifecycle.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow

/** Desktop equivalent: Compose owns the window composition lifecycle. */
@Composable
fun <T> StateFlow<T>.collectAsStateWithLifecycle(): State<T> = collectAsState()
