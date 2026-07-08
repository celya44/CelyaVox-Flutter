# Corrections Firebase Push & Enregistrement SIP

## 🔴 Problèmes identifiés

### 1. **Délai insuffisant (500ms → 2500ms)**
- **Problème** : Le délai `FULL_SCREEN_DELAY_MS = 500L` était beaucoup trop court
- **Impact** : L'UI s'affichait avant que l'enregistrement SIP soit complétéé
- **Solution** : Augmenté à 2500ms pour permettre l'initialisation et l'enregistrement SIP

### 2. **Pas de WakeLock pendant l'enregistrement**
- **Problème** : `registerSipInBackground()` lancait un simple Thread sans WakeLock
- **Impact** : Le device pouvait s'endormir et tuer le processus d'enregistrement
- **Solution** : Ajouté `WakeLockManager` pour maintenir le device éveillé pendant ~4s

### 3. **Pas d'attente de confirmation d'enregistrement**
- **Problème** : Pas de vérification que PJSIP s'était bien enregistré
- **Impact** : L'UI s'affichait potentiellement avant que SIP soit prêt
- **Solution** : Ajout d'une attente de 2s dans le thread avec logs améliorés

### 4. **PJSIP init() peut échouer silencieusement**
- **Problème** : L'initialisation du moteur PJSIP dans le thread pouvait échouer
- **Impact** : L'enregistrement SIP échouait sans feedback clair
- **Solution** : Ajout de vérification explicite et logs d'erreur

## ✅ Changements implémentés

### Fichier : `VoipFirebaseService.kt`

#### 1. **Augmentation du délai de push à UI**
```kotlin
private const val FULL_SCREEN_DELAY_MS = 2500L  // Était 500L
```

#### 2. **Ajout de WakeLock et meilleure gestion du thread**
```kotlin
private fun registerSipInBackground() {
    val wakeLockManager = WakeLockManager(applicationContext)
    Thread {
        wakeLockManager.acquire()
        Log.i(TAG, "WakeLock acquired for SIP registration from push")
        try {
            // Vérification des données de provisioning
            val manager = ProvisioningManager(applicationContext)
            val username = manager.getSipUsername()
            val password = manager.getSipPassword()
            val domain = manager.getSipDomain()
            val proxy = manager.getSipProxy() ?: ""
            
            if (username.isNullOrBlank() || password.isNullOrBlank() || domain.isNullOrBlank()) {
                Log.w(TAG, "Skipping SIP register: missing provisioning data")
                return@Thread
            }
            
            // Initialisation explicite du moteur PJSIP
            if (!PjsipEngine.instance.init()) {
                Log.w(TAG, "Failed to initialize PJSIP engine")
                return@Thread
            }
            
            // Enregistrement SIP
            val ok = PjsipEngine.instance.register(username, password, domain, proxy)
            Log.i(TAG, "SIP register triggered from push: $ok")
            
            // Attente pour permettre au registre SIP de se complèter
            Thread.sleep(2000)
            Log.i(TAG, "Waited 2s for SIP registration to settle")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register SIP from push", e)
        } finally {
            wakeLockManager.release()
            Log.i(TAG, "WakeLock released after SIP registration attempt")
        }
    }.start()
}
```

#### 3. **Logs améliorés dans handleIncomingCallPush()**
```kotlin
private fun handleIncomingCallPush(callId: String, callerId: String) {
    // ...
    Log.i(TAG, "Delaying incoming call UI by ${FULL_SCREEN_DELAY_MS}ms to allow SIP register to complete")
    Handler(Looper.getMainLooper()).postDelayed({
        Log.d(TAG, "Showing full-screen incoming call UI after ${FULL_SCREEN_DELAY_MS}ms delay")
        val registered = VoipConnectionService.registerSelfManaged(this)
        Log.i(TAG, "VoipConnectionService.registerSelfManaged() returned: $registered")
        // ...
    }, FULL_SCREEN_DELAY_MS)
}
```

## 📊 Chronologie du flux corrigé

```
1. FCM push arrive
   ↓
2. registerSipInBackground() lance un Thread avec WakeLock
   ├─ Initialise PJSIP engine
   ├─ S'enregistre auprès du serveur SIP
   └─ Attend 2s pour confirmation
   ↓
3. En parallèle, attendre FULL_SCREEN_DELAY_MS (2500ms)
   ↓
4. Afficher l'UI de l'appel entrant
   ├─ VoipConnectionService.registerSelfManaged()
   └─ Démarrer incoming call
   ↓
5. Relâcher le WakeLock
```

## 🔍 Points de contrôle à vérifier

### Logcat logs à observer

```
# Vérifier que le WakeLock est bien acquis et relâché
I/VoipFirebaseService: WakeLock acquired for SIP registration from push
I/VoipFirebaseService: SIP register triggered from push: true
I/VoipFirebaseService: Waited 2s for SIP registration to settle
I/VoipFirebaseService: WakeLock released after SIP registration attempt

# Vérifier le timing de l'UI
D/VoipFirebaseService: Showing full-screen incoming call UI after 2500ms delay

# Vérifier la connexion
I/VoipFirebaseService: VoipConnectionService.registerSelfManaged() returned: true
```

## ⚙️ Recommandations supplémentaires

### 1. **Tester sur des devices variés**
- Tester avec différents niveaux de batterie
- Tester avec Doze mode activé
- Tester en Bluetooth et WiFi

### 2. **Ajouter un mécanisme de reregistration**
Si les appels continuent à échouer, ajouter dans `MainActivity.didChangeAppLifecycleState()`:
```kotlin
override fun didChangeAppLifecycleState(AppLifecycleState state) {
  if (state == AppLifecycleState.resumed) {
    FcmTokenSync.instance.syncCachedToken();
    voipEngine.registerProvisioned();  // ← Déjà présent
    // Ajouter reregistration après 30s si besoin
  }
}
```

### 3. **Monitoring de la registration SIP**
Écouter l'événement `RegistrationEvent` en Dart:
```dart
VoipEvents.stream.listen((event) {
  if (event is RegistrationEvent) {
    debugPrint('SIP Registration: ${event.statusText}');
  }
});
```

### 4. **Vérifier les permissions Android**
S'assurer que les permissions suivantes sont accordées:
- ✅ `INTERNET`
- ✅ `WAKE_LOCK`
- ✅ `FOREGROUND_SERVICE`
- ✅ `FOREGROUND_SERVICE_PHONE_CALL`
- ✅ `MANAGE_OWN_CALLS`
- ✅ `USE_FULL_SCREEN_INTENT`
- ✅ `POST_NOTIFICATIONS`

## 📝 Notes de déploiement

1. **Testez d'abord en debug**
   ```bash
   flutter run -v
   ```

2. **Vérifiez les logs lors d'un push entrant**
   ```bash
   adb logcat | grep VoipFirebaseService
   ```

3. **Testez avec différents délais de réseau**
   - Sur une connexion lente
   - Sur WiFi depuis l'écran verrouillé

## 🐛 Debugging avancé

Si les appels ne déclenchent pas toujours:

1. **Vérifier l'initialisation PJSIP au démarrage**
   ```
   I/VoipEngine: PJSIP init succeeded
   ```

2. **Vérifier les logs de provisioning**
   ```bash
   adb logcat | grep ProvisioningManager
   ```

3. **Vérifier l'état du ConnectionService**
   ```bash
   adb shell telecom get-phone-account-handle
   ```

4. **Tester FCM localement** avec Firebase Console
   - Vérifier que le device reçoit bien les messages
   - Tester avec l'app en background
   - Tester avec l'app fermée (tuée)
