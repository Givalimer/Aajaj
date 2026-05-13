// Aajaj Defender 2026 Ultra
// Meme "antivirus" for Windows. Win32 + GDI only, no deps, no installer.
// (c) 2026 Givalimer. Parody software. Does not scan, does not protect, does not care.

#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif

#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#include <windowsx.h>
#include <commctrl.h>
#include <mmsystem.h>
#include <shellapi.h>
#include <string>
#include <vector>
#include <deque>
#include <random>
#include <algorithm>
#include <cwchar>

#pragma comment(lib, "comctl32.lib")
#pragma comment(lib, "winmm.lib")
#pragma comment(lib, "gdi32.lib")
#pragma comment(lib, "user32.lib")
#pragma comment(lib, "msimg32.lib")

// ---------- IDs ----------
#define ID_TIMER_SCAN          1001
#define ID_TIMER_SHAKE         1002
#define ID_TIMER_FLASH         1003
#define ID_TIMER_MATRIX        1004
#define ID_TIMER_INVERT        1005
#define ID_TIMER_BLINK         1006
#define ID_TIMER_SOUND         1007
#define ID_TIMER_POPUP_STORM   1008

#define BTN_SCAN        2001
#define BTN_REMOVE      2002
#define BTN_QUARANTINE  2003
#define BTN_PANIC       2004
#define BTN_CURSOR      2005

// ---------- Colors / palette ----------
static const COLORREF COL_BG        = RGB(18, 22, 30);
static const COLORREF COL_BG2       = RGB(26, 32, 44);
static const COLORREF COL_PANEL     = RGB(30, 38, 52);
static const COLORREF COL_PANEL_HI  = RGB(40, 50, 68);
static const COLORREF COL_ACCENT    = RGB(0, 220, 180);
static const COLORREF COL_ACCENT_HI = RGB(80, 255, 220);
static const COLORREF COL_DANGER    = RGB(255, 70, 90);
static const COLORREF COL_DANGER_HI = RGB(255, 130, 140);
static const COLORREF COL_WARN      = RGB(255, 200, 60);
static const COLORREF COL_TEXT      = RGB(230, 236, 245);
static const COLORREF COL_MUTED     = RGB(140, 150, 170);
static const COLORREF COL_OK        = RGB(120, 230, 160);

// ---------- Globals ----------
static HINSTANCE g_hInst = nullptr;
static HWND      g_hMain = nullptr;
static HWND      g_hScan = nullptr, g_hRemove = nullptr, g_hQuar = nullptr, g_hPanic = nullptr, g_hCursor = nullptr;

static HFONT g_fontTitle = nullptr;
static HFONT g_fontBody  = nullptr;
static HFONT g_fontMono  = nullptr;
static HFONT g_fontBig   = nullptr;

static HCURSOR g_curNormal = nullptr;
static HCURSOR g_curCustom = nullptr;
static int     g_cursorMode = 0; // 0 normal, 1 custom

// scan state
static bool   g_scanning = false;
static int    g_scanProgress = 0;     // 0..1000
static int    g_threatsFound = 0;
static std::deque<std::wstring> g_logLines;      // rolling scan log (left column)
static std::deque<std::wstring> g_threatLines;   // right column red list
static int    g_scanTick = 0;

// effects state
static int    g_shakeFrames = 0;
static POINT  g_shakeOrigin = {0,0};
static int    g_flashFrames = 0;         // red border flash
static int    g_matrixFrames = 0;        // green rain overlay
static int    g_invertFrames = 0;        // screen invert
static int    g_blinkOn = 0;             // header blink

// matrix rain
struct RainCol { int x; int y; int speed; int len; };
static std::vector<RainCol> g_rain;

// rng
static std::mt19937 g_rng{ (unsigned)GetTickCount() };
static int randi(int a, int b) { std::uniform_int_distribution<int> d(a,b); return d(g_rng); }

// ---------- Forward decls ----------
LRESULT CALLBACK WndProc(HWND, UINT, WPARAM, LPARAM);
static void DrawUI(HDC hdc, RECT client);
static void StartScan();
static void StopScan(bool completed);
static void OnRemoveClicked(HWND parent);
static void OnQuarantineClicked(HWND parent);
static void OnPanicClicked(HWND parent);
static void OnCursorClicked(HWND parent);
static void KickEffect_Shake(int frames);
static void KickEffect_Flash(int frames);
static void KickEffect_Matrix(int frames);
static void KickEffect_Invert(int frames);
static HCURSOR MakeTrollCursor();
static void InitMatrixRain(int w, int h);
static void PlayMeme(int which);

// ---------- Meme data ----------
static const wchar_t* kDirs[] = {
    L"C:\\Users\\kolya\\Downloads\\",
    L"C:\\Users\\kolya\\Desktop\\Новая папка\\",
    L"C:\\Users\\kolya\\AppData\\Local\\Temp\\",
    L"C:\\Users\\Public\\Documents\\",
    L"C:\\Windows\\System32\\",
    L"D:\\Games\\Cracked\\",
    L"D:\\Torrents\\done\\",
    L"C:\\ProgramData\\TotallyNotVirus\\",
    L"C:\\Users\\kolya\\OneDrive\\skibidi\\",
    L"C:\\$Recycle.Bin\\",
    L"C:\\Users\\kolya\\Videos\\ohio\\",
};

static const wchar_t* kSafeFiles[] = {
    L"config.ini", L"readme.txt", L"kernel32.dll", L"ntdll.dll",
    L"diplom_final_final2.docx", L"resume_v7.pdf", L"photo_2024.jpg",
    L"report.xlsx", L"cat.png", L"homework.doc", L"setup.log",
    L"desktop.ini", L"thumbs.db", L"steam_api.dll", L"d3d11.dll",
    L"passwords_DO_NOT_OPEN.txt", L"taxes.xlsx", L"MyLittlePony.mp4",
};

static const wchar_t* kThreats[] = {
    L"your_mom.exe",
    L"skibidi_toilet.dll",
    L"amogus_miner.bat",
    L"ohio_rizz.scr",
    L"gigachad_cryptor.exe",
    L"bruh_moment.js",
    L"not_a_virus_trust_me.exe",
    L"free_robux_generator.exe",
    L"amongus_sus_dropper.dll",
    L"fortnite_v-bucks_hack.exe",
    L"doomer.mp3.exe",
    L"cheat_engine_FINAL.zip.exe",
    L"backrooms_level0.scr",
    L"sigma_grindset.bat",
    L"minecraft_bedwars_hack.jar.exe",
    L"rickroll.wmv.exe",
    L"crypto_miner_but_worse.exe",
    L"cr1ng3.dll",
    L"windows_defender_real.exe",
    L"this_is_fine.png.exe",
    L"chuvak_eto_ne_virus.exe",
};

