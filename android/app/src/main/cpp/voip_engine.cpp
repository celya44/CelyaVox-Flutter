#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <string>

#include <pjlib.h>
#include <pjsip.h>
#include <pjsip_ua.h>
#include <pjsua-lib/pjsua.h>

#define LOG_TAG "PjsipNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static JavaVM *g_vm = nullptr;
static jclass g_engineClass = nullptr;
static std::mutex g_mutex;
static bool g_initialized = false;
static pjsua_acc_id g_acc_id = PJSUA_INVALID_ID;

// Forward declarations
static void emit_event(const char *type, const char *message);

static JNIEnv *attach_thread(bool *did_attach) {
    *did_attach = false;
    if (!g_vm) return nullptr;
    JNIEnv *env = nullptr;
    jint res = g_vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) == 0) {
            *did_attach = true;
        }
    }
    return env;
}

static void detach_thread(bool did_attach) {
    if (did_attach && g_vm) {
        g_vm->DetachCurrentThread();
    }
}

static void emit_event(const char *type, const char *message) {
    bool did_attach = false;
    JNIEnv *env = attach_thread(&did_attach);
    if (!env || !g_engineClass) return;

    jmethodID mid = env->GetStaticMethodID(g_engineClass, "handleNativeEvent", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (!mid) {
        LOGE("Failed to find handleNativeEvent");
        detach_thread(did_attach);
        return;
    }
    jstring jtype = env->NewStringUTF(type ? type : "");
    jstring jmsg = env->NewStringUTF(message ? message : "");
    env->CallStaticVoidMethod(g_engineClass, mid, jtype, jmsg);
    env->DeleteLocalRef(jtype);
    env->DeleteLocalRef(jmsg);
    detach_thread(did_attach);
}

static void on_incoming_call(pjsua_acc_id acc_id, pjsua_call_id call_id, pjsip_rx_data *rdata) {
    (void)acc_id;
    (void)rdata;
    char buf[32];
    pj_ansi_snprintf(buf, sizeof(buf), "%d", call_id);
    emit_event("incoming_call", buf);
    pjsua_call_setting opt;
    pjsua_call_setting_default(&opt);
    opt.aud_cnt = 1;
    opt.vid_cnt = 0;
    pjsua_call_answer(call_id, 180, nullptr, nullptr);
}

static void on_call_state(pjsua_call_id call_id, pjsip_event *e) {
    (void)e;
    pjsua_call_info ci;
    if (pjsua_call_get_info(call_id, &ci) != PJ_SUCCESS) return;
    if (ci.state == PJSIP_INV_STATE_CONFIRMED) {
        emit_event("call_connected", std::to_string(call_id).c_str());
    } else if (ci.state == PJSIP_INV_STATE_DISCONNECTED) {
        emit_event("call_ended", std::to_string(call_id).c_str());
    }
}

static void on_reg_state(pjsua_acc_id acc_id) {
    pjsua_acc_info info;
    if (pjsua_acc_get_info(acc_id, &info) != PJ_SUCCESS) return;
    emit_event("registration", info.status_text.ptr ? info.status_text.ptr : "");
}

static bool ensure_endpoint() {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_initialized) return true;

    pj_status_t status = pjsua_create();
    if (status != PJ_SUCCESS) {
        LOGE("pjsua_create failed");
        return false;
    }

    pjsua_config ua_cfg;
    pjsua_config_default(&ua_cfg);
    ua_cfg.cb.on_incoming_call = &on_incoming_call;
    ua_cfg.cb.on_call_state = &on_call_state;
    ua_cfg.cb.on_reg_state = &on_reg_state;

    pjsua_logging_config log_cfg;
    pjsua_logging_config_default(&log_cfg);
    log_cfg.console_level = 4;

    pjsua_media_config media_cfg;
    pjsua_media_config_default(&media_cfg);
    media_cfg.has_ioqueue = PJ_TRUE;
    media_cfg.clock_rate = 16000;
    media_cfg.snd_clock_rate = 16000;
    media_cfg.vid_preview_enable = PJ_FALSE;
    media_cfg.enable_ice = PJ_FALSE;

    status = pjsua_init(&ua_cfg, &log_cfg, &media_cfg);
    if (status != PJ_SUCCESS) {
        LOGE("pjsua_init failed: %d", status);
        pjsua_destroy();
        return false;
    }

    pjsua_transport_config trans_cfg;
    pjsua_transport_config_default(&trans_cfg);
    trans_cfg.port = 5060;
    status = pjsua_transport_create(PJSIP_TRANSPORT_UDP, &trans_cfg, nullptr);
    if (status != PJ_SUCCESS) {
        LOGE("transport create failed: %d", status);
        pjsua_destroy();
        return false;
    }

    status = pjsua_start();
    if (status != PJ_SUCCESS) {
        LOGE("pjsua_start failed: %d", status);
        pjsua_destroy();
        return false;
    }

    g_initialized = true;
    LOGI("PJSIP initialized");
    return true;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_fr_celya_celyavox_PjsipEngine_nativeInit(JNIEnv *env, jclass clazz) {
    g_engineClass = static_cast<jclass>(env->NewGlobalRef(clazz));
    return ensure_endpoint() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_fr_celya_celyavox_PjsipEngine_nativeRegister(JNIEnv *env, jclass, jstring juser, jstring jpass, jstring jdomain, jstring jproxy) {
    if (!ensure_endpoint()) return JNI_FALSE;

    const char *user = env->GetStringUTFChars(juser, nullptr);
    const char *pass = env->GetStringUTFChars(jpass, nullptr);
    const char *domain = env->GetStringUTFChars(jdomain, nullptr);
    const char *proxy = env->GetStringUTFChars(jproxy, nullptr);

    std::lock_guard<std::mutex> lock(g_mutex);

    if (g_acc_id != PJSUA_INVALID_ID) {
        pjsua_acc_del(g_acc_id);
        g_acc_id = PJSUA_INVALID_ID;
    }

    pjsua_acc_config acc_cfg;
    pjsua_acc_config_default(&acc_cfg);

    std::string id = "sip:" + std::string(user) + "@" + std::string(domain);
    std::string reg_uri = "sip:" + std::string(domain);

    acc_cfg.id = pj_str(const_cast<char *>(id.c_str()));
    acc_cfg.reg_uri = pj_str(const_cast<char *>(reg_uri.c_str()));
    acc_cfg.cred_count = 1;
    acc_cfg.cred_info[0].realm = pj_str(const_cast<char *>("*"));
    acc_cfg.cred_info[0].scheme = pj_str(const_cast<char *>("digest"));
    acc_cfg.cred_info[0].username = pj_str(const_cast<char *>(user));
    acc_cfg.cred_info[0].data_type = PJSIP_CRED_DATA_PLAIN_PASSWD;
    acc_cfg.cred_info[0].data = pj_str(const_cast<char *>(pass));

    if (proxy && std::string(proxy).length() > 0) {
        static pj_str_t proxies[1];
        proxies[0] = pj_str(const_cast<char *>(proxy));
        acc_cfg.proxy_cnt = 1;
        acc_cfg.proxy = proxies;
    }

    pj_status_t status = pjsua_acc_add(&acc_cfg, PJ_TRUE, &g_acc_id);

    env->ReleaseStringUTFChars(juser, user);
    env->ReleaseStringUTFChars(jpass, pass);
    env->ReleaseStringUTFChars(jdomain, domain);
    env->ReleaseStringUTFChars(jproxy, proxy);

    if (status != PJ_SUCCESS) {
        LOGE("Account add failed: %d", status);
        return JNI_FALSE;
    }
    LOGI("Registered account id=%d", g_acc_id);
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_fr_celya_celyavox_PjsipEngine_nativeUnregister(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_acc_id != PJSUA_INVALID_ID) {
        pjsua_acc_del(g_acc_id);
        g_acc_id = PJSUA_INVALID_ID;
        LOGI("Account unregistered");
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_fr_celya_celyavox_PjsipEngine_nativeMakeCall(JNIEnv *env, jclass, jstring jnumber) {
    if (!ensure_endpoint() || g_acc_id == PJSUA_INVALID_ID) return JNI_FALSE;
    const char *number = env->GetStringUTFChars(jnumber, nullptr);
    std::string dest = "sip:" + std::string(number);
    pjsua_call_id call_id = PJSUA_INVALID_ID;
    pj_status_t status = pjsua_call_make_call(g_acc_id, pj_str(const_cast<char *>(dest.c_str())), 0, nullptr, nullptr, &call_id);
    env->ReleaseStringUTFChars(jnumber, number);
    if (status != PJ_SUCCESS) {
        LOGE("make_call failed: %d", status);
        return JNI_FALSE;
    }
    LOGI("Calling %s (id=%d)", dest.c_str(), call_id);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_fr_celya_celyavox_PjsipEngine_nativeAcceptCall(JNIEnv *env, jclass, jstring jcallId) {
    const char *cid = env->GetStringUTFChars(jcallId, nullptr);
    int call_id = atoi(cid);
    env->ReleaseStringUTFChars(jcallId, cid);
    pj_status_t status = pjsua_call_answer(call_id, 200, nullptr, nullptr);
    if (status != PJ_SUCCESS) {
        LOGE("accept call failed: %d", status);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_fr_celya_celyavox_PjsipEngine_nativeHangupCall(JNIEnv *env, jclass, jstring jcallId) {
    const char *cid = env->GetStringUTFChars(jcallId, nullptr);
    int call_id = atoi(cid);
    env->ReleaseStringUTFChars(jcallId, cid);
    pj_status_t status = pjsua_call_hangup(call_id, 0, nullptr, nullptr);
    if (status != PJ_SUCCESS) {
        LOGE("hangup failed: %d", status);
        return JNI_FALSE;
    }
    return JNI_TRUE;
}

jint JNI_OnLoad(JavaVM *vm, void *) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}
