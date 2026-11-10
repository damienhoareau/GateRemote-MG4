package fr.gateremote.mg4.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import fr.gateremote.mg4.data.DEFAULT_RADIUS_METERS
import fr.gateremote.mg4.data.Gate
import fr.gateremote.mg4.data.GateAction
import fr.gateremote.mg4.logic.HTTP_METHODS
import fr.gateremote.mg4.logic.Position
import fr.gateremote.mg4.logic.validateHttp
import java.util.UUID

private enum class ActionType(val label: String) {
    NONE("Aucune"),
    HTTP("Requête HTTP"),
    APP("Ouvrir une application"),
}

/** État du formulaire d'une commande (ouverture ou fermeture). */
private class ActionForm(initial: GateAction?) {
    var type by mutableStateOf(
        when (initial) {
            null -> ActionType.NONE
            is GateAction.Http -> ActionType.HTTP
            is GateAction.LaunchApp -> ActionType.APP
        }
    )
    private val http = initial as? GateAction.Http
    var url by mutableStateOf(http?.url ?: "")
    var method by mutableStateOf(http?.method?.uppercase() ?: "GET")
    var headers by mutableStateOf(http?.headers ?: "")
    var body by mutableStateOf(http?.body ?: "")
    var packageName by mutableStateOf((initial as? GateAction.LaunchApp)?.packageName ?: "")

    fun toAction(): GateAction? = when (type) {
        ActionType.NONE -> null
        ActionType.HTTP -> GateAction.Http(url.trim(), method, headers.trim(), body)
        ActionType.APP -> GateAction.LaunchApp(packageName.trim())
    }

    fun error(): String? = when (val action = toAction()) {
        null -> null
        is GateAction.Http -> validateHttp(action)
        is GateAction.LaunchApp -> if (action.packageName.isEmpty()) "Choisissez une application" else null
    }
}