static const wchar_t* kThreatTypes[] = {
    L"Trojan.Skibidi.Gen",
    L"Worm.Ohio.Rizz.A",
    L"Backdoor.Sigma.b",
    L"Miner.Amogus.42",
    L"Adware.FreeVBucks",
    L"Rootkit.Gigachad",
    L"PUP.NotAVirus",
    L"Ransom.BruhMoment",
    L"Spyware.YourMom",
    L"Trojan.Rickroll.C",
    L"Exploit.Fortnite.69",
};

static const wchar_t* kQuips[] = {
    L"[OK]   clean",
    L"[scan] signature match: none",
    L"[heur] entropy: low",
    L"[ok]   cert valid",
    L"[scan] ...",
    L"[idle] nothing sus",
    L"[ok]   cleared",
    L"[meh]  suspicious name, not a threat",
};

static void AddLog(const std::wstring& s) {
    g_logLines.push_back(s);
    while (g_logLines.size() > 40) g_logLines.pop_front();
}
static void AddThreat(const std::wstring& s) {
    g_threatLines.push_back(s);
    while (g_threatLines.size() > 24) g_threatLines.pop_front();
}

// ---------- Helpers ----------
static void SetTextFont(HDC hdc, HFONT f, COLORREF c) {
    SelectObject(hdc, f);
    SetTextColor(hdc, c);
    SetBkMode(hdc, TRANSPARENT);
}
static void FillRoundedRect(HDC hdc, RECT r, int rad, COLORREF fill) {
    HBRUSH b = CreateSolidBrush(fill);
    HPEN   p = CreatePen(PS_SOLID, 1, fill);
    HGDIOBJ ob = SelectObject(hdc, b);
    HGDIOBJ op = SelectObject(hdc, p);
    RoundRect(hdc, r.left, r.top, r.right, r.bottom, rad, rad);
    SelectObject(hdc, ob); SelectObject(hdc, op);
    DeleteObject(b); DeleteObject(p);
}
static void StrokeRoundedRect(HDC hdc, RECT r, int rad, COLORREF col, int width) {
    HPEN p = CreatePen(PS_SOLID, width, col);
    HGDIOBJ ob = SelectObject(hdc, GetStockObject(NULL_BRUSH));
    HGDIOBJ op = SelectObject(hdc, p);
    RoundRect(hdc, r.left, r.top, r.right, r.bottom, rad, rad);
    SelectObject(hdc, ob); SelectObject(hdc, op);
    DeleteObject(p);
}
static COLORREF Lerp(COLORREF a, COLORREF b, float t) {
    int ar = GetRValue(a), ag = GetGValue(a), ab = GetBValue(a);
    int br = GetRValue(b), bg = GetGValue(b), bb = GetBValue(b);
    return RGB(
        (int)(ar + (br-ar)*t),
        (int)(ag + (bg-ag)*t),
        (int)(ab + (bb-ab)*t));
}

// ---------- Custom buttons (owner-draw via subclass not needed; we use BS_OWNERDRAW) ----------
struct BtnStyle { COLORREF fill, fillHi, text, border; };

static void DrawOwnerBtn(DRAWITEMSTRUCT* dis, const BtnStyle& s, const wchar_t* label) {
    RECT r = dis->rcItem;
    bool hot     = (dis->itemState & ODS_SELECTED) || (dis->itemState & ODS_HOTLIGHT);
    bool pressed = (dis->itemState & ODS_SELECTED);
    bool focused = (dis->itemState & ODS_FOCUS) != 0;

    // background
    HBRUSH bg = CreateSolidBrush(COL_PANEL);
    FillRect(dis->hDC, &r, bg);
    DeleteObject(bg);

    RECT rr = r;
    if (pressed) { rr.top += 1; rr.left += 1; }
    FillRoundedRect(dis->hDC, rr, 10, hot ? s.fillHi : s.fill);
    StrokeRoundedRect(dis->hDC, rr, 10, s.border, 1);

    SetTextFont(dis->hDC, g_fontBody, s.text);
    DrawTextW(dis->hDC, label, -1, &rr, DT_SINGLELINE | DT_CENTER | DT_VCENTER);

    if (focused) {
        RECT fr = rr; InflateRect(&fr, -3, -3);
        StrokeRoundedRect(dis->hDC, fr, 8, s.text, 1);
    }
}

// ---------- Fake scan engine ----------
static std::wstring RandomPath(bool threat) {
    std::wstring p = kDirs[randi(0, (int)(sizeof(kDirs)/sizeof(*kDirs))-1)];
    if (threat) p += kThreats[randi(0, (int)(sizeof(kThreats)/sizeof(*kThreats))-1)];
    else        p += kSafeFiles[randi(0, (int)(sizeof(kSafeFiles)/sizeof(*kSafeFiles))-1)];
    return p;
}
static std::wstring RandomThreatType() {
    return kThreatTypes[randi(0, (int)(sizeof(kThreatTypes)/sizeof(*kThreatTypes))-1)];
}
static std::wstring RandomQuip() {
    return kQuips[randi(0, (int)(sizeof(kQuips)/sizeof(*kQuips))-1)];
}

static void StartScan() {
    if (g_scanning) return;
    g_scanning = true;
    g_scanProgress = 0;
    g_threatsFound = 0;
    g_scanTick = 0;
    g_logLines.clear();
    g_threatLines.clear();
    AddLog(L"=== Aajaj Defender: deep scan initiated ===");
    AddLog(L"loading skibidi signatures v9000...");
    AddLog(L"heuristic engine: ONLINE");
    SetTimer(g_hMain, ID_TIMER_SCAN, 80, nullptr);
    EnableWindow(g_hScan, FALSE);
    SetWindowTextW(g_hScan, L"SCANNING...");
    PlayMeme(0);
    InvalidateRect(g_hMain, nullptr, FALSE);
}
static void StopScan(bool completed) {
    g_scanning = false;
    KillTimer(g_hMain, ID_TIMER_SCAN);
    EnableWindow(g_hScan, TRUE);
    SetWindowTextW(g_hScan, L"START DEEP SCAN");
    if (completed) {
        AddLog(L"=== scan complete ===");
        wchar_t buf[128];
        swprintf(buf, 128, L"threats detected: %d", g_threatsFound);
        AddLog(buf);
        if (g_threatsFound > 0) {
            KickEffect_Flash(40);
            PlayMeme(1);
        } else {
            PlayMeme(3);
        }
    }
    InvalidateRect(g_hMain, nullptr, FALSE);
}

