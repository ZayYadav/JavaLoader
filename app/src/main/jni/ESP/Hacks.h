#ifndef ESP_HACKS_H
#define ESP_HACKS_H

#include "socket.h"
#include "Color.h"
#include "items.h"
#include "backends/ModsLoader.h"
#include <algorithm>
#include <chrono>

Color clrEnemy, clrLine, clrLineBot, clrEdge, clrBox, clrAlert, clr, clrTeam, clrDist, clrHealth, clrText, grenadeColor, clrDistance;
float h, w, x, y, z, magic_number, mx, my, top, bottom, textsize, mScale, skelSize, LineSize;
Options options{1, -1, -1, 3, false, false, 1, false, 200, 200, 200, 19, 19, -1, false};
OtherFeature otherFeature{false, false, false, false};

int botCount, playerCount;
Response response;
Request request;
char text[100];
int connectionWaitFrames = 0;

Color getRainbowColor(float speed = 2.0f, float offset = 0) {
    static float hue = 0;
    hue += speed / 60.0f;
    if (hue > 36000) hue = 0;
    float currentHue = fmod(hue + offset, 360.0f);
    float s = 1.0f, v = 1.0f;
    float c = v * s;
    float x_col = c * (1 - fabs(fmod(currentHue / 60.0, 2) - 1));
    float m = v - c;
    float r, g, b;
    if (currentHue < 60) { r = c; g = x_col; b = 0; }
    else if (currentHue < 120) { r = x_col; g = c; b = 0; }
    else if (currentHue < 180) { r = 0; g = c; b = x_col; }
    else if (currentHue < 240) { r = 0; g = x_col; b = c; }
    else if (currentHue < 300) { r = x_col; g = 0; b = c; }
    else { r = c; g = 0; b = x_col; }
    return Color((int)((r+m)*255), (int)((g+m)*255), (int)((b+m)*255), 255);
}

// Helper to decode colon-separated decimal ASCII names (e.g., "115:97:105" -> "sai")
void decodePlayerName(const char* input, char* output) {
    if (!input || !output) return;

    // Check if it's actually a decimal encoded string (contains colons and digits)
    bool isEncoded = false;
    for (int i = 0; input[i] != '\0' && i < 10; i++) {
        if (input[i] == ':') { isEncoded = true; break; }
    }

    if (isEncoded) {
        int outIdx = 0;
        int currentVal = 0;
        bool hasVal = false;
        for (int i = 0; input[i] != '\0' && outIdx < 63; i++) {
            if (input[i] >= '0' && input[i] <= '9') {
                currentVal = currentVal * 10 + (input[i] - '0');
                hasVal = true;
            } else if (input[i] == ':') {
                if (hasVal) {
                    output[outIdx++] = (char)currentVal;
                    currentVal = 0;
                    hasVal = false;
                }
            } else {
                // Unexpected character, might not be decimal encoded after all
                // but we'll keep going or fallback
            }
        }
        // Last value if no trailing colon
        if (hasVal && outIdx < 63) {
            output[outIdx++] = (char)currentVal;
        }
        output[outIdx] = '\0';
    } else {
        // Not encoded, just copy as normal printable string
        int outIdx = 0;
        for (int i = 0; input[i] != '\0' && outIdx < 63; i++) {
            if (isprint((unsigned char)input[i])) {
                output[outIdx++] = input[i];
            }
        }
        output[outIdx] = '\0';
    }
}

