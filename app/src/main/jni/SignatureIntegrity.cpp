#include <jni.h>
#include <obfuscate.h>

#include <algorithm>
#include <array>
#include <cctype>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <set>
#include <string>
#include <vector>

#include <dlfcn.h>
#include <limits.h>
#include <sys/stat.h>
#include <unistd.h>

namespace {
constexpr size_t SHA256_SIZE = 32u;
constexpr size_t SHA256_BLOCK = 64u;

struct Sha256Context {
    uint8_t data[SHA256_BLOCK]{};
    size_t dataLength = 0;
    uint64_t bitLength = 0;
    uint32_t state[8] = {
            0x6a09e667u, 0xbb67ae85u, 0x3c6ef372u, 0xa54ff53au,
            0x510e527fu, 0x9b05688cu, 0x1f83d9abu, 0x5be0cd19u
    };
};

constexpr uint32_t K[64] = {
        0x428a2f98u,0x71374491u,0xb5c0fbcfu,0xe9b5dba5u,0x3956c25bu,0x59f111f1u,0x923f82a4u,0xab1c5ed5u,
        0xd807aa98u,0x12835b01u,0x243185beu,0x550c7dc3u,0x72be5d74u,0x80deb1feu,0x9bdc06a7u,0xc19bf174u,
        0xe49b69c1u,0xefbe4786u,0x0fc19dc6u,0x240ca1ccu,0x2de92c6fu,0x4a7484aau,0x5cb0a9dcu,0x76f988dau,
        0x983e5152u,0xa831c66du,0xb00327c8u,0xbf597fc7u,0xc6e00bf3u,0xd5a79147u,0x06ca6351u,0x14292967u,
        0x27b70a85u,0x2e1b2138u,0x4d2c6dfcu,0x53380d13u,0x650a7354u,0x766a0abbu,0x81c2c92eu,0x92722c85u,
        0xa2bfe8a1u,0xa81a664bu,0xc24b8b70u,0xc76c51a3u,0xd192e819u,0xd6990624u,0xf40e3585u,0x106aa070u,
        0x19a4c116u,0x1e376c08u,0x2748774cu,0x34b0bcb5u,0x391c0cb3u,0x4ed8aa4au,0x5b9cca4fu,0x682e6ff3u,
        0x748f82eeu,0x78a5636fu,0x84c87814u,0x8cc70208u,0x90befffau,0xa4506cebu,0xbef9a3f7u,0xc67178f2u
};

inline uint32_t rotr(uint32_t value, uint32_t bits) {
    return (value >> bits) | (value << (32u - bits));
}

void sha256Transform(Sha256Context &ctx, const uint8_t block[SHA256_BLOCK]) {
    uint32_t w[64];
    for (size_t i = 0; i < 16; ++i) {
        const size_t j = i * 4u;
        w[i] = (static_cast<uint32_t>(block[j]) << 24u)
                | (static_cast<uint32_t>(block[j + 1u]) << 16u)
                | (static_cast<uint32_t>(block[j + 2u]) << 8u)
                | static_cast<uint32_t>(block[j + 3u]);
    }
    for (size_t i = 16; i < 64; ++i) {
        uint32_t s0 = rotr(w[i - 15u], 7u) ^ rotr(w[i - 15u], 18u) ^ (w[i - 15u] >> 3u);
        uint32_t s1 = rotr(w[i - 2u], 17u) ^ rotr(w[i - 2u], 19u) ^ (w[i - 2u] >> 10u);
        w[i] = w[i - 16u] + s0 + w[i - 7u] + s1;
    }

    uint32_t a = ctx.state[0];
    uint32_t b = ctx.state[1];
    uint32_t c = ctx.state[2];
    uint32_t d = ctx.state[3];
    uint32_t e = ctx.state[4];
    uint32_t f = ctx.state[5];
    uint32_t g = ctx.state[6];
    uint32_t h = ctx.state[7];

    for (size_t i = 0; i < 64; ++i) {
        uint32_t s1 = rotr(e, 6u) ^ rotr(e, 11u) ^ rotr(e, 25u);
        uint32_t ch = (e & f) ^ ((~e) & g);
        uint32_t temp1 = h + s1 + ch + K[i] + w[i];
        uint32_t s0 = rotr(a, 2u) ^ rotr(a, 13u) ^ rotr(a, 22u);
        uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        uint32_t temp2 = s0 + maj;
        h = g;
        g = f;
        f = e;
        e = d + temp1;
        d = c;
        c = b;
        b = a;
        a = temp1 + temp2;
    }

    ctx.state[0] += a;
    ctx.state[1] += b;
    ctx.state[2] += c;
    ctx.state[3] += d;
    ctx.state[4] += e;
    ctx.state[5] += f;
    ctx.state[6] += g;
    ctx.state[7] += h;
    std::fill(std::begin(w), std::end(w), 0u);
}

void sha256Update(Sha256Context &ctx, const uint8_t *data, size_t length) {
    for (size_t i = 0; i < length; ++i) {
        ctx.data[ctx.dataLength++] = data[i];
        if (ctx.dataLength == SHA256_BLOCK) {
            sha256Transform(ctx, ctx.data);
            ctx.bitLength += 512u;
            ctx.dataLength = 0;
        }
    }
}

std::array<uint8_t, SHA256_SIZE> sha256(const uint8_t *data, size_t length) {
    Sha256Context ctx;
    sha256Update(ctx, data, length);

    size_t i = ctx.dataLength;
    ctx.data[i++] = 0x80u;
    if (i > 56u) {
        while (i < SHA256_BLOCK) ctx.data[i++] = 0u;
        sha256Transform(ctx, ctx.data);
        i = 0u;
    }
    while (i < 56u) ctx.data[i++] = 0u;

    ctx.bitLength += static_cast<uint64_t>(ctx.dataLength) * 8u;
    for (size_t offset = 0; offset < 8u; ++offset) {
        ctx.data[63u - offset] = static_cast<uint8_t>((ctx.bitLength >> (offset * 8u)) & 0xffu);
    }
    sha256Transform(ctx, ctx.data);

    std::array<uint8_t, SHA256_SIZE> result{};
    for (size_t word = 0; word < 8u; ++word) {
        result[word * 4u] = static_cast<uint8_t>((ctx.state[word] >> 24u) & 0xffu);
        result[word * 4u + 1u] = static_cast<uint8_t>((ctx.state[word] >> 16u) & 0xffu);
        result[word * 4u + 2u] = static_cast<uint8_t>((ctx.state[word] >> 8u) & 0xffu);
        result[word * 4u + 3u] = static_cast<uint8_t>(ctx.state[word] & 0xffu);
    }
    std::memset(&ctx, 0, sizeof(ctx));
    return result;
}

bool constantTimeEqual(const uint8_t *first, const uint8_t *second, size_t length) {
    uint8_t diff = 0u;
    for (size_t i = 0; i < length; ++i) diff |= first[i] ^ second[i];
    return diff == 0u;
}

bool endsWith(const std::string &value, const std::string &suffix) {
    return value.size() >= suffix.size()
            && value.compare(value.size() - suffix.size(), suffix.size(), suffix) == 0;
}

bool canonicalize(const std::string &path, std::string &output) {
    if (path.empty() || path[0] != '/') return false;
    char resolved[PATH_MAX];
    if (realpath(path.c_str(), resolved) == nullptr) return false;
    output.assign(resolved);
    return !output.empty() && output[0] == '/';
}

bool packageOk(const char *packageName) {
    return packageName != nullptr
            && std::strcmp(packageName, OBFUSCATE("OneCore.Vip")) == 0;
}

bool processNameMatches() {
    std::ifstream input("/proc/self/cmdline", std::ios::binary);
    if (!input.is_open()) return false;
    std::string cmdline;
    std::getline(input, cmdline, '\0');
    const std::string expected = OBFUSCATE("OneCore.Vip");
    return cmdline == expected || cmdline.rfind(expected + ":", 0) == 0;
}

bool selfLibraryPath(std::string &libraryPath) {
    Dl_info info{};
    if (dladdr(reinterpret_cast<void *>(&selfLibraryPath), &info) == 0
            || info.dli_fname == nullptr) {
        return false;
    }
    std::string raw(info.dli_fname);
    if (raw.find('!') != std::string::npos || !endsWith(raw, "/libclient.so")) return false;
    return canonicalize(raw, libraryPath) && endsWith(libraryPath, "/libclient.so");
}

bool deriveRuntimeBaseApk(std::string &baseApk, std::string &libraryPath) {
    if (!selfLibraryPath(libraryPath)) return false;
    const std::string marker = "/lib/arm64/libclient.so";
    size_t markerPosition = libraryPath.rfind(marker);
    if (markerPosition == std::string::npos || markerPosition == 0u) return false;

    std::string installRoot = libraryPath.substr(0, markerPosition);
    if (!(installRoot.rfind("/data/app/", 0) == 0
            || installRoot.rfind("/mnt/expand/", 0) == 0)) {
        return false;
    }

    std::string candidate = installRoot + "/base.apk";
    if (!canonicalize(candidate, baseApk)) return false;
    if (baseApk != candidate || !endsWith(baseApk, "/base.apk")) return false;

    struct stat st{};
    return lstat(baseApk.c_str(), &st) == 0
            && S_ISREG(st.st_mode)
            && (st.st_mode & (S_IWGRP | S_IWOTH)) == 0
            && access(baseApk.c_str(), W_OK) != 0;
}

bool sameFile(const std::string &first, const std::string &second) {
    struct stat a{};
    struct stat b{};
    return stat(first.c_str(), &a) == 0
            && stat(second.c_str(), &b) == 0
            && S_ISREG(a.st_mode)
            && S_ISREG(b.st_mode)
            && a.st_dev == b.st_dev
            && a.st_ino == b.st_ino
            && a.st_size == b.st_size;
}

bool mapsBoundToSelf(const std::string &libraryPath) {
    std::ifstream maps("/proc/self/maps");
    if (!maps.is_open()) return false;
    std::set<std::string> clientPaths;
    std::string line;
    while (std::getline(maps, line)) {
        size_t pathPosition = line.find('/');
        if (pathPosition == std::string::npos) continue;
        std::string path = line.substr(pathPosition);
        size_t deleted = path.find(" (deleted)");
        if (deleted != std::string::npos) path.erase(deleted);
        if (path.find("libclient.so") == std::string::npos) continue;
        std::string canonical;
        if (!canonicalize(path, canonical)) return false;
        clientPaths.insert(canonical);
    }
    return clientPaths.size() == 1u && *clientPaths.begin() == libraryPath;
}

bool verifyProcessBoundApk(const char *claimedApkPath, const char *packageName) {
    if (!packageOk(packageName) || !processNameMatches()) return false;

    std::string baseApk;
    std::string libraryPath;
    if (!deriveRuntimeBaseApk(baseApk, libraryPath)) return false;
    if (claimedApkPath == nullptr || claimedApkPath[0] == '\0') return false;

    std::string claimed;
    if (!canonicalize(claimedApkPath, claimed)
            || claimed != baseApk
            || !sameFile(claimed, baseApk)) {
        return false;
    }
    return mapsBoundToSelf(libraryPath);
}

bool readByteArray(JNIEnv *env, jobjectArray parent, jsize index, std::vector<uint8_t> &output) {
    auto array = static_cast<jbyteArray>(env->GetObjectArrayElement(parent, index));
    if (array == nullptr) return false;
    const jsize length = env->GetArrayLength(array);
    if (length <= 0) {
        env->DeleteLocalRef(array);
        return false;
    }
    output.resize(static_cast<size_t>(length));
    env->GetByteArrayRegion(array, 0, length, reinterpret_cast<jbyte *>(output.data()));
    env->DeleteLocalRef(array);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        output.clear();
        return false;
    }
    return true;
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pubgm_security_NativeSigningVerifier_verifySigningIdentity(
        JNIEnv *env,
        jclass,
        jobjectArray allowedDigests,
        jobjectArray certificates,
        jstring actualPackage,
        jstring expectedPackage) {
    if (allowedDigests == nullptr || certificates == nullptr
            || actualPackage == nullptr || expectedPackage == nullptr) {
        return JNI_FALSE;
    }

    const char *actual = env->GetStringUTFChars(actualPackage, nullptr);
    const char *expected = env->GetStringUTFChars(expectedPackage, nullptr);
    if (actual == nullptr || expected == nullptr) {
        if (actual != nullptr) env->ReleaseStringUTFChars(actualPackage, actual);
        if (expected != nullptr) env->ReleaseStringUTFChars(expectedPackage, expected);
        return JNI_FALSE;
    }
    const bool packageMatches = std::strcmp(actual, expected) == 0 && packageOk(actual);
    env->ReleaseStringUTFChars(actualPackage, actual);
    env->ReleaseStringUTFChars(expectedPackage, expected);
    if (!packageMatches) return JNI_FALSE;

    const jsize allowedCount = env->GetArrayLength(allowedDigests);
    const jsize certificateCount = env->GetArrayLength(certificates);
    if (allowedCount <= 0 || certificateCount <= 0) return JNI_FALSE;

    std::vector<std::array<uint8_t, SHA256_SIZE>> allowed;
    allowed.reserve(static_cast<size_t>(allowedCount));
    for (jsize index = 0; index < allowedCount; ++index) {
        std::vector<uint8_t> digest;
        if (!readByteArray(env, allowedDigests, index, digest) || digest.size() != SHA256_SIZE) {
            std::fill(digest.begin(), digest.end(), 0u);
            return JNI_FALSE;
        }
        std::array<uint8_t, SHA256_SIZE> item{};
        std::copy(digest.begin(), digest.end(), item.begin());
        std::fill(digest.begin(), digest.end(), 0u);
        allowed.push_back(item);
    }

    for (jsize index = 0; index < certificateCount; ++index) {
        std::vector<uint8_t> certificate;
        if (!readByteArray(env, certificates, index, certificate)) return JNI_FALSE;
        auto actualDigest = sha256(certificate.data(), certificate.size());
        std::fill(certificate.begin(), certificate.end(), 0u);

        bool signerAllowed = false;
        for (const auto &candidate : allowed) {
            signerAllowed |= constantTimeEqual(actualDigest.data(), candidate.data(), SHA256_SIZE);
        }
        std::fill(actualDigest.begin(), actualDigest.end(), 0u);
        if (!signerAllowed) return JNI_FALSE;
    }

    for (auto &digest : allowed) std::fill(digest.begin(), digest.end(), 0u);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pubgm_security_NativeSigningVerifier_verifyProcessBoundApkNative(
        JNIEnv *env,
        jclass,
        jstring apkPath,
        jstring packageName) {
    if (apkPath == nullptr || packageName == nullptr) return JNI_FALSE;
    const char *apk = env->GetStringUTFChars(apkPath, nullptr);
    const char *pkg = env->GetStringUTFChars(packageName, nullptr);
    if (apk == nullptr || pkg == nullptr) {
        if (apk != nullptr) env->ReleaseStringUTFChars(apkPath, apk);
        if (pkg != nullptr) env->ReleaseStringUTFChars(packageName, pkg);
        return JNI_FALSE;
    }
    const bool ok = verifyProcessBoundApk(apk, pkg);
    env->ReleaseStringUTFChars(apkPath, apk);
    env->ReleaseStringUTFChars(packageName, pkg);
    return ok ? JNI_TRUE : JNI_FALSE;
}
