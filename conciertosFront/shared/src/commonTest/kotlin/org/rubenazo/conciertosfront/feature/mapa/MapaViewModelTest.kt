package org.rubenazo.conciertosfront.feature.mapa

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.rubenazo.conciertosfront.core.domain.model.Artist
import org.rubenazo.conciertosfront.core.domain.model.Concert
import org.rubenazo.conciertosfront.core.domain.model.SalaConcierto
import org.rubenazo.conciertosfront.core.location.LocationAuthorizationStatus
import org.rubenazo.conciertosfront.core.map.CameraBounds
import org.rubenazo.conciertosfront.core.map.LatLng
import org.rubenazo.conciertosfront.testutil.FakeConcertRepository
import org.rubenazo.conciertosfront.testutil.FakeDatabaseProvider
import org.rubenazo.conciertosfront.testutil.FakeLocationProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MapaViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var fakeRepo: FakeConcertRepository
    private lateinit var fakeLocation: FakeLocationProvider
    private lateinit var fakeProvider: FakeDatabaseProvider
    private lateinit var viewModel: MapaViewModel

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakeRepo = FakeConcertRepository()
        fakeLocation = FakeLocationProvider()
        fakeProvider = FakeDatabaseProvider()
        viewModel = MapaViewModel(fakeLocation, fakeRepo, fakeProvider)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // TST-MAP-01: Bounds null at init — no bounding-box query issued
    @Test
    fun initialBoundsNull_doesNotTriggerBoundingBoxQuery() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceTimeBy(50)
        // No onBoundsChanged called — pipeline should not fire
        advanceTimeBy(500)
        // boundingBoxConcerts is empty; concerts should remain empty too (pipeline inactive)
        fakeRepo.boundingBoxConcerts = listOf(sampleConcert("c1"))
        advanceTimeBy(500)
        // concerts must still be the initial empty list because no bounds were emitted
        assertEquals(emptyList(), viewModel.uiState.value.concerts)
    }

    // TST-MAP-02: onBoundsChanged triggers query after debounce of 300ms
    @Test
    fun onBoundsChanged_triggersQueryAfterDebounce() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceTimeBy(50)

        val expected = listOf(sampleConcert("c1"))
        fakeRepo.boundingBoxConcerts = expected

        val bounds = CameraBounds(latMin = 40.0, latMax = 41.0, lngMin = -4.0, lngMax = -3.0)
        viewModel.onBoundsChanged(bounds)

        // Before debounce fires — pipeline not yet triggered
        advanceTimeBy(200)
        assertEquals(emptyList(), viewModel.uiState.value.concerts)

        // After debounce fires
        advanceTimeBy(200) // total 400ms > 300ms debounce
        assertEquals(expected, viewModel.uiState.value.concerts)
    }

    // TST-MAP-03: Multiple bound changes in < 300ms produce only one query
    @Test
    fun onBoundsChanged_multipleRapidCalls_singleQueryAfterDebounce() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceTimeBy(50)

        val expected = listOf(sampleConcert("final"))
        fakeRepo.boundingBoxConcerts = expected

        val bounds1 = CameraBounds(40.0, 41.0, -4.0, -3.0)
        val bounds2 = CameraBounds(41.0, 42.0, -3.0, -2.0)
        val bounds3 = CameraBounds(42.0, 43.0, -2.0, -1.0)

        viewModel.onBoundsChanged(bounds1)
        advanceTimeBy(100)
        viewModel.onBoundsChanged(bounds2)
        advanceTimeBy(100)
        viewModel.onBoundsChanged(bounds3)
        advanceTimeBy(100)

        // Debounce not yet expired after last emission
        assertEquals(emptyList(), viewModel.uiState.value.concerts)

        // Advance past debounce window — only ONE query fires (for bounds3)
        advanceTimeBy(300)
        assertEquals(expected, viewModel.uiState.value.concerts)
    }

    // TST-MAP-04: setDateFilter with active bounds triggers new query
    @Test
    fun setDateFilter_withActiveBounds_triggersNewQuery() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceTimeBy(50)

        val bounds = CameraBounds(40.0, 41.0, -4.0, -3.0)
        viewModel.onBoundsChanged(bounds)
        advanceTimeBy(400) // let first query fire with empty result

        // Now update date filter — should re-trigger
        val expected = listOf(sampleConcert("filtered"))
        fakeRepo.boundingBoxConcerts = expected
        viewModel.setDateFilter("2026-07-01", "2026-07-31")
        advanceTimeBy(400)

        assertEquals(expected, viewModel.uiState.value.concerts)
    }

    // TST-MAP-05: concerts in bounding box emitted to uiState
    @Test
    fun boundsChanged_concertsEmittedToUiState() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceTimeBy(50)

        val expected = listOf(sampleConcert("c1"), sampleConcert("c2"))
        fakeRepo.boundingBoxConcerts = expected

        viewModel.onBoundsChanged(CameraBounds(40.0, 41.0, -4.0, -3.0))
        advanceTimeBy(400)

        assertEquals(2, viewModel.uiState.value.concerts.size)
        assertEquals("c1", viewModel.uiState.value.concerts[0].id)
        assertEquals("c2", viewModel.uiState.value.concerts[1].id)
    }

    // TST-MAP-06: Empty bounding box result emits empty list to uiState
    @Test
    fun boundsChanged_emptyResult_emitsEmptyList() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceTimeBy(50)

        fakeRepo.boundingBoxConcerts = emptyList()

        viewModel.onBoundsChanged(CameraBounds(90.0, 90.0, 180.0, 180.0))
        advanceTimeBy(400)

        assertEquals(emptyList(), viewModel.uiState.value.concerts)
    }

    // TST-MAP-07: GPS location sets cameraPosition (migrated: GRANTED)
    @Test
    fun gpsLocationAvailable_cameraPositionSetToGpsCoords() = runTest(testDispatcher) {
        val gpsLocation = LatLng(48.8566, 2.3522) // Paris
        val locationProvider = FakeLocationProvider(location = gpsLocation)
        val vm = MapaViewModel(locationProvider, fakeRepo, fakeProvider)
        backgroundScope.launch { vm.uiState.collect {} }
        // Prime _concerts so combine(_baseState, _concerts) can emit
        vm.onBoundsChanged(CameraBounds(40.0, 41.0, -4.0, -3.0))
        advanceTimeBy(400)

        vm.checkPermissionState(LocationAuthorizationStatus.GRANTED)
        advanceUntilIdle()

        assertEquals(gpsLocation.lat, vm.uiState.value.cameraPosition.lat)
        assertEquals(gpsLocation.lng, vm.uiState.value.cameraPosition.lng)
    }

    // TST-MAP-08: Null location falls back to Madrid (40.4168, -3.7038) (migrated: GRANTED)
    @Test
    fun locationNull_cameraFallsBackToMadrid() = runTest(testDispatcher) {
        val locationProvider = FakeLocationProvider(location = null)
        val vm = MapaViewModel(locationProvider, fakeRepo, fakeProvider)
        backgroundScope.launch { vm.uiState.collect {} }
        // Prime _concerts so combine(_baseState, _concerts) can emit
        vm.onBoundsChanged(CameraBounds(40.0, 41.0, -4.0, -3.0))
        advanceTimeBy(400)

        vm.checkPermissionState(LocationAuthorizationStatus.GRANTED)
        advanceUntilIdle()

        assertEquals(40.4168, vm.uiState.value.cameraPosition.lat)
        assertEquals(-3.7038, vm.uiState.value.cameraPosition.lng)
    }

    // TST-MAP-09: Exception in LocationProvider falls back to Madrid (migrated: GRANTED)
    @Test
    fun locationException_cameraFallsBackToMadrid() = runTest(testDispatcher) {
        val locationProvider = FakeLocationProvider(location = null).also { it.throwException = true }
        val vm = MapaViewModel(locationProvider, fakeRepo, fakeProvider)
        backgroundScope.launch { vm.uiState.collect {} }
        // Prime _concerts so combine(_baseState, _concerts) can emit
        vm.onBoundsChanged(CameraBounds(40.0, 41.0, -4.0, -3.0))
        advanceTimeBy(400)

        vm.checkPermissionState(LocationAuthorizationStatus.GRANTED)
        advanceUntilIdle()

        assertEquals(40.4168, vm.uiState.value.cameraPosition.lat)
        assertEquals(-3.7038, vm.uiState.value.cameraPosition.lng)
    }

    // TST-MAP-10: reset signal increments _dbVersion which triggers pipeline re-query
    @Test
    fun resetSignal_incrementsDbVersionAndRetriggersPipeline() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceTimeBy(50)

        val expected = listOf(sampleConcert("after-reset"))
        fakeRepo.boundingBoxConcerts = expected

        // Set up bounds first so the pipeline is active
        viewModel.onBoundsChanged(CameraBounds(40.0, 41.0, -4.0, -3.0))
        advanceTimeBy(400) // let initial query fire (empty result)

        // Now emit reset signal — should re-trigger the combine pipeline
        fakeProvider.emitReset()
        advanceTimeBy(400)

        assertEquals(expected, viewModel.uiState.value.concerts)
    }

    // TST-MAP-11: NOT_DETERMINED status shows rationale dialog
    @Test
    fun notDetermined_showsDialog() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        // Prime the _concerts pipeline so combine(_baseState, _concerts) can emit
        viewModel.onBoundsChanged(CameraBounds(40.0, 41.0, -4.0, -3.0))
        advanceTimeBy(400)

        viewModel.checkPermissionState(LocationAuthorizationStatus.NOT_DETERMINED)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showPermissionDialog)
        assertFalse(viewModel.uiState.value.userLocationResolved)
    }

    // TST-MAP-12: DENIED status resolves silently to default location — no dialog, no request
    @Test
    fun denied_resolvesToDefaultSilently() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        // Prime the _concerts pipeline so combine(_baseState, _concerts) can emit
        viewModel.onBoundsChanged(CameraBounds(40.0, 41.0, -4.0, -3.0))
        advanceTimeBy(400)

        viewModel.checkPermissionState(LocationAuthorizationStatus.DENIED)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.userLocationResolved)
        assertFalse(viewModel.uiState.value.showPermissionDialog)
        // Camera stays at default (Puerta del Sol) — no location fetch happened
        assertEquals(DEFAULT_LOCATION.lat, viewModel.uiState.value.cameraPosition.lat)
        assertEquals(DEFAULT_LOCATION.lng, viewModel.uiState.value.cameraPosition.lng)
    }

    // TST-MAP-13: After userLocationResolved, subsequent checkPermissionState calls are no-ops
    @Test
    fun guardedAfterResolved_isNoOp() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        // Prime the _concerts pipeline so combine(_baseState, _concerts) can emit
        viewModel.onBoundsChanged(CameraBounds(40.0, 41.0, -4.0, -3.0))
        advanceTimeBy(400)

        // First call resolves
        viewModel.checkPermissionState(LocationAuthorizationStatus.DENIED)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.userLocationResolved)

        // Second call with NOT_DETERMINED should NOT show dialog (guard triggers)
        viewModel.checkPermissionState(LocationAuthorizationStatus.NOT_DETERMINED)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showPermissionDialog)
    }

    // TST-MAP-14: Dialog dismissed ("No, gracias") resolves silently for this session only
    @Test
    fun dialogDismissed_resolvesSilently() = runTest(testDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect {} }
        // Prime the _concerts pipeline so combine(_baseState, _concerts) can emit
        viewModel.onBoundsChanged(CameraBounds(40.0, 41.0, -4.0, -3.0))
        advanceTimeBy(400)

        // Show dialog via NOT_DETERMINED
        viewModel.checkPermissionState(LocationAuthorizationStatus.NOT_DETERMINED)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showPermissionDialog)

        // User dismisses
        viewModel.onPermissionDialogDismissed()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.showPermissionDialog)
        assertTrue(viewModel.uiState.value.userLocationResolved)
        // Camera stays at default — no location fetch
        assertEquals(DEFAULT_LOCATION.lat, viewModel.uiState.value.cameraPosition.lat)
        assertEquals(DEFAULT_LOCATION.lng, viewModel.uiState.value.cameraPosition.lng)
    }

    // TST-MAP-15: W-01 — cross-instantiation invariant: dismiss on first launch must NOT suppress
    // the dialog on a subsequent cold launch (new VM instance with NOT_DETERMINED status).
    @Test
    fun notDetermined_onSubsequentLaunch_repromptsAgain() = runTest(testDispatcher) {
        // --- First "launch" session ---
        val vm1 = MapaViewModel(fakeLocation, fakeRepo, fakeProvider)
        backgroundScope.launch { vm1.uiState.collect {} }
        // Prime _concerts so combine(_baseState, _concerts) can emit
        vm1.onBoundsChanged(CameraBounds(40.0, 41.0, -4.0, -3.0))
        advanceTimeBy(400)

        vm1.checkPermissionState(LocationAuthorizationStatus.NOT_DETERMINED)
        advanceUntilIdle()
        assertTrue(vm1.uiState.value.showPermissionDialog)

        // User dismisses — resolves for this session only
        vm1.onPermissionDialogDismissed()
        advanceUntilIdle()
        assertFalse(vm1.uiState.value.showPermissionDialog)
        assertTrue(vm1.uiState.value.userLocationResolved)

        // --- Second "cold launch" session (brand-new VM instance) ---
        val vm2 = MapaViewModel(fakeLocation, fakeRepo, fakeProvider)
        backgroundScope.launch { vm2.uiState.collect {} }
        // Prime _concerts so combine(_baseState, _concerts) can emit
        vm2.onBoundsChanged(CameraBounds(40.0, 41.0, -4.0, -3.0))
        advanceTimeBy(400)

        // OS still reports NOT_DETERMINED (e.g. iOS 'Allow Once' reverts to NOT_DETERMINED)
        vm2.checkPermissionState(LocationAuthorizationStatus.NOT_DETERMINED)
        advanceUntilIdle()

        // Prior dismiss must NOT have persisted — new VM must re-prompt
        assertTrue(vm2.uiState.value.showPermissionDialog)
        assertFalse(vm2.uiState.value.userLocationResolved)
    }

    // Helpers

    private fun sampleConcert(id: String) = Concert(
        id = id,
        salaConcierto = SalaConcierto(
            id = "s1", name = "Sala Test", address = null,
            city = "Madrid", province = "Madrid",
            lat = 40.4, lng = -3.7,
            imageUrl = null, description = null, sourceUrl = null,
        ),
        artists = listOf(Artist("a1", "Artista", "Rock", null, null, null, null)),
        date = "2026-06-15", time = "21:00", price = "20€",
        sourceUrl = null, updatedAt = "2026-05-26T00:00:00Z",
    )
}
