package org.rubenazo.conciertosfront.feature.sync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.rubenazo.conciertosfront.core.domain.model.SyncResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(
    syncResult: SyncResult?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = "DEBUG DATA",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Monospace,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                ),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                item {
                    SelectionContainer {
                        Text(
                            text = buildDebugText(syncResult),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

private fun buildDebugText(syncResult: SyncResult?): String = buildString {
    if (syncResult == null) {
        appendLine("No sync data")
        return@buildString
    }

    appendLine("=== SYNC SUMMARY ===")
    appendLine("Salas: ${syncResult.salasCount} | Artists: ${syncResult.artistsCount} | Concerts: ${syncResult.concertsCount}")
    if (syncResult.deletedConcertsCount > 0) {
        appendLine("Deleted: ${syncResult.deletedConcertsCount}")
    }
    appendLine("Network: ${if (syncResult.hadNetwork) "OK" else "OFFLINE"}")
    if (syncResult.errors.isNotEmpty()) {
        appendLine("Errors: ${syncResult.errors.joinToString()}")
    }

    val hasNewData = syncResult.newSalas.isNotEmpty() ||
        syncResult.newArtists.isNotEmpty() ||
        syncResult.newConcerts.isNotEmpty()

    if (!hasNewData) {
        appendLine()
        appendLine("No new data downloaded")
        return@buildString
    }

    val salaMap = syncResult.newSalas.associateBy { it.id }
    val artistMap = syncResult.newArtists.associateBy { it.id }

    if (syncResult.newSalas.isNotEmpty()) {
        appendLine()
        appendLine("=== NEW SALAS (${syncResult.newSalas.size}) ===")
        syncResult.newSalas.forEach { sala ->
            appendLine("${sala.name} — ${sala.city} (${sala.province})")
        }
    }

    if (syncResult.newArtists.isNotEmpty()) {
        appendLine()
        appendLine("=== NEW ARTISTS (${syncResult.newArtists.size}) ===")
        syncResult.newArtists.forEach { artist ->
            appendLine("${artist.name}${artist.genre?.let { " [$it]" } ?: ""}")
        }
    }

    if (syncResult.newConcerts.isNotEmpty()) {
        appendLine()
        appendLine("=== NEW CONCERTS (${syncResult.newConcerts.size}) ===")
        syncResult.newConcerts.forEach { concert ->
            val salaName = salaMap[concert.salaConciertoId]?.name ?: concert.salaConciertoId
            val artistNames = concert.artistIds.map { artistMap[it]?.name ?: it }
            appendLine("${concert.date}${concert.time?.let { " $it" } ?: ""} — $salaName")
            appendLine("  ${artistNames.joinToString(", ")}${concert.price?.let { " — $it" } ?: ""}")
        }
    }
}
