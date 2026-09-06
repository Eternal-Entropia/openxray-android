if (ANDROID)
    add_library(main SHARED
        entry_point.cpp
        stdafx.h
    )
    set_target_properties(main PROPERTIES
        OUTPUT_NAME "main"
        PREFIX "lib"
    )
    target_include_directories(main
        PRIVATE
        "${CMAKE_SOURCE_DIR}/src"
    )
    target_link_libraries(main
        PRIVATE
        xrCore
        xrAPI
        xrEngine
        xrRender_GL
        xrGame
        SDL2::SDL2
        log
        android
    )
    install(TARGETS main LIBRARY
        DESTINATION "${CMAKE_INSTALL_LIBDIR}"
    )
else()
    add_executable(xr_3da)

    target_sources(xr_3da PRIVATE
        entry_point.cpp
        stdafx.h

        resource.h
        resource.rc

        splash_oxr.bmp
        stalker_cop.ico
        stalker_cs.ico
        stalker_soc.ico
    )

    set_property(
        DIRECTORY "${CMAKE_CURRENT_SOURCE_DIR}"
        PROPERTY
        VS_STARTUP_PROJECT xr_3da
    )

    target_include_directories(xr_3da
        PRIVATE
        "${CMAKE_SOURCE_DIR}/src"
    )

    target_link_libraries(xr_3da
        PRIVATE
        xrCore
        xrAPI
        xrEngine
        xrRender_GL
        xrGame
    )

    set_target_properties(xr_3da PROPERTIES
        PREFIX ""
    )

    install(TARGETS xr_3da RUNTIME
        DESTINATION "${CMAKE_INSTALL_BINDIR}"
    )
endif()
