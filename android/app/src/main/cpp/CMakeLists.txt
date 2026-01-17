
cmake_minimum_required(VERSION 3.22.1)
project(voip_engine)
set(CMAKE_VERBOSE_MAKEFILE ON)
set(CMAKE_MESSAGE_LOG_LEVEL VERBOSE)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Define endianness for PJSIP
add_definitions(-DPJ_IS_LITTLE_ENDIAN=1 -DPJ_IS_BIG_ENDIAN=0)

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

# Add library search path
link_directories(${PJSIP_LIB_DIR})

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(
    voip_engine
    ${PJSIP_LIB_DIR}/libpjsua2.a
    ${PJSIP_LIB_DIR}/libpjsua.a
    ${PJSIP_LIB_DIR}/libpjsip-ua.a
    ${PJSIP_LIB_DIR}/libpjsip-simple.a
    ${PJSIP_LIB_DIR}/libpjsip.a
    ${PJSIP_LIB_DIR}/libpjmedia.a
    ${PJSIP_LIB_DIR}/libpjmedia-codec.a
    ${PJSIP_LIB_DIR}/libpjmedia-audiodev.a
    ${PJSIP_LIB_DIR}/libpjnath.a
    ${PJSIP_LIB_DIR}/libpjlib-util.a
    ${PJSIP_LIB_DIR}/libpj.a
    ${android-lib}
    ${log-lib}
)
