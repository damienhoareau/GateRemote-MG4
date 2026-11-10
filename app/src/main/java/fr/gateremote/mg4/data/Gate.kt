package fr.gateremote.mg4.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

const val DEFAULT_RADIUS_METERS = 150

/** Un portail : une position, un rayon de détection et les commandes à envoyer. */
@Serializable
data class Gate(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = DEFAULT_RADIUS_METERS,
    /** Commande d'ouverture. Si [closeAction] est absent, elle sert aussi à fermer (impulsion). */
    val openAction: GateAction? = null,
    val closeAction: GateAction? = null,
)

/** Ce que fait l'application quand on appuie sur un bouton du portail. */
@Serializable
sealed interface GateAction {

    /** Appel HTTP vers un module connecté, un webhook, Home Assistant… */
    @Serializable
    @SerialName("http")
    data class Http(
        val url: String,
        val method: String = "GET",
        /** Une en-tête par ligne, au format `Nom: valeur`. */
        val headers: String = "",
        val body: String = "",
    ) : GateAction

    /** Ouvre une autre application installée (par exemple celle du visiophone). */
    @Serializable
    @SerialName("app")
    data class LaunchApp(val packageName: String) : GateAction
}

object GateJson {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    fun encode(gates: List<Gate>): String = json.encodeToString(gates)

    /** @throws IllegalArgumentException si le texte n'est pas une liste de portails valide. */
    fun decode(text: String): List<Gate> = json.decodeFromString(text)
}
