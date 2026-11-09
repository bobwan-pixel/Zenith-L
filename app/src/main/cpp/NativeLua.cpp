#include <jni.h>
#include <android/log.h>
extern "C" {
#include <lua.h>
#include <lauxlib.h>
#include <lualib.h>
}

#include <atomic>
#include <algorithm>
#include <chrono>
#include <cstdint>
#include <cstdio>
#include <limits>
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

// The callback is scoped to nativeExecute and is only used on that same JNI thread.
thread_local JNIEnv* outputEnv = nullptr;
thread_local jobject outputListener = nullptr;

std::u16string utf8ToUtf16(const std::string& input) {
    std::u16string output;
    output.reserve(input.size());

    for (size_t index = 0; index < input.size();) {
        const auto first = static_cast<unsigned char>(input[index]);
        uint32_t codePoint = 0;
        size_t length = 0;

        if (first <= 0x7FU) {
            codePoint = first;
            length = 1;
        } else if (first >= 0xC2U && first <= 0xDFU && index + 1 < input.size()) {
            const auto second = static_cast<unsigned char>(input[index + 1]);
            if ((second & 0xC0U) == 0x80U) {
                codePoint = ((first & 0x1FU) << 6U) | (second & 0x3FU);
                length = 2;
            }
        } else if (first >= 0xE0U && first <= 0xEFU && index + 2 < input.size()) {
            const auto second = static_cast<unsigned char>(input[index + 1]);
            const auto third = static_cast<unsigned char>(input[index + 2]);
            const bool validSecond = (second & 0xC0U) == 0x80U &&
                (first != 0xE0U || second >= 0xA0U) &&
                (first != 0xEDU || second <= 0x9FU);
            if (validSecond && (third & 0xC0U) == 0x80U) {
                codePoint = ((first & 0x0FU) << 12U) |
                    ((second & 0x3FU) << 6U) | (third & 0x3FU);
                length = 3;
            }
        } else if (first >= 0xF0U && first <= 0xF4U && index + 3 < input.size()) {
            const auto second = static_cast<unsigned char>(input[index + 1]);
            const auto third = static_cast<unsigned char>(input[index + 2]);
            const auto fourth = static_cast<unsigned char>(input[index + 3]);
            const bool validSecond = (second & 0xC0U) == 0x80U &&
                (first != 0xF0U || second >= 0x90U) &&
                (first != 0xF4U || second <= 0x8FU);
            if (validSecond && (third & 0xC0U) == 0x80U &&
                (fourth & 0xC0U) == 0x80U) {
                codePoint = ((first & 0x07U) << 18U) |
                    ((second & 0x3FU) << 12U) |
                    ((third & 0x3FU) << 6U) | (fourth & 0x3FU);
                length = 4;
            }
        }

        if (length == 0) {
            output.push_back(static_cast<char16_t>(0xFFFDU));
            ++index;
            continue;
        }

        if (codePoint <= 0xFFFFU) {
            output.push_back(static_cast<char16_t>(codePoint));
        } else {
            codePoint -= 0x10000U;
            output.push_back(static_cast<char16_t>(0xD800U + (codePoint >> 10U)));
            output.push_back(static_cast<char16_t>(0xDC00U + (codePoint & 0x3FFU)));
        }
        index += length;
    }
    return output;
}

void deliverOutput(const std::string& message) {
    if (outputEnv == nullptr || outputListener == nullptr) return;

    jclass listenerClass = outputEnv->GetObjectClass(outputListener);
    if (listenerClass == nullptr) {
        if (outputEnv->ExceptionCheck()) outputEnv->ExceptionClear();
        return;
    }

    const jmethodID callback = outputEnv->GetMethodID(listenerClass, "onOutput", "(Ljava/lang/String;)V");
    if (callback != nullptr) {
        const std::u16string utf16 = utf8ToUtf16(message);
        jstring value = outputEnv->NewString(
            reinterpret_cast<const jchar*>(utf16.data()),
            static_cast<jsize>(utf16.size()));
        if (value != nullptr) {
            outputEnv->CallVoidMethod(outputListener, callback, value);
            outputEnv->DeleteLocalRef(value);
        }
    }
    if (outputEnv->ExceptionCheck()) outputEnv->ExceptionClear();
    outputEnv->DeleteLocalRef(listenerClass);
}

void* boundedAllocator(void* userData, void* pointer, size_t oldSize, size_t newSize) {
    auto* runtime = static_cast<Runtime*>(userData);
    if (newSize == 0) {
        std::free(pointer);
        runtime->allocatedBytes -= std::min(runtime->allocatedBytes, oldSize);
        return nullptr;
    }
    const size_t accountedOld = pointer == nullptr ? 0 : oldSize;
    if (newSize > accountedOld) {
        const size_t additionalBytes = newSize - accountedOld;
        if (runtime->allocatedBytes > Runtime::memoryLimitBytes ||
            additionalBytes > Runtime::memoryLimitBytes - runtime->allocatedBytes) {
            return nullptr;
        }
    }
    void* resized = std::realloc(pointer, newSize);
    if (resized != nullptr) runtime->allocatedBytes = runtime->allocatedBytes - std::min(runtime->allocatedBytes, accountedOld) + newSize;
    return resized;
}

