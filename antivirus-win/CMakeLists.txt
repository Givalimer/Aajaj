cmake_minimum_required(VERSION 3.16)
project(AajajDefender LANGUAGES CXX RC)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

if(NOT WIN32)
    message(FATAL_ERROR "AajajDefender is a Windows-only app (Win32 + GDI). Build on Windows or cross-compile with MinGW-w64.")
endif()

add_executable(AajajDefender WIN32
    src/main.cpp
    res/resources.rc
)

target_link_libraries(AajajDefender PRIVATE
    gdi32
    user32
    winmm
    comctl32
    comdlg32
    msimg32
    shell32
)

if(MSVC)
    target_compile_options(AajajDefender PRIVATE /W3 /utf-8)
    target_compile_options(AajajDefender PRIVATE $<$<COMPILE_LANGUAGE:RC>:/I${CMAKE_CURRENT_SOURCE_DIR}/res>)
else()
    target_compile_options(AajajDefender PRIVATE -Wall -Wextra -municode -static -static-libgcc -static-libstdc++)
    target_compile_options(AajajDefender PRIVATE $<$<COMPILE_LANGUAGE:RC>:-I${CMAKE_CURRENT_SOURCE_DIR}/res>)
    target_link_options(AajajDefender PRIVATE -municode -static -static-libgcc -static-libstdc++ -mwindows)
endif()

set_target_properties(AajajDefender PROPERTIES
    OUTPUT_NAME "AajajDefender"
)
