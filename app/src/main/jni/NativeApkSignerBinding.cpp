#include <jni.h>
#include <obfuscate.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#include <cerrno>
#include <fcntl.h>
#include <limits.h>
#include <sys/stat.h>
#include <unistd.h>

namespace {
constexpr uint32_t EOCD_MAGIC = 0x06054b50u;
constexpr uint32_t V2_BLOCK_ID = 0x7109871au;
constexpr size_t EOCD_MIN = 22u;
constexpr size_t EOCD_SEARCH = EOCD_MIN + 65535u;
constexpr size_t SIGNING_FOOTER = 24u;
constexpr uint64_t MAX_SIGNING_VALUE = 16u * 1024u * 1024u;
constexpr char APK_SIG_MAGIC[16] = {
        'A','P','K',' ','S','i','g',' ','B','l','o','c','k',' ','4','2'
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

uint64_t le64(const uint8_t *p) {
    return static_cast<uint64_t>(le32(p))
            | (static_cast<uint64_t>(le32(p + 4u)) << 32u);
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
    return cmdline == expected || cmdline.rfind(expected + ":", 0) == 0;
}

bool validInstalledBasePath(const char *apkPath, std::string &canonical) {
    if (apkPath == nullptr || apkPath[0] == '\0' || !canonicalize(apkPath, canonical)) return false;
    if (canonical.size() < 9u
            || canonical.compare(canonical.size() - 9u, 9u, "/base.apk") != 0) {
        return false;
    }
    if (!(canonical.rfind("/data/app/", 0) == 0
            || canonical.rfind("/mnt/expand/", 0) == 0)) {
        return false;
    }
    struct stat st{};
    return lstat(canonical.c_str(), &st) == 0
            && S_ISREG(st.st_mode)
            && (st.st_mode & (S_IWGRP | S_IWOTH)) == 0;
}

bool findCentralDirectory(int fd, off_t fileSize, uint32_t &centralOffset) {
    if (fileSize < static_cast<off_t>(EOCD_MIN)) return false;
    const size_t search = static_cast<size_t>(std::min<uint64_t>(
            static_cast<uint64_t>(fileSize), EOCD_SEARCH));
    const off_t tailOffset = fileSize - static_cast<off_t>(search);
    std::vector<uint8_t> tail(search);
    if (!readFully(fd, tailOffset, tail.data(), tail.size())) return false;

    for (size_t i = search - EOCD_MIN + 1u; i-- > 0u;) {
        if (le32(tail.data() + i) != EOCD_MAGIC) continue;
        const uint16_t commentLength = le16(tail.data() + i + 20u);
        if (i + EOCD_MIN + commentLength != search) continue;
        if (le16(tail.data() + i + 4u) != 0u || le16(tail.data() + i + 6u) != 0u) {
            return false;
        }
        const uint32_t value = le32(tail.data() + i + 16u);
        if (value == 0xffffffffu
                || static_cast<uint64_t>(value) >= static_cast<uint64_t>(fileSize)) {
            return false;
        }
        centralOffset = value;
        return true;
    }
    return false;
}

bool readLengthPrefixed32(const uint8_t *&cursor, const uint8_t *end,
                          const uint8_t *&data, size_t &length) {
    if (cursor > end || static_cast<size_t>(end - cursor) < 4u) return false;
    const uint32_t value = le32(cursor);
    cursor += 4u;
    if (static_cast<uint64_t>(value) > static_cast<uint64_t>(end - cursor)) return false;
    data = cursor;
    length = static_cast<size_t>(value);
    cursor += length;
    return true;
}

bool signerLeafCertificate(const uint8_t *signer, size_t signerLength,
                           std::vector<uint8_t> &leafCertificate) {
    const uint8_t *cursor = signer;
    const uint8_t *end = signer + signerLength;
    const uint8_t *signedData = nullptr;
    const uint8_t *ignored = nullptr;
    size_t signedDataLength = 0u;
    size_t ignoredLength = 0u;

    if (!readLengthPrefixed32(cursor, end, signedData, signedDataLength)
            || !readLengthPrefixed32(cursor, end, ignored, ignoredLength)
            || !readLengthPrefixed32(cursor, end, ignored, ignoredLength)
            || cursor != end) {
        return false;
    }

    cursor = signedData;
    end = signedData + signedDataLength;
    const uint8_t *digests = nullptr;
    const uint8_t *certificates = nullptr;
    const uint8_t *attributes = nullptr;
    size_t digestsLength = 0u;
    size_t certificatesLength = 0u;
    size_t attributesLength = 0u;
    if (!readLengthPrefixed32(cursor, end, digests, digestsLength)
            || !readLengthPrefixed32(cursor, end, certificates, certificatesLength)
            || !readLengthPrefixed32(cursor, end, attributes, attributesLength)
            || certificatesLength == 0u) {
        return false;
    }

    // Current apksig may append one compatibility/stripping-protection slot to v2 signedData.
    // Accept it only when it is present as one empty length-prefixed field; reject arbitrary tail data.
    if (cursor < end) {
        const uint8_t *compat = nullptr;
        size_t compatLength = 0u;
        if (!readLengthPrefixed32(cursor, end, compat, compatLength) || compatLength != 0u) {
            return false;
        }
    }
    if (cursor != end) return false;

    cursor = certificates;
    end = certificates + certificatesLength;
    const uint8_t *leaf = nullptr;
    size_t leafLength = 0u;
    if (!readLengthPrefixed32(cursor, end, leaf, leafLength) || leafLength == 0u) return false;
    leafCertificate.assign(leaf, leaf + leafLength);

    // Validate the remaining chain as well instead of silently accepting malformed trailing bytes.
    while (cursor < end) {
        const uint8_t *certificate = nullptr;
        size_t certificateLength = 0u;
        if (!readLengthPrefixed32(cursor, end, certificate, certificateLength)
                || certificateLength == 0u) {
            return false;
        }
    }
    return cursor == end;
}

bool parseV2LeafCertificates(const uint8_t *value, size_t valueLength,
                             std::vector<std::vector<uint8_t>> &certificates) {
    const uint8_t *cursor = value;
    const uint8_t *end = value + valueLength;
    const uint8_t *signers = nullptr;
    size_t signersLength = 0u;
    if (!readLengthPrefixed32(cursor, end, signers, signersLength)
            || cursor != end || signersLength == 0u) {
        return false;
    }

    cursor = signers;
    end = signers + signersLength;
    while (cursor < end) {
        const uint8_t *signer = nullptr;
        size_t signerLength = 0u;
        if (!readLengthPrefixed32(cursor, end, signer, signerLength)
                || signerLength == 0u || certificates.size() >= 8u) {
            return false;
        }
        std::vector<uint8_t> leaf;
        if (!signerLeafCertificate(signer, signerLength, leaf)) return false;
        for (const auto &existing : certificates) {
            if (existing == leaf) return false;
        }
        certificates.push_back(std::move(leaf));
    }
    return cursor == end && !certificates.empty();
}

bool extractV2LeafCertificates(int fd, off_t fileSize,
                               std::vector<std::vector<uint8_t>> &certificates) {
    uint32_t centralOffset = 0u;
    if (!findCentralDirectory(fd, fileSize, centralOffset)
            || centralOffset < SIGNING_FOOTER) {
        return false;
    }

    uint8_t footer[SIGNING_FOOTER];
    const off_t footerOffset = static_cast<off_t>(centralOffset) - SIGNING_FOOTER;
    if (!readFully(fd, footerOffset, footer, sizeof(footer))
            || std::memcmp(footer + 8u, APK_SIG_MAGIC, sizeof(APK_SIG_MAGIC)) != 0) {
        return false;
    }

    const uint64_t blockSize = le64(footer);
    if (blockSize < SIGNING_FOOTER || blockSize > static_cast<uint64_t>(centralOffset)) {
        return false;
    }
    const uint64_t totalBlockSize = blockSize + 8u;
    if (totalBlockSize > static_cast<uint64_t>(centralOffset)) return false;
    const off_t blockStart = static_cast<off_t>(centralOffset - totalBlockSize);

    uint8_t firstSize[8];
    if (!readFully(fd, blockStart, firstSize, sizeof(firstSize))
            || le64(firstSize) != blockSize) {
        return false;
    }

    off_t cursor = blockStart + 8;
    const off_t pairsEnd = footerOffset;
    bool foundV2 = false;
    while (cursor < pairsEnd) {
        if (pairsEnd - cursor < 8) return false;
        uint8_t lengthBytes[8];
        if (!readFully(fd, cursor, lengthBytes, sizeof(lengthBytes))) return false;
        const uint64_t pairLength = le64(lengthBytes);
        const uint64_t remaining = static_cast<uint64_t>(pairsEnd - cursor - 8);
        if (pairLength < 4u || pairLength > MAX_SIGNING_VALUE || pairLength > remaining) {
            return false;
        }

        uint8_t idBytes[4];
        if (!readFully(fd, cursor + 8, idBytes, sizeof(idBytes))) return false;
        const uint32_t id = le32(idBytes);
        const size_t valueLength = static_cast<size_t>(pairLength - 4u);
        if (id == V2_BLOCK_ID) {
            if (foundV2 || valueLength == 0u) return false;
            std::vector<uint8_t> value(valueLength);
            if (!readFully(fd, cursor + 12, value.data(), value.size())
                    || !parseV2LeafCertificates(value.data(), value.size(), certificates)) {
                return false;
            }
            foundV2 = true;
        }
        cursor += static_cast<off_t>(8u + pairLength);
    }
    return foundV2 && cursor == pairsEnd && !certificates.empty();
}

bool readCertificates(const char *apkPath, const char *packageName,
                      std::vector<std::vector<uint8_t>> &certificates) {
    if (!packageAndProcessOk(packageName)) return false;
    std::string canonical;
    if (!validInstalledBasePath(apkPath, canonical)) return false;

    int fd = open(canonical.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return false;
    struct stat st{};
    bool ok = fstat(fd, &st) == 0 && S_ISREG(st.st_mode)
            && st.st_size > static_cast<off_t>(EOCD_MIN)
            && (st.st_mode & (S_IWGRP | S_IWOTH)) == 0;
    if (ok) ok = extractV2LeafCertificates(fd, st.st_size, certificates);
    close(fd);
    return ok;
}
} // namespace

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_pubgm_security_NativeSigningVerifier_readApkV2SignerCertificatesNative(
        JNIEnv *env,
        jclass,
        jstring apkPath,
        jstring packageName) {
    if (apkPath == nullptr || packageName == nullptr) return nullptr;
    const char *apk = env->GetStringUTFChars(apkPath, nullptr);
    const char *pkg = env->GetStringUTFChars(packageName, nullptr);
    if (apk == nullptr || pkg == nullptr) {
        if (apk != nullptr) env->ReleaseStringUTFChars(apkPath, apk);
        if (pkg != nullptr) env->ReleaseStringUTFChars(packageName, pkg);
        return nullptr;
    }

    std::vector<std::vector<uint8_t>> certificates;
    const bool ok = readCertificates(apk, pkg, certificates);
    env->ReleaseStringUTFChars(apkPath, apk);
    env->ReleaseStringUTFChars(packageName, pkg);
    if (!ok || certificates.empty()) return nullptr;

    jclass byteArrayClass = env->FindClass("[B");
    if (byteArrayClass == nullptr) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return nullptr;
    }
    jobjectArray result = env->NewObjectArray(
            static_cast<jsize>(certificates.size()), byteArrayClass, nullptr);
    env->DeleteLocalRef(byteArrayClass);
    if (result == nullptr) return nullptr;

    for (size_t i = 0u; i < certificates.size(); ++i) {
        const auto &certificate = certificates[i];
        if (certificate.empty() || certificate.size() > static_cast<size_t>(INT32_MAX)) return nullptr;
        jbyteArray item = env->NewByteArray(static_cast<jsize>(certificate.size()));
        if (item == nullptr) return nullptr;
        env->SetByteArrayRegion(item, 0, static_cast<jsize>(certificate.size()),
                                reinterpret_cast<const jbyte *>(certificate.data()));
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            env->DeleteLocalRef(item);
            return nullptr;
        }
        env->SetObjectArrayElement(result, static_cast<jsize>(i), item);
        env->DeleteLocalRef(item);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return nullptr;
        }
    }
    return result;
}