std::string utf16ToUtf8(const jchar* chars, jsize length) {
    std::string output;
    output.reserve(static_cast<size_t>(length) * 3U);
    for (jsize index = 0; index < length; ++index) {
        uint32_t codePoint = chars[index];
        if (codePoint >= 0xD800U && codePoint <= 0xDBFFU) {
            if (index + 1 < length && chars[index + 1] >= 0xDC00U && chars[index + 1] <= 0xDFFFU) {
                codePoint = 0x10000U + ((codePoint - 0xD800U) << 10U) +
                    (static_cast<uint32_t>(chars[++index]) - 0xDC00U);
            } else codePoint = 0xFFFDU;
        } else if (codePoint >= 0xDC00U && codePoint <= 0xDFFFU) codePoint = 0xFFFDU;

        if (codePoint <= 0x7FU) output.push_back(static_cast<char>(codePoint));
        else if (codePoint <= 0x7FFU) {
            output.push_back(static_cast<char>(0xC0U | (codePoint >> 6U)));
            output.push_back(static_cast<char>(0x80U | (codePoint & 0x3FU)));
        } else if (codePoint <= 0xFFFFU) {
            output.push_back(static_cast<char>(0xE0U | (codePoint >> 12U)));
            output.push_back(static_cast<char>(0x80U | ((codePoint >> 6U) & 0x3FU)));
            output.push_back(static_cast<char>(0x80U | (codePoint & 0x3FU)));
        } else {
            output.push_back(static_cast<char>(0xF0U | (codePoint >> 18U)));
            output.push_back(static_cast<char>(0x80U | ((codePoint >> 12U) & 0x3FU)));
            output.push_back(static_cast<char>(0x80U | ((codePoint >> 6U) & 0x3FU)));
            output.push_back(static_cast<char>(0x80U | (codePoint & 0x3FU)));
        }
    }
    return output;
}

std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const jsize length = env->GetStringLength(value);
    const jchar* chars = env->GetStringChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result = utf16ToUtf8(chars, length);
    env->ReleaseStringChars(value, chars);
    return result;
}