static void OnScanTick() {
    g_scanTick++;
    int step = randi(6, 16);
    g_scanProgress += step;
    if (g_scanProgress > 1000) g_scanProgress = 1000;

    // 2-3 new log lines per tick
    int lines = randi(2, 3);
    for (int i=0;i<lines;i++) {
        bool isThreat = (randi(0,100) < 14);
        std::wstring path = RandomPath(isThreat);
        if (isThreat) {
            std::wstring tt = RandomThreatType();
            std::wstring line = L"[!!]  THREAT  " + tt + L"  <- " + path;
            AddLog(line);
            AddThreat(tt + L"  " + path);
            g_threatsFound++;
            KickEffect_Flash(12);
            if (randi(0,100) < 35) KickEffect_Shake(8);
            if (randi(0,100) < 20) PlayMeme(randi(1,4));
        } else {
            std::wstring line = RandomQuip() + L"  " + path;
            AddLog(line);
        }
    }

    if (g_scanProgress >= 1000) {
        StopScan(true);
    }
    InvalidateRect(g_hMain, nullptr, FALSE);
}

// ---------- Sounds ----------
// 0 = scan start, 1 = error/threat, 2 = bruh-ish descending beeps,
// 3 = clean/ok, 4 = vine boom (low kick via Beep)
static DWORD WINAPI SoundThread(LPVOID param) {
    int which = (int)(INT_PTR)param;
    switch (which) {
        case 0:
            PlaySoundW(L"SystemAsterisk", nullptr, SND_ALIAS | SND_ASYNC);
            break;
        case 1:
            PlaySoundW(L"SystemHand", nullptr, SND_ALIAS | SND_ASYNC);
            break;
        case 2: // bruh descending
            Beep(523, 110); Beep(466, 110); Beep(392, 180); Beep(196, 240);
            break;
        case 3:
            PlaySoundW(L"SystemAsterisk", nullptr, SND_ALIAS | SND_ASYNC);
            break;
        case 4: // vine boom-ish (low thud)
            Beep(90, 40); Beep(60, 90); Beep(45, 160);
            break;
        case 5: // metal pipe (short clangy chirps)
            Beep(1200, 40); Beep(1600, 30); Beep(900, 60); Beep(1400, 50);
            break;
        case 6: // windows xp error trio
            Beep(880, 120); Beep(587, 120); Beep(440, 260);
            break;
    }
    return 0;
}
static void PlayMeme(int which) {
    HANDLE h = CreateThread(nullptr, 0, SoundThread, (LPVOID)(INT_PTR)which, 0, nullptr);
    if (h) CloseHandle(h);
}

// ---------- Action buttons (the joke ones) ----------
static const wchar_t* kPopupTitles[] = {
    L"Aajaj Defender",
    L"КРИТИЧЕСКАЯ ОШИБКА",
    L"Windows Defender (настоящий)",
    L"Процессор перегрелся",
    L"Skibidi Alert",
    L"А ты уверен?",
    L"Не нажимай эту кнопку",
    L"ohio detected",
};
static const wchar_t* kPopupBodies[] = {
    L"Вирус удалён...\n\n...но вернулся. Он живёт здесь теперь.",
    L"Для удаления угрозы необходимо купить лицензию Aajaj Premium.\nЦена: твоя душа (или 299 руб/мес).",
    L"Ошибка 0x00SKIBIDI: туалет переполнен.",
    L"Процесс your_mom.exe не может быть завершён. Она важнее.",
    L"Вы уверены что хотите удалить вирус?\n\n(на самом деле он уже удалил тебя)",
    L"Нажмите ОК чтобы согласиться, или ОК чтобы согласиться.",
    L"Я же просил не нажимать.",
    L"Обнаружено 3 новых вируса пока ты читал это сообщение.",
    L"Rickroll.dll успешно интегрирован в реестр.",
    L"Антивирус сам оказался вирусом. Извините.",
};

static void ShowMemePopup(HWND parent) {
    const wchar_t* title = kPopupTitles[randi(0,(int)(sizeof(kPopupTitles)/sizeof(*kPopupTitles))-1)];
    const wchar_t* body  = kPopupBodies[randi(0,(int)(sizeof(kPopupBodies)/sizeof(*kPopupBodies))-1)];
    UINT icon = MB_ICONWARNING;
    int r = randi(0,3);
    if (r==0) icon = MB_ICONERROR;
    else if (r==1) icon = MB_ICONINFORMATION;
    else if (r==2) icon = MB_ICONWARNING;
    else icon = MB_ICONQUESTION;
    PlayMeme(randi(1,6));
    MessageBoxW(parent, body, title, MB_OK | icon | MB_TOPMOST);
}

static void OnRemoveClicked(HWND parent) {
    if (g_threatsFound <= 0) {
        MessageBoxW(parent, L"Нечего удалять. Сначала запусти сканирование, шерлок.", L"Aajaj Defender", MB_OK | MB_ICONINFORMATION);
        return;
    }
    KickEffect_Shake(20);
    KickEffect_Flash(20);
    PlayMeme(6);
    ShowMemePopup(parent);
    // second popup storm
    for (int i=0;i<3;i++) ShowMemePopup(parent);
}
static void OnQuarantineClicked(HWND parent) {
    KickEffect_Matrix(180);
    PlayMeme(5);
    MessageBoxW(parent,
        L"Угрозы успешно перенесены в карантин.\n\n"
        L"...карантин находится в твоей папке Downloads. Удачи.",
        L"Карантин", MB_OK | MB_ICONWARNING | MB_TOPMOST);
}
static void OnPanicClicked(HWND parent) {
    KickEffect_Shake(60);
    KickEffect_Flash(60);
    KickEffect_Invert(30);
    KickEffect_Matrix(120);
    PlayMeme(4);
    PlayMeme(6);
    MessageBoxW(parent,
        L"ПАНИКА АКТИВИРОВАНА.\n\n"
        L"Все системы защиты переведены в режим \"а хуй его знает\".\n"
        L"Рекомендуем выключить компьютер и закопать его в лесу.",
        L"!!! PANIC MODE !!!", MB_OK | MB_ICONERROR | MB_TOPMOST);
}
static void OnCursorClicked(HWND parent) {
    g_cursorMode = 1 - g_cursorMode;
    if (g_cursorMode == 1) {
        SetClassLongPtrW(parent, GCLP_HCURSOR, (LONG_PTR)g_curCustom);
        SetCursor(g_curCustom);
    } else {
        SetClassLongPtrW(parent, GCLP_HCURSOR, (LONG_PTR)g_curNormal);
        SetCursor(g_curNormal);
    }
    POINT pt; GetCursorPos(&pt); SetCursorPos(pt.x, pt.y); // force refresh
}

