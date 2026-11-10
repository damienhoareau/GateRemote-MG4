package fr.gateremote.mg4.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.gateremote.mg4.data.Gate
import fr.gateremote.mg4.data.GateAction
import fr.gateremote.mg4.logic.Position
import fr.gateremote.mg4.logic.RankedGate
import fr.gateremote.mg4.logic.formatDistance
import fr.gateremote.mg4.logic.rankGates
import kotlin.math.roundToInt

/** Commande en attente de confirmation parce que la voiture n'est pas à portée du portail. */
private data class PendingTrigger(val ranked: RankedGate, val action: GateAction, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    gates: List<Gate>,
    position: Position?,
    hasLocationPermission: Boolean,
    busy: Set<String>,
    snackbar: SnackbarHostState,
    onRequestPermission: () -> Unit,
    onTrigger: (Gate, GateAction) -> Unit,
    onAdd: () -> Unit,
    onEdit: (Gate) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    val ranked = rankGates(gates, position?.latitude, position?.longitude)
    val nearby = ranked.filter { it.isNearby }
    val others = ranked.filterNot { it.isNearby }
    var pending by remember { mutableStateOf<PendingTrigger?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    val trigger = { item: RankedGate, action: GateAction, label: String ->
        if (item.isNearby) onTrigger(item.gate, action)
        else pending = PendingTrigger(item, action, label)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("GateRemote")
                        Text(
                            locationStatus(hasLocationPermission, position),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Ajouter un portail") }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "Menu") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Exporter la configuration") },
                            onClick = { menuOpen = false; onExport() },
                        )
                        DropdownMenuItem(
                            text = { Text("Importer une configuration") },
                            onClick = { menuOpen = false; onImport() },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (gates.isEmpty()) {
            EmptyState(Modifier.padding(padding), onAdd)
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 340.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!hasLocationPermission) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PermissionBanner(onRequestPermission)
                }
            }
            if (nearby.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("À proximité") }
                items(nearby, key = { it.gate.id }) { item ->
                    GateCard(item, item.gate.id in busy, trigger, onEdit)
                }
            }
            if (others.isNotEmpty()) {
                if (nearby.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) { SectionTitle("Autres portails") }
                }
                items(others, key = { it.gate.id }) { item ->
                    GateCard(item, item.gate.id in busy, trigger, onEdit)
                }
            }
        }
    }

    pending?.let { request ->
        val where = request.ranked.distanceMeters
            ?.let { "Vous êtes à ${formatDistance(it)} de ce portail." }
            ?: "Votre position est inconnue."
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("${request.label} « ${request.ranked.gate.name} » ?") },
            text = { Text("$where Envoyer la commande quand même ?") },
            confirmButton = {
                TextButton(onClick = {
                    onTrigger(request.ranked.gate, request.action)
                    pending = null
                }) { Text("Envoyer") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Annuler") } },
        )
    }
}

private fun locationStatus(hasPermission: Boolean, position: Position?): String = when {
    !hasPermission -> "Localisation non autorisée"
    position == null -> "Recherche de la position…"
    position.accuracyMeters != null -> "Position connue (± ${position.accuracyMeters.roundToInt()} m)"
    else -> "Position connue"
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun PermissionBanner(onRequestPermission: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Sans accès à la position, l'application ne peut pas repérer les portails proches.",
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.size(12.dp))
            Button(onClick = onRequestPermission) { Text("Autoriser") }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onAdd: () -> Unit) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Text("Aucun portail enregistré", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "Garez-vous devant un portail, puis ajoutez-le : sa position sera reprise automatiquement.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAdd, modifier = Modifier.height(64.dp)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.size(8.dp))
                Text("Ajouter un portail", fontSize = 20.sp)
            }
        }
    }
}

@Composable
private fun GateCard(
    item: RankedGate,
    isBusy: Boolean,
    onTrigger: (RankedGate, GateAction, String) -> Unit,
    onEdit: (Gate) -> Unit,
) {
    val gate = item.gate
    val near = item.isNearby
    val colors = if (near) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    // Les portails à portée ont des boutons plus hauts, faciles à viser en voiture.
    val buttonHeight = if (near) 88.dp else 60.dp
    val buttonFont = if (near) 26.sp else 20.sp

    Card(colors = colors) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        gate.name,
                        style = if (near) MaterialTheme.typography.headlineMedium
                        else MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    item.distanceMeters?.let {
                        Text(formatDistance(it), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (isBusy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                IconButton(onClick = { onEdit(gate) }) { Icon(Icons.Filled.Edit, "Modifier ${gate.name}") }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().padding(end = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val open = gate.openAction
                val close = gate.closeAction
                val buttonModifier = Modifier.weight(1f).height(buttonHeight)
                when {
                    open == null && close == null ->
                        OutlinedButton(onClick = { onEdit(gate) }, modifier = buttonModifier) {
                            Text("Configurer la commande", fontSize = 18.sp)
                        }

                    open != null && close == null ->
                        // Une seule commande : c'est une impulsion qui ouvre ou ferme selon l'état.
                        Button(
                            onClick = { onTrigger(item, open, "Actionner") },
                            enabled = !isBusy,
                            modifier = buttonModifier,
                        ) { Text("Ouvrir / Fermer", fontSize = buttonFont) }

                    else -> {
                        if (open != null) {
                            Button(
                                onClick = { onTrigger(item, open, "Ouvrir") },
                                enabled = !isBusy,
                                modifier = buttonModifier,
                            ) { Text("Ouvrir", fontSize = buttonFont) }
                        }
                        if (close != null) {
                            FilledTonalButton(
                                onClick = { onTrigger(item, close, "Fermer") },
                                enabled = !isBusy,
                                modifier = buttonModifier,
                                colors = if (near) ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ) else ButtonDefaults.filledTonalButtonColors(),
                            ) { Text("Fermer", fontSize = buttonFont) }
                        }
                    }
                }
            }
        }
    }
}
