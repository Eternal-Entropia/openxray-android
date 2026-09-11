include_guard()

message(STATUS "OpenXRay Android: Configuring dependencies for ABI ${ANDROID_ABI}, API ${ANDROID_PLATFORM}")

# Use standard memory allocator on Android
set(MEMORY_ALLOCATOR "standard" CACHE STRING "Use specific memory allocator" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "Build shared libraries on Android" FORCE)

# Short base directory to avoid Windows MAX_PATH (260 chars) limitation in Ninja
set(FETCHCONTENT_BASE_DIR "C:/oxr_deps" CACHE PATH "Short directory to avoid Windows MAX_PATH" FORCE)

include(FetchContent)
set(FETCHCONTENT_QUIET ON)

# 1. libogg
FetchContent_Declare(
    ogg
    GIT_REPOSITORY https://github.com/xiph/ogg.git
    GIT_TAG v1.3.5
)
FetchContent_MakeAvailable(ogg)
if (TARGET ogg AND NOT TARGET Ogg::Ogg)
    add_library(Ogg::Ogg ALIAS ogg)
endif()

# 2. libvorbis
FetchContent_Declare(
    vorbis
    GIT_REPOSITORY https://github.com/xiph/vorbis.git
    GIT_TAG v1.3.7
)
FetchContent_MakeAvailable(vorbis)
if (TARGET vorbis AND NOT TARGET Vorbis::Vorbis)
    add_library(Vorbis::Vorbis ALIAS vorbis)
endif()
if (TARGET vorbisfile AND NOT TARGET Vorbis::VorbisFile)
    add_library(Vorbis::VorbisFile ALIAS vorbisfile)
endif()
if (TARGET vorbisenc AND NOT TARGET Vorbis::VorbisEnc)
    add_library(Vorbis::VorbisEnc ALIAS vorbisenc)
endif()

# 3. LZO
FetchContent_Declare(
    lzo
    URL https://www.oberhumer.com/opensource/lzo/download/lzo-2.10.tar.gz
)
FetchContent_MakeAvailable(lzo)
if (TARGET lzo_static_lib AND NOT TARGET LZO::LZO)
    add_library(LZO::LZO ALIAS lzo_static_lib)
elseif (TARGET lzo_shared_lib AND NOT TARGET LZO::LZO)
    add_library(LZO::LZO ALIAS lzo_shared_lib)
elseif (TARGET lzo AND NOT TARGET LZO::LZO)
    add_library(LZO::LZO ALIAS lzo)
endif()

# Theora
if (NOT TARGET Theora::Theora)
    set(_theora_src_dir "${CMAKE_CURRENT_LIST_DIR}/android/theora")
    add_library(theora STATIC
        "${_theora_src_dir}/apiwrapper.c"
        "${_theora_src_dir}/bitpack.c"
        "${_theora_src_dir}/decapiwrapper.c"
        "${_theora_src_dir}/decinfo.c"
        "${_theora_src_dir}/decode.c"
        "${_theora_src_dir}/dequant.c"
        "${_theora_src_dir}/fragment.c"
        "${_theora_src_dir}/huffdec.c"
        "${_theora_src_dir}/idct.c"
        "${_theora_src_dir}/info.c"
        "${_theora_src_dir}/internal.c"
        "${_theora_src_dir}/quant.c"
        "${_theora_src_dir}/state.c"
    )
    set_target_properties(theora PROPERTIES POSITION_INDEPENDENT_CODE ON)
    target_include_directories(theora
        PUBLIC
            "${CMAKE_SOURCE_DIR}/sdk/include"
        PRIVATE
            "${_theora_src_dir}"
    )
    target_link_libraries(theora PUBLIC Ogg::Ogg)
    add_library(Theora::Theora ALIAS theora)
    if (NOT TARGET Theora::TheoraDec)
        add_library(Theora::TheoraDec ALIAS theora)
    endif()
endif()

# 4. OpenAL-Soft
set(ALSOFT_UTILS OFF CACHE BOOL "" FORCE)
set(ALSOFT_EXAMPLES OFF CACHE BOOL "" FORCE)
set(ALSOFT_TESTS OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    openal-soft
    GIT_REPOSITORY https://github.com/kcat/openal-soft.git
    GIT_TAG 1.23.1
)
FetchContent_MakeAvailable(openal-soft)
if (TARGET OpenAL AND NOT TARGET OpenAL::OpenAL)
    add_library(OpenAL::OpenAL ALIAS OpenAL)
endif()

# 5. SDL2
set(SDL_STATIC OFF CACHE BOOL "" FORCE)
set(SDL_SHARED ON CACHE BOOL "" FORCE)
set(SDL_TEST OFF CACHE BOOL "" FORCE)
FetchContent_Declare(
    SDL2
    GIT_REPOSITORY https://github.com/libsdl-org/SDL.git
    GIT_TAG release-2.30.12
)
FetchContent_MakeAvailable(SDL2)
if (TARGET SDL2 AND NOT TARGET SDL2::SDL2)
    add_library(SDL2::SDL2 ALIAS SDL2)
endif()

# Include directories needed across engine modules on Android
include_directories(
    SYSTEM
    ${CMAKE_SOURCE_DIR}/sdk/include
    ${CMAKE_SOURCE_DIR}/sdk/include/glad
    ${CMAKE_SOURCE_DIR}/sdk/include/KHR
)
