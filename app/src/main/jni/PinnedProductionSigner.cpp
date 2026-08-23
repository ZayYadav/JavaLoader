#include <jni.h>
#include <obfuscate.h>

#include <algorithm>
#include <array>
#include <cctype>
#include <cerrno>
#include <cstdint>
#include <cstring>
#include <string>
#include <vector>

#include <dlfcn.h>
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
constexpr size_t SHA256_SIZE = 32u;
constexpr size_t SHA256_BLOCK = 64u;
constexpr char APK_SIG_MAGIC[16] = {
        'A','P','K',' ','S','i','g',' ','B','l','o','c','k',' ','4','2'
};

// OneCore MyThos production certificate SHA-256.
// This is intentionally independent from Java BuildConfig and server configuration.
constexpr std::array<uint8_t, SHA256_SIZE> PRODUCTION_CERT_SHA256 = {
        0xB3,0x43,0x11,0x1B,0xBF,0xA3,0x0E,0xD5,
        0x65,0xF9,0xB6,0x1A,0x52,0xCF,0x5B,0x34,
        0xCB,0x2E,0xAF,0x04,0x85,0xEB,0x8B,0x39,
        0x6A,0xB3,0x73,0xB2,0xEC,0xCA,0x53,0xD4
};

struct Sha256Context {
    uint8_t data[SHA256_BLOCK]{};
    size_t dataLength = 0u;
    uint64_t bitLength = 0u;
    uint32_t state[8] = {
            0x6a09e667u, 0xbb67ae85u, 0x3c6ef372u, 0xa54ff53au,
            0x510e527fu, 0x9b05688cu, 0x1f83d9abu, 0x5be0cd19u
    };
};

