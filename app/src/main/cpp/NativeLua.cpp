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
#include <cstring>
#include <limits>
#include <cstdlib>
#include <initializer_list>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <unordered_map>

extern "C" void zenithCheckExecution(lua_State* state);

namespace {
constexpr char kTag[] = "ZenithLua";
enum class Control { Running, Paused, Stop };
struct Runtime {
    lua_State* state = nullptr;
    size_t allocatedBytes = 0;
    static constexpr size_t memoryLimitBytes = 32U * 1024U * 1024U;
    std::atomic<Control> control{Control::Running};
    std::atomic<bool> closing{false};
    std::string id;
    ~Runtime() { if (state != nullptr) lua_close(state); }
};
std::mutex runtimesMutex;
std::unordered_map<std::string, std::shared_ptr<Runtime>> runtimes;

// The callback is scoped to nativeExecute and is only used on that same JNI thread.
thread_local JNIEnv* outputEnv = nullptr;
thread_local jobject outputListener = nullptr;

// JNI local references are valid only for the duration of the native call.
// Keep the callback thread-local and guarantee it is cleared on every C++ exit.
class OutputCallbackScope {
public:
    OutputCallbackScope(JNIEnv* env, jobject listener) {
        outputEnv = env;
        outputListener = listener;
    }

    ~OutputCallbackScope() {
        outputListener = nullptr;
        outputEnv = nullptr;
    }

    OutputCallbackScope(const OutputCallbackScope&) = delete;
    OutputCallbackScope& operator=(const OutputCallbackScope&) = delete;
};

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

class ScopedLocalRef {
public:
    ScopedLocalRef(JNIEnv* env, jobject value) : env_(env), value_(value) {}
    ~ScopedLocalRef() {
        if (value_ != nullptr) env_->DeleteLocalRef(value_);
    }

    ScopedLocalRef(const ScopedLocalRef&) = delete;
    ScopedLocalRef& operator=(const ScopedLocalRef&) = delete;