jstring newJavaString(JNIEnv* env, const std::string& value) {
    const std::u16string utf16 = utf8ToUtf16(value);
    if (utf16.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;
    return env->NewString(reinterpret_cast<const jchar*>(utf16.data()), static_cast<jsize>(utf16.size()));
}

int zenithPrint(lua_State* state) {
    try {
        std::string message;
        const int count = lua_gettop(state);
        for (int index = 1; index <= count; ++index) {
            if (index > 1) message.push_back('\t');
            switch (lua_type(state, index)) {
                case LUA_TSTRING: {
                    size_t length = 0;
                    const char* value = lua_tolstring(state, index, &length);
                    if (value != nullptr) message.append(value, length);
                    break;
                }
                case LUA_TNUMBER: {
                    char numberText[64] = {};
                    std::snprintf(numberText, sizeof(numberText), "%.14g",
                        static_cast<double>(lua_tonumber(state, index)));
                    message.append(numberText);
                    break;
                }
                case LUA_TBOOLEAN:
                    message.append(lua_toboolean(state, index) ? "true" : "false");
                    break;
                case LUA_TNIL:
                    message.append("nil");
                    break;
                default: {
                    const char* typeName = lua_typename(state, lua_type(state, index));
                    char pointerText[2 + sizeof(void*) * 2 + 1] = {};
                    std::snprintf(pointerText, sizeof(pointerText), "%p", const_cast<void*>(lua_topointer(state, index)));
                    message.append(typeName == nullptr ? "value" : typeName);
                    message.append(": ");
                    message.append(pointerText);
                    break;
                }
            }
        }
        __android_log_write(ANDROID_LOG_INFO, kTag, message.c_str());
        deliverOutput(message);
        return 0;
    } catch (...) {
        return luaL_error(state, "print output allocation failed");
    }
}

int unavailableApi(lua_State* state) {
    const char* function = lua_tostring(state, lua_upvalueindex(1));
    return luaL_error(state, "%s is INTERFACE_ONLY: Game Bridge is unavailable", function);
}

int getZenithVersion(lua_State* state) {
    lua_pushstring(state, lua_tostring(state, lua_upvalueindex(1)));
    return 1;
}

int getRuntimeInfo(lua_State* state) {
    lua_newtable(state);
    lua_pushliteral(state, "Lua 5.4.9");
    lua_setfield(state, -2, "luaVersion");
    lua_pushinteger(state, static_cast<lua_Integer>(Runtime::memoryLimitBytes));
    lua_setfield(state, -2, "memoryLimitBytes");
    lua_pushinteger(state, 3);
    lua_setfield(state, -2, "maxSessions");
    return 1;
}

int getSessionInfo(lua_State* state) {
    lua_newtable(state);
    lua_pushstring(state, lua_tostring(state, lua_upvalueindex(1)));
    lua_setfield(state, -2, "id");
    lua_pushliteral(state, "RUNNING");
    lua_setfield(state, -2, "runtimeStatus");
    return 1;
}

int getBridgeStatus(lua_State* state) {
    lua_pushliteral(state, "UNAVAILABLE");
    return 1;
}

void openLibrary(lua_State* state, const char* name, lua_CFunction open) {
    luaL_requiref(state, name, open, 1);
    lua_pop(state, 1);
}

void instructionHook(lua_State* state, lua_Debug*) {
    auto** runtimeSlot = static_cast<Runtime**>(lua_getextraspace(state));
    auto* runtime = runtimeSlot == nullptr ? nullptr : *runtimeSlot;
    if (runtime == nullptr) return;
    while (runtime->control.load(std::memory_order_relaxed) == Control::Paused) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    if (runtime->control.load(std::memory_order_relaxed) == Control::Stop) {
        luaL_error(state, "Execution stopped by user");
    }
}

void registerHostApi(lua_State* state, const std::string& appVersion, const std::string& sessionId) {
    for (const char* unsafeBaseFunction : {"dofile", "loadfile", "load"}) {
        lua_pushnil(state);
        lua_setglobal(state, unsafeBaseFunction);
    }
    lua_pushcfunction(state, zenithPrint);
    lua_setglobal(state, "print");

    lua_newtable(state);
    lua_pushstring(state, appVersion.c_str());
    lua_pushcclosure(state, getZenithVersion, 1);
    lua_setfield(state, -2, "getVersion");
    lua_pushstring(state, "0.4");
    lua_setfield(state, -2, "apiVersion");
    lua_pushstring(state, "UNAVAILABLE");
    lua_setfield(state, -2, "gameBridgeStatus");
    lua_pushcfunction(state, getRuntimeInfo);
    lua_setfield(state, -2, "getRuntimeInfo");
    lua_pushstring(state, sessionId.c_str());
    lua_pushcclosure(state, getSessionInfo, 1);
    lua_setfield(state, -2, "getSessionInfo");
    lua_pushcfunction(state, getBridgeStatus);
    lua_setfield(state, -2, "getBridgeStatus");
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
    const char* zenithFunctions[] = {"getScriptInfo", "getGameInfo", "getGameVersion", "getSessions", "getBots", "getBot", "startBot", "stopBot", "pauseBot", "restartBot"};
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
Java_id_zenithl_launcher_lua_NativeLua_nativeCreate(JNIEnv* env, jobject, jstring sessionId, jstring appVersion) {
    const std::string id = toString(env, sessionId);
    const std::string version = toString(env, appVersion);
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
    registerHostApi(runtime->state, version, id);
    // No file, process, package, dynamic-loader, or debug libraries are opened.
    std::lock_guard<std::mutex> lock(runtimesMutex);
    if (runtimes.find(id) != runtimes.end()) return JNI_FALSE;
    runtimes.emplace(id, std::move(runtime));
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_id_zenithl_launcher_lua_NativeLua_nativeExecute(JNIEnv* env, jobject, jstring sessionId, jstring source, jstring chunkName, jobject listener) {
    auto runtime = findRuntime(toString(env, sessionId));
    if (runtime == nullptr || runtime->state == nullptr) return newJavaString(env, "Runtime not initialized");
    const std::string code = toString(env, source);
    const std::string name = toString(env, chunkName);
    outputEnv = env;
    outputListener = listener;
    lua_sethook(runtime->state, instructionHook, LUA_MASKCOUNT, 1000);
    int status = luaL_loadbuffer(runtime->state, code.data(), code.size(), name.c_str());
    if (status == LUA_OK) status = lua_pcall(runtime->state, 0, LUA_MULTRET, 0);
    lua_sethook(runtime->state, nullptr, 0, 0);
    outputListener = nullptr;
    outputEnv = nullptr;
    if (status == LUA_OK) {
        lua_settop(runtime->state, 0);
        return newJavaString(env, "");
    }
    const char* error = lua_tostring(runtime->state, -1);
    luaL_traceback(runtime->state, runtime->state, error == nullptr ? "Unknown Lua error" : error, 1);
    const char* trace = lua_tostring(runtime->state, -1);
    jstring result = newJavaString(env, trace == nullptr ? "Lua execution failed" : trace);
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
