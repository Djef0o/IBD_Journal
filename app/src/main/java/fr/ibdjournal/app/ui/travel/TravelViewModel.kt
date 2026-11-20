package fr.ibdjournal.app.ui.travel

import android.net.Uri
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.ibdjournal.app.data.CareRepository
import fr.ibdjournal.app.data.ProfileRepository
import fr.ibdjournal.app.domain.DEFAULT_TRAVEL_MARGIN_DAYS
import fr.ibdjournal.app.domain.TravelLine
import fr.ibdjournal.app.domain.today
import fr.ibdjournal.app.domain.travelPlan
import fr.ibdjournal.app.report.TravelLanguage
import fr.ibdjournal.app.report.TravelPackData
import fr.ibdjournal.app.report.TravelPackGenerator
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** What the user chose for the trip. */
data class Trip(
    val departure: LocalDate,
    val returnDate: LocalDate,
    val marginDays: Int,
    val destination: String,
    val language: TravelLanguage,
)

/** Longest safety margin offered, in days. */
const val MAX_TRAVEL_MARGIN_DAYS = 30

@HiltViewModel
class TravelViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val careRepository: CareRepository,
    private val generator: TravelPackGenerator,
) : ViewModel() {

    private val _trip = MutableStateFlow(
        Trip(
            departure = today().plus(7, DateTimeUnit.DAY),
            returnDate = today().plus(14, DateTimeUnit.DAY),
            marginDays = DEFAULT_TRAVEL_MARGIN_DAYS,
            destination = "",
            language = defaultLanguage(),
        ),
    )
    val trip: StateFlow<Trip> = _trip.asStateFlow()

    /** Intakes to carry for the chosen dates, by treatment. */
    val plan: StateFlow<List<TravelLine>> = combine(profileRepository.medications, _trip) { medications, trip ->
        travelPlan(medications, trip.departure, trip.returnDate, trip.marginDays)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDeparture(date: LocalDate) {
        _trip.value = _trip.value.let { it.copy(departure = date, returnDate = maxOf(it.returnDate, date)) }
    }

    fun setReturn(date: LocalDate) {
        _trip.value = _trip.value.let { it.copy(returnDate = date, departure = minOf(it.departure, date)) }
    }

    fun setMargin(days: Int) {
        _trip.value = _trip.value.copy(marginDays = days.coerceIn(0, MAX_TRAVEL_MARGIN_DAYS))
    }

    fun setDestination(text: String) {
        _trip.value = _trip.value.copy(destination = text.take(MAX_DESTINATION_LENGTH))
    }

    fun setLanguage(language: TravelLanguage) {
        _trip.value = _trip.value.copy(language = language)
    }

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    private val results = Channel<Result<Uri>>(Channel.BUFFERED)

    /** PDFs ready to share, or failures, consumed only once. */
    val pdfs: Flow<Result<Uri>> = results.receiveAsFlow()

    fun create() {
        if (_creating.value) return
        _creating.value = true
        viewModelScope.launch {
            val result = try {
                val trip = _trip.value
                Result.success(
                    generator.generate(
                        TravelPackData(
                            profile = profileRepository.profile.first(),
                            emergency = careRepository.emergencyInfo.first(),
                            lines = plan.value,
                            departure = trip.departure,
                            returnDate = trip.returnDate,
                            marginDays = trip.marginDays,
                            destination = trip.destination,
                            language = trip.language,
                        ),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                _creating.value = false
            }
            results.send(result)
        }
    }

    /** The app's language when the pack is offered in it, English otherwise. */
    private fun defaultLanguage(): TravelLanguage {
        val locales = AppCompatDelegate.getApplicationLocales()
        val tag = if (locales.isEmpty) Locale.getDefault().language else locales[0]?.language
        return TravelLanguage.entries.firstOrNull { it.tag == tag } ?: TravelLanguage.ENGLISH
    }

    private companion object {
        const val MAX_DESTINATION_LENGTH = 60
    }
}
