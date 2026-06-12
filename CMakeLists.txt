cmake_minimum_required(VERSION 3.20)

project(MobileSharing LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(QT_NO_PRIVATE_MODULE_WARNING ON)

################################################################################

# Generic dependencies
find_package(Qt6 REQUIRED COMPONENTS Core Gui Qml)
set(CORE_LIBRARIES Qt::Core Qt::Gui Qt::Qml)

# Generic sources
set(CORE_SOURCES
    SharingApplication.cpp SharingApplication.h
    SharingUtils.cpp SharingUtils.h)

# OS specific sources & dependencies
if (ANDROID)
    if(Qt6Gui_VERSION VERSION_GREATER_EQUAL 6.10.0)
        find_package(Qt6 REQUIRED COMPONENTS CorePrivate)
        set(PLATFORM_LIBRARIES ${CORE_LIBRARIES} Qt6::CorePrivate)
    else()
        find_package(Qt6 REQUIRED COMPONENTS Core)
        set(PLATFORM_LIBRARIES ${CORE_LIBRARIES} Qt6::CorePrivate)
    endif()

    set(PLATFORM_SOURCES
        SharingUtils_android.cpp
        SharingUtils_android.h)
elseif (IOS)
    find_package(Qt6 REQUIRED COMPONENTS Quick)
    set(PLATFORM_LIBRARIES Qt::Quick)
    set(PLATFORM_SOURCES
        SharingUtils_ios.mm
        docviewcontroller_ios.mm)
endif()

################################################################################

qt_add_library(MobileSharing STATIC)

qt_add_qml_module(MobileSharing
    URI MobileSharing
    VERSION 1.0
    DEPENDENCIES QtQuick
    PLUGIN_TARGET MobileSharing_plugin
    SOURCES ${CORE_SOURCES} ${PLATFORM_SOURCES}
    #OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/qml/MobileSharing"
)

target_link_libraries(MobileSharing PRIVATE ${CORE_LIBRARIES} ${PLATFORM_LIBRARIES})
target_include_directories(MobileSharing PUBLIC ${CMAKE_CURRENT_LIST_DIR})

################################################################################

# On Android the module owns its Java sources and FileProvider resources.
# We copy these files into QT_ANDROID_PACKAGE_SOURCE_DIR at configure time.
if (ANDROID)
    # If by any chance QT_ANDROID_PACKAGE_SOURCE_DIR exists as a variable (?) we use it
    if (NOT DEFINED MOBILESHARING_ANDROID_PACKAGE_SOURCE_DIR AND DEFINED QT_ANDROID_PACKAGE_SOURCE_DIR)
        set(MOBILESHARING_ANDROID_PACKAGE_SOURCE_DIR "${QT_ANDROID_PACKAGE_SOURCE_DIR}")
    endif()

    # If by any chance the directory "${CMAKE_SOURCE_DIR}/assets/android/" exists, we use it
    if (NOT DEFINED MOBILESHARING_ANDROID_PACKAGE_SOURCE_DIR AND EXISTS "${CMAKE_SOURCE_DIR}/assets/android/")
        set(MOBILESHARING_ANDROID_PACKAGE_SOURCE_DIR "${CMAKE_SOURCE_DIR}/assets/android/")
    endif()

    if (DEFINED MOBILESHARING_ANDROID_PACKAGE_SOURCE_DIR)
        file(COPY
            "${CMAKE_CURRENT_SOURCE_DIR}/android/src"
            "${CMAKE_CURRENT_SOURCE_DIR}/android/res"
            DESTINATION "${MOBILESHARING_ANDROID_PACKAGE_SOURCE_DIR}")
        message(STATUS "MobileSharing > Deployed Android Java/res into ${MOBILESHARING_ANDROID_PACKAGE_SOURCE_DIR}")
    else()
        message(WARNING "MobileSharing > MOBILESHARING_ANDROID_PACKAGE_SOURCE_DIR is not set, "
                        "please provide the same path than your application's QT_ANDROID_PACKAGE_SOURCE_DIR before add_subdirectory(MobileSharing).")
    endif()
endif()

################################################################################
