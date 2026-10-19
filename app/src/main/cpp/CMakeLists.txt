cmake_minimum_required(VERSION 3.22.1)
project(zenith_lua LANGUAGES C CXX)

set(LUA_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/lua-5.4.9")
add_library(zenith_lua SHARED
    NativeLua.cpp
    ${LUA_ROOT}/lapi.c ${LUA_ROOT}/lcode.c ${LUA_ROOT}/lctype.c
    ${LUA_ROOT}/ldebug.c ${LUA_ROOT}/ldo.c ${LUA_ROOT}/ldump.c
    ${LUA_ROOT}/lfunc.c ${LUA_ROOT}/lgc.c ${LUA_ROOT}/llex.c
    ${LUA_ROOT}/lmem.c ${LUA_ROOT}/lobject.c ${LUA_ROOT}/lopcodes.c
    ${LUA_ROOT}/lparser.c ${LUA_ROOT}/lstate.c ${LUA_ROOT}/lstring.c
    ${LUA_ROOT}/ltable.c ${LUA_ROOT}/ltm.c ${LUA_ROOT}/lundump.c
    ${LUA_ROOT}/lvm.c ${LUA_ROOT}/lzio.c ${LUA_ROOT}/lauxlib.c
    ${LUA_ROOT}/lbaselib.c ${LUA_ROOT}/lmathlib.c ${LUA_ROOT}/ltablib.c
    ${LUA_ROOT}/lstrlib.c ${LUA_ROOT}/lutf8lib.c ${LUA_ROOT}/lcorolib.c
)
target_include_directories(zenith_lua PRIVATE "${LUA_ROOT}")
target_compile_definitions(zenith_lua PRIVATE LUA_USE_POSIX)
target_compile_options(zenith_lua PRIVATE -Wall -Wextra -Werror=return-type)
target_link_libraries(zenith_lua log m)
