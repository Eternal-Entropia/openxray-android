# Distributed under the OSI-approved BSD 3-Clause License.  See accompanying
# file Copyright.txt or https://cmake.org/licensing for details.

cmake_minimum_required(VERSION 3.22.1-g37088a8-dirty)

# We name the project and the target for the ExternalProject_Add() call
# to something that will highlight to the user what we are working on if
# something goes wrong and an error message is produced.

project(lzo-populate NONE)



include(ExternalProject)
ExternalProject_Add(lzo-populate
                     "UPDATE_DISCONNECTED" "False" "URL" "https://www.oberhumer.com/opensource/lzo/download/lzo-2.10.tar.gz"
                    SOURCE_DIR          "C:/oxr_deps/lzo-src"
                    BINARY_DIR          "C:/oxr_deps/lzo-build"
                    CONFIGURE_COMMAND   ""
                    BUILD_COMMAND       ""
                    INSTALL_COMMAND     ""
                    TEST_COMMAND        ""
                    USES_TERMINAL_DOWNLOAD  YES
                    USES_TERMINAL_UPDATE    YES
)


