
cmake_minimum_required(VERSION 3.22.1)
project(voip_engine)
set(CMAKE_VERBOSE_MAKEFILE ON)
set(CMAKE_MESSAGE_LOG_LEVEL VERBOSE)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Headers expected under android/pjsip/pjproject-<version>/
include_directories(
    ${CMAKE_SOURCE_DIR}/../../../../pjsip/pjproject-2.14.1/pjsip/include
    ${CMAKE_SOURCE_DIR}/../../../../pjsip/pjproject-2.14.1/pjlib/include
    ${CMAKE_SOURCE_DIR}/../../../../pjsip/pjproject-2.14.1/pjlib-util/include
    ${CMAKE_SOURCE_DIR}/../../../../pjsip/pjproject-2.14.1/pjmedia/include
    ${CMAKE_SOURCE_DIR}/../../../../pjsip/pjproject-2.14.1/pjnath/include
)

add_library(voip_engine SHARED voip_engine.cpp)

# PJSIP prebuilt libraries expected in app/src/main/jniLibs/<abi>/
set(PJSIP_LIB_DIR ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# Prefer shared libpjsua2 if present; fall back to static archive.
set(PJSUA2_SO ${PJSIP_LIB_DIR}/libpjsua2.so)
set(PJSUA2_A ${PJSIP_LIB_DIR}/libpjsua2.a)
if(EXISTS ${PJSUA2_SO})
    add_library(pjsua2 SHARED IMPORTED)
    set_target_properties(pjsua2 PROPERTIES IMPORTED_LOCATION ${PJSUA2_SO})
elseif(EXISTS ${PJSUA2_A})
    add_library(pjsua2 STATIC IMPORTED)
    set_target_properties(pjsua2 PROPERTIES IMPORTED_LOCATION ${PJSUA2_A})
else()
    message(FATAL_ERROR "libpjsua2 not found in ${PJSIP_LIB_DIR} (expected .so or .a)")
endif()

if(EXISTS ${PJSIP_LIB_DIR}/libpjsip-ua.so)
    add_library(pjsipua SHARED IMPORTED)
    set_target_properties(pjsipua PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjsip-ua.so)
elseif(EXISTS ${PJSIP_LIB_DIR}/libpjsip-ua.a)
    add_library(pjsipua STATIC IMPORTED)
    set_target_properties(pjsipua PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjsip-ua.a)
else()
    message(FATAL_ERROR "libpjsip-ua not found in ${PJSIP_LIB_DIR}")
endif()

if(EXISTS ${PJSIP_LIB_DIR}/libpjsip-simple.so)
    add_library(pjsipsimple SHARED IMPORTED)
    set_target_properties(pjsipsimple PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjsip-simple.so)
elseif(EXISTS ${PJSIP_LIB_DIR}/libpjsip-simple.a)
    add_library(pjsipsimple STATIC IMPORTED)
    set_target_properties(pjsipsimple PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjsip-simple.a)
else()
    message(FATAL_ERROR "libpjsip-simple not found in ${PJSIP_LIB_DIR}")
endif()

if(EXISTS ${PJSIP_LIB_DIR}/libpjsip.so)
    add_library(pjsip SHARED IMPORTED)
    set_target_properties(pjsip PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjsip.so)
elseif(EXISTS ${PJSIP_LIB_DIR}/libpjsip.a)
    add_library(pjsip STATIC IMPORTED)
    set_target_properties(pjsip PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjsip.a)
else()
    message(FATAL_ERROR "libpjsip not found in ${PJSIP_LIB_DIR}")
endif()

if(EXISTS ${PJSIP_LIB_DIR}/libpjsua.so)
    add_library(pjsua SHARED IMPORTED)
    set_target_properties(pjsua PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjsua.so)
if(EXISTS ${PJSIP_LIB_DIR}/libpjsip.so)
    add_library(pjsip SHARED IMPORTED)
    set_target_properties(pjsip PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjsip.so)
elseif(EXISTS ${PJSIP_LIB_DIR}/libpjsip.a)
    add_library(pjsip STATIC IMPORTED)
    set_target_properties(pjsip PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjsip.a)
else()
    message(FATAL_ERROR "libpjsip not found in ${PJSIP_LIB_DIR}")
endif()
    set_target_properties(pjmedia PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjmedia.so)
elseif(EXISTS ${PJSIP_LIB_DIR}/libpjmedia.a)
    add_library(pjmedia STATIC IMPORTED)
    set_target_properties(pjmedia PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjmedia.a)
else()
    message(FATAL_ERROR "libpjmedia not found in ${PJSIP_LIB_DIR}")
endif()

if(EXISTS ${PJSIP_LIB_DIR}/libpjmedia-codec.so)
    add_library(pjmediacodec SHARED IMPORTED)
    set_target_properties(pjmediacodec PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjmedia-codec.so)
if(EXISTS ${PJSIP_LIB_DIR}/libpj.so)
    add_library(pj SHARED IMPORTED)
    set_target_properties(pj PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpj.so)
elseif(EXISTS ${PJSIP_LIB_DIR}/libpj.a)
    add_library(pj STATIC IMPORTED)
    set_target_properties(pj PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpj.a)
else()
    message(FATAL_ERROR "libpj not found in ${PJSIP_LIB_DIR}")
endif()
    set_target_properties(pjmediaaudiodev PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjmedia-audiodev.so)
elseif(EXISTS ${PJSIP_LIB_DIR}/libpjmedia-audiodev.a)
    add_library(pjmediaaudiodev STATIC IMPORTED)
    set_target_properties(pjmediaaudiodev PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjmedia-audiodev.a)
else()
    message(FATAL_ERROR "libpjmedia-audiodev not found in ${PJSIP_LIB_DIR}")
endif()

if(EXISTS ${PJSIP_LIB_DIR}/libpjnath.so)
    add_library(pjnath SHARED IMPORTED)
    set_target_properties(pjnath PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjnath.so)
elseif(EXISTS ${PJSIP_LIB_DIR}/libpjnath.a)
    add_library(pjnath STATIC IMPORTED)
    set_target_properties(pjnath PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjnath.a)
else()
    message(FATAL_ERROR "libpjnath not found in ${PJSIP_LIB_DIR}")
endif()

if(EXISTS ${PJSIP_LIB_DIR}/libpjlib-util.so)
    add_library(pjlibutil SHARED IMPORTED)
    set_target_properties(pjlibutil PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjlib-util.so)
elseif(EXISTS ${PJSIP_LIB_DIR}/libpjlib-util.a)
    add_library(pjlibutil STATIC IMPORTED)
    set_target_properties(pjlibutil PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpjlib-util.a)
else()
    message(FATAL_ERROR "libpjlib-util not found in ${PJSIP_LIB_DIR}")
endif()

if(EXISTS ${PJSIP_LIB_DIR}/libpj.so)
    add_library(pj SHARED IMPORTED)
    set_target_properties(pj PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpj.so)
elseif(EXISTS ${PJSIP_LIB_DIR}/libpj.a)
    add_library(pj STATIC IMPORTED)
    set_target_properties(pj PROPERTIES IMPORTED_LOCATION ${PJSIP_LIB_DIR}/libpj.a)
else()
    message(FATAL_ERROR "libpj not found in ${PJSIP_LIB_DIR}")
endif()

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    voip_engine
    pjsua2
    pjsua
    pjsipua
    pjsipsimple
    pjsip
    pjmedia
    pjmediacodec
    pjmediaaudiodev
    pjnath
    pjlibutil
    pj
    ${android-lib}
    ${log-lib}
)