// ---------- Effects ----------
static void KickEffect_Shake(int frames) {
    if (g_shakeFrames <= 0) {
        RECT wr; GetWindowRect(g_hMain, &wr);
        g_shakeOrigin.x = wr.left;
        g_shakeOrigin.y = wr.top;
    }
    g_shakeFrames = std::max(g_shakeFrames, frames);
    SetTimer(g_hMain, ID_TIMER_SHAKE, 16, nullptr);
}
static void OnShakeTick() {
    if (g_shakeFrames <= 0) {
        KillTimer(g_hMain, ID_TIMER_SHAKE);
        RECT wr; GetWindowRect(g_hMain, &wr);
        int w = wr.right-wr.left, h = wr.bottom-wr.top;
        SetWindowPos(g_hMain, nullptr, g_shakeOrigin.x, g_shakeOrigin.y, w, h, SWP_NOZORDER|SWP_NOSIZE|SWP_NOACTIVATE);
        return;
    }
    int amp = 8 + g_shakeFrames/4;
    int dx = randi(-amp, amp);
    int dy = randi(-amp, amp);
    RECT wr; GetWindowRect(g_hMain, &wr);
    int w = wr.right-wr.left, h = wr.bottom-wr.top;
    SetWindowPos(g_hMain, nullptr, g_shakeOrigin.x+dx, g_shakeOrigin.y+dy, w, h, SWP_NOZORDER|SWP_NOSIZE|SWP_NOACTIVATE);
    g_shakeFrames--;
}
static void KickEffect_Flash(int frames) {
    g_flashFrames = std::max(g_flashFrames, frames);
    SetTimer(g_hMain, ID_TIMER_FLASH, 50, nullptr);
}
static void OnFlashTick() {
    if (g_flashFrames <= 0) { KillTimer(g_hMain, ID_TIMER_FLASH); InvalidateRect(g_hMain, nullptr, FALSE); return; }
    g_flashFrames--;
    InvalidateRect(g_hMain, nullptr, FALSE);
}
static void KickEffect_Matrix(int frames) {
    g_matrixFrames = std::max(g_matrixFrames, frames);
    if (g_rain.empty()) {
        RECT cr; GetClientRect(g_hMain, &cr);
        InitMatrixRain(cr.right, cr.bottom);
    }
    SetTimer(g_hMain, ID_TIMER_MATRIX, 40, nullptr);
}
static void OnMatrixTick() {
    if (g_matrixFrames <= 0) { KillTimer(g_hMain, ID_TIMER_MATRIX); InvalidateRect(g_hMain, nullptr, FALSE); return; }
    g_matrixFrames--;
    RECT cr; GetClientRect(g_hMain, &cr);
    for (auto& c : g_rain) {
        c.y += c.speed;
        if (c.y - c.len*14 > cr.bottom) {
            c.y = -randi(0, 200);
            c.speed = randi(4, 12);
            c.len = randi(5, 18);
            c.x = randi(0, cr.right);
        }
    }
    InvalidateRect(g_hMain, nullptr, FALSE);
}
static void InitMatrixRain(int w, int /*h*/) {
    g_rain.clear();
    int cols = std::max(10, w / 12);
    g_rain.reserve(cols);
    for (int i=0;i<cols;i++) {
        RainCol c;
        c.x = i*12 + randi(-3,3);
        c.y = randi(-400, 0);
        c.speed = randi(4, 12);
        c.len = randi(5, 18);
        g_rain.push_back(c);
    }
}
static void KickEffect_Invert(int frames) {
    g_invertFrames = std::max(g_invertFrames, frames);
    SetTimer(g_hMain, ID_TIMER_INVERT, 33, nullptr);
}
static void OnInvertTick() {
    if (g_invertFrames <= 0) { KillTimer(g_hMain, ID_TIMER_INVERT); InvalidateRect(g_hMain, nullptr, FALSE); return; }
    g_invertFrames--;
    InvalidateRect(g_hMain, nullptr, FALSE);
}
static void OnBlinkTick() {
    g_blinkOn = 1 - g_blinkOn;
    // only redraw the header band area, but simpler: whole thing
    InvalidateRect(g_hMain, nullptr, FALSE);
}

// ---------- Custom troll cursor ----------
// 32x32 ARROW with a red X and a tiny skibidi toilet vibe (just shapes, no font)
static HCURSOR MakeTrollCursor() {
    const int W = 32, H = 32;
    HDC hdcScreen = GetDC(nullptr);
    HDC hdcMask = CreateCompatibleDC(hdcScreen);
    HDC hdcColor = CreateCompatibleDC(hdcScreen);

    BITMAPINFO bi{};
    bi.bmiHeader.biSize = sizeof(BITMAPINFOHEADER);
    bi.bmiHeader.biWidth = W;
    bi.bmiHeader.biHeight = -H;
    bi.bmiHeader.biPlanes = 1;
    bi.bmiHeader.biBitCount = 32;
    bi.bmiHeader.biCompression = BI_RGB;

    void* colorBits = nullptr;
    HBITMAP hColor = CreateDIBSection(hdcScreen, &bi, DIB_RGB_COLORS, &colorBits, nullptr, 0);
    HBITMAP hMask = CreateBitmap(W, H, 1, 1, nullptr);

    HGDIOBJ oc = SelectObject(hdcColor, hColor);
    HGDIOBJ om = SelectObject(hdcMask, hMask);

    // mask: white = transparent, black = opaque
    RECT all = {0,0,W,H};
    HBRUSH whiteB = (HBRUSH)GetStockObject(WHITE_BRUSH);
    HBRUSH blackB = (HBRUSH)GetStockObject(BLACK_BRUSH);
    FillRect(hdcMask, &all, whiteB);
    FillRect(hdcColor, &all, blackB); // start with transparent-ish

    // helper to plot pixel (BGRA in DIB)
    auto put = [&](int x, int y, BYTE r, BYTE g, BYTE b, BYTE a=255){
        if (x<0||y<0||x>=W||y>=H) return;
        BYTE* p = (BYTE*)colorBits + (y*W + x)*4;
        p[0]=b; p[1]=g; p[2]=r; p[3]=a;
        // mark opaque in mask
        SetPixel(hdcMask, x, y, RGB(0,0,0));
    };

    // cursor arrow outline + fill (classic)
    // arrow shape (simplified)
    for (int y=0;y<24;y++) {
        int w = (y<22) ? (y/2 + 1) : 0;
        if (y < 20) w = y/2 + 2;
        for (int x=0;x<=w;x++) {
            // border
            BYTE r=255,g=255,b=255;
            if (x==0 || x==w || y==0) { r=0;g=0;b=0; }
            put(1+x, 1+y, r,g,b);
        }
    }
    // red X stamp over the arrow tail (mocking "forbidden")
    for (int t=-6;t<=6;t++) {
        put(18+t, 18+t, 255,40,40);
        put(18+t, 18-t, 255,40,40);
        put(18+t+1, 18+t, 255,40,40);
        put(18+t+1, 18-t, 255,40,40);
    }
    // little green accent pixel
    put(4,4, 0,220,180);
    put(5,4, 0,220,180);

    SelectObject(hdcColor, oc);
    SelectObject(hdcMask, om);

    ICONINFO ii{};
    ii.fIcon = FALSE;
    ii.xHotspot = 1;
    ii.yHotspot = 1;
    ii.hbmMask = hMask;
    ii.hbmColor = hColor;
    HCURSOR cur = (HCURSOR)CreateIconIndirect(&ii);

    DeleteObject(hColor);
    DeleteObject(hMask);
    DeleteDC(hdcColor);
    DeleteDC(hdcMask);
    ReleaseDC(nullptr, hdcScreen);
    return cur;
}