private fun parseCoordinate(text: String, max: Double): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it in -max..max }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGateScreen(
    initial: Gate?,
    position: Position?,
    busy: Set<String>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onSave: (Gate) -> Unit,
    onDelete: (Gate) -> Unit,
    onTest: (gateId: String, name: String, action: GateAction) -> Unit,
) {
    val gateId = remember { initial?.id ?: UUID.randomUUID().toString() }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    // Nouveau portail : on part de la position actuelle de la voiture si elle est connue.
    var latitude by remember { mutableStateOf((initial?.latitude ?: position?.latitude)?.toString() ?: "") }
    var longitude by remember { mutableStateOf((initial?.longitude ?: position?.longitude)?.toString() ?: "") }
    var radius by remember { mutableStateOf((initial?.radiusMeters ?: DEFAULT_RADIUS_METERS).toString()) }
    val openForm = remember { ActionForm(initial?.openAction) }
    val closeForm = remember { ActionForm(initial?.closeAction) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun buildGate(): Gate? {
        val lat = parseCoordinate(latitude, 90.0)
        val lon = parseCoordinate(longitude, 180.0)
        val radiusMeters = radius.trim().toIntOrNull()
        error = when {
            name.isBlank() -> "Donnez un nom au portail"
            lat == null -> "Latitude invalide"
            lon == null -> "Longitude invalide"
            radiusMeters == null || radiusMeters !in 10..5000 -> "Le rayon doit être compris entre 10 et 5000 m"
            openForm.error() != null -> "Ouverture : ${openForm.error()}"
            closeForm.error() != null -> "Fermeture : ${closeForm.error()}"
            openForm.type == ActionType.NONE && closeForm.type != ActionType.NONE ->
                "Renseignez la commande d'ouverture (elle sert seule si le portail fonctionne par impulsion)"
            else -> null
        }
        if (error != null) return null
        return Gate(gateId, name.trim(), lat!!, lon!!, radiusMeters!!, openForm.toAction(), closeForm.toAction())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "Nouveau portail" else "Modifier le portail") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                },
                actions = {
                    if (initial != null) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, "Supprimer") }
                    }
                    Button(onClick = { buildGate()?.let(onSave) }, modifier = Modifier.padding(end = 8.dp)) {
                        Text("Enregistrer")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .widthIn(max = 720.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom") },
                placeholder = { Text("Maison, Bureau…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Emplacement", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = latitude,
                    onValueChange = { latitude = it },
                    label = { Text("Latitude") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = longitude,
                    onValueChange = { longitude = it },
                    label = { Text("Longitude") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        position?.let {
                            latitude = it.latitude.toString()
                            longitude = it.longitude.toString()
                        }
                    },
                    enabled = position != null,
                ) { Text(if (position != null) "Utiliser ma position actuelle" else "Position actuelle inconnue") }
                OutlinedTextField(
                    value = radius,
                    onValueChange = { radius = it },
                    label = { Text("Rayon de détection (m)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            HorizontalDivider()
            ActionEditor(
                title = "Commande d'ouverture",
                hint = "Si le portail fonctionne par impulsion (la même commande ouvre et ferme), " +
                    "renseignez seulement celle-ci.",
                form = openForm,
                testEnabled = gateId !in busy,
                onTest = { onTest(gateId, name, it) },
            )
            HorizontalDivider()
            ActionEditor(
                title = "Commande de fermeture (facultative)",
                hint = null,
                form = closeForm,
                testEnabled = gateId !in busy,
                onTest = { onTest(gateId, name, it) },
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete && initial != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer « ${initial.name} » ?") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(initial) }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun ActionEditor(
    title: String,
    hint: String?,
    form: ActionForm,
    testEnabled: Boolean,
    onTest: (GateAction) -> Unit,
) {
    var pickingApp by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        hint?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionType.entries.forEach { type ->
                FilterChip(selected = form.type == type, onClick = { form.type = type }, label = { Text(type.label) })
            }
        }

        when (form.type) {
            ActionType.NONE -> Unit

            ActionType.HTTP -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HTTP_METHODS.forEach { method ->
                        FilterChip(
                            selected = form.method == method,
                            onClick = { form.method = method },
                            label = { Text(method) },
                        )
                    }
                }
                OutlinedTextField(
                    value = form.url,
                    onValueChange = { form.url = it },
                    label = { Text("Adresse (URL)") },
                    placeholder = { Text("https://…") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.headers,
                    onValueChange = { form.headers = it },
                    label = { Text("En-têtes (facultatif, une par ligne)") },
                    placeholder = { Text("Authorization: Bearer …") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (form.method != "GET") {
                    OutlinedTextField(
                        value = form.body,
                        onValueChange = { form.body = it },
                        label = { Text("Corps de la requête (facultatif)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            ActionType.APP -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = form.packageName,
                        onValueChange = { form.packageName = it },
                        label = { Text("Identifiant de l'application") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { pickingApp = true }) { Text("Choisir…") }
                }
            }
        }

        if (form.type != ActionType.NONE) {
            OutlinedButton(
                onClick = { form.toAction()?.let(onTest) },
                enabled = testEnabled && form.error() == null,
            ) { Text("Tester maintenant") }
        }
    }

    if (pickingApp) {
        AppPickerDialog(
            onPick = { form.packageName = it; pickingApp = false },
            onDismiss = { pickingApp = false },
        )
    }
}

private data class InstalledApp(val label: String, val packageName: String)

private fun launchableApps(context: Context): List<InstalledApp> {
    val manager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return manager.queryIntentActivities(intent, 0)
        .map { InstalledApp(it.loadLabel(manager).toString(), it.activityInfo.packageName) }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

@Composable
private fun AppPickerDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val apps = remember { launchableApps(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choisir une application") },
        text = {
            LazyColumn {
                items(apps, key = { it.packageName }) { app ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(app.packageName) }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            app.packageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
