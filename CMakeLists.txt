cmake_minimum_required(VERSION 3.22)

list(APPEND CMAKE_MODULE_PATH "${CMAKE_CURRENT_SOURCE_DIR}/cmake")
include(PreProjectInit)

calculate_xray_build_id(XRAY_BUILD_ID)
project(OpenXRay
    DESCRIPTION "OpenXRay is an improved version of the X-Ray Engine, \
                the game engine used in the world-famous S.T.A.L.K.E.R. game series by GSC Game World."
    VERSION 1.6.02.${XRAY_BUILD_ID}
    HOMEPAGE_URL "https://github.com/OpenXRay/xray-16"
    LANGUAGES CXX C
)

option(BUILD_SHARED_LIBS "Build shared libraries"     ${BUILD_SHARED_LIBS_DEFAULT_VALUE})

option(XRAY_USE_LUAJIT   "Use LuaJIT"                 ON)
option(XRAY_USE_ASAN     "Use AddressSanitizer"       OFF)
option(XRAY_ENABLE_TRACY "Enable tracy profiler"      OFF)
option(XRAY_ENABLE_GL_ERROR_CHECK "Check every GL call with glGetError (very slow on mobile GPUs)" OFF)
option(XRAY_USE_GLES     "Use OpenGL ES (3.2) renderer backend" OFF)

include(XRay.Build)
include(XRay.Packaging)

if (ANDROID AND CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a")
    # 16 KB page alignment is required for arm64 devices with big pages;
    # 32-bit ABI targets use the default (4 KiB) page alignment.
    string(APPEND CMAKE_SHARED_LINKER_FLAGS " -Wl,-z,max-page-size=16384")
    string(APPEND CMAKE_EXE_LINKER_FLAGS " -Wl,-z,max-page-size=16384")
endif()

add_subdirectory(Externals)
add_subdirectory(src)
if (EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/res/CMakeLists.txt")
    add_subdirectory(res)
endif()
if (NOT ANDROID AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/misc/CMakeLists.txt")
    add_subdirectory(misc)
endif()

if (ANDROID)
    set(_all_oxr_targets
        xrCore xrAPI xrEngine xrRender_GL xrGame xrPhysics
        xrUICore xrScriptEngine xrSound xrAICore xrCDB
        xrLuaJIT xrLuabind xrNetServer xrODE xrOPCODE
        xrParticles xrMaterialSystem xrGameSpy xrImGui
        SDL2 main OpenAL
    )
    foreach(_target IN LISTS _all_oxr_targets)
        if (TARGET ${_target})
            set_target_properties(${_target} PROPERTIES PREFIX "lib")
        endif()
    endforeach()
endif()
