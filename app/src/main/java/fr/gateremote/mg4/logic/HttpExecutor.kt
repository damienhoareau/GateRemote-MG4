package fr.gateremote.mg4.logic

import fr.gateremote.mg4.data.GateAction
import java.net.HttpURLConnection
import java.net.URL

sealed interface ActionResult {
    val message: String

    data class Success(override val message: String) : ActionResult
    data class Failure(override val message: String) : ActionResult
}

val HTTP_METHODS = listOf("GET", "POST", "PUT")

/** Découpe le champ « en-têtes » (une par ligne, `Nom: valeur`) ; les lignes invalides sont ignorées. */
fun parseHeaders(raw: String): List<Pair<String, String>> =
    raw.lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) null
            else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
        }
        .toList()

/** Renvoie un message d'erreur si l'action ne peut pas être envoyée telle quelle, sinon `null`. */
fun validateHttp(action: GateAction.Http): String? {
    val url = action.url.trim()
    return when {
        url.isEmpty() -> "L'adresse (URL) est vide"
        !url.startsWith("http://") && !url.startsWith("https://") ->
            "L'adresse doit commencer par http:// ou https://"
        action.method.uppercase() !in HTTP_METHODS -> "Méthode HTTP non prise en charge : ${action.method}"
        else -> null
    }
}

object HttpExecutor {
    private const val TIMEOUT_MS = 8_000

    /** Envoie la requête. Bloquant : à appeler hors du thread principal. */
    fun execute(action: GateAction.Http): ActionResult {
        validateHttp(action)?.let { return ActionResult.Failure(it) }
        val method = action.method.uppercase()
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(action.url.trim()).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                useCaches = false
            }
            val headers = parseHeaders(action.headers)
            headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            if (method != "GET" && action.body.isNotEmpty()) {
                if (headers.none { it.first.equals("Content-Type", ignoreCase = true) }) {
                    connection.setRequestProperty("Content-Type", "application/json")
                }
                connection.doOutput = true
                connection.outputStream.use { it.write(action.body.toByteArray()) }
            }

            val code = connection.responseCode
            // On vide la réponse pour que la connexion se termine proprement.
            runCatching { (connection.errorStream ?: connection.inputStream)?.use { it.readBytes() } }
            if (code in 200..299) ActionResult.Success("Commande envoyée (HTTP $code)")
            else ActionResult.Failure("Refusé par le serveur (HTTP $code)")
        } catch (e: java.net.SocketTimeoutException) {
            ActionResult.Failure("Pas de réponse (délai dépassé)")
        } catch (e: java.net.UnknownHostException) {
            ActionResult.Failure("Serveur introuvable — vérifiez la connexion Internet")
        } catch (e: Exception) {
            ActionResult.Failure("Échec : ${e.message ?: e.javaClass.simpleName}")
        } finally {
            connection?.disconnect()
        }
    }
}
