# Findmosquitto.cmake - Find mosquitto library
#
# The following variables are set:
#   MOSQUITTO_FOUND        - True if mosquitto was found
#   MOSQUITTO_INCLUDE_DIRS - The mosquitto include directories
#   MOSQUITTO_LIBRARIES    - The mosquitto libraries

find_path(MOSQUITTO_INCLUDE_DIR
    NAMES mosquitto.h
    PATHS ${CMAKE_SYSROOT}/usr/include
)

find_library(MOSQUITTO_LIBRARY
    NAMES mosquitto libmosquitto
    PATHS ${CMAKE_SYSROOT}/usr/lib
)

find_library(MOSQUITTO_STATIC_LIBRARY
    NAMES libmosquitto.a
    PATHS ${CMAKE_SYSROOT}/usr/lib
)

include(FindPackageHandleStandardArgs)
find_package_handle_standard_args(mosquitto
    REQUIRED_VARS
        MOSQUITTO_INCLUDE_DIR
        MOSQUITTO_STATIC_LIBRARY
)

if(mosquitto_FOUND)
    set(MOSQUITTO_INCLUDE_DIRS ${MOSQUITTO_INCLUDE_DIR})
    set(MOSQUITTO_LIBRARIES ${MOSQUITTO_STATIC_LIBRARY})
endif()

mark_as_advanced(
    MOSQUITTO_INCLUDE_DIR
    MOSQUITTO_LIBRARY
    MOSQUITTO_STATIC_LIBRARY
)
