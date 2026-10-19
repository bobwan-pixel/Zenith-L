#include <jni.h>
#include <android/log.h>
#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>

#include <atomic>
#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <initializer_list>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>

namespace {
constexpr char kTag[] = "ZenithLua";
enum class Control { Running, Paused, Stop };
struct Runtime {
    lua_State* state = nullptr;
    size_t allocatedBytes = 0;
    static constexpr size_t memoryLimitBytes = 32U * 1024U * 1024U;
    std::atomic<Control> control{Control::Running};
    std::string id;
    ~Runtime() { if (state != nullptr) lua_close(state); }
};
std::mutex runtimesMutex;
std::unordered_map<std::string, std::shared_ptr<Runtime>> runtimes;

void* boundedAllocator(void* userData, void* pointer, size_t oldSize, size_t newSize) {
    auto* runtime = static_cast<Runtime*>(userData);
    if (newSize == 0) {
        std::free(pointer);
        runtime->allocatedBytes -= std::min(runtime->allocatedBytes, oldSize);
        return nullptr;
    }
    const size_t accountedOld = pointer == nullptr ? 0 : oldSize;
    if (newSize > accountedOld && runtime->allocatedBytes + (newSize - accountedOld) > Runtime::memoryLimitBytes) return nullptr;
    void* resized = std::realloc(pointer, newSize);
    if (resized != nullptr) runtime->allocatedBytes = runtime->allocatedBytes - std::min(runtime->allocatedBytes, accountedOld) + newSize;
    return resized;
}

std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

int zenithPrint(lua_State* state) {
    std::string message;
    const int count = lua_gettop(state);
    for (int index = 1; index <= count; ++index) {
        size_t length = 0;
        const char* value = luaL_tolstring(state, index, &length);
        if (index > 1) message.push_back('\t');
        if (value != nullptr) message.append(value, length);
        lua_pop(state, 1);
    }
    __android_log_write(ANDROID_LOG_INFO, kTag, message.c_str());
    return 0;
}

int unavailableApi(lua_State* state) {
    const char* function = lua_tostring(state, lua_upvalueindex(1));
    return luaL_error(state, "%s is INTERFACE_ONLY: Game Bridge is unavailable", function);
}

int getZenithVersion(lua_State* state) {
    lua_pushstring(state, "0.3.0");
    return 1;
}

void openLibrary(lua_State* state, const char* name, lua_CFunction open) {
    luaL_requiref(state, name, open, 1);
    lua_pop(state, 1);
}

void instructionHook(lua_State* state, lua_Debug*) {
    auto* runtime = static_cast<Runtime*>(lua_getextraspace(state));
    if (runtime == nullptr) return;
    while (runtime->control.load(std::memory_order_relaxed) == Control::Paused) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    if (runtime->control.load(std::memory_order_relaxed) == Control::Stop) {
        luaL_error(state, "Execution stopped by user");
    }
}

void registerHostApi(lua_State* state) {
    for (const char* unsafeBaseFunction : {"dofile", "loadfile", "load"}) {
        lua_pushnil(state);
        lua_setglobal(state, unsafeBaseFunction);
    }
    lua_pushcfunction(state, zenithPrint);
    lua_setglobal(state, "print");

    lua_newtable(state);
    lua_pushcfunction(state, getZenithVersion);
    lua_setfield(state, -2, "getVersion");
    lua_pushstring(state, "0.3");
    lua_setfield(state, -2, "apiVersion");
    lua_pushstring(state, "INTERFACE_ONLY");
    lua_setfield(state, -2, "gameBridgeStatus");
    lua_setglobal(state, "Zenith");

    const char* glFunctions[] = {
        "getTile", "getTiles", "getWorldName", "getInventory", "getLocal",
        "getPlayerList", "getObjectList", "getNPCList", "findItemID",
        "getItemInfoByID", "FindPath", "Sleep", "runThread", "runCoroutine",
        "AddHook", "removeHook", "SendPacket", "SendPacketRaw", "SendVariant",
        "GetTile", "GetTiles", "GetWorldName", "GetInventory", "GetLocal",
        "GetPlayerList", "GetObjectList", "GetNPCList", "FindItemID",
        "getPlayerByNetID", "getItemInfoManager"
    };
    for (const char* function : glFunctions) {
        lua_pushstring(state, function);
        lua_pushcclosure(state, unavailableApi, 1);
        lua_setglobal(state, function);
    }
    const char* zenithFunctions[] = {"getRuntimeInfo", "getScriptInfo", "getSessionInfo", "getBots", "getBot", "startBot", "stopBot", "pauseBot", "restartBot"};
    lua_getglobal(state, "Zenith");
    for (const char* function : zenithFunctions) {
        lua_pushstring(state, function);
        lua_pushcclosure(state, unavailableApi, 1);
        lua_setfield(state, -2, function);
    }
    lua_pop(state, 1);
}

std::shared_ptr<Runtime> findRuntime(const std::string& id) {
    std::lock_guard<std::mutex> lock(runtimesMutex);
    const auto found = runtimes.find(id);
    return found == runtimes.end() ? nullptr : found->second;
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_zenithl_launcher_lua_NativeLua_nativeCreate(JNIEnv* env, jobject, jstring sessionId) {
    const std::string id = toString(env, sessionId);
    if (id.empty()) return JNI_FALSE;
    auto runtime = std::make_shared<Runtime>();
    runtime->id = id;
    runtime->state = lua_newstate(boundedAllocator, runtime.get());
    if (runtime->state == nullptr) return JNI_FALSE;
    *static_cast<Runtime**>(lua_getextraspace(runtime->state)) = runtime.get();
    openLibrary(runtime->state, "_G", luaopen_base);
    openLibrary(runtime->state, "table", luaopen_table);
    openLibrary(runtime->state, "string", luaopen_string);
    openLibrary(runtime->state, "math", luaopen_math);
    openLibrary(runtime->state, "utf8", luaopen_utf8);
    openLibrary(runtime->state, "coroutine", luaopen_coroutine);
    registerHostApi(runtime->state);
    // No file, process, package, dynamic-loader, or debug libraries are opened.
    std::lock_guard<std::mutex> lock(runtimesMutex);
    if (runtimes.find(id) != runtimes.end()) return JNI_FALSE;
    runtimes.emplace(id, std::move(runtime));
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_id_zenithl_launcher_lua_NativeLua_nativeExecute(JNIEnv* env, jobject, jstring sessionId, jstring source, jstring chunkName) {
    auto runtime = findRuntime(toString(env, sessionId));
    if (runtime == nullptr || runtime->state == nullptr) return env->NewStringUTF("Runtime not initialized");
    const std::string code = toString(env, source);
    const std::string name = toString(env, chunkName);
    runtime->control.store(Control::Running, std::memory_order_relaxed);
    lua_sethook(runtime->state, instructionHook, LUA_MASKCOUNT, 1000);
    int status = luaL_loadbuffer(runtime->state, code.data(), code.size(), name.c_str());
    if (status == LUA_OK) status = lua_pcall(runtime->state, 0, LUA_MULTRET, 0);
    lua_sethook(runtime->state, nullptr, 0, 0);
    if (status == LUA_OK) {
        lua_settop(runtime->state, 0);
        return env->NewStringUTF("");
    }
    const char* error = lua_tostring(runtime->state, -1);
    luaL_traceback(runtime->state, runtime->state, error == nullptr ? "Unknown Lua error" : error, 1);
    const char* trace = lua_tostring(runtime->state, -1);
    jstring result = env->NewStringUTF(trace == nullptr ? "Lua execution failed" : trace);
    lua_settop(runtime->state, 0);
    return result;
}

extern "C" JNIEXPORT void JNICALL
Java_id_zenithl_launcher_lua_NativeLua_nativeControl(JNIEnv* env, jobject, jstring sessionId, jint action) {
    auto runtime = findRuntime(toString(env, sessionId));
    if (runtime == nullptr) return;
    const auto control = action == 1 ? Control::Paused : action == 2 ? Control::Stop : Control::Running;
    runtime->control.store(control, std::memory_order_relaxed);
}

extern "C" JNIEXPORT void JNICALL
Java_id_zenithl_launcher_lua_NativeLua_nativeClose(JNIEnv* env, jobject, jstring sessionId) {
    const std::string id = toString(env, sessionId);
    std::lock_guard<std::mutex> lock(runtimesMutex);
    const auto found = runtimes.find(id);
    if (found != runtimes.end()) {
        found->second->control.store(Control::Stop, std::memory_order_relaxed);
        runtimes.erase(found);
    }
}
