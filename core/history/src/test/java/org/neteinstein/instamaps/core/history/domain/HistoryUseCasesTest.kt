package org.neteinstein.instamaps.core.history.domain

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

private class FakeHistoryRepository(
    initial: List<HistoryEntry> = emptyList(),
) : HistoryRepository {
    private val entries = MutableStateFlow(initial)
    val recordedCalls = mutableListOf<Pair<String, List<HistoryLocation>>>()

    override fun observeEntries(): Flow<List<HistoryEntry>> = entries

    override suspend fun recordEntry(
        url: String,
        locations: List<HistoryLocation>,
    ) {
        recordedCalls.add(url to locations)
        val newEntry = HistoryEntry(id = "id-${recordedCalls.size}", url = url, timestamp = 0L, locations = locations)
        entries.value = listOf(newEntry) + entries.value
    }
}

class ObserveHistoryUseCaseTest {
    @Test
    fun `emits the list currently held by the repository`() =
        runTest {
            val entry = HistoryEntry(id = "1", url = "https://instagram.com/reel/abc", timestamp = 100L)
            val repository = FakeHistoryRepository(initial = listOf(entry))
            val useCase = ObserveHistoryUseCase(repository)

            useCase().test {
                assertEquals(listOf(entry), awaitItem())
            }
        }

    @Test
    fun `emits an empty list when nothing has been recorded`() =
        runTest {
            val repository = FakeHistoryRepository()
            val useCase = ObserveHistoryUseCase(repository)

            useCase().test {
                assertEquals(emptyList<HistoryEntry>(), awaitItem())
            }
        }
}

class RecordHistoryEntryUseCaseTest {
    @Test
    fun `delegates to the repository with the given url and locations`() =
        runTest {
            val repository = FakeHistoryRepository()
            val useCase = RecordHistoryEntryUseCase(repository)
            val locations = listOf(HistoryLocation(name = "Time Out Market", address = "Lisbon, Portugal"))

            useCase(url = "https://instagram.com/reel/abc", locations = locations)

            assertEquals(listOf("https://instagram.com/reel/abc" to locations), repository.recordedCalls)
        }

    @Test
    fun `defaults to an empty location list`() =
        runTest {
            val repository = FakeHistoryRepository()
            val useCase = RecordHistoryEntryUseCase(repository)

            useCase(url = "https://instagram.com/reel/abc")

            assertEquals(listOf("https://instagram.com/reel/abc" to emptyList<HistoryLocation>()), repository.recordedCalls)
        }
}
