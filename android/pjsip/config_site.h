#ifndef PJ_CONFIG_SITE_H
#define PJ_CONFIG_SITE_H

/* Define endianness for Android */
#define PJ_IS_LITTLE_ENDIAN 1
#define PJ_IS_BIG_ENDIAN 0

/* Disable video stack entirely */
#define PJMEDIA_HAS_VIDEO                 0
#define PJMEDIA_VIDEO_DEV_HAS_OPENGL      0
#define PJMEDIA_VIDEO_DEV_HAS_DSHOW       0
#define PJMEDIA_VIDEO_DEV_HAS_V4L2        0

/* Enable required audio codecs */
#define PJMEDIA_HAS_G711_CODEC            1
/* Disable Opus to avoid missing opus headers in CI; add back when libopus is provisioned. */
#define PJMEDIA_HAS_OPUS_CODEC            0

/* Disable unused codecs for a lean mobile build */
#define PJMEDIA_HAS_G722_CODEC            0
#define PJMEDIA_HAS_L16_CODEC             0
#define PJMEDIA_HAS_SPEEX_CODEC           0
#define PJMEDIA_HAS_ILBC_CODEC            0
#define PJMEDIA_HAS_SILK_CODEC            0

/* Audio backend: prefer OpenSL ES on Android */
#define PJMEDIA_AUDIO_DEV_HAS_OPENSL      1
#define PJMEDIA_AUDIO_DEV_HAS_ANDROID_JNI 0
#define PJMEDIA_AUDIO_DEV_HAS_PORTAUDIO   0

/* Networking: UDP only */
#define PJSIP_HAS_TCP                     0
#define PJSIP_HAS_TLS_TRANSPORT           0
#define PJSIP_HAS_UDP                     1

/* Security / SRTP left out intentionally for minimal footprint */
#define PJMEDIA_HAS_SRTP                  0

/* Optimize for mobile VoIP */
#define PJ_ENABLE_EXTRA_CHECK             0
#define PJ_LOG_MAX_LEVEL                  3
#define PJMEDIA_ECHO_SUPPRESSOR           1
#define PJMEDIA_ECHO_USE_SIMPLE_FILTER    1
#define PJMEDIA_SOUND_BUFFER_COUNT        4

/* Avoid duplicate JNI_OnLoad (PJSIP provides its own when enabled) */
#define PJ_ANDROID_JNI                     0

#endif /* PJ_CONFIG_SITE_H */
