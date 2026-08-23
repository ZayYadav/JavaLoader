#include <jni.h>
#include <obfuscate.h>

#include <algorithm>
#include <array>
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
constexpr size_t SHA256_SIZE = 32u;
constexpr size_t SHA256_BLOCK = 64u;
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

struct Sha256Context {
    uint8_t data[SHA256_BLOCK]{};
    size_t dataLength = 0u;
    uint64_t bitLength = 0u;
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
    for (size_t i = 0; i < 16u; ++i) {
        const size_t j = i * 4u;
        w[i] = (static_cast<uint32_t>(block[j]) << 24u)
                | (static_cast<uint32_t>(block[j + 1u]) << 16u)
                | (static_cast<uint32_t>(block[j + 2u]) << 8u)
                | static_cast<uint32_t>(block[j + 3u]);
    }
    for (size_t i = 16u; i < 64u; ++i) {
        const uint32_t s0 = rotr(w[i - 15u], 7u) ^ rotr(w[i - 15u], 18u)
                ^ (w[i - 15u] >> 3u);
        const uint32_t s1 = rotr(w[i - 2u], 17u) ^ rotr(w[i - 2u], 19u)
                ^ (w[i - 2u] >> 10u);
        w[i] = w[i - 16u] + s0 + w[i - 7u] + s1;
    }

    uint32_t a = ctx.state[0], b = ctx.state[1], c = ctx.state[2], d = ctx.state[3];
    uint32_t e = ctx.state[4], f = ctx.state[5], g = ctx.state[6], h = ctx.state[7];
    for (size_t i = 0; i < 64u; ++i) {
        const uint32_t s1 = rotr(e, 6u) ^ rotr(e, 11u) ^ rotr(e, 25u);
        const uint32_t ch = (e & f) ^ ((~e) & g);
        const uint32_t temp1 = h + s1 + ch + K[i] + w[i];
        const uint32_t s0 = rotr(a, 2u) ^ rotr(a, 13u) ^ rotr(a, 22u);
        const uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        const uint32_t temp2 = s0 + maj;
        h = g; g = f; f = e; e = d + temp1;
        d = c; c = b; b = a; a = temp1 + temp2;
    }

    ctx.state[0] += a; ctx.state[1] += b; ctx.state[2] += c; ctx.state[3] += d;
    ctx.state[4] += e; ctx.state[5] += f; ctx.state[6] += g; ctx.state[7] += h;
    std::fill(std::begin(w), std::end(w), 0u);
}

void sha256Update(Sha256Context &ctx, const uint8_t *data, size_t length) {
    for (size_t i = 0; i < length; ++i) {
        ctx.data[ctx.dataLength++] = data[i];
        if (ctx.dataLength == SHA256_BLOCK) {
            sha256Transform(ctx, ctx.data);
            ctx.bitLength += 512u;
            ctx.dataLength = 0u;
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
    for (size_t offset = 0u; offset < 8u; ++offset) {
        ctx.data[63u - offset] = static_cast<uint8_t>(
                (ctx.bitLength >> (offset * 8u)) & 0xffu);
    }
    sha256Transform(ctx, ctx.data);

    std::array<uint8_t, SHA256_SIZE> result{};
    for (size_t word = 0u; word < 8u; ++word) {
        result[word * 4u] = static_cast<uint8_t>((ctx.state[word] >> 24u) & 0xffu);
        result[word * 4u + 1u] = static_cast<uint8_t>((ctx.state[word] >> 16u) & 0xffu);
        result[word * 4u + 2u] = static_cast<uint8_t>((ctx.state[word] >> 8u) & 0xffu);
        result[word * 4u + 3u] = static_cast<uint8_t>(ctx.state[word] & 0xffu);
    }
    std::memset(&ctx, 0, sizeof(ctx));
    return result;
}

bool constantTimeEqual(const uint8_t *a, const uint8_t *b, size_t length) {
    uint8_t diff = 0u;
    for (size_t i = 0u; i < length; ++i) diff |= a[i] ^ b[i];
    return diff == 0u;
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
        if (value == 0xffffffffu || static_cast<uint64_t>(value) >= static_cast<uint64_t>(fileSize)) {
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

bool signerLeafDigest(const uint8_t *signer, size_t signerLength,
                      std::array<uint8_t, SHA256_SIZE> &digest) {
    const uint8_t *cursor = signer;
    const uint8_t *end = signer + signerLength;
    const uint8_t *signedData = nullptr, *ignored = nullptr;
    size_t signedDataLength = 0u, ignoredLength = 0u;
    if (!readLengthPrefixed32(cursor, end, signedData, signedDataLength)
            || !readLengthPrefixed32(cursor, end, ignored, ignoredLength)
            || !readLengthPrefixed32(cursor, end, ignored, ignoredLength)
            || cursor != end) {
        return false;
    }

    cursor = signedData;
    end = signedData + signedDataLength;
    const uint8_t *digests = nullptr, *certificates = nullptr, *attributes = nullptr;
    size_t digestsLength = 0u, certificatesLength = 0u, attributesLength = 0u;
    if (!readLengthPrefixed32(cursor, end, digests, digestsLength)
            || !readLengthPrefixed32(cursor, end, certificates, certificatesLength)
            || !readLengthPrefixed32(cursor, end, attributes, attributesLength)
            || cursor != end || certificatesLength == 0u) {
        return false;
    }

    cursor = certificates;
    end = certificates + certificatesLength;
    const uint8_t *leaf = nullptr;
    size_t leafLength = 0u;
    if (!readLengthPrefixed32(cursor, end, leaf, leafLength) || leafLength == 0u) return false;
    digest = sha256(leaf, leafLength);
    return true;
}

bool parseV2SignerDigests(const uint8_t *value, size_t valueLength,
                          std::vector<std::array<uint8_t, SHA256_SIZE>> &digests) {
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
                || signerLength == 0u || digests.size() >= 8u) {
            return false;
        }
        std::array<uint8_t, SHA256_SIZE> digest{};
        if (!signerLeafDigest(signer, signerLength, digest)) return false;
        for (const auto &existing : digests) {
            if (constantTimeEqual(existing.data(), digest.data(), SHA256_SIZE)) return false;
        }
        digests.push_back(digest);
    }
    return cursor == end && !digests.empty();
}

bool extractV2SignerDigests(int fd, off_t fileSize,
                            std::vector<std::array<uint8_t, SHA256_SIZE>> &digests) {
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

    uint8_t headerSizeBytes[8];
    if (!readFully(fd, blockStart, headerSizeBytes, sizeof(headerSizeBytes))
            || le64(headerSizeBytes) != blockSize) {
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
                    || !parseV2SignerDigests(value.data(), value.size(), digests)) {
                return false;
            }
            foundV2 = true;
        }
        cursor += static_cast<off_t>(8u + pairLength);
    }
    return foundV2 && cursor == pairsEnd && !digests.empty();
}