// ---------- UI layout ----------
struct Layout {
    RECT header;
    RECT statsBar;
    RECT progress;
    RECT logPanel;
    RECT threatsPanel;
    RECT btnScan, btnRemove, btnQuar, btnPanic, btnCursor;
    RECT footer;
};
static Layout ComputeLayout(RECT client) {
    Layout L{};
    int pad = 16;
    L.header     = { client.left+pad, client.top+pad, client.right-pad, client.top+pad+78 };
    L.statsBar   = { L.header.left, L.header.bottom+8, L.header.right, L.header.bottom+8+56 };
    L.progress   = { L.statsBar.left, L.statsBar.bottom+8, L.statsBar.right, L.statsBar.bottom+8+28 };

    int btnRowTop = client.bottom - pad - 46;
    L.btnScan    = { L.header.left, btnRowTop, L.header.left+190, btnRowTop+46 };
    L.btnRemove  = { L.btnScan.right+10, btnRowTop, L.btnScan.right+10+170, btnRowTop+46 };
    L.btnQuar    = { L.btnRemove.right+10, btnRowTop, L.btnRemove.right+10+170, btnRowTop+46 };
    L.btnPanic   = { L.btnQuar.right+10, btnRowTop, L.btnQuar.right+10+150, btnRowTop+46 };
    L.btnCursor  = { L.header.right-170, btnRowTop, L.header.right, btnRowTop+46 };

    int bodyTop = L.progress.bottom+12;
    int bodyBottom = btnRowTop - 12;
    int midX = (L.header.left + L.header.right)/2;
    L.logPanel     = { L.header.left, bodyTop, midX-6, bodyBottom };
    L.threatsPanel = { midX+6, bodyTop, L.header.right, bodyBottom };

    L.footer = { L.header.left, client.bottom-pad-20, L.header.right, client.bottom-pad };
    return L;
}

static void PositionButtons(const Layout& L) {
    auto mv = [](HWND h, RECT r){ MoveWindow(h, r.left, r.top, r.right-r.left, r.bottom-r.top, TRUE); };
    mv(g_hScan,   L.btnScan);
    mv(g_hRemove, L.btnRemove);
    mv(g_hQuar,   L.btnQuar);
    mv(g_hPanic,  L.btnPanic);
    mv(g_hCursor, L.btnCursor);
}

// ---------- Painting ----------
static void PaintGradientBackground(HDC hdc, RECT r) {
    // vertical gradient via GradientFill
    TRIVERTEX v[2];
    v[0].x = r.left; v[0].y = r.top;
    v[0].Red = GetRValue(COL_BG)<<8; v[0].Green = GetGValue(COL_BG)<<8; v[0].Blue = GetBValue(COL_BG)<<8; v[0].Alpha = 0;
    v[1].x = r.right; v[1].y = r.bottom;
    v[1].Red = GetRValue(COL_BG2)<<8; v[1].Green = GetGValue(COL_BG2)<<8; v[1].Blue = GetBValue(COL_BG2)<<8; v[1].Alpha = 0;
    GRADIENT_RECT gr{0,1};
    GradientFill(hdc, v, 2, &gr, 1, GRADIENT_FILL_RECT_V);
}

static void PaintShieldIcon(HDC hdc, int cx, int cy, int size, COLORREF col) {
    // stylized shield: trapezoid with rounded top, check mark
    HPEN pen = CreatePen(PS_SOLID, 3, col);
    HBRUSH fill = CreateSolidBrush(Lerp(col, RGB(0,0,0), 0.6f));
    HGDIOBJ op = SelectObject(hdc, pen);
    HGDIOBJ ob = SelectObject(hdc, fill);
    POINT pts[6] = {
        {cx-size/2, cy-size/2+4},
        {cx,        cy-size/2-4},
        {cx+size/2, cy-size/2+4},
        {cx+size/2-4, cy+size/2-6},
        {cx,        cy+size/2},
        {cx-size/2+4, cy+size/2-6},
    };
    Polygon(hdc, pts, 6);
    SelectObject(hdc, op); SelectObject(hdc, ob);
    DeleteObject(pen); DeleteObject(fill);

    // check mark
    HPEN p2 = CreatePen(PS_SOLID, 4, col);
    HGDIOBJ op2 = SelectObject(hdc, p2);
    MoveToEx(hdc, cx - size/4, cy, nullptr);
    LineTo(hdc, cx - size/16, cy + size/4);
    LineTo(hdc, cx + size/3, cy - size/6);
    SelectObject(hdc, op2);
    DeleteObject(p2);
}

static void PaintHeader(HDC hdc, RECT r) {
    FillRoundedRect(hdc, r, 14, COL_PANEL);
    StrokeRoundedRect(hdc, r, 14, COL_PANEL_HI, 1);

    int iconX = r.left + 40;
    int iconY = (r.top + r.bottom)/2;
    COLORREF shieldCol = (g_threatsFound > 0) ? COL_DANGER : COL_ACCENT;
    if (g_scanning) shieldCol = (g_blinkOn ? COL_WARN : COL_ACCENT);
    PaintShieldIcon(hdc, iconX, iconY, 54, shieldCol);

    RECT tr = { r.left+80, r.top+10, r.right-16, r.top+46 };
    SetTextFont(hdc, g_fontTitle, COL_TEXT);
    DrawTextW(hdc, L"Aajaj Defender 2026 Ultra", -1, &tr, DT_SINGLELINE | DT_VCENTER);

    RECT sr = { r.left+80, r.top+44, r.right-16, r.top+72 };
    SetTextFont(hdc, g_fontBody, COL_MUTED);
    const wchar_t* sub = L"status: protected. skibidi signatures up to date.";
    if (g_scanning)         sub = L"status: scanning... do not reboot, do not breathe.";
    else if (g_threatsFound>0) sub = L"status: YOUR PC IS HAUNTED. take action.";
    DrawTextW(hdc, sub, -1, &sr, DT_SINGLELINE | DT_VCENTER);

    // top-right pill with "REAL TIME" label
    RECT pill = { r.right-170, r.top+14, r.right-16, r.top+40 };
    FillRoundedRect(hdc, pill, 12, Lerp(COL_ACCENT, COL_BG, 0.75f));
    StrokeRoundedRect(hdc, pill, 12, COL_ACCENT, 1);
    SetTextFont(hdc, g_fontBody, COL_ACCENT_HI);
    DrawTextW(hdc, L"REAL-TIME: ON", -1, &pill, DT_SINGLELINE|DT_CENTER|DT_VCENTER);
}