void DrawESP (ESP& esp, int screenWidth, int screenHeight) {
    bool isAuth = (!g_Token.empty() && !g_Auth.empty() && g_Token == g_Auth);
    float mScaleY = screenHeight / 1080.0f;

    // --- WATERMARK BOTTOM CENTER (Dark Red Color) ---
    esp.DrawTextName(Color(255, 255, 0, 255), "ZORO || LAODER", Vec2(screenWidth / 2.0f, screenHeight - 25), 26 * mScaleY);

    if (clientD < 0) {
        esp.DrawText(Color(255, 255, 0, 180), "ZORO || LAODER NOT CONNECTED", Vec2(screenWidth / 2.0f, 80 * mScaleY), 22 * mScaleY);
    }

    if (!isAuth) {
        esp.DrawText(Color(255, 255, 0, 180), "AUTHENTICATING...", Vec2(screenWidth / 2.0f, 110 * mScaleY), 22 * mScaleY);
        return;
    }

    request.ScreenHeight = screenHeight;
    request.ScreenWidth = screenWidth;
    request.options = options;
    request.otherFeature = otherFeature;
    request.Mode = InitMode;
    request.radarPos = Vec2(200, 200);
    request.radarSize = 85.0f;

    botCount = 0, playerCount = 0;

    if (clientD >= 0) {
        connectionWaitFrames = 0;
        if (send((void *) &request, sizeof(request))) {
            if (receive((void *) &response) > 0 && response.Success) {
                // Box thickness mota kar ke 3.0f kiya hai (Solid View)
                float BoxSize = (mScaleY * 3.0f);
                float textsize = 18 * mScaleY;

                for (int i = 0; i < response.PlayerCount && i < maxplayerCount; i++) {
                    PlayerData Player = response.Players[i];
                    if (Player.isBot) botCount++; else playerCount++;

                    x = Player.HeadLocation.x;
                    y = Player.HeadLocation.y;
                    magic_number = (Player.Distance * response.fov);
                    if (magic_number < 0.1f) magic_number = 0.1f;

                    float namewidht = (screenWidth / 6.0f) / magic_number;
                    float pp2 = namewidht / 2.0f;
                    float mx = (screenWidth / 4.0f) / magic_number;
                    float my = (screenWidth / 1.38f) / magic_number;
                    float top = y - my + (screenWidth / 1.7f) / magic_number;
                    float bottom = Player.Bone.lAn.y + my - (screenWidth / 1.7f) / magic_number;

                    // Dark Solid Green (Visible) | Dark Solid Red (Cover)
                    if (Player.isVisible) {
                        clr = Color(0, 255, 0, 255); // Solid Green
                    } else {
                        clr = Color(255, 0, 0, 255); // Solid Red
                    }

                    if (Player.HeadLocation.z != 1.0f) {
                        if (x > -200 && x < screenWidth + 200) {
                            
                            // Skeleton Lines Layer Thicker (2.5f)
                            if (isSkeleton && Player.Bone.isBone) {
                                float skelThick = 2.5f;
                                esp.DrawLine(clr, skelThick, Player.Bone.neck, Player.Bone.cheast);
                                esp.DrawLine(clr, skelThick, Player.Bone.cheast, Player.Bone.pelvis);
                                esp.DrawLine(clr, skelThick, Player.Bone.neck, Player.Bone.lSh);
                                esp.DrawLine(clr, skelThick, Player.Bone.neck, Player.Bone.rSh);
                                esp.DrawLine(clr, skelThick, Player.Bone.lSh, Player.Bone.lElb);
                                esp.DrawLine(clr, skelThick, Player.Bone.rSh, Player.Bone.rElb);
                                esp.DrawLine(clr, skelThick, Player.Bone.lElb, Player.Bone.lWr);
                                esp.DrawLine(clr, skelThick, Player.Bone.rElb, Player.Bone.rWr);
                                esp.DrawLine(clr, skelThick, Player.Bone.pelvis, Player.Bone.lTh);
                                esp.DrawLine(clr, skelThick, Player.Bone.pelvis, Player.Bone.rTh);
                                esp.DrawLine(clr, skelThick, Player.Bone.lTh, Player.Bone.lKn);
                                esp.DrawLine(clr, skelThick, Player.Bone.rTh, Player.Bone.rKn);
                                esp.DrawLine(clr, skelThick, Player.Bone.lKn, Player.Bone.lAn);
                                esp.DrawLine(clr, skelThick, Player.Bone.rKn, Player.Bone.rAn);
                            }

                            // Box Lines Layer Thicker
                            if (isPlayerBox) {
                               esp.DrawLine(clr, BoxSize, Vec2(x + pp2, top), Vec2(x + namewidht, top));
                               esp.DrawLine(clr, BoxSize, Vec2(x - pp2, top), Vec2(x - namewidht, top));
                               esp.DrawLine(clr, BoxSize, Vec2(x + namewidht, top), Vec2(x + namewidht, top + pp2));
                               esp.DrawLine(clr, BoxSize, Vec2(x - namewidht, top), Vec2(x - namewidht, top + pp2));
                               esp.DrawLine(clr, BoxSize, Vec2(x + pp2, bottom), Vec2(x + namewidht, bottom));
                               esp.DrawLine(clr, BoxSize, Vec2(x - pp2, bottom), Vec2(x - namewidht, bottom));
                               esp.DrawLine(clr, BoxSize, Vec2(x - namewidht, bottom), Vec2(x - namewidht, bottom - pp2));
                               esp.DrawLine(clr, BoxSize, Vec2(x + namewidht, bottom), Vec2(x + namewidht, bottom - pp2));
                            }

                            // ESP Line Thickness (2.5f)
                            if (isPlayerLine) esp.DrawLine(clr, 2.5f, Vec2(screenWidth / 2.0f, 120), Vec2(x, top - 25));

                            if (isPlayerHealth) {
                                float hl = std::max(screenWidth / 70.0f, mx);
                                Color ch = (Player.Health < 25) ? Color(255, 0, 0, 255) : (Player.Health < 50 ? Color(255, 165, 0, 255) : Color(0, 255, 0, 255));
                                if (Player.Health == 0) esp.DrawText(Color(255, 0, 0, 255), "Knocked", Vec2(x, top - 12), textsize);
                                else esp.DrawFilledRect(ch, Vec2(x - hl, top - 18), Vec2(x - hl + (2 * hl) * Player.Health / 100.0f, top - 14));
                            }

                            // --- NAME DRAWING (Feet position + Yellow Color + Alphabet Name Parsing) ---
                            if (isPlayerName) {
                                char safeName[64]; memset(safeName, 0, 64);
                                if (Player.isBot) {
                                    strcpy(safeName, "BOT");
                                } else {
                                    decodePlayerName(Player.PlayerNameByte, safeName);
                                    if (strlen(safeName) == 0) {
                                        strcpy(safeName, "Player");
                                    }
                                }
                                // Yellow Color & Positioned at Feet (bottom + 10)
                                esp.DrawName(Color(255, 255, 0, 255), safeName, Vec2(x, bottom + 10), textsize);
                            }

                            // Head Circle - Dark Red Color
                            if (isPlayerHead) esp.DrawCircle(Color(180, 0, 0, 255), Vec2(Player.HeadLocation.x, Player.HeadLocation.y), (screenHeight / 11.0f) / magic_number, 3.5f);

                            if (isPlayerDistance) {
                                char distTxt[32]; sprintf(distTxt, "%0.0f m", Player.Distance);
                                // Solid White Text
                                esp.DrawText(Color(255, 255, 255, 255), distTxt, Vec2(x, bottom + 28), textsize);
                            }
                        }
                    }
                }

                // Vehicles ESP - Fully Fixed
                if (isVehicles) {
                    for (int i = 0; i < response.VehicleCount && i < maxvehicleCount; i++) {
                        if (response.Vehicles[i].Location.z != 1.0f) {
                            esp.DrawVehicles(response.Vehicles[i].VehicleName, response.Vehicles[i].Distance, response.Vehicles[i].Health, response.Vehicles[i].Fuel, Vec2(response.Vehicles[i].Location.x, response.Vehicles[i].Location.y), textsize);
                        }
                    }
                }
            }
        }
    } else {
        if (++connectionWaitFrames > 120) {
            esp.DrawText(Color(255, 0, 0, 255), "", Vec2(screenWidth / 2.0f, screenHeight / 2.0f), 35 * mScaleY);
        }
    }

    // --- ROUNDED MEDIUM ENEMY COUNTER ---
    if (clientD >= 0) {
        float centerX = screenWidth / 2.0f;
        float startY = 35.0f;
        
        float boxW = 110.0f * mScaleY;
        float boxH = 55.0f * mScaleY;
        float radius = 25.0f * mScaleY;

        // Rounded Background
        esp.DrawFilledRoundRect(Color(0, 0, 0, 220), Vec2(centerX - boxW, startY), Vec2(centerX + boxW, startY + boxH), radius);

        // Rotating Rainbow Border
        Color rainbow = getRainbowColor(10.0f);
        esp.DrawRoundRect(rainbow, 3.0f, Vec2(centerX - boxW, startY), Vec2(centerX + boxW, startY + boxH), radius);

        // Center Divider
        esp.DrawLine(Color(255, 255, 255, 200), 1.5f, Vec2(centerX, startY + 10), Vec2(centerX, startY + boxH - 10));

        char eTxt[16], bTxt[16];
        sprintf(eTxt, "%d", playerCount);
        sprintf(bTxt, "%d", botCount);

        esp.DrawText(Color(0, 255, 0, 255), eTxt, Vec2(centerX - boxW/2, startY + boxH/2 + 11 * mScaleY), 30 * mScaleY);
        esp.DrawText(Color(255, 50, 150, 255), bTxt, Vec2(centerX + boxW/2, startY + boxH/2 + 11 * mScaleY), 30 * mScaleY);
    }

    // FOV Circle - Dark Red Color
    if (options.openState == 0 || options.aimBullet == 0 || options.aimT == 0) {
        esp.DrawCircle(Color(180, 0, 0, 255), Vec2(screenWidth / 2.0f, screenHeight / 2.0f), (float)options.aimingRange, 2.0f);
    }
}

#endif //ESP_HACKS_H