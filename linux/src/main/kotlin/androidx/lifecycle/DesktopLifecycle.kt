package androidx.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Lifecycle-independent scope for a desktop screen model. */
open class ViewModel {
    internal val desktopScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

val ViewModel.viewModelScope: CoroutineScope
    get() = desktopScope