static void PaintStatsBar(HDC hdc, RECT r) {
    FillRoundedRect(hdc, r, 12, COL_PANEL);
    StrokeRoundedRect(hdc, r, 12, COL_PANEL_HI, 1);

    int cellW = (r.right - r.left)/4;
    auto drawCell = [&](int i, const wchar_t* label, const std::wstring& value, COLORREF valCol){
        RECT cr = { r.left + i*cellW + 12, r.top+6, r.left + (i+1)*cellW - 12, r.bottom-6 };
        SetTextFont(hdc, g_fontBody, COL_MUTED);
        RECT lr = cr; lr.bottom = cr.top + 18;
        DrawTextW(hdc, label, -1, &lr, DT_SINGLELINE|DT_LEFT|DT_VCENTER);
        SetTextFont(hdc, g_fontBig, valCol);
        RECT vr = cr; vr.top = lr.bottom - 2;
        DrawTextW(hdc, value.c_str(), -1, &vr, DT_SINGLELINE|DT_LEFT|DT_VCENTER);
    };
    wchar_t bufP[32], bufT[32], bufS[32];
    swprintf(bufP, 32, L"%d%%", g_scanProgress/10);
    swprintf(bufT, 32, L"%d", g_threatsFound);
    swprintf(bufS, 32, L"%d", g_scanTick * randi(23, 47));
    drawCell(0, L"PROGRESS",   bufP, COL_ACCENT_HI);
    drawCell(1, L"THREATS",    bufT, g_threatsFound>0 ? COL_DANGER_HI : COL_OK);
    drawCell(2, L"SCANNED",    bufS, COL_TEXT);
    drawCell(3, L"ENGINE",     L"skibidi v9000", COL_WARN);
}

static void PaintProgress(HDC hdc, RECT r) {
    FillRoundedRect(hdc, r, 8, COL_PANEL);
    StrokeRoundedRect(hdc, r, 8, COL_PANEL_HI, 1);
    RECT inner = r; InflateRect(&inner, -3, -3);
    int w = inner.right - inner.left;
    int fill = (int)((long long)w * g_scanProgress / 1000);
    RECT fr = inner; fr.right = inner.left + fill;
    if (fill > 0) {
        // gradient fill
        TRIVERTEX v[2];
        v[0].x = fr.left; v[0].y = fr.top;
        v[0].Red = GetRValue(COL_ACCENT)<<8; v[0].Green = GetGValue(COL_ACCENT)<<8; v[0].Blue = GetBValue(COL_ACCENT)<<8; v[0].Alpha = 0;
        v[1].x = fr.right; v[1].y = fr.bottom;
        v[1].Red = GetRValue(COL_ACCENT_HI)<<8; v[1].Green = GetGValue(COL_ACCENT_HI)<<8; v[1].Blue = GetBValue(COL_ACCENT_HI)<<8; v[1].Alpha = 0;
        GRADIENT_RECT gr{0,1};
        GradientFill(hdc, v, 2, &gr, 1, GRADIENT_FILL_RECT_H);
    }
}

static void PaintLogPanel(HDC hdc, RECT r) {
    FillRoundedRect(hdc, r, 12, COL_PANEL);
    StrokeRoundedRect(hdc, r, 12, COL_PANEL_HI, 1);
    RECT hdr = r; hdr.bottom = r.top + 28;
    SetTextFont(hdc, g_fontBody, COL_MUTED);
    RECT ht = hdr; ht.left += 14;
    DrawTextW(hdc, L"scan log", -1, &ht, DT_SINGLELINE|DT_LEFT|DT_VCENTER);

    RECT body = r; body.top = hdr.bottom; InflateRect(&body, -10, -6);
    // clip
    HRGN clip = CreateRectRgn(body.left, body.top, body.right, body.bottom);
    SelectClipRgn(hdc, clip);

    SetTextFont(hdc, g_fontMono, COL_TEXT);
    int y = body.bottom - 16;
    for (auto it = g_logLines.rbegin(); it != g_logLines.rend() && y > body.top; ++it) {
        const std::wstring& s = *it;
        COLORREF c = COL_TEXT;
        if (s.find(L"THREAT") != std::wstring::npos) c = COL_DANGER_HI;
        else if (s.find(L"===") != std::wstring::npos) c = COL_ACCENT_HI;
        else if (s.find(L"[!!]") != std::wstring::npos) c = COL_DANGER;
        else if (s.find(L"[ok]") != std::wstring::npos || s.find(L"[OK]") != std::wstring::npos) c = COL_OK;
        else if (s.find(L"[heur]") != std::wstring::npos) c = COL_WARN;
        else c = COL_MUTED;
        SetTextColor(hdc, c);
        RECT lr = { body.left, y, body.right, y+16 };
        DrawTextW(hdc, s.c_str(), -1, &lr, DT_SINGLELINE|DT_LEFT|DT_NOPREFIX);
        y -= 16;
    }
    SelectClipRgn(hdc, nullptr);
    DeleteObject(clip);
}

static void PaintThreatsPanel(HDC hdc, RECT r) {
    COLORREF fill = COL_PANEL;
    if (g_threatsFound>0 && g_flashFrames>0 && (g_flashFrames/2)%2==0) fill = Lerp(COL_PANEL, COL_DANGER, 0.25f);
    FillRoundedRect(hdc, r, 12, fill);
    StrokeRoundedRect(hdc, r, 12, g_threatsFound>0 ? COL_DANGER : COL_PANEL_HI, 1);
    RECT hdr = r; hdr.bottom = r.top + 28;
    SetTextFont(hdc, g_fontBody, g_threatsFound>0 ? COL_DANGER_HI : COL_MUTED);
    RECT ht = hdr; ht.left += 14;
    wchar_t buf[64]; swprintf(buf, 64, L"threats (%d)", g_threatsFound);
    DrawTextW(hdc, buf, -1, &ht, DT_SINGLELINE|DT_LEFT|DT_VCENTER);

    RECT body = r; body.top = hdr.bottom; InflateRect(&body, -10, -6);
    HRGN clip = CreateRectRgn(body.left, body.top, body.right, body.bottom);
    SelectClipRgn(hdc, clip);

    if (g_threatLines.empty()) {
        SetTextFont(hdc, g_fontBody, COL_MUTED);
        RECT mid = body;
        DrawTextW(hdc, L"(пока чисто. запусти сканирование чтобы убедиться что это не так)", -1, &mid, DT_CENTER|DT_VCENTER|DT_WORDBREAK);
    } else {
        SetTextFont(hdc, g_fontMono, COL_DANGER_HI);
        int y = body.top;
        for (const auto& s : g_threatLines) {
            RECT lr = { body.left, y, body.right, y+18 };
            DrawTextW(hdc, s.c_str(), -1, &lr, DT_SINGLELINE|DT_LEFT|DT_NOPREFIX);
            y += 18;
            if (y > body.bottom) break;
        }
    }
    SelectClipRgn(hdc, nullptr);
    DeleteObject(clip);
}

