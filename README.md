# GateRemote-MG4

Application Android pour ouvrir ou fermer ses portails depuis la voiture (pensée pour l'écran de la MG4, utilisable aussi sur un téléphone ou une tablette Android).

L'application suit la position GPS et **met en avant les portails à portée**, avec de gros boutons « Ouvrir » / « Fermer ». Rien ne se déclenche tout seul : c'est toujours vous qui appuyez.

## Fonctionnement

- **Écran principal** : les portails situés dans leur rayon de détection apparaissent en haut, dans la section « À proximité ». Les autres restent accessibles en dessous, avec leur distance ; une confirmation est demandée avant d'envoyer une commande à un portail hors de portée.
- **Ajout d'un portail** : garez-vous devant, touchez **+** : la position actuelle est reprise automatiquement. Réglez le rayon de détection (150 m par défaut) et la ou les commandes.
- **Commandes** : chaque portail a une commande d'ouverture et, si besoin, une commande de fermeture distincte. Si le portail fonctionne par impulsion (la même commande ouvre et ferme), ne renseignez que la première : un seul bouton « Ouvrir / Fermer » est affiché.
- **Export / import** (menu ⋮) : la configuration tient dans un fichier JSON. Pratique pour la saisir sur un téléphone puis la reprendre dans la voiture.

### Types de commande

| Type | Usage |
| --- | --- |
| **Requête HTTP** | Appelle une URL (GET, POST ou PUT, en-têtes et corps libres). Convient à un relais connecté (Shelly, ESP32/ESPHome…), un webhook, Home Assistant, etc. |
| **Ouvrir une application** | Lance une autre application installée sur le même appareil, par exemple celle du visiophone. |

Exemples de requêtes HTTP :

```
# Relais Shelly (1re génération) sur le réseau local : impulsion sur la sortie 0
GET  http://192.168.1.50/relay/0?turn=on&timer=1

# Webhook Home Assistant exposé sur Internet
POST https://ma-maison.example.org/api/webhook/ouvrir-portail
```

### Portails gérés par iSCS Sentinel (visiophone)

SCS Sentinel ne publie pas d'API pour son application iSCS Sentinel : GateRemote ne peut donc pas commander ces portails directement. Deux possibilités :

1. **Ouvrir une application** : si iSCS Sentinel est installée sur le même appareil, GateRemote la lance au bon moment ; l'ouverture se fait ensuite dans iSCS Sentinel.
2. **Ajouter un relais connecté** (contact sec, type Shelly) sur l'entrée « impulsion » de la motorisation, en parallèle du visiophone. Le portail devient alors pilotable par une requête HTTP, en un seul appui.

## Installation

L'APK se récupère dans l'onglet **Actions** du dépôt GitHub (artefact `GateRemote-MG4-debug` de la dernière exécution), ou se compile localement (voir plus bas).

- **Téléphone / tablette Android** (8.0 ou plus récent) : copiez l'APK et ouvrez-le, en autorisant l'installation depuis des sources inconnues.
- **Écran de la MG4** : le système est basé sur Android, mais MG ne prévoit pas l'installation d'applications tierces. La manipulation dépend de la version du logiciel de la voiture et n'est pas officielle ; renseignez-vous sur la méthode adaptée à votre version avant de vous lancer. L'application n'utilise pas les services Google Play, absents de la voiture.

La voiture (ou le téléphone) doit avoir accès à Internet ou au réseau local des modules pour que les requêtes HTTP aboutissent.

## Limites actuelles

- La position n'est suivie que lorsque l'application est affichée à l'écran : pas de notification en arrière-plan.
- Les jetons et mots de passe saisis dans les en-têtes sont enregistrés en clair dans le stockage privé de l'application, et figurent dans le fichier exporté.
- L'état réel du portail (ouvert ou fermé) n'est pas connu de l'application.

## Compilation

Prérequis : JDK 17 ou plus récent et le SDK Android (plateforme 35).

```sh
./gradlew testDebugUnitTest assembleDebug
# APK : app/build/outputs/apk/debug/app-debug.apk
```

## Organisation du code

- `data/` : modèle `Gate` / `GateAction`, sérialisation JSON, stockage.
- `logic/` : calcul des distances et classement des portails, envoi des requêtes HTTP, suivi GPS.
- `ui/` : écrans Jetpack Compose (liste des portails, édition).
