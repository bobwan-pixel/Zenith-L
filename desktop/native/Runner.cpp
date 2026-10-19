extern "C" {
#include "lua.h"
#include "lauxlib.h"
#include "lualib.h"
}
#include <algorithm>
#include <chrono>
#include <cstdio>
#include <cstdlib>
#include <initializer_list>
#include <fstream>
#include <iostream>
#include <iterator>
#include <string>

namespace {
constexpr size_t kMemoryLimit = 32U * 1024U * 1024U;
constexpr lua_Integer kInstructionLimit = 100'000'000;
constexpr auto kTimeLimit = std::chrono::seconds(120);
struct Runtime { size_t bytes = 0; lua_Integer instructions = 0; std::chrono::steady_clock::time_point start; };

void* allocate(void* user, void* pointer, size_t oldSize, size_t newSize) {
    auto* runtime = static_cast<Runtime*>(user);
    if (newSize == 0) { std::free(pointer); runtime->bytes -= std::min(runtime->bytes, oldSize); return nullptr; }
    const size_t accounted = pointer == nullptr ? 0 : oldSize;
    if (newSize > accounted && runtime->bytes + newSize - accounted > kMemoryLimit) return nullptr;
    void* result = std::realloc(pointer, newSize);
    if (result != nullptr) runtime->bytes = runtime->bytes - std::min(runtime->bytes, accounted) + newSize;
    return result;
}

int zenithPrint(lua_State* state) {
    const int count = lua_gettop(state);
    for (int i = 1; i <= count; ++i) {
        size_t length = 0; const char* value = luaL_tolstring(state, i, &length);
        if (i > 1) std::cout << '\t';
        if (value != nullptr) std::cout.write(value, static_cast<std::streamsize>(length));
        lua_pop(state, 1);
    }
    std::cout << std::endl;
    return 0;
}

void watchdog(lua_State* state, lua_Debug*) {
    auto* runtime = static_cast<Runtime*>(lua_getextraspace(state));
    runtime->instructions += 1000;
    if (runtime->instructions > kInstructionLimit || std::chrono::steady_clock::now() - runtime->start > kTimeLimit)
        luaL_error(state, "Zenith Safety Watchdog: execution budget exceeded");
}

void openLibrary(lua_State* state, const char* name, lua_CFunction function) {
    luaL_requiref(state, name, function, 1); lua_pop(state, 1);
}
}

int main(int argc, char** argv) {
    if (argc != 2) { std::cerr << "Usage: zenith-lua.exe <script.lua>\n"; return 2; }
    std::ifstream input(argv[1], std::ios::binary);
    if (!input) { std::cerr << "Could not read Lua script\n"; return 2; }
    input.seekg(0, std::ios::end);
    const auto sourceSize = input.tellg();
    if (sourceSize < 0 || sourceSize > 1'048'576) { std::cerr << "Lua script exceeds the 1 MiB limit\n"; return 2; }
    input.seekg(0, std::ios::beg);
    const std::string source{std::istreambuf_iterator<char>(input), std::istreambuf_iterator<char>()};
    Runtime runtime; runtime.start = std::chrono::steady_clock::now();
    lua_State* state = lua_newstate(allocate, &runtime);
    if (state == nullptr) { std::cerr << "Could not allocate bounded Lua state\n"; return 3; }
    *static_cast<Runtime**>(lua_getextraspace(state)) = &runtime;
    openLibrary(state, "_G", luaopen_base);
    openLibrary(state, "table", luaopen_table);
    openLibrary(state, "string", luaopen_string);
    openLibrary(state, "math", luaopen_math);
    openLibrary(state, "utf8", luaopen_utf8);
    openLibrary(state, "coroutine", luaopen_coroutine);
    for (const char* name : {"dofile", "loadfile", "load", "collectgarbage"}) { lua_pushnil(state); lua_setglobal(state, name); }
    lua_pushcfunction(state, zenithPrint); lua_setglobal(state, "print");
    lua_sethook(state, watchdog, LUA_MASKCOUNT, 1000);
    int status = luaL_loadbuffer(state, source.data(), source.size(), argv[1]);
    if (status == LUA_OK) status = lua_pcall(state, 0, LUA_MULTRET, 0);
    if (status != LUA_OK) {
        const char* message = lua_tostring(state, -1);
        luaL_traceback(state, state, message == nullptr ? "Lua execution failed" : message, 1);
        std::cerr << (lua_tostring(state, -1) == nullptr ? "Lua execution failed" : lua_tostring(state, -1)) << std::endl;
    }
    lua_close(state);
    return status == LUA_OK ? 0 : 1;
}