static void PaintFooter(HDC hdc, RECT r) {
    SetTextFont(hdc, g_fontBody, COL_MUTED);
    DrawTextW(hdc,
        L"Aajaj Defender is parody software. It does not actually scan anything. If it did, it would find nothing. You're fine. Probably.",
        -1, &r, DT_SINGLELINE|DT_LEFT|DT_VCENTER|DT_END_ELLIPSIS);
}

static void PaintMatrixOverlay(HDC hdc, RECT client) {
    if (g_matrixFrames <= 0 || g_rain.empty()) return;
    // darken
    HBRUSH darkB = CreateSolidBrush(RGB(0,8,0));
    for (int i=0;i<1;i++) FillRect(hdc, &client, darkB);
    DeleteObject(darkB);

    HFONT oldF = (HFONT)SelectObject(hdc, g_fontMono);
    SetBkMode(hdc, TRANSPARENT);
    for (const auto& c : g_rain) {
        for (int i=0;i<c.len;i++) {
            int yy = c.y - i*14;
            if (yy < client.top-14 || yy > client.bottom) continue;
            int bright = 255 - i*16; if (bright<40) bright=40;
            SetTextColor(hdc, RGB(0, bright, 0));
            wchar_t ch[2] = { (wchar_t)(0x30A0 + (randi(0, 90))), 0 }; // katakana-ish
            TextOutW(hdc, c.x, yy, ch, 1);
        }
    }
    SelectObject(hdc, oldF);
}

static void PaintFlashBorder(HDC hdc, RECT client) {
    if (g_flashFrames <= 0) return;
    int t = (g_flashFrames / 2) % 2;
    if (t == 0) return;
    int W = 10;
    HBRUSH b = CreateSolidBrush(COL_DANGER);
    RECT top = {client.left, client.top, client.right, client.top+W};
    RECT bot = {client.left, client.bottom-W, client.right, client.bottom};
    RECT lft = {client.left, client.top, client.left+W, client.bottom};
    RECT rgt = {client.right-W, client.top, client.right, client.bottom};
    FillRect(hdc, &top, b); FillRect(hdc, &bot, b); FillRect(hdc, &lft, b); FillRect(hdc, &rgt, b);
    DeleteObject(b);
}

// ---------- WM_PAINT ----------
static void OnPaint(HWND hwnd) {
    PAINTSTRUCT ps;
    HDC hdcWin = BeginPaint(hwnd, &ps);
    RECT client; GetClientRect(hwnd, &client);

    // double buffer
    HDC mem = CreateCompatibleDC(hdcWin);
    HBITMAP bmp = CreateCompatibleBitmap(hdcWin, client.right, client.bottom);
    HGDIOBJ obmp = SelectObject(mem, bmp);

    PaintGradientBackground(mem, client);

    Layout L = ComputeLayout(client);
    PaintHeader(mem, L.header);
    PaintStatsBar(mem, L.statsBar);
    PaintProgress(mem, L.progress);
    PaintLogPanel(mem, L.logPanel);
    PaintThreatsPanel(mem, L.threatsPanel);
    PaintFooter(mem, L.footer);

    PaintMatrixOverlay(mem, client);
    PaintFlashBorder(mem, client);

    // invert
    if (g_invertFrames > 0 && (g_invertFrames/2)%2==0) {
        // invert whole client
        BitBlt(mem, 0, 0, client.right, client.bottom, mem, 0, 0, NOTSRCCOPY);
    }

    BitBlt(hdcWin, 0, 0, client.right, client.bottom, mem, 0, 0, SRCCOPY);

    SelectObject(mem, obmp);
    DeleteObject(bmp);
    DeleteDC(mem);
    EndPaint(hwnd, &ps);
}

// ---------- Font / init ----------
static HFONT MakeFont(int h, int weight, const wchar_t* face) {
    return CreateFontW(h, 0, 0, 0, weight, FALSE, FALSE, FALSE,
        DEFAULT_CHARSET, OUT_DEFAULT_PRECIS, CLIP_DEFAULT_PRECIS, CLEARTYPE_QUALITY,
        DEFAULT_PITCH | FF_DONTCARE, face);
}
static void InitFonts() {
    g_fontTitle = MakeFont(-26, FW_BOLD, L"Segoe UI");
    g_fontBody  = MakeFont(-15, FW_SEMIBOLD, L"Segoe UI");
    g_fontBig   = MakeFont(-22, FW_BOLD, L"Segoe UI");
    g_fontMono  = MakeFont(-13, FW_NORMAL, L"Consolas");
}
static void FreeFonts() {
    if (g_fontTitle) DeleteObject(g_fontTitle);
    if (g_fontBody)  DeleteObject(g_fontBody);
    if (g_fontBig)   DeleteObject(g_fontBig);
    if (g_fontMono)  DeleteObject(g_fontMono);
}

