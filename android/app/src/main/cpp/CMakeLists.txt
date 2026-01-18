
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

if(NOT EXISTS ${PJSIP_LIB_DIR})
    message(FATAL_ERROR "PJSIP libs directory not found: ${PJSIP_LIB_DIR}")
endif()

set(PJSIP_LIB_SUFFIXES ".so" ".a")

function(find_pjsip_lib out_var name)
    set(found "")
    foreach(suffix IN LISTS PJSIP_LIB_SUFFIXES)
        if(EXISTS "${PJSIP_LIB_DIR}/lib${name}${suffix}")
            set(found "${PJSIP_LIB_DIR}/lib${name}${suffix}")
            break()
        endif()
    endforeach()

    if(NOT found)
        message(FATAL_ERROR "PJSIP library not found: lib${name}.so|.a in ${PJSIP_LIB_DIR}")
    endif()

    add_library(${out_var} UNKNOWN IMPORTED)
    set_target_properties(${out_var} PROPERTIES IMPORTED_LOCATION "${found}")
endfunction()

# Import PJSIP libraries (shared or static)
find_pjsip_lib(PJ_LIB pj)
find_pjsip_lib(PJLIB_UTIL_LIB pjlib-util)
find_pjsip_lib(PJNATH_LIB pjnath)
find_pjsip_lib(PJMEDIA_AUDIODEV_LIB pjmedia-audiodev)
find_pjsip_lib(PJMEDIA_CODEC_LIB pjmedia-codec)
find_pjsip_lib(PJMEDIA_LIB pjmedia)
find_pjsip_lib(PJSIP_LIB pjsip)
find_pjsip_lib(PJSIP_SIMPLE_LIB pjsip-simple)
find_pjsip_lib(PJSIP_UA_LIB pjsip-ua)
find_pjsip_lib(PJSUA_LIB pjsua)
find_pjsip_lib(PJSUA2_LIB pjsua2)

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
