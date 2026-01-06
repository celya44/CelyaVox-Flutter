# Native libraries

Place the built PJSIP shared libraries here, e.g.:

```
android/app/src/main/jniLibs/
  ├── armeabi-v7a/
  │   ├── libpjsip.so
  │   ├── libpjsua2.so
  │   └── ...
  └── arm64-v8a/
      ├── libpjsip.so
      ├── libpjsua2.so
      └── ...
```

Run `android/pjsip/build_pjsip.sh` to produce the `.so` files for `armeabi-v7a` and `arm64-v8a`.
