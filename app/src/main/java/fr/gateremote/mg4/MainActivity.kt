package fr.gateremote.mg4

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import fr.gateremote.mg4.logic.locationUpdates
import fr.gateremote.mg4.ui.EditGateScreen
import fr.gateremote.mg4.ui.HomeScreen

class MainActivity : ComponentActivity() {

    private val viewModel: GateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // En voiture, l'écran doit rester allumé tant que l'application est affichée.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                App(viewModel)
            }
        }
    }
}

private sealed interface Screen {
    data object Home : Screen
    /** `gateId == null` : création d'un nouveau portail. */
    data class Edit(val gateId: String?) : Screen
}

@Composable
private fun App(viewModel: GateViewModel) {
    val context = LocalContext.current
    val gates by viewModel.gates.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result -> hasLocationPermission = result.values.any { it } }
    val requestPermission = {
        permissionLauncher.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        )
    }
    LaunchedEffect(Unit) { if (!hasLocationPermission) requestPermission() }

    // Le GPS n'est écouté que lorsque l'application est à l'écran.
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(hasLocationPermission, lifecycleOwner) {
        if (hasLocationPermission) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                locationUpdates(context).collect { viewModel.position.value = it }
            }
        }
    }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportTo) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importFrom) }

    when (val current = screen) {
        Screen.Home -> HomeScreen(
            gates = gates,
            position = position,
            hasLocationPermission = hasLocationPermission,
            busy = busy,
            snackbar = snackbar,
            onRequestPermission = requestPermission,
            onTrigger = { gate, action -> viewModel.trigger(gate.id, gate.name, action) },
            onAdd = { screen = Screen.Edit(null) },
            onEdit = { screen = Screen.Edit(it.id) },
            onExport = {
                try {
                    exportLauncher.launch("portails.json")
                } catch (e: ActivityNotFoundException) {
                    viewModel.notify("Aucun gestionnaire de fichiers sur cet appareil")
                }
            },
            onImport = {
                try {
                    importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                } catch (e: ActivityNotFoundException) {
                    viewModel.notify("Aucun gestionnaire de fichiers sur cet appareil")
                }
            },
        )

        is Screen.Edit -> {
            BackHandler { screen = Screen.Home }
            EditGateScreen(
                initial = gates.firstOrNull { it.id == current.gateId },
                position = position,
                busy = busy,
                snackbar = snackbar,
                onBack = { screen = Screen.Home },
                onSave = { viewModel.save(it); screen = Screen.Home },
                onDelete = { viewModel.delete(it.id); screen = Screen.Home },
                onTest = { gateId, name, action -> viewModel.trigger(gateId, name, action) },
            )
        }
    }
}