    jobject get() const { return value_; }

private:
    JNIEnv* env_;
    jobject value_;
};

void deliverOutput(const std::string& message) {
    if (outputEnv == nullptr || outputListener == nullptr) return;

    ScopedLocalRef listenerClass(outputEnv, outputEnv->GetObjectClass(outputListener));
    if (listenerClass.get() == nullptr) {
        if (outputEnv->ExceptionCheck()) outputEnv->ExceptionClear();
        return;
    }

    const jmethodID callback = outputEnv->GetMethodID(
        static_cast<jclass>(listenerClass.get()), "onOutput", "(Ljava/lang/String;)V");
    if (callback != nullptr) {
        const std::u16string utf16 = utf8ToUtf16(message);
        ScopedLocalRef value(outputEnv, outputEnv->NewString(
            reinterpret_cast<const jchar*>(utf16.data()),
            static_cast<jsize>(utf16.size())));
        if (value.get() != nullptr) {
            outputEnv->CallVoidMethod(outputListener, callback, value.get());
        }
    }

    // Output is best-effort: a listener exception must not leave a pending
    // JNI exception that breaks the Lua execution or later JNI calls.
    if (outputEnv->ExceptionCheck()) outputEnv->ExceptionClear();
}

void* boundedAllocator(void* userData, void* pointer, size_t oldSize, size_t newSize) {
    auto* runtime = static_cast<Runtime*>(userData);
    if (newSize == 0) {
        // Lua may pass a type tag in oldSize when pointer is null. It is not
        // an allocation size and must never affect the memory counter.
        if (pointer != nullptr) {
            std::free(pointer);
            runtime->allocatedBytes -= std::min(runtime->allocatedBytes, oldSize);
        }
        return nullptr;
    }

    // Per Lua's allocator contract, oldSize is meaningful only for a non-null
    // pointer. Treat a null pointer as a fresh allocation.
    const size_t accountedOld = pointer == nullptr ? 0 : oldSize;
    const size_t retainedBytes =
        runtime->allocatedBytes - std::min(runtime->allocatedBytes, accountedOld);

    if (newSize > Runtime::memoryLimitBytes ||
        retainedBytes > Runtime::memoryLimitBytes - newSize) {
        return nullptr;
    }

    void* resized = std::realloc(pointer, newSize);
    if (resized != nullptr) runtime->allocatedBytes = retainedBytes + newSize;
    return resized;
}

std::string utf16ToUtf8(const jchar* chars, jsize length) {
    std::string output;
    output.reserve(static_cast<size_t>(length));
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

class ScopedStringChars {
public:
    ScopedStringChars(JNIEnv* env, jstring value)
        : env_(env), value_(value), chars_(value == nullptr ? nullptr : env->GetStringChars(value, nullptr)) {}

    ~ScopedStringChars() {
        if (chars_ != nullptr) env_->ReleaseStringChars(value_, chars_);
    }

    ScopedStringChars(const ScopedStringChars&) = delete;
    ScopedStringChars& operator=(const ScopedStringChars&) = delete;

    const jchar* get() const { return chars_; }

private:
    JNIEnv* env_;
    jstring value_;
    const jchar* chars_;
};

std::string toString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const jsize length = env->GetStringLength(value);
    ScopedStringChars chars(env, value);
    if (chars.get() == nullptr) return {};
    return utf16ToUtf8(chars.get(), length);
}

jstring newJavaString(JNIEnv* env, const std::string& value) {
    const std::u16string utf16 = utf8ToUtf16(value);
    if (utf16.size() > static_cast<size_t>(std::numeric_limits<jsize>::max())) return nullptr;
    return env->NewString(reinterpret_cast<const jchar*>(utf16.data()), static_cast<jsize>(utf16.size()));
}

constexpr size_t kMaxPrintBytes = 16U * 1024U;
constexpr char kPrintTruncatedSuffix[] = "...[truncated]";
constexpr size_t kMaxPrintContentBytes =
    kMaxPrintBytes - (sizeof(kPrintTruncatedSuffix) - 1U);

void appendPrintOutput(std::string& message, const char* value, size_t length, bool& truncated) {
    if (truncated || value == nullptr) return;
    const size_t available = kMaxPrintContentBytes - message.size();
    const size_t copied = std::min(length, available);
    message.append(value, copied);
    truncated = copied < length;
}

int zenithPrint(lua_State* state) {
    try {
        std::string message;
        bool truncated = false;
        const int count = lua_gettop(state);
        for (int index = 1; index <= count && !truncated; ++index) {
            if (index > 1) appendPrintOutput(message, "\t", 1, truncated);
            switch (lua_type(state, index)) {
                case LUA_TSTRING: {
                    size_t length = 0;
                    const char* value = lua_tolstring(state, index, &length);
                    appendPrintOutput(message, value, length, truncated);
                    break;
                }
                case LUA_TNUMBER: {
                    char numberText[64] = {};
                    std::snprintf(numberText, sizeof(numberText), "%.14g",
                        static_cast<double>(lua_tonumber(state, index)));
                    appendPrintOutput(message, numberText, std::strlen(numberText), truncated);
                    break;
                }
                case LUA_TBOOLEAN: {
                    const char* value = lua_toboolean(state, index) ? "true" : "false";
                    appendPrintOutput(message, value, std::strlen(value), truncated);
                    break;
                }
                case LUA_TNIL:
                    appendPrintOutput(message, "nil", 3, truncated);
                    break;
                default: {
                    const char* typeName = lua_typename(state, lua_type(state, index));
                    char pointerText[2 + sizeof(void*) * 2 + 1] = {};
                    std::snprintf(pointerText, sizeof(pointerText), "%p", const_cast<void*>(lua_topointer(state, index)));
                    const char* safeTypeName = typeName == nullptr ? "value" : typeName;
                    appendPrintOutput(message, safeTypeName, std::strlen(safeTypeName), truncated);
                    appendPrintOutput(message, ": ", 2, truncated);
                    appendPrintOutput(message, pointerText, std::strlen(pointerText), truncated);
                    break;
                }
            }
        }
        if (truncated) message.append(kPrintTruncatedSuffix);
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
    zenithCheckExecution(state);
}

void registerHostApi(lua_State* state, const std::string& appVersion, const std::string& sessionId) {
    for (const char* unsafeBaseFunction : {"dofile", "loadfile", "load", "warn"}) {
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

struct RuntimeInitContext {
    const std::string* appVersion;
    const std::string* sessionId;
};

// Run every allocation-capable Lua initialization operation inside lua_pcall.
// Lua's default panic handler aborts the process for an error outside a protected call.
int initializeRuntime(lua_State* state) {
    auto* context = static_cast<RuntimeInitContext*>(lua_touserdata(state, 1));
    if (context == nullptr || context->appVersion == nullptr || context->sessionId == nullptr) {
        return luaL_error(state, "Invalid runtime initialization context");
    }
    openLibrary(state, "_G", luaopen_base);
    openLibrary(state, "table", luaopen_table);
    openLibrary(state, "string", luaopen_string);
    openLibrary(state, "math", luaopen_math);
    openLibrary(state, "utf8", luaopen_utf8);
    openLibrary(state, "coroutine", luaopen_coroutine);
    registerHostApi(state, *context->appVersion, *context->sessionId);
    return 0;
}

int tracebackHandler(lua_State* state) {
    const char* error = lua_tostring(state, 1);
    luaL_traceback(state, state, error == nullptr ? "Unknown Lua error" : error, 1);
    return 1;
}

std::shared_ptr<Runtime> findRuntime(const std::string& id) {
    std::lock_guard<std::mutex> lock(runtimesMutex);
    const auto found = runtimes.find(id);
    return found == runtimes.end() ? nullptr : found->second;
}
}

extern "C" void zenithCheckExecution(lua_State* state) {
    auto** runtimeSlot = static_cast<Runtime**>(lua_getextraspace(state));
    auto* runtime = runtimeSlot == nullptr ? nullptr : *runtimeSlot;
    if (runtime == nullptr) return;
    while (runtime->control.load(std::memory_order_acquire) == Control::Paused) {
        std::this_thread::sleep_for(std::chrono::milliseconds(20));
    }
    if (runtime->control.load(std::memory_order_acquire) == Control::Stop) {
        luaL_error(state, "Execution stopped by user");
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_id_zenithl_launcher_lua_NativeLua_nativeCreate(JNIEnv* env, jobject, jstring nativeSessionId, jstring appVersion, jstring displaySessionId) {
    try {
        const std::string id = toString(env, nativeSessionId);
        const std::string version = toString(env, appVersion);
        const std::string displayId = toString(env, displaySessionId);
        if (id.empty() || displayId.empty() || env->ExceptionCheck()) return JNI_FALSE;
        auto runtime = std::make_shared<Runtime>();
        runtime->id = id;
        runtime->state = lua_newstate(boundedAllocator, runtime.get());
        if (runtime->state == nullptr) return JNI_FALSE;
        *static_cast<Runtime**>(lua_getextraspace(runtime->state)) = runtime.get();
        RuntimeInitContext initContext{&version, &displayId};
        lua_pushcfunction(runtime->state, initializeRuntime);
        lua_pushlightuserdata(runtime->state, &initContext);
        if (lua_pcall(runtime->state, 1, 0, 0) != LUA_OK) return JNI_FALSE;
        std::lock_guard<std::mutex> lock(runtimesMutex);
        if (runtimes.find(id) != runtimes.end()) return JNI_FALSE;
        runtimes.emplace(id, std::move(runtime));
        return JNI_TRUE;
    } catch (...) {
        // Never allow a C++ exception to cross the JNI boundary.
        return JNI_FALSE;
    }
}

extern "C" JNIEXPORT jstring JNICALL
Java_id_zenithl_launcher_lua_NativeLua_nativeExecute(JNIEnv* env, jobject, jstring sessionId, jstring source, jstring chunkName, jobject listener) {
    try {
        auto runtime = findRuntime(toString(env, sessionId));
        if (env->ExceptionCheck()) return nullptr;
        if (runtime == nullptr || runtime->state == nullptr) return newJavaString(env, "Runtime not initialized");
        const std::string code = toString(env, source);
        const std::string name = toString(env, chunkName);
        if (env->ExceptionCheck()) return nullptr;

        OutputCallbackScope outputScope(env, listener);
        lua_sethook(runtime->state, instructionHook, LUA_MASKCOUNT, 1000);
        int status = luaL_loadbuffer(runtime->state, code.data(), code.size(), name.c_str());
        if (status == LUA_OK) status = lua_pcall(runtime->state, 0, LUA_MULTRET, 0);
        lua_sethook(runtime->state, nullptr, 0, 0);
        // A coroutine's hook raises an error on that coroutine's stack. Lua's
        // coroutine.resume() converts that error into (false, message), so the
        // main chunk can otherwise finish successfully after Stop was requested.
        // The runtime control state is authoritative for the session outcome.
        const bool stopRequested =
            runtime->control.load(std::memory_order_acquire) == Control::Stop;
        if (stopRequested) {
            lua_settop(runtime->state, 0);
            return newJavaString(env, "Execution stopped by user");
        }
        if (status == LUA_OK) {
            lua_settop(runtime->state, 0);
            return newJavaString(env, "");
        }
        // Traceback generation can allocate. Protect it so memory exhaustion becomes
        // a normal Lua error instead of reaching Lua's process-aborting panic handler.
        lua_pushcfunction(runtime->state, tracebackHandler);
        lua_insert(runtime->state, -2);
        const int tracebackStatus = lua_pcall(runtime->state, 1, 1, 0);
        const char* trace = lua_tostring(runtime->state, -1);
        const char* fallback = tracebackStatus == LUA_OK
            ? "Lua execution failed"
            : "Lua execution failed (traceback allocation failed)";
        jstring result = newJavaString(env, trace == nullptr ? fallback : trace);
        lua_settop(runtime->state, 0);
        return result;
    } catch (...) {
        // The Kotlin engine treats a null result as a failed native call.
        return nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_id_zenithl_launcher_lua_NativeLua_nativeControl(JNIEnv* env, jobject, jstring sessionId, jint action) {
    try {
        auto runtime = findRuntime(toString(env, sessionId));
        if (runtime == nullptr || env->ExceptionCheck()) return;
        const auto control = action == 1 ? Control::Paused : action == 2 ? Control::Stop : Control::Running;
        if (runtime->closing.load(std::memory_order_acquire)) {
            runtime->control.store(Control::Stop, std::memory_order_release);
            return;
        }
        runtime->control.store(control, std::memory_order_release);
        // nativeClose() may race after findRuntime() returned this shared pointer.
        // Recheck closing so a late pause/resume cannot overwrite the close request.
        if (runtime->closing.load(std::memory_order_acquire)) {
            runtime->control.store(Control::Stop, std::memory_order_release);
        }
    } catch (...) {
        // Control is best-effort; execution remains protected by the instruction hook.
    }
}

extern "C" JNIEXPORT void JNICALL
Java_id_zenithl_launcher_lua_NativeLua_nativeClose(JNIEnv* env, jobject, jstring sessionId) {
    try {
        const std::string id = toString(env, sessionId);
        if (env->ExceptionCheck()) return;

        // Keep the removed runtime alive until after the registry lock is released.
        // Its destructor calls lua_close(), which may run Lua finalizers.
        std::shared_ptr<Runtime> retired;
        {
            std::lock_guard<std::mutex> lock(runtimesMutex);
            const auto found = runtimes.find(id);
            if (found != runtimes.end()) {
                found->second->closing.store(true, std::memory_order_release);
                found->second->control.store(Control::Stop, std::memory_order_release);
                retired = std::move(found->second);
                runtimes.erase(found);
            }
        }
        // If this was the final reference, lua_close() runs here, outside the lock.
    } catch (...) {
        // The shared runtime remains valid for any active nativeExecute call.
    }
}
