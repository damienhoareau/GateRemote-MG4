package fr.gateremote.mg4.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** Liste des portails, conservée en JSON dans le stockage privé de l'application. */
class GateRepository(private val file: File) {

    private val _gates = MutableStateFlow(load())
    val gates: StateFlow<List<Gate>> = _gates.asStateFlow()

    private fun load(): List<Gate> =
        try {
            if (file.exists()) GateJson.decode(file.readText()) else emptyList()
        } catch (e: Exception) {
            // Fichier illisible : on le met de côté plutôt que de l'écraser.
            file.copyTo(File(file.parentFile, file.name + ".invalide"), overwrite = true)
            emptyList()
        }

    @Synchronized
    fun replaceAll(gates: List<Gate>) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(GateJson.encode(gates))
        if (!tmp.renameTo(file)) {
            tmp.copyTo(file, overwrite = true)
            tmp.delete()
        }
        _gates.value = gates
    }

    fun save(gate: Gate) {
        val current = _gates.value
        replaceAll(
            if (current.any { it.id == gate.id }) current.map { if (it.id == gate.id) gate else it }
            else current + gate
        )
    }

    fun delete(id: String) = replaceAll(_gates.value.filterNot { it.id == id })
}
