# Guide de Test - Firebase Push + Enregistrement SIP

## 🧪 Tests à effectuer

### Test 1 : Vérifier les logs lors d'une notification entrante

**Objectif** : Vérifier que le WakeLock est bien acquis et que l'enregistrement SIP se complète

**Étapes** :
1. Démarrer le monitoring des logs :
   ```bash
   adb logcat -c && adb logcat | grep -E "VoipFirebaseService|PjsipEngine|WakeLockManager"
   ```

2. Verrouiller l'écran du device

3. Envoyer une notification FCM via Firebase Console :
   - Aller à Firebase Console → Project Settings
   - Sélectionner l'app Android
   - Envoyer un message test avec :
     ```json
     {
       "type": "incoming_call",
       "callId": "test-123",
       "callerId": "TestCaller"
     }
     ```

4. Observez les logs :
   ```
   I/VoipFirebaseService: Data push received: type=incoming_call priority=2 callId=test-123 callerId=TestCaller
   I/VoipFirebaseService: Incoming call push received (callId=test-123, callerId=TestCaller)
   I/VoipFirebaseService: registerSipInBackground()
   D/WakeLockManager: CPU wake lock acquired
   D/WakeLockManager: Wi-Fi lock acquired
   D/PjsipEngine: nativeInit() called
   I/VoipFirebaseService: SIP register triggered from push: true
   I/VoipFirebaseService: Waited 2s for SIP registration to settle
   D/WakeLockManager: CPU wake lock released
   D/WakeLockManager: Wi-Fi lock released
   ```

---

### Test 2 : Vérifier le délai avant l'UI

**Objectif** : Confirmer que l'UI s'affiche après 2500ms et que la connexion SIP est prête

**Étapes** :
1. Noter le timestamp du log "Data push received"
2. Noter le timestamp du log "Showing full-screen incoming call UI"
3. La différence devrait être **≈2500ms**

**Logs attendus** :
```
I/VoipFirebaseService: Data push received: type=incoming_call priority=2 callId=test-123 callerId=TestCaller
I/VoipFirebaseService: Delaying incoming call UI by 2500ms to allow SIP register to complete
...
D/VoipFirebaseService: Showing full-screen incoming call UI after 2500ms delay
D/VoipFirebaseService: VoipConnectionService.registerSelfManaged() returned: true
D/VoipFirebaseService: Starting incoming call through ConnectionService for callId=test-123
```

---

### Test 3 : Tester avec différentes conditions de batterie

**Objectif** : Vérifier que le WakeLock maintient le device éveillé

**Étapes** :
1. Activer **Doze Mode** (sauf sur emulateur) :
   ```bash
   adb shell dumpsys deviceidle force-idle
   adb shell dumpsys deviceidle step
   ```

2. Envoyer une notification FCM

3. Vérifier les logs de WakeLock

4. Désactiver Doze :
   ```bash
   adb shell dumpsys deviceidle disable
   ```

---

### Test 4 : Tester avec une connexion réseau lente

**Objectif** : S'assurer que le délai de 2500ms est suffisant même avec une latence réseau

**Étapes** :
1. Utiliser Android Studio Network Simulator :
   - Profiler → Network → Simulate network conditions
   - Sélectionner "Slow 3G"

2. Envoyer une notification FCM

3. Vérifier que l'appel apparaît même avec la latence

**Note** : Si les appels n'apparaissent pas, augmenter `FULL_SCREEN_DELAY_MS` à 3500ms

---

### Test 5 : Tester avec l'app fermée

**Objectif** : Vérifier que le push réveille l'app et que l'enregistrement SIP se fait

**Étapes** :
1. Fermer l'app complètement (swipe dans recents)
2. Vérifier que l'app n'est pas en processus :
   ```bash
   adb shell ps | grep celyavox
   ```

3. Envoyer une notification FCM

4. Vérifier que l'UI apparaît

5. Vérifier les logs d'enregistrement SIP

---

## 📊 Checklist de debugging

- [ ] Vérifier que `WakeLockManager` logs s'affichent
- [ ] Vérifier que `PJSIP init succeeded` s'affiche lors du premier appel
- [ ] Vérifier que `SIP register triggered from push: true` s'affiche
- [ ] Vérifier que le délai entre push et UI est **~2500ms**
- [ ] Vérifier que ConnectionService s'initialise correctement
- [ ] Vérifier que les permissions sont accordées (via adb ou Settings)
- [ ] Vérifier que Firebase Cloud Messaging est configuré
- [ ] Vérifier que le device reçoit les tokens FCM

---

## 🔧 Commandes utiles de debugging

### Voir tous les logs relevants
```bash
adb logcat -c && adb logcat | grep -v "^D/" | grep -E "VoipFirebaseService|PjsipEngine|VoipConnectionService|WakeLockManager|ProvisioningManager"
```

### Voir uniquement les erreurs
```bash
adb logcat *:E | grep -E "VoipFirebaseService|PjsipEngine|celyavox"
```

### Envoyer un push test via adb
```bash
# Obtenir le FCM token d'abord
adb logcat | grep "FCM token"

# Puis utiliser Firebase Console pour envoyer à ce device
```

### Vérifier l'état du ConnectionService
```bash
adb shell telecom get-phone-account-handle
adb shell dumpsys telecom
```

### Vérifier les permissions
```bash
adb shell pm list permissions | grep celya
adb shell pm dump fr.celya.celyavox | grep Permission
```

### Vérifier l'état de la batterie en Doze
```bash
adb shell dumpsys deviceidle
adb shell dumpsys battery
```

### Vérifier si le moteur SIP est initialisé
```bash
adb logcat | grep "PJSIP"
```

---

## ⚠️ Problèmes connus et solutions

### Problème 1 : "WakeLock not acquired"
**Cause** : Permission `WAKE_LOCK` non accordée
**Solution** :
```bash
adb shell pm grant fr.celya.celyavox android.permission.WAKE_LOCK
```

### Problème 2 : "SIP register triggered from push: false"
**Cause** : PJSIP engine non initialisé ou credentials manquants
**Solution** :
```bash
# Vérifier le log complet d'initialisation
adb logcat | grep "PJSIP\|SipAccountManager"
# Vérifier que le provisioning est complété
adb logcat | grep "ProvisioningManager"
```

### Problème 3 : UI n'apparaît pas après 2500ms
**Cause** : ConnectionService non enregistré ou problème d'initialisation
**Solution** :
```bash
# Vérifier les logs ConnectionService
adb logcat | grep "VoipConnectionService"
# Vérifier les logs de Telecom
adb logcat | grep "Telecom"
# Vérifier la permission MANAGE_OWN_CALLS
adb shell pm grant fr.celya.celyavox android.permission.MANAGE_OWN_CALLS
```

### Problème 4 : Device s'endort pendant l'enregistrement
**Cause** : WakeLock libéré trop tôt
**Solution** : Augmenter le sleep dans `registerSipInBackground()` :
```kotlin
Thread.sleep(4000)  // Au lieu de 2000
```

---

## 📋 Rapport à générer

Après les tests, générer un rapport incluant :

```bash
# Exporter les logs
adb logcat > test_report_$(date +%s).log

# Exporter l'état du device
adb shell dumpsys > device_state_$(date +%s).txt

# Exporter les permissions
adb shell pm list permissions > permissions_$(date +%s).txt
```

Inclure dans le rapport :
- Timestamps des événements
- Logs complets de VoipFirebaseService
- État du ConnectionService
- Permissions accordées/refusées
- Observations sur le timing