constexpr uint32_t SHA_K[64] = {
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
    for (size_t i = 0u; i < 16u; ++i) {
        const size_t j = i * 4u;
        w[i] = (static_cast<uint32_t>(block[j]) << 24u)
                | (static_cast<uint32_t>(block[j + 1u]) << 16u)
                | (static_cast<uint32_t>(block[j + 2u]) << 8u)
                | static_cast<uint32_t>(block[j + 3u]);
    }
    for (size_t i = 16u; i < 64u; ++i) {
        const uint32_t s0 = rotr(w[i - 15u], 7u) ^ rotr(w[i - 15u], 18u) ^ (w[i - 15u] >> 3u);
        const uint32_t s1 = rotr(w[i - 2u], 17u) ^ rotr(w[i - 2u], 19u) ^ (w[i - 2u] >> 10u);
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

    for (size_t i = 0u; i < 64u; ++i) {
        const uint32_t s1 = rotr(e, 6u) ^ rotr(e, 11u) ^ rotr(e, 25u);
        const uint32_t ch = (e & f) ^ ((~e) & g);
        const uint32_t temp1 = h + s1 + ch + SHA_K[i] + w[i];
        const uint32_t s0 = rotr(a, 2u) ^ rotr(a, 13u) ^ rotr(a, 22u);
        const uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        const uint32_t temp2 = s0 + maj;
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
    for (size_t i = 0u; i < length; ++i) {
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
        ctx.data[63u - offset] = static_cast<uint8_t>((ctx.bitLength >> (offset * 8u)) & 0xffu);
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

bool productionDigestMatches(const std::vector<uint8_t> &certificate) {
    if (certificate.empty()) return false;
    const auto digest = sha256(certificate.data(), certificate.size());
    return constantTimeEqual(digest.data(), PRODUCTION_CERT_SHA256.data(), SHA256_SIZE);
}

uint16_t le16(const uint8_t *p) {
    return static_cast<uint16_t>(p[0]) | (static_cast<uint16_t>(p[1]) << 8u);
}

uint32_t le32(const uint8_t *p) {
    return static_cast<uint32_t>(p[0])
            | (static_cast<uint32_t>(p[1]) << 8u)
            | (static_cast<uint32_t>(p[2]) << 16u)
            | (static_cast<uint32_t>(p[3]) << 24u);
}

uint64_t le64(const uint8_t *p) {
    return static_cast<uint64_t>(le32(p)) | (static_cast<uint64_t>(le32(p + 4u)) << 32u);
}

bool readFully(int fd, off_t offset, void *buffer, size_t length) {
    auto *out = static_cast<uint8_t *>(buffer);
    size_t done = 0u;
    while (done < length) {
        const ssize_t count = pread(fd, out + done, length - done, offset + static_cast<off_t>(done));
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

bool processNameMatches() {
    int fd = open("/proc/self/cmdline", O_RDONLY | O_CLOEXEC);
    if (fd < 0) return false;
    char buffer[256]{};
    const ssize_t count = read(fd, buffer, sizeof(buffer) - 1u);
    close(fd);
    if (count <= 0) return false;
    const std::string process(buffer, strnlen(buffer, static_cast<size_t>(count)));
    const std::string expected = OBFUSCATE("OneCore.Vip");
    return process == expected || process.rfind(expected + ":", 0u) == 0u;
}

bool packageMatches(const char *packageName) {
    return packageName != nullptr && std::strcmp(packageName, OBFUSCATE("OneCore.Vip")) == 0;
}

bool deriveOwnBaseApk(std::string &baseApk, std::string &libraryPath) {
    Dl_info info{};
    if (dladdr(reinterpret_cast<void *>(&deriveOwnBaseApk), &info) == 0 || info.dli_fname == nullptr) {
        return false;
    }
    std::string raw(info.dli_fname);
    if (raw.find('!') != std::string::npos) return false;
    if (!canonicalize(raw, libraryPath)) return false;
    const std::string marker = "/lib/arm64/libclient.so";
    const size_t position = libraryPath.rfind(marker);
    if (position == std::string::npos || position == 0u) return false;
    const std::string root = libraryPath.substr(0u, position);
    if (!(root.rfind("/data/app/", 0u) == 0u || root.rfind("/mnt/expand/", 0u) == 0u)) {
        return false;
    }
    const std::string candidate = root + "/base.apk";
    if (!canonicalize(candidate, baseApk) || baseApk != candidate) return false;
    struct stat st{};
    return lstat(baseApk.c_str(), &st) == 0
            && S_ISREG(st.st_mode)
            && (st.st_mode & (S_IWGRP | S_IWOTH)) == 0
            && access(baseApk.c_str(), W_OK) != 0;
}

bool sameFile(const std::string &aPath, const std::string &bPath) {
    struct stat a{};
    struct stat b{};
    return stat(aPath.c_str(), &a) == 0 && stat(bPath.c_str(), &b) == 0
            && S_ISREG(a.st_mode) && S_ISREG(b.st_mode)
            && a.st_dev == b.st_dev && a.st_ino == b.st_ino && a.st_size == b.st_size;
}

bool findCentralDirectory(int fd, off_t fileSize, uint32_t &centralOffset) {
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
        if (le16(tail.data() + i + 4u) != 0u || le16(tail.data() + i + 6u) != 0u) return false;
        const uint32_t offset = le32(tail.data() + i + 16u);
        if (offset == 0xffffffffu || static_cast<uint64_t>(offset) >= static_cast<uint64_t>(fileSize)) {
            return false;
        }
        centralOffset = offset;
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
    if (cursor < end) {
        const uint8_t *compat = nullptr;
        size_t compatLength = 0u;
        if (!readLengthPrefixed32(cursor, end, compat, compatLength) || compatLength != 0u) return false;
    }
    if (cursor != end) return false;

    cursor = certificates;
    end = certificates + certificatesLength;
    const uint8_t *leaf = nullptr;
    size_t leafLength = 0u;
    if (!readLengthPrefixed32(cursor, end, leaf, leafLength) || leafLength == 0u) return false;
    leafCertificate.assign(leaf, leaf + leafLength);
    while (cursor < end) {
        const uint8_t *certificate = nullptr;
        size_t certificateLength = 0u;
        if (!readLengthPrefixed32(cursor, end, certificate, certificateLength)
                || certificateLength == 0u) return false;
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
            || cursor != end || signersLength == 0u) return false;
    cursor = signers;
    end = signers + signersLength;
    while (cursor < end) {
        const uint8_t *signer = nullptr;
        size_t signerLength = 0u;
        if (!readLengthPrefixed32(cursor, end, signer, signerLength)
                || signerLength == 0u || certificates.size() >= 4u) return false;
        std::vector<uint8_t> leaf;
        if (!signerLeafCertificate(signer, signerLength, leaf)) return false;
        certificates.push_back(std::move(leaf));
    }
    return cursor == end && !certificates.empty();
}

bool extractV2LeafCertificates(int fd, off_t fileSize,
                               std::vector<std::vector<uint8_t>> &certificates) {
    uint32_t centralOffset = 0u;
    if (!findCentralDirectory(fd, fileSize, centralOffset) || centralOffset < SIGNING_FOOTER) return false;
    uint8_t footer[SIGNING_FOOTER];
    const off_t footerOffset = static_cast<off_t>(centralOffset) - SIGNING_FOOTER;
    if (!readFully(fd, footerOffset, footer, sizeof(footer))
            || std::memcmp(footer + 8u, APK_SIG_MAGIC, sizeof(APK_SIG_MAGIC)) != 0) return false;
    const uint64_t blockSize = le64(footer);
    if (blockSize < SIGNING_FOOTER || blockSize > static_cast<uint64_t>(centralOffset)) return false;
    const uint64_t totalBlockSize = blockSize + 8u;
    if (totalBlockSize > static_cast<uint64_t>(centralOffset)) return false;
    const off_t blockStart = static_cast<off_t>(centralOffset - totalBlockSize);
    uint8_t firstSize[8];
    if (!readFully(fd, blockStart, firstSize, sizeof(firstSize)) || le64(firstSize) != blockSize) return false;

    off_t cursor = blockStart + 8;
    const off_t pairsEnd = footerOffset;
    bool foundV2 = false;
    while (cursor < pairsEnd) {
        if (pairsEnd - cursor < 8) return false;
        uint8_t lengthBytes[8];
        if (!readFully(fd, cursor, lengthBytes, sizeof(lengthBytes))) return false;
        const uint64_t pairLength = le64(lengthBytes);
        const uint64_t remaining = static_cast<uint64_t>(pairsEnd - cursor - 8);
        if (pairLength < 4u || pairLength > MAX_SIGNING_VALUE || pairLength > remaining) return false;
        uint8_t idBytes[4];
        if (!readFully(fd, cursor + 8, idBytes, sizeof(idBytes))) return false;
        const uint32_t id = le32(idBytes);
        const size_t valueLength = static_cast<size_t>(pairLength - 4u);
        if (id == V2_BLOCK_ID) {
            if (foundV2 || valueLength == 0u) return false;
            std::vector<uint8_t> value(valueLength);
            if (!readFully(fd, cursor + 12, value.data(), value.size())
                    || !parseV2LeafCertificates(value.data(), value.size(), certificates)) return false;
            foundV2 = true;
        }
        cursor += static_cast<off_t>(8u + pairLength);
    }
    return foundV2 && cursor == pairsEnd && certificates.size() == 1u;
}

bool verifyPinnedApkPath(const std::string &path) {
    int fd = open(path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) return false;
    struct stat st{};
    bool ok = fstat(fd, &st) == 0 && S_ISREG(st.st_mode)
            && st.st_size > static_cast<off_t>(EOCD_MIN)
            && (st.st_mode & (S_IWGRP | S_IWOTH)) == 0;
    std::vector<std::vector<uint8_t>> certificates;
    if (ok) ok = extractV2LeafCertificates(fd, st.st_size, certificates);
    close(fd);
    return ok && certificates.size() == 1u && productionDigestMatches(certificates[0]);
}

bool verifySelfInstalledBase(std::string *resolvedBase = nullptr) {
    if (!processNameMatches()) return false;
    std::string baseApk;
    std::string libraryPath;
    if (!deriveOwnBaseApk(baseApk, libraryPath)) return false;
    if (!verifyPinnedApkPath(baseApk)) return false;
    if (resolvedBase != nullptr) *resolvedBase = baseApk;
    return true;
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

bool digestStringMatchesPin(const char *value) {
    if (value == nullptr) return false;
    std::string normalized;
    for (const unsigned char ch : std::string(value)) {
        if (ch == ':') continue;
        if (!std::isxdigit(ch)) return false;
        normalized.push_back(static_cast<char>(std::toupper(ch)));
    }
    static constexpr char EXPECTED[] =
            "B343111BBFA30ED565F9B61A52CF5B34CB2EAF0485EB8B396AB373B2ECCA53D4";
    if (normalized.size() != 64u) return false;
    uint8_t diff = 0u;
    for (size_t i = 0u; i < 64u; ++i) diff |= static_cast<uint8_t>(normalized[i] ^ EXPECTED[i]);
    return diff == 0u;
}
} // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *, void *) {
    return verifySelfInstalledBase() ? JNI_VERSION_1_6 : JNI_ERR;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pubgm_security_ProductionSignerGuard_nativeExpectedDigestMatches(
        JNIEnv *env, jclass, jstring expectedSha256) {
    if (expectedSha256 == nullptr) return JNI_FALSE;
    const char *value = env->GetStringUTFChars(expectedSha256, nullptr);
    if (value == nullptr) return JNI_FALSE;
    const bool ok = digestStringMatchesPin(value);
    env->ReleaseStringUTFChars(expectedSha256, value);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pubgm_security_ProductionSignerGuard_nativeVerifyInstalledBasePinned(
        JNIEnv *env, jclass, jstring apkPath, jstring packageName) {
    if (apkPath == nullptr || packageName == nullptr) return JNI_FALSE;
    const char *apk = env->GetStringUTFChars(apkPath, nullptr);
    const char *pkg = env->GetStringUTFChars(packageName, nullptr);
    if (apk == nullptr || pkg == nullptr) {
        if (apk != nullptr) env->ReleaseStringUTFChars(apkPath, apk);
        if (pkg != nullptr) env->ReleaseStringUTFChars(packageName, pkg);
        return JNI_FALSE;
    }
    bool ok = packageMatches(pkg) && processNameMatches();
    std::string ownBase;
    std::string claimed;
    if (ok) ok = verifySelfInstalledBase(&ownBase);
    if (ok) ok = canonicalize(apk, claimed) && claimed == ownBase && sameFile(claimed, ownBase);
    if (ok) ok = verifyPinnedApkPath(claimed);
    env->ReleaseStringUTFChars(apkPath, apk);
    env->ReleaseStringUTFChars(packageName, pkg);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_pubgm_security_ProductionSignerGuard_nativeVerifyCertificatesPinned(
        JNIEnv *env, jclass, jobjectArray certificates, jstring packageName) {
    if (certificates == nullptr || packageName == nullptr) return JNI_FALSE;
    const char *pkg = env->GetStringUTFChars(packageName, nullptr);
    if (pkg == nullptr) return JNI_FALSE;
    bool ok = packageMatches(pkg) && processNameMatches();
    env->ReleaseStringUTFChars(packageName, pkg);
    if (!ok || env->GetArrayLength(certificates) != 1) return JNI_FALSE;
    std::vector<uint8_t> certificate;
    if (!readByteArray(env, certificates, 0, certificate)) return JNI_FALSE;
    ok = productionDigestMatches(certificate) && verifySelfInstalledBase();
    std::fill(certificate.begin(), certificate.end(), 0u);
    return ok ? JNI_TRUE : JNI_FALSE;
}