bool readAllowedDigests(JNIEnv *env, jobjectArray source,
                        std::vector<std::array<uint8_t, SHA256_SIZE>> &allowed) {
    if (source == nullptr) return false;
    const jsize count = env->GetArrayLength(source);
    if (count <= 0 || count > 16) return false;
    allowed.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; ++i) {
        auto bytes = static_cast<jbyteArray>(env->GetObjectArrayElement(source, i));
        if (bytes == nullptr || env->GetArrayLength(bytes) != static_cast<jsize>(SHA256_SIZE)) {
            if (bytes != nullptr) env->DeleteLocalRef(bytes);
            return false;
        }
        std::array<uint8_t, SHA256_SIZE> digest{};
        env->GetByteArrayRegion(bytes, 0, static_cast<jsize>(SHA256_SIZE),
                                reinterpret_cast<jbyte *>(digest.data()));
        env->DeleteLocalRef(bytes);
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            return false;
        }
        allowed.push_back(digest);
    }
    return !allowed.empty();
}

bool allSignersAllowed(
        const std::vector<std::array<uint8_t, SHA256_SIZE>> &actual,
        const std::vector<std::array<uint8_t, SHA256_SIZE>> &allowed) {
    if (actual.empty() || allowed.empty()) return false;
    for (const auto &signer : actual) {
        bool matched = false;
        for (const auto &expected : allowed) {
            matched |= constantTimeEqual(signer.data(), expected.data(), SHA256_SIZE);
        }
        if (!matched) return false;
    }
    return true;
}

bool verifyOnDiskV2(const char *apkPath, const char *packageName,
                    const std::vector<std::array<uint8_t, SHA256_SIZE>> &allowed) {
    if (apkPath == nullptr || apkPath[0] == '\0' || !packageAndProcessOk(packageName)) return false;
    std::string canonical;
    if (!canonicalize(apkPath, canonical)
            || canonical.size() < 9u
            || canonical.compare(canonical.size() - 9u, 9u, "/base.apk") != 0
            || !(canonical.rfind("/data/app/", 0) == 0
                 || canonical.rfind("/mnt/expand/", 0) == 0)) {
        return false;
    }

    int fd = open(canonical.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return false;
    struct stat st{};
    bool ok = fstat(fd, &st) == 0 && S_ISREG(st.st_mode)
            && st.st_size > static_cast<off_t>(EOCD_MIN)
            && (st.st_mode & (S_IWGRP | S_IWOTH)) == 0;
    std::vector<std::array<uint8_t, SHA256_SIZE>> actual;
    if (ok) ok = extractV2SignerDigests(fd, st.st_size, actual);
    close(fd);
    return ok && allSignersAllowed(actual, allowed);
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pubgm_security_NativeSigningVerifier_verifyApkSigningBlockNative(
        JNIEnv *env,
        jclass,
        jstring apkPath,
        jstring packageName,
        jobjectArray allowedDigests) {
    if (apkPath == nullptr || packageName == nullptr || allowedDigests == nullptr) {
        return JNI_FALSE;
    }

    std::vector<std::array<uint8_t, SHA256_SIZE>> allowed;
    if (!readAllowedDigests(env, allowedDigests, allowed)) return JNI_FALSE;

    const char *apk = env->GetStringUTFChars(apkPath, nullptr);
    const char *pkg = env->GetStringUTFChars(packageName, nullptr);
    if (apk == nullptr || pkg == nullptr) {
        if (apk != nullptr) env->ReleaseStringUTFChars(apkPath, apk);
        if (pkg != nullptr) env->ReleaseStringUTFChars(packageName, pkg);
        return JNI_FALSE;
    }

    const bool ok = verifyOnDiskV2(apk, pkg, allowed);
    env->ReleaseStringUTFChars(apkPath, apk);
    env->ReleaseStringUTFChars(packageName, pkg);
    return ok ? JNI_TRUE : JNI_FALSE;
}
