# Configuration Tunable - Firebase Push + SIP Registration

## 🎛️ Constantes ajustables

### Fichier : `VoipFirebaseService.kt`

#### Délai avant affichage de l'UI

**Emplacement** : Line ~207
```kotlin
private const val FULL_SCREEN_DELAY_MS = 2500L
```

**Valeurs recommandées** :
- `2500L` : Défaut (bon pour plupart des devices)
- `3500L` : Si tests montrent des échecss fréquents
- `1500L` : Pour devices très rapides (testez d'abord)

**À ajuster si** :
- ✗ L'UI n'apparaît jamais → augmenter
- ✗ L'appel crée mais n'est pas prêt → augmenter
- ✓ L'UI apparaît rapidement et fonctionne → ne toucher pas

---

### Fichier : `registerSipInBackground()`

#### Sleep d'attente d'enregistrement SIP

**Emplacement** : Dans la fonction `registerSipInBackground()`
```kotlin
Thread.sleep(2000)  // Attendre 2 secondes
```

**Valeurs recommandées** :
- `2000L` : Défaut (bon pour plupart des cas)
- `3000L` : Si NATé ou proxy lent
- `1500L` : Pour connections très rapides (testez)

**À ajuster si** :
- ✗ L'enregistrement SIP timeout → augmenter
- ✗ L'enregistrement prend trop longtemps → augmenter légèrement le délai principal

**Important** : Ce délai + FULL_SCREEN_DELAY_MS = temps total avant UI
- Temps total = max(2000ms d'enregistrement, 2500ms de délai)
- = 2500ms théoriquement

---

## 🔄 Configuration avancée

### Ajouter un timeout configurable

Pour permettre un timeout dynamique, ajouter dans `VoipFirebaseService.kt` :

```kotlin
companion object {
    private const val FULL_SCREEN_DELAY_MS = 2500L
    private const val SIP_REGISTRATION_TIMEOUT_MS = 2000L
    
    // Nouvelles constantes configurables
    private const val SIP_REGISTRATION_RETRY_COUNT = 1
    private const val SIP_REGISTRATION_RETRY_DELAY_MS = 500L
}

private fun registerSipInBackground() {
    val wakeLockManager = WakeLockManager(applicationContext)
    Thread {
        wakeLockManager.acquire()
        var registrationAttempts = 0
        var success = false
        
        while (registrationAttempts < SIP_REGISTRATION_RETRY_COUNT && !success) {
            registrationAttempts++
            Log.i(TAG, "SIP registration attempt $registrationAttempts of $SIP_REGISTRATION_RETRY_COUNT")
            
            try {
                val manager = ProvisioningManager(applicationContext)
                val username = manager.getSipUsername()
                val password = manager.getSipPassword()
                val domain = manager.getSipDomain()
                val proxy = manager.getSipProxy() ?: ""
                
                if (username.isNullOrBlank() || password.isNullOrBlank() || domain.isNullOrBlank()) {
                    Log.w(TAG, "Skipping SIP register: missing provisioning data")
                    break
                }
                
                if (!PjsipEngine.instance.init()) {
                    Log.w(TAG, "Failed to initialize PJSIP engine")
                    if (registrationAttempts < SIP_REGISTRATION_RETRY_COUNT) {
                        Thread.sleep(SIP_REGISTRATION_RETRY_DELAY_MS)
                        continue
                    } else {
                        break
                    }
                }
                
                success = PjsipEngine.instance.register(username, password, domain, proxy)
                Log.i(TAG, "SIP register attempt $registrationAttempts: $success")
                
                if (success) {
                    Thread.sleep(SIP_REGISTRATION_TIMEOUT_MS)
                    Log.i(TAG, "SIP registration succeeded after $registrationAttempts attempt(s)")
                } else if (registrationAttempts < SIP_REGISTRATION_RETRY_COUNT) {
                    Thread.sleep(SIP_REGISTRATION_RETRY_DELAY_MS)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during SIP registration attempt $registrationAttempts", e)
                if (registrationAttempts < SIP_REGISTRATION_RETRY_COUNT) {
                    Thread.sleep(SIP_REGISTRATION_RETRY_DELAY_MS)
                }
            }
        }
        
        if (!success) {
            Log.w(TAG, "SIP registration failed after $registrationAttempts attempt(s)")
        }
        
        wakeLockManager.release()
        Log.i(TAG, "WakeLock released after SIP registration (success=$success)")
    }.start()
}
```

**Constantes à ajuster** :
```kotlin
SIP_REGISTRATION_RETRY_COUNT = 1        // Nombre de tentatives
SIP_REGISTRATION_RETRY_DELAY_MS = 500L  // Délai entre tentatives
SIP_REGISTRATION_TIMEOUT_MS = 2000L     // Timeout d'attente
```

---

## 🔐 Vérification des permissions (Android 12+)

Ajouter dans `MainActivity.kt` si non présent :

```kotlin
private fun requestStartupPermissionsIfNeeded() {
    val permissions = arrayOf(
        android.Manifest.permission.INTERNET,
        android.Manifest.permission.WAKE_LOCK,
        android.Manifest.permission.FOREGROUND_SERVICE,
        android.Manifest.permission.FOREGROUND_SERVICE_PHONE_CALL,
        android.Manifest.permission.MANAGE_OWN_CALLS,
        android.Manifest.permission.USE_FULL_SCREEN_INTENT,
    )
    
    val missingPermissions = permissions.filter {
        !PermissionChecker.checkSelfPermission(this, it)
    }
    
    if (missingPermissions.isNotEmpty()) {
        Log.w(TAG, "Missing permissions: $missingPermissions")
        ActivityCompat.requestPermissions(
            this, 
            missingPermissions.toTypedArray(), 
            REQ_PERMISSIONS
        )
    }
}
```

---

## 📊 Profiling

### Pour mesurer le temps d'enregistrement SIP

Ajouter un timestamp :

```kotlin
private fun registerSipInBackground() {
    val startTime = System.currentTimeMillis()
    val wakeLockManager = WakeLockManager(applicationContext)
    Thread {
        wakeLockManager.acquire()
        try {
            // ... code d'enregistrement ...
            val ok = PjsipEngine.instance.register(username, password, domain, proxy)
            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "SIP registration completed in ${duration}ms: $ok")
        } finally {
            wakeLockManager.release()
        }
    }.start()
}
```

Cela permettra de voir dans les logs :
```
I/VoipFirebaseService: SIP registration completed in 1234ms: true
```

---

## 📈 Recommandations par scénario

### Scenario 1 : Device SIP simple (home)
```kotlin
FULL_SCREEN_DELAY_MS = 2000L
SIP_REGISTRATION_TIMEOUT_MS = 1500L
```

### Scenario 2 : Device NAT/Proxy complexe
```kotlin
FULL_SCREEN_DELAY_MS = 3500L
SIP_REGISTRATION_TIMEOUT_MS = 3000L
SIP_REGISTRATION_RETRY_COUNT = 2
```

### Scenario 3 : Réseau mobile (3G/4G)
```kotlin
FULL_SCREEN_DELAY_MS = 4000L
SIP_REGISTRATION_TIMEOUT_MS = 3500L
SIP_REGISTRATION_RETRY_COUNT = 2
SIP_REGISTRATION_RETRY_DELAY_MS = 1000L
```

### Scenario 4 : Edge case - très lent
```kotlin
FULL_SCREEN_DELAY_MS = 5000L
SIP_REGISTRATION_TIMEOUT_MS = 4000L
SIP_REGISTRATION_RETRY_COUNT = 3
SIP_REGISTRATION_RETRY_DELAY_MS = 2000L
```

---

## ✅ Checklist de déploiement

Avant de déployer :

- [ ] Tester avec `FULL_SCREEN_DELAY_MS = 2500L` (défaut)
- [ ] Tester avec device verrouillé
- [ ] Tester avec Doze Mode activé
- [ ] Tester avec 4G et WiFi
- [ ] Vérifier les logs de timing
- [ ] Si < 80% de succès : augmenter délai de 500ms
- [ ] Mesurer temps moyen d'enregistrement SIP
- [ ] Ajuster basé sur mesures réelles

---

## 🔗 Références

- [Android WakeLock Documentation](https://developer.android.com/training/scheduling/wakelock)
- [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging)
- [Android Telecom Framework](https://developer.android.com/reference/android/telecom/ConnectionService)
- [PJSIP Documentation](https://pjsip.org/)