// ---------- WndProc ----------
LRESULT CALLBACK WndProc(HWND hwnd, UINT msg, WPARAM w, LPARAM l) {
    switch (msg) {
        case WM_CREATE: {
            InitFonts();
            g_curNormal = LoadCursor(nullptr, IDC_ARROW);
            g_curCustom = MakeTrollCursor();

            DWORD btnStyle = WS_CHILD | WS_VISIBLE | BS_OWNERDRAW | BS_NOTIFY;
            g_hScan   = CreateWindowW(L"BUTTON", L"START DEEP SCAN", btnStyle, 0,0,10,10, hwnd, (HMENU)(INT_PTR)BTN_SCAN,   g_hInst, nullptr);
            g_hRemove = CreateWindowW(L"BUTTON", L"REMOVE THREATS",  btnStyle, 0,0,10,10, hwnd, (HMENU)(INT_PTR)BTN_REMOVE, g_hInst, nullptr);
            g_hQuar   = CreateWindowW(L"BUTTON", L"QUARANTINE",      btnStyle, 0,0,10,10, hwnd, (HMENU)(INT_PTR)BTN_QUARANTINE, g_hInst, nullptr);
            g_hPanic  = CreateWindowW(L"BUTTON", L"PANIC!!!",        btnStyle, 0,0,10,10, hwnd, (HMENU)(INT_PTR)BTN_PANIC,  g_hInst, nullptr);
            g_hCursor = CreateWindowW(L"BUTTON", L"TROLL CURSOR",    btnStyle, 0,0,10,10, hwnd, (HMENU)(INT_PTR)BTN_CURSOR, g_hInst, nullptr);

            SendMessage(g_hScan,   WM_SETFONT, (WPARAM)g_fontBody, TRUE);
            SendMessage(g_hRemove, WM_SETFONT, (WPARAM)g_fontBody, TRUE);
            SendMessage(g_hQuar,   WM_SETFONT, (WPARAM)g_fontBody, TRUE);
            SendMessage(g_hPanic,  WM_SETFONT, (WPARAM)g_fontBody, TRUE);
            SendMessage(g_hCursor, WM_SETFONT, (WPARAM)g_fontBody, TRUE);

            // header blink
            SetTimer(hwnd, ID_TIMER_BLINK, 500, nullptr);

            AddLog(L"Aajaj Defender 2026 Ultra loaded.");
            AddLog(L"real-time protection: ON");
            AddLog(L"signatures updated from totally-not-sketchy-server.ru");
            AddLog(L"click START DEEP SCAN when ready.");
            return 0;
        }
        case WM_SIZE: {
            RECT client; GetClientRect(hwnd, &client);
            Layout L = ComputeLayout(client);
            PositionButtons(L);
            if (!g_rain.empty()) InitMatrixRain(client.right, client.bottom);
            InvalidateRect(hwnd, nullptr, FALSE);
            return 0;
        }
        case WM_ERASEBKGND:
            return 1; // we paint full
        case WM_PAINT:
            OnPaint(hwnd);
            return 0;
        case WM_TIMER:
            switch (w) {
                case ID_TIMER_SCAN:   OnScanTick(); break;
                case ID_TIMER_SHAKE:  OnShakeTick(); break;
                case ID_TIMER_FLASH:  OnFlashTick(); break;
                case ID_TIMER_MATRIX: OnMatrixTick(); break;
                case ID_TIMER_INVERT: OnInvertTick(); break;
                case ID_TIMER_BLINK:  OnBlinkTick(); break;
            }
            return 0;
        case WM_DRAWITEM: {
            DRAWITEMSTRUCT* dis = (DRAWITEMSTRUCT*)l;
            if (dis->CtlType != ODT_BUTTON) break;
            wchar_t label[128]={0};
            GetWindowTextW(dis->hwndItem, label, 128);
            BtnStyle s;
            switch ((int)dis->CtlID) {
                case BTN_SCAN:
                    s = { Lerp(COL_ACCENT, COL_BG, 0.35f), Lerp(COL_ACCENT, COL_BG, 0.15f), COL_TEXT, COL_ACCENT };
                    break;
                case BTN_REMOVE:
                    s = { Lerp(COL_DANGER, COL_BG, 0.35f), Lerp(COL_DANGER, COL_BG, 0.15f), COL_TEXT, COL_DANGER };
                    break;
                case BTN_QUARANTINE:
                    s = { Lerp(COL_WARN, COL_BG, 0.4f), Lerp(COL_WARN, COL_BG, 0.2f), COL_TEXT, COL_WARN };
                    break;
                case BTN_PANIC:
                    s = { COL_DANGER, COL_DANGER_HI, RGB(20,10,10), RGB(255,200,200) };
                    break;
                case BTN_CURSOR:
                    s = { COL_PANEL_HI, Lerp(COL_PANEL_HI, COL_ACCENT, 0.4f), COL_TEXT, COL_MUTED };
                    break;
                default:
                    s = { COL_PANEL_HI, COL_PANEL, COL_TEXT, COL_MUTED };
            }
            DrawOwnerBtn(dis, s, label);
            return TRUE;
        }
        case WM_COMMAND: {
            int id = LOWORD(w);
            int code = HIWORD(w);
            if (code == BN_CLICKED) {
                switch (id) {
                    case BTN_SCAN:       StartScan(); break;
                    case BTN_REMOVE:     OnRemoveClicked(hwnd); break;
                    case BTN_QUARANTINE: OnQuarantineClicked(hwnd); break;
                    case BTN_PANIC:      OnPanicClicked(hwnd); break;
                    case BTN_CURSOR:     OnCursorClicked(hwnd); break;
                }
            }
            return 0;
        }
        case WM_CTLCOLORBTN:
        case WM_CTLCOLORSTATIC: {
            HDC dc = (HDC)w;
            SetBkMode(dc, TRANSPARENT);
            SetTextColor(dc, COL_TEXT);
            return (LRESULT)GetStockObject(NULL_BRUSH);
        }
        case WM_SETCURSOR: {
            if (g_cursorMode == 1 && g_curCustom) {
                SetCursor(g_curCustom);
                return TRUE;
            }
            break;
        }
        case WM_DESTROY:
            KillTimer(hwnd, ID_TIMER_BLINK);
            FreeFonts();
            if (g_curCustom) DestroyCursor(g_curCustom);
            PostQuitMessage(0);
            return 0;
    }
    return DefWindowProcW(hwnd, msg, w, l);
}

// ---------- Entry ----------
int WINAPI wWinMain(HINSTANCE hInst, HINSTANCE, LPWSTR, int nShow) {
    g_hInst = hInst;

    INITCOMMONCONTROLSEX icc{ sizeof(icc), ICC_STANDARD_CLASSES | ICC_PROGRESS_CLASS };
    InitCommonControlsEx(&icc);

    WNDCLASSEXW wc{};
    wc.cbSize = sizeof(wc);
    wc.style = CS_HREDRAW | CS_VREDRAW | CS_DBLCLKS;
    wc.lpfnWndProc = WndProc;
    wc.hInstance = hInst;
    wc.hCursor = LoadCursor(nullptr, IDC_ARROW);
    wc.hbrBackground = nullptr;
    wc.lpszClassName = L"AajajDefenderWnd";
    wc.hIcon = LoadIcon(nullptr, IDI_APPLICATION);
    wc.hIconSm = LoadIcon(nullptr, IDI_APPLICATION);
    RegisterClassExW(&wc);

    int sw = GetSystemMetrics(SM_CXSCREEN);
    int sh = GetSystemMetrics(SM_CYSCREEN);
    int W = 1100, H = 720;
    int X = (sw-W)/2, Y = (sh-H)/2;

    g_hMain = CreateWindowExW(
        WS_EX_APPWINDOW,
        L"AajajDefenderWnd",
        L"Aajaj Defender 2026 Ultra",
        WS_OVERLAPPED | WS_CAPTION | WS_SYSMENU | WS_MINIMIZEBOX | WS_THICKFRAME | WS_MAXIMIZEBOX,
        X, Y, W, H,
        nullptr, nullptr, hInst, nullptr);

    if (!g_hMain) return 1;
    ShowWindow(g_hMain, nShow);
    UpdateWindow(g_hMain);

    MSG msg;
    while (GetMessage(&msg, nullptr, 0, 0)) {
        TranslateMessage(&msg);
        DispatchMessage(&msg);
    }
    return (int)msg.wParam;
}
