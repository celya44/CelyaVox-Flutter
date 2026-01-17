
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

# Find PJSIP libraries
find_library(PJ_LIB pj PATHS ${PJSIP_LIB_DIR} REQUIRED)
find_library(PJLIB_UTIL_LIB pjlib-util PATHS ${PJSIP_LIB_DIR} REQUIRED)
find_library(PJNATH_LIB pjnath PATHS ${PJSIP_LIB_DIR} REQUIRED)
find_library(PJMEDIA_AUDIODEV_LIB pjmedia-audiodev PATHS ${PJSIP_LIB_DIR} REQUIRED)
find_library(PJMEDIA_CODEC_LIB pjmedia-codec PATHS ${PJSIP_LIB_DIR} REQUIRED)
find_library(PJMEDIA_LIB pjmedia PATHS ${PJSIP_LIB_DIR} REQUIRED)
find_library(PJSIP_LIB pjsip PATHS ${PJSIP_LIB_DIR} REQUIRED)
find_library(PJSIP_SIMPLE_LIB pjsip-simple PATHS ${PJSIP_LIB_DIR} REQUIRED)
find_library(PJSIP_UA_LIB pjsip-ua PATHS ${PJSIP_LIB_DIR} REQUIRED)
find_library(PJSUA_LIB pjsua PATHS ${PJSIP_LIB_DIR} REQUIRED)
find_library(PJSUA2_LIB pjsua2 PATHS ${PJSIP_LIB_DIR} REQUIRED)

# Set linker flags to include the library path
set_target_properties(voip_engine PROPERTIES LINK_FLAGS "-L${PJSIP_LIB_DIR} -Wl,--start-group")

find_library(log-lib log)

target_link_libraries(
    voip_engine
    ${PJ_LIB}
    ${PJLIB_UTIL_LIB}
    ${PJNATH_LIB}
    ${PJMEDIA_AUDIODEV_LIB}
    ${PJMEDIA_CODEC_LIB}
    ${PJMEDIA_LIB}
    ${PJSIP_LIB}
    ${PJSIP_SIMPLE_LIB}
    ${PJSIP_UA_LIB}
    ${PJSUA_LIB}
    ${PJSUA2_LIB}
    -Wl,--end-group
    ${log-lib}
)
