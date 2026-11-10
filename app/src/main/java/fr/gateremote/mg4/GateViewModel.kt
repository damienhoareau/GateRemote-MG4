package fr.gateremote.mg4

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.gateremote.mg4.data.Gate
import fr.gateremote.mg4.data.GateAction
import fr.gateremote.mg4.data.GateJson
import fr.gateremote.mg4.data.GateRepository
import fr.gateremote.mg4.logic.ActionResult
import fr.gateremote.mg4.logic.HttpExecutor
import fr.gateremote.mg4.logic.Position
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class GateViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GateRepository(File(application.filesDir, "gates.json"))
    val gates: StateFlow<List<Gate>> = repository.gates

    val position = MutableStateFlow<Position?>(null)

    /** Identifiants des portails dont une commande est en cours d'envoi. */
    private val _busy = MutableStateFlow<Set<String>>(emptySet())
    val busy: StateFlow<Set<String>> = _busy

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()

    fun notify(message: String) {
        _messages.tryEmit(message)
    }

    fun save(gate: Gate) = viewModelScope.launch(Dispatchers.IO) { repository.save(gate) }

    fun delete(id: String) = viewModelScope.launch(Dispatchers.IO) { repository.delete(id) }

    /** Exécute [action] pour le portail [gateName] et affiche le résultat. */
    fun trigger(gateId: String, gateName: String, action: GateAction) {
        if (gateId in _busy.value) return
        _busy.update { it + gateId }
        viewModelScope.launch {
            val result = try {
                run(action)
            } finally {
                _busy.update { it - gateId }
            }
            val label = gateName.ifBlank { "Portail" }
            _messages.emit("$label : ${result.message}")
        }
    }

    private suspend fun run(action: GateAction): ActionResult = when (action) {
        is GateAction.Http -> withContext(Dispatchers.IO) { HttpExecutor.execute(action) }
        is GateAction.LaunchApp -> launchApp(action.packageName)
    }

    private fun launchApp(packageName: String): ActionResult {
        val app = getApplication<Application>()
        val intent = app.packageManager.getLaunchIntentForPackage(packageName.trim())
            ?: return ActionResult.Failure("Application « $packageName » introuvable sur cet appareil")
        return try {
            app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ActionResult.Success("Application ouverte")
        } catch (e: Exception) {
            ActionResult.Failure("Impossible d'ouvrir l'application")
        }
    }

    fun exportTo(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val message = try {
            val stream = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                ?: error("fichier inaccessible")
            stream.use { it.write(GateJson.encode(gates.value).toByteArray()) }
            "Configuration exportée (${gates.value.size} portail(s))"
        } catch (e: Exception) {
            "Export impossible : ${e.message}"
        }
        _messages.emit(message)
    }

    /** Remplace tous les portails par ceux du fichier choisi. */
    fun importFrom(uri: Uri) = viewModelScope.launch(Dispatchers.IO) {
        val message = try {
            val stream = getApplication<Application>().contentResolver.openInputStream(uri)
                ?: error("fichier inaccessible")
            val imported = GateJson.decode(stream.use { it.readBytes() }.decodeToString())
            repository.replaceAll(imported)
            "${imported.size} portail(s) importé(s)"
        } catch (e: Exception) {
            "Import impossible : fichier non reconnu"
        }
        _messages.emit(message)
    }
}
