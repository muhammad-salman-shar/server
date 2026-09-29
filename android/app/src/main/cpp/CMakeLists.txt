cmake_minimum_required(VERSION 3.22.1)

project(samu_jni)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Android 15 16KB page size compliance
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")

# ARM64 optimizations
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O3 -march=armv8-a+crc")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O3 -march=armv8-a+crc")
endif()

# llama.cpp options (minimal, phone-friendly)
set(GGML_CPU_AARCH64 ON CACHE BOOL "" FORCE)
set(GGML_DOTPROD ON CACHE BOOL "" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "Disable OpenMP (no libomp.so packaging)" FORCE)
set(GGML_STATIC OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)

set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)

# llama.cpp submodule lives next to this file
add_subdirectory(llama.cpp)

# All llama targets need c++_shared on Android (NDK)
if(ANDROID)
    foreach(target ggml-base ggml ggml-cpu llama)
        if(TARGET ${target})
            target_link_libraries(${target} PRIVATE c++_shared)
        endif()
    endforeach()
endif()

add_library(samu_jni SHARED samu_jni.cpp)

target_link_libraries(samu_jni
    llama
    c++_shared
    android
    log
)

target_include_directories(samu_jni PRIVATE
    llama.cpp/include
    llama.cpp/ggml/include
)
