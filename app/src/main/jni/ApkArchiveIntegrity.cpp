#include <jni.h>
#include <obfuscate.h>

#include <algorithm>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <set>
#include <string>
#include <vector>

#include <fcntl.h>
#include <limits.h>
#include <sys/stat.h>
#include <unistd.h>
#include <zlib.h>

namespace {
constexpr uint32_t EOCD_MAGIC = 0x06054b50u;
constexpr uint32_t CENTRAL_MAGIC = 0x02014b50u;
constexpr uint32_t LOCAL_MAGIC = 0x04034b50u;
constexpr size_t EOCD_MIN = 22u;
constexpr size_t EOCD_SEARCH = EOCD_MIN + 65535u;
constexpr size_t CENTRAL_FIXED = 46u;
constexpr size_t LOCAL_FIXED = 30u;
constexpr uint64_t MAX_ENTRY_BYTES = 256ull * 1024ull * 1024ull;
constexpr size_t MAX_CRITICAL_ENTRIES = 4096u;
constexpr size_t IO_BUFFER = 32u * 1024u;

struct Entry {
    std::string name;
    uint16_t flags = 0u;
    uint16_t method = 0u;
    uint32_t crc = 0u;
    uint32_t compressedSize = 0u;
    uint32_t size = 0u;
    uint32_t localOffset = 0u;
};

uint16_t le16(const uint8_t *p) {
    return static_cast<uint16_t>(p[0])
            | (static_cast<uint16_t>(p[1]) << 8u);
}

uint32_t le32(const uint8_t *p) {
    return static_cast<uint32_t>(p[0])
            | (static_cast<uint32_t>(p[1]) << 8u)
            | (static_cast<uint32_t>(p[2]) << 16u)
            | (static_cast<uint32_t>(p[3]) << 24u);
}

bool readFully(int fd, off_t offset, void *buffer, size_t length) {
    auto *out = static_cast<uint8_t *>(buffer);
    size_t done = 0u;
    while (done < length) {
        ssize_t count = pread(fd, out + done, length - done,
                              offset + static_cast<off_t>(done));
        if (count < 0) {
            if (errno == EINTR) continue;
            return false;
        }
        if (count == 0) return false;
        done += static_cast<size_t>(count);
    }
    return true;
}

bool canonicalize(const std::string &path, std::string &out) {
    if (path.empty() || path[0] != '/') return false;
    char resolved[PATH_MAX];
    if (realpath(path.c_str(), resolved) == nullptr) return false;
    out.assign(resolved);
    return !out.empty() && out[0] == '/';
}

bool packageAndProcessOk(const char *packageName) {
    if (packageName == nullptr || std::strcmp(packageName, OBFUSCATE("OneCore.Vip")) != 0) {
        return false;
    }
    int fd = open("/proc/self/cmdline", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    char buffer[256]{};
    ssize_t count = read(fd, buffer, sizeof(buffer) - 1u);
    close(fd);
    if (count <= 0) return false;
    std::string cmdline(buffer, strnlen(buffer, static_cast<size_t>(count)));
    const std::string expected = OBFUSCATE("OneCore.Vip");
    return cmdline == expected || cmdline.rfind(expected + ":", 0u) == 0u;
}

bool validInstalledBasePath(const char *apkPath, std::string &canonical) {
    if (apkPath == nullptr || apkPath[0] == '\0'
            || !canonicalize(apkPath, canonical)) {
        return false;
    }
    if (canonical.size() < 9u
            || canonical.compare(canonical.size() - 9u, 9u, "/base.apk") != 0) {
        return false;
    }
    if (!(canonical.rfind("/data/app/", 0u) == 0u
            || canonical.rfind("/mnt/expand/", 0u) == 0u)) {
        return false;
    }
    struct stat st{};
    return lstat(canonical.c_str(), &st) == 0
            && S_ISREG(st.st_mode)
            && (st.st_mode & (S_IWGRP | S_IWOTH)) == 0
            && access(canonical.c_str(), W_OK) != 0;
}

bool endsWith(const std::string &value, const std::string &suffix) {
    return value.size() >= suffix.size()
            && value.compare(value.size() - suffix.size(), suffix.size(), suffix) == 0;
}

bool safeName(const std::string &name) {
    if (name.empty() || name.size() > 4096u || name[0] == '/'
            || name.find('\\') != std::string::npos
            || name.find('|') != std::string::npos
            || name == ".." || name.rfind("../", 0u) == 0u
            || name.find("../") != std::string::npos) {
        return false;
    }
    for (unsigned char ch : name) {
        if (ch == 0u || ch == '\n' || ch == '\r') return false;
    }
    return true;
}

bool isDexName(const std::string &name) {
    if (name == "classes.dex") return true;
    constexpr char prefix[] = "classes";
    constexpr char suffix[] = ".dex";
    if (name.size() <= (sizeof(prefix) - 1u) + (sizeof(suffix) - 1u)
            || name.rfind(prefix, 0u) != 0u || !endsWith(name, suffix)) {
        return false;
    }
    const size_t begin = sizeof(prefix) - 1u;
    const size_t end = name.size() - (sizeof(suffix) - 1u);
    if (begin >= end) return false;
    for (size_t i = begin; i < end; ++i) {
        if (name[i] < '0' || name[i] > '9') return false;
    }
    return true;
}

bool isCritical(const std::string &name) {
    return name == "AndroidManifest.xml"
            || name == "resources.arsc"
            || name == "lib/arm64-v8a/libclient.so"
            || isDexName(name)
            || name.rfind("assets/", 0u) == 0u;
}

bool findEocd(int fd, off_t fileSize, uint16_t &entryCount,
              uint32_t &centralOffset, uint32_t &centralSize) {
    if (fileSize < static_cast<off_t>(EOCD_MIN)) return false;
    const size_t search = static_cast<size_t>(std::min<uint64_t>(
            static_cast<uint64_t>(fileSize), EOCD_SEARCH));
    const off_t tailOffset = fileSize - static_cast<off_t>(search);
    std::vector<uint8_t> tail(search);
    if (!readFully(fd, tailOffset, tail.data(), tail.size())) return false;

    for (size_t pos = search - EOCD_MIN + 1u; pos > 0u; --pos) {
        const size_t i = pos - 1u;
        if (le32(tail.data() + i) != EOCD_MAGIC) continue;
        const uint16_t commentLength = le16(tail.data() + i + 20u);
        if (i + EOCD_MIN + commentLength != search) continue;

        const uint16_t disk = le16(tail.data() + i + 4u);
        const uint16_t centralDisk = le16(tail.data() + i + 6u);
        const uint16_t entriesOnDisk = le16(tail.data() + i + 8u);
        const uint16_t totalEntries = le16(tail.data() + i + 10u);
        const uint32_t size = le32(tail.data() + i + 12u);
        const uint32_t offset = le32(tail.data() + i + 16u);
        if (disk != 0u || centralDisk != 0u || entriesOnDisk != totalEntries
                || totalEntries == 0xffffu || size == 0xffffffffu || offset == 0xffffffffu) {
            return false;
        }
        if (static_cast<uint64_t>(offset) + size > static_cast<uint64_t>(fileSize)) {
            return false;
        }
        entryCount = totalEntries;
        centralOffset = offset;
        centralSize = size;
        return totalEntries > 0u;
    }
    return false;
}

bool validateLocalHeader(int fd, const Entry &entry, uint32_t centralOffset,
                         off_t &dataOffset) {
    if (entry.localOffset >= centralOffset) return false;
    uint8_t fixed[LOCAL_FIXED];
    if (!readFully(fd, static_cast<off_t>(entry.localOffset), fixed, sizeof(fixed))
            || le32(fixed) != LOCAL_MAGIC) {
        return false;
    }
    const uint16_t localFlags = le16(fixed + 6u);
    const uint16_t localMethod = le16(fixed + 8u);
    const uint16_t nameLength = le16(fixed + 26u);
    const uint16_t extraLength = le16(fixed + 28u);
    if ((localFlags & 0x0001u) != 0u || localMethod != entry.method
            || nameLength == 0u || nameLength > 4096u) {
        return false;
    }
    std::vector<char> name(nameLength);
    if (!readFully(fd, static_cast<off_t>(entry.localOffset + LOCAL_FIXED),
                   name.data(), name.size())) {
        return false;
    }
    if (std::string(name.begin(), name.end()) != entry.name) return false;

    const uint64_t start = static_cast<uint64_t>(entry.localOffset)
            + LOCAL_FIXED + nameLength + extraLength;
    const uint64_t end = start + entry.compressedSize;
    if (start > centralOffset || end > centralOffset) return false;
    dataOffset = static_cast<off_t>(start);
    return true;
}

bool verifyStoredCrc(int fd, off_t dataOffset, const Entry &entry) {
    if (entry.compressedSize != entry.size) return false;
    std::vector<uint8_t> buffer(IO_BUFFER);
    uint64_t remaining = entry.size;
    off_t cursor = dataOffset;
    uLong crc = crc32(0L, Z_NULL, 0);
    while (remaining > 0u) {
        const size_t amount = static_cast<size_t>(std::min<uint64_t>(remaining, buffer.size()));
        if (!readFully(fd, cursor, buffer.data(), amount)) return false;
        crc = crc32(crc, reinterpret_cast<const Bytef *>(buffer.data()),
                    static_cast<uInt>(amount));
        cursor += static_cast<off_t>(amount);
        remaining -= amount;
    }
    return static_cast<uint32_t>(crc) == entry.crc;
}

bool verifyDeflatedCrc(int fd, off_t dataOffset, const Entry &entry) {
    std::vector<uint8_t> input(IO_BUFFER);
    std::vector<uint8_t> output(IO_BUFFER);
    z_stream stream{};
    if (inflateInit2(&stream, -MAX_WBITS) != Z_OK) return false;

    bool ok = true;
    bool ended = false;
    uint64_t remaining = entry.compressedSize;
    uint64_t producedTotal = 0u;
    off_t cursor = dataOffset;
    uLong crc = crc32(0L, Z_NULL, 0);

    while (ok && remaining > 0u && !ended) {
        const size_t amount = static_cast<size_t>(std::min<uint64_t>(remaining, input.size()));
        if (!readFully(fd, cursor, input.data(), amount)) {
            ok = false;
            break;
        }
        cursor += static_cast<off_t>(amount);
        remaining -= amount;
        stream.next_in = reinterpret_cast<Bytef *>(input.data());
        stream.avail_in = static_cast<uInt>(amount);

        while (ok && stream.avail_in > 0u && !ended) {
            stream.next_out = reinterpret_cast<Bytef *>(output.data());
            stream.avail_out = static_cast<uInt>(output.size());
            const int code = inflate(&stream, Z_NO_FLUSH);
            const size_t produced = output.size() - stream.avail_out;
            if (produced > 0u) {
                producedTotal += produced;
                if (producedTotal > entry.size || producedTotal > MAX_ENTRY_BYTES) {
                    ok = false;
                    break;
                }
                crc = crc32(crc, reinterpret_cast<const Bytef *>(output.data()),
                            static_cast<uInt>(produced));
            }
            if (code == Z_STREAM_END) {
                ended = true;
            } else if (code != Z_OK) {
                ok = false;
            } else if (produced == 0u && stream.avail_in > 0u && stream.avail_out > 0u) {
                ok = false;
            }
        }
    }

    if (ok && ended && (remaining != 0u || stream.avail_in != 0u)) ok = false;
    if (ok && (!ended || producedTotal != entry.size
            || static_cast<uint64_t>(stream.total_in) != entry.compressedSize
            || static_cast<uint32_t>(crc) != entry.crc)) {
        ok = false;
    }
    inflateEnd(&stream);
    return ok;
}

bool verifyEntryCrc(int fd, off_t dataOffset, const Entry &entry) {
    if (entry.size > MAX_ENTRY_BYTES || entry.compressedSize > MAX_ENTRY_BYTES) return false;
    if (entry.method == 0u) return verifyStoredCrc(fd, dataOffset, entry);
    if (entry.method == 8u) return verifyDeflatedCrc(fd, dataOffset, entry);
    return false;
}

std::string row(const Entry &entry) {
    return entry.name + "|" + std::to_string(static_cast<uint64_t>(entry.crc))
            + "|" + std::to_string(static_cast<uint64_t>(entry.size))
            + "|" + std::to_string(static_cast<uint64_t>(entry.compressedSize))
            + "|" + std::to_string(static_cast<unsigned int>(entry.method));
}

bool buildMap(const char *apkPath, const char *packageName, bool verifyContentCrc,
              std::vector<std::string> &rows) {
    if (!packageAndProcessOk(packageName)) return false;
    std::string canonical;
    if (!validInstalledBasePath(apkPath, canonical)) return false;

    int fd = open(canonical.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return false;
    struct stat st{};
    bool ok = fstat(fd, &st) == 0 && S_ISREG(st.st_mode)
            && st.st_size >= static_cast<off_t>(EOCD_MIN)
            && (st.st_mode & (S_IWGRP | S_IWOTH)) == 0;

    uint16_t expectedEntries = 0u;
    uint32_t centralOffset = 0u;
    uint32_t centralSize = 0u;
    if (ok) ok = findEocd(fd, st.st_size, expectedEntries, centralOffset, centralSize);

    std::set<std::string> allNames;
    uint32_t seenEntries = 0u;
    int manifestCount = 0;
    int primaryDexCount = 0;
    int resourcesCount = 0;
    int clientCount = 0;
    off_t cursor = static_cast<off_t>(centralOffset);
    const off_t centralEnd = cursor + static_cast<off_t>(centralSize);

    while (ok && cursor < centralEnd) {
        uint8_t fixed[CENTRAL_FIXED];
        if (!readFully(fd, cursor, fixed, sizeof(fixed)) || le32(fixed) != CENTRAL_MAGIC) {
            ok = false;
            break;
        }
        const uint16_t flags = le16(fixed + 8u);
        const uint16_t method = le16(fixed + 10u);
        const uint32_t crc = le32(fixed + 16u);
        const uint32_t compressedSize = le32(fixed + 20u);
        const uint32_t size = le32(fixed + 24u);
        const uint16_t nameLength = le16(fixed + 28u);
        const uint16_t extraLength = le16(fixed + 30u);
        const uint16_t commentLength = le16(fixed + 32u);
        const uint16_t diskStart = le16(fixed + 34u);
        const uint32_t localOffset = le32(fixed + 42u);
        if (nameLength == 0u || nameLength > 4096u || diskStart != 0u
                || localOffset == 0xffffffffu || size == 0xffffffffu
                || compressedSize == 0xffffffffu || (flags & 0x0001u) != 0u) {
            ok = false;
            break;
        }

        const uint64_t advance = CENTRAL_FIXED + static_cast<uint64_t>(nameLength)
                + extraLength + commentLength;
        if (advance > static_cast<uint64_t>(centralEnd - cursor)) {
            ok = false;
            break;
        }
        std::vector<char> nameBytes(nameLength);
        if (!readFully(fd, cursor + CENTRAL_FIXED, nameBytes.data(), nameBytes.size())) {
            ok = false;
            break;
        }
        std::string name(nameBytes.begin(), nameBytes.end());
        if (!safeName(name) || !allNames.insert(name).second) {
            ok = false;
            break;
        }
        ++seenEntries;

        const bool directory = !name.empty() && name.back() == '/';
        if (!directory) {
            if (name == "AndroidManifest.xml") ++manifestCount;
            if (name == "classes.dex") ++primaryDexCount;
            if (name == "resources.arsc") ++resourcesCount;
            if (name == "lib/arm64-v8a/libclient.so") ++clientCount;

            if (isCritical(name)) {
                if (rows.size() >= MAX_CRITICAL_ENTRIES
                        || size > MAX_ENTRY_BYTES || compressedSize > MAX_ENTRY_BYTES
                        || (method != 0u && method != 8u)) {
                    ok = false;
                    break;
                }
                Entry entry{name, flags, method, crc, compressedSize, size, localOffset};
                off_t dataOffset = 0;
                if (!validateLocalHeader(fd, entry, centralOffset, dataOffset)
                        || (verifyContentCrc && !verifyEntryCrc(fd, dataOffset, entry))) {
                    ok = false;
                    break;
                }
                rows.push_back(row(entry));
            }
        }
        cursor += static_cast<off_t>(advance);
    }

    if (ok && (cursor != centralEnd || seenEntries != expectedEntries
            || manifestCount != 1 || primaryDexCount != 1
            || resourcesCount != 1 || clientCount != 1 || rows.empty())) {
        ok = false;
    }
    close(fd);
    if (!ok) {
        rows.clear();
        return false;
    }
    std::sort(rows.begin(), rows.end());
    return true;
}
} // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_pubgm_security_ApkArchiveIntegrity_readCriticalArchiveMapNative(
        JNIEnv *env,
        jclass,
        jstring apkPath,
        jstring packageName,
        jboolean verifyContentCrc) {
    if (apkPath == nullptr || packageName == nullptr) return nullptr;
    const char *apk = env->GetStringUTFChars(apkPath, nullptr);
    const char *pkg = env->GetStringUTFChars(packageName, nullptr);
    if (apk == nullptr || pkg == nullptr) {
        if (apk != nullptr) env->ReleaseStringUTFChars(apkPath, apk);
        if (pkg != nullptr) env->ReleaseStringUTFChars(packageName, pkg);
        return nullptr;
    }

    std::vector<std::string> rows;
    const bool ok = buildMap(apk, pkg, verifyContentCrc == JNI_TRUE, rows);
    env->ReleaseStringUTFChars(apkPath, apk);
    env->ReleaseStringUTFChars(packageName, pkg);
    if (!ok || rows.empty()) return nullptr;

    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(rows.size()), stringClass, nullptr);
    env->DeleteLocalRef(stringClass);
    if (result == nullptr) return nullptr;

    for (size_t i = 0u; i < rows.size(); ++i) {
        jstring value = env->NewStringUTF(rows[i].c_str());
        if (value == nullptr) {
            if (env->ExceptionCheck()) env->ExceptionClear();
            return nullptr;
        }
        env->SetObjectArrayElement(result, static_cast<jsize>(i), value);
        env->DeleteLocalRef(value);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return nullptr;
        }
    }
    return result;
}
