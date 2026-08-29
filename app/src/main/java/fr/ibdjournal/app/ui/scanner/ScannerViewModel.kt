package fr.ibdjournal.app.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.DiaryRepository
import fr.ibdjournal.app.data.model.Product
import fr.ibdjournal.app.data.model.ProductLookup
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** État du scan d'un code-barres. */
sealed interface ScanState {
    data object Waiting : ScanState
    data object Scanning : ScanState
    data class Found(val product: Product) : ScanState
    data class NotFound(val barcode: String) : ScanState
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val repository: DiaryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<ScanState>(ScanState.Waiting)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private var processing = false

    /** Appelé par l'analyseur d'images à chaque code-barres détecté. */
    fun onBarcodeDetected(barcode: String) {
        if (processing) return
        processing = true
        _state.value = ScanState.Scanning
        viewModelScope.launch {
            _state.value = when (val result = repository.lookupProduct(barcode)) {
                is ProductLookup.Found -> ScanState.Found(result.product)
                is ProductLookup.NotFound -> ScanState.NotFound(barcode)
            }
            processing = false
        }
    }
}
