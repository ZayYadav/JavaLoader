#ifndef ESP_SOCKET_H
#define ESP_SOCKET_H

#include "import.h"

#define SOCKET_NAME "\0ZX_SECURE_COMM_V4"
#define BACKLOG 8
int sock = -1, clientD = -1;
sockaddr_un addr_server;
char socket_name[108];

int Create() {
    sock = socket(AF_UNIX, SOCK_STREAM, 0);
    return (sock >= 0);
}

void Close() {
    if (clientD >= 0) {
        close(clientD);
        clientD = -1;
    }
    if (sock >= 0) {
        close(sock);
        sock = -1;
    }
}

int Accept() {
    if (sock < 0) return 0;
    clientD = accept(sock, nullptr, nullptr);
    if (clientD < 0) {
        return 0;
    }
    return 1;
}

int Bind() {
    if (sock < 0) return 0;
    memset(socket_name, 0, sizeof(socket_name));
    memcpy(&socket_name[0], "\0", 1);
    strcpy(&socket_name[1], SOCKET_NAME);

    memset(&addr_server, 0, sizeof(addr_server));
    addr_server.sun_family = AF_UNIX;
    strncpy(addr_server.sun_path, socket_name, sizeof(addr_server.sun_path) - 1);

    if (bind(sock, (struct sockaddr *) &addr_server, sizeof(addr_server)) < 0) {
        Close();
        return 0;
    }
    return 1;
}

int Listen() {
    if (sock < 0) return 0;
    if (listen(sock, BACKLOG) < 0) {
        Close();
        return 0;
    }
    return 1;
}

int sendData(void *inData, size_t size) {
    if (clientD < 0) return 0;
    char *buffer = (char *) inData;
    int totalSent = 0;

    while (size > 0) {
        int numSent = write(clientD, buffer, size);
        if (numSent <= 0) {
            if (errno == EINTR) continue;
            Close();
            return 0;
        }
        size -= numSent;
        buffer += numSent;
        totalSent += numSent;
    }
    return totalSent;
}

int send(void* inData, size_t size) {
    uint32_t length = htonl(size);
    if(sendData(&length, sizeof(uint32_t)) <= 0) return 0;
    return (sendData(inData, size) > 0);
}

int recvData(void *outData, size_t size) {
    if (clientD < 0) return 0;
    char *buffer = (char *) outData;
    int totalRecv = 0;

    while (size > 0) {
        int numRecv = read(clientD, buffer, size);
        if (numRecv <= 0) {
            if (errno == EINTR) continue;
            Close();
            return 0;
        }
        size -= numRecv;
        buffer += numRecv;
        totalRecv += numRecv;
    }
    return totalRecv;
}

size_t receive(void* outData) {
    uint32_t length = 0;
    if (recvData(&length, sizeof(uint32_t)) <= 0) return 0;
    length = ntohl(length);
    if (length > 500000) return 0;
    return recvData(outData, static_cast<size_t>(length));
}

#endif //ESP_SOCKET_H
