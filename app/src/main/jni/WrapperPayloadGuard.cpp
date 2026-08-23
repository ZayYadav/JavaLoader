#include <jni.h>
#include <obfuscate.h>

#include <algorithm>
#include <cctype>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <fstream>
#include <set>
#include <sstream>
#include <string>
#include <vector>

#include <dlfcn.h>
#include <dirent.h>
#include <fcntl.h>
#include <limits.h>
#include <sys/stat.h>
#include <unistd.h>

namespace {
constexpr uint32_t ZIP_EOCD_MAGIC = 0x06054b50u;
constexpr uint32_t ZIP_CENTRAL_MAGIC = 0x02014b50u;
constexpr size_t ZIP_EOCD_MIN = 22u;
constexpr size_t ZIP_EOCD_SEARCH = ZIP_EOCD_MIN + 65535u;
constexpr size_t ZIP_CENTRAL_FIXED = 46u;

uint16_t le16(const unsigned char *p) {
    return static_cast<uint16_t>(p[0]) | (static_cast<uint16_t>(p[1]) << 8u);
}

uint32_t le32(const unsigned char *p) {
    return static_cast<uint32_t>(p[0])
            | (static_cast<uint32_t>(p[1]) << 8u)
            | (static_cast<uint32_t>(p[2]) << 16u)
            | (static_cast<uint32_t>(p[3]) << 24u);
}

bool readFully(int fd, off_t offset, void *buffer, size_t length) {
    auto *out = static_cast<unsigned char *>(buffer);
    size_t done = 0;
    while (done < length) {
        ssize_t count = pread(fd, out + done, length - done, offset + static_cast<off_t>(done));
        if (count < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        if (count == 0) return false;
        done += static_cast<size_t>(count);
    }
    return true;
}

std::string lowerCopy(std::string value) {
    for (char &ch : value) ch = static_cast<char>(std::tolower(static_cast<unsigned char>(ch)));
    return value;
}

bool endsWith(const std::string &value, const std::string &suffix) {
    return value.size() >= suffix.size()
            && value.compare(value.size() - suffix.size(), suffix.size(), suffix) == 0;
}

bool canonicalize(const std::string &path, std::string &out) {
    if (path.empty() || path[0] != '/') return false;
    char resolved[PATH_MAX];
    if (realpath(path.c_str(), resolved) == nullptr) return false;
    out.assign(resolved);
    return !out.empty() && out[0] == '/';
}

bool packageOk(const char *packageName) {
    return packageName != nullptr && std::strcmp(packageName, OBFUSCATE("OneCore.Vip")) == 0;
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
    if (dladdr(reinterpret_cast<void *>(&selfLibraryPath), &info) == 0 || info.dli_fname == nullptr) {
        return false;
    }
    std::string raw(info.dli_fname);
    if (raw.find('!') != std::string::npos || !endsWith(raw, "/libclient.so")) return false;
    return canonicalize(raw, libraryPath) && endsWith(libraryPath, "/libclient.so");
}

bool deriveBaseApk(std::string &baseApk, std::string &libraryPath) {
    if (!selfLibraryPath(libraryPath)) return false;
    const std::string marker = "/lib/arm64/libclient.so";
    size_t pos = libraryPath.rfind(marker);
    if (pos == std::string::npos || pos == 0u) return false;
    std::string installRoot = libraryPath.substr(0, pos);
    if (!(installRoot.rfind("/data/app/", 0) == 0 || installRoot.rfind("/mnt/expand/", 0) == 0)) {
        return false;
    }
    std::string candidate = installRoot + "/base.apk";
    if (!canonicalize(candidate, baseApk) || baseApk != candidate) return false;
    struct stat st{};
    return lstat(baseApk.c_str(), &st) == 0 && S_ISREG(st.st_mode)
            && (st.st_mode & (S_IWGRP | S_IWOTH)) == 0 && access(baseApk.c_str(), W_OK) != 0;
}

bool sameFile(const std::string &aPath, const std::string &bPath) {
    struct stat a{};
    struct stat b{};
    return stat(aPath.c_str(), &a) == 0 && stat(bPath.c_str(), &b) == 0
            && S_ISREG(a.st_mode) && S_ISREG(b.st_mode)
            && a.st_dev == b.st_dev && a.st_ino == b.st_ino && a.st_size == b.st_size;
}

bool externalCodePath(const std::string &path) {
    const std::string lower = lowerCopy(path);
    bool code = endsWith(lower, ".apk") || endsWith(lower, ".dex") || endsWith(lower, ".jar")
            || endsWith(lower, ".odex") || endsWith(lower, ".vdex");
    if (!code) return false;
    return lower.rfind("/data/local/tmp/", 0) == 0 || lower.rfind("/sdcard/", 0) == 0
            || lower.rfind("/storage/", 0) == 0;
}

bool mapsClean(const std::string &libraryPath) {
    std::ifstream maps("/proc/self/maps");
    if (!maps.is_open()) return false;
    std::set<std::string> clients;
    std::string line;
    while (std::getline(maps, line)) {
        size_t pathPos = line.find('/');
        if (pathPos == std::string::npos) continue;
        std::string path = line.substr(pathPos);
        size_t deleted = path.find(" (deleted)");
        if (deleted != std::string::npos) path.erase(deleted);
        if (externalCodePath(path)) return false;
        if (path.find("libclient.so") == std::string::npos) continue;
        std::istringstream parser(line);
        std::string range, perms;
        parser >> range >> perms;
        if (perms.find('w') != std::string::npos && perms.find('x') != std::string::npos) return false;
        std::string canonical;
        if (!canonicalize(path, canonical)) return false;
        clients.insert(canonical);
    }
    return clients.size() == 1u && *clients.begin() == libraryPath;
}

bool suspiciousOpenExternalCode() {
    DIR *dir = opendir("/proc/self/fd");
    if (dir == nullptr) return true;
    bool bad = false;
    dirent *entry = nullptr;
    while ((entry = readdir(dir)) != nullptr && !bad) {
        if (!std::isdigit(static_cast<unsigned char>(entry->d_name[0]))) continue;
        std::string fdPath = std::string("/proc/self/fd/") + entry->d_name;
        char target[PATH_MAX];
        ssize_t count = readlink(fdPath.c_str(), target, sizeof(target) - 1u);
        if (count <= 0) continue;
        target[count] = '\0';
        std::string path(target);
        size_t deleted = path.find(" (deleted)");
        if (deleted != std::string::npos) path.erase(deleted);
        if (externalCodePath(path)) bad = true;
    }
    closedir(dir);
    return bad;
}

bool nestedPayloadsPresent(const std::string &baseApk) {
    int fd = open(baseApk.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return true;
    struct stat st{};
    if (fstat(fd, &st) != 0 || st.st_size < static_cast<off_t>(ZIP_EOCD_MIN)) {
        close(fd); return true;
    }

    const uint64_t fileSize = static_cast<uint64_t>(st.st_size);
    const size_t search = static_cast<size_t>(std::min<uint64_t>(fileSize, ZIP_EOCD_SEARCH));
    const off_t tailOffset = st.st_size - static_cast<off_t>(search);
    std::vector<unsigned char> tail(search);
    if (!readFully(fd, tailOffset, tail.data(), tail.size())) { close(fd); return true; }

    uint32_t centralOffset = 0, centralSize = 0;
    bool found = false;
    for (size_t i = search - ZIP_EOCD_MIN + 1u; i-- > 0u;) {
        if (le32(tail.data() + i) != ZIP_EOCD_MAGIC) continue;
        uint16_t commentLen = le16(tail.data() + i + 20u);
        if (i + ZIP_EOCD_MIN + commentLen != search) continue;
        centralSize = le32(tail.data() + i + 12u);
        centralOffset = le32(tail.data() + i + 16u);
        found = true; break;
    }
    if (!found || static_cast<uint64_t>(centralOffset) + centralSize > fileSize) {
        close(fd); return true;
    }

    off_t cursor = static_cast<off_t>(centralOffset);
    const off_t end = cursor + static_cast<off_t>(centralSize);
    int manifests = 0, primaryDex = 0;
    bool bad = false;
    while (cursor < end) {
        unsigned char fixed[ZIP_CENTRAL_FIXED];
        if (!readFully(fd, cursor, fixed, sizeof(fixed)) || le32(fixed) != ZIP_CENTRAL_MAGIC) {
            bad = true; break;
        }
        uint16_t nameLen = le16(fixed + 28u), extraLen = le16(fixed + 30u), commentLen = le16(fixed + 32u);
        if (nameLen == 0u || nameLen > 4096u) { bad = true; break; }
        std::vector<char> name(nameLen);
        if (!readFully(fd, cursor + ZIP_CENTRAL_FIXED, name.data(), name.size())) { bad = true; break; }
        std::string raw(name.begin(), name.end());
        std::string lower = lowerCopy(raw);
        if (raw == "AndroidManifest.xml") manifests++;
        if (raw == "classes.dex") primaryDex++;
        bool payloadArea = lower.rfind("assets/", 0) == 0 || lower.rfind("res/raw/", 0) == 0;
        bool executable = endsWith(lower, ".apk") || endsWith(lower, ".dex") || endsWith(lower, ".jar")
                || endsWith(lower, ".odex") || endsWith(lower, ".vdex");
        bool wrapper = lower == "origin.apk" || endsWith(lower, "/origin.apk")
                || lower == "original.apk" || endsWith(lower, "/original.apk")
                || lower.find("original_app") != std::string::npos
                || lower == "backup.apk" || endsWith(lower, "/backup.apk");
        if ((!raw.empty() && raw[0] == '/') || lower.find("../") != std::string::npos
                || wrapper || (payloadArea && executable)) { bad = true; break; }
        uint64_t advance = ZIP_CENTRAL_FIXED + static_cast<uint64_t>(nameLen) + extraLen + commentLen;
        if (advance > static_cast<uint64_t>(end - cursor)) { bad = true; break; }
        cursor += static_cast<off_t>(advance);
    }
    close(fd);
    return bad || cursor != end || manifests != 1 || primaryDex != 1;
}

bool verify(const char *claimedPath, const char *packageName) {
    if (!packageOk(packageName) || !processNameMatches() || claimedPath == nullptr) return false;
    std::string baseApk, libraryPath, claimed;
    if (!deriveBaseApk(baseApk, libraryPath) || !canonicalize(claimedPath, claimed)) return false;
    if (claimed != baseApk || !sameFile(claimed, baseApk)) return false;
    if (!mapsClean(libraryPath) || suspiciousOpenExternalCode() || nestedPayloadsPresent(baseApk)) return false;
    return mapsClean(libraryPath) && sameFile(claimed, baseApk);
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pubgm_security_WrapperPayloadGuard_verifyArchiveAndRuntimeNative(
        JNIEnv *env, jclass, jstring apkPath, jstring packageName) {
    if (apkPath == nullptr || packageName == nullptr) return JNI_FALSE;
    const char *apk = env->GetStringUTFChars(apkPath, nullptr);
    const char *pkg = env->GetStringUTFChars(packageName, nullptr);
    if (apk == nullptr || pkg == nullptr) {
        if (apk != nullptr) env->ReleaseStringUTFChars(apkPath, apk);
        if (pkg != nullptr) env->ReleaseStringUTFChars(packageName, pkg);
        return JNI_FALSE;
    }
    bool ok = verify(apk, pkg);
    env->ReleaseStringUTFChars(apkPath, apk);
    env->ReleaseStringUTFChars(packageName, pkg);
    return ok ? JNI_TRUE : JNI_FALSE;
}
