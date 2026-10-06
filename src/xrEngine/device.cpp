#include "stdafx.h"

#include "Render.h"

#include "xrCore/FS_impl.h"
#include "xrCore/Threading/TaskManager.hpp"

#include "XR_IOConsole.h"
#include "xr_input.h"

#include "IGame_Level.h"
#include "IGame_Persistent.h"

#include "xrScriptEngine/script_space.hpp"

#include <SDL.h>

ENGINE_API CRenderDevice Device;
ENGINE_API CLoadScreenRenderer load_screen_renderer;

ENGINE_API bool g_bRendering = false;

ENGINE_API bool g_bBenchmark = false;
string512 g_sBenchmarkName;

int ps_fps_limit = 501;
int ps_fps_limit_in_menu = 60;

float ps_render_scale = 1.0f;

bool g_bLoaded = false;
ref_light precache_light = 0;

using namespace xray;

// ---------------------------------------------------------------------------
// Frame stall watchdog + per-phase frame tracing (Android debug builds).
// Goal: when a frame never finishes (the post-eStart freeze on Mali), dump the
// main thread's stack at the exact stall point instead of guessing.
// ---------------------------------------------------------------------------
#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
#include <atomic>
#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <pthread.h>
#include <signal.h>
#include <unistd.h>
#include <time.h>
#include <dlfcn.h>
#include <android/log.h>
#include <unwind.h>
#include <dirent.h>
#include <stdlib.h>

#include "frame_trace.h"

namespace ft
{
enum Phase
{
    PH_FRAME_START = 0,
    PH_BEFORE_FRAME,
    PH_FRAME_MOVE,   // game update / scripts / physics
    PH_CAMERA,
    PH_PARALLEL,     // worker thread
    PH_RENDER_BEGIN, // first GL touch
    PH_SEQ_RENDER,   // draw calls / shader compiles
    PH_IMGUI,
    PH_RENDER_END,   // Present / swap: blocks if GPU is stuck
    PH_TASK_WAIT,
    PH_SLEEP,
    PH_FRAME_END,
    PH_MAIN_LOOP_PRE,    // CApplication loop top, before ProcessFrame
    PH_MAIN_LOOP_EVENTS,  // SDL_PeepEvents / ProcessEvent
    PH_MAIN_LOOP_ACTIVATE,// OnWindowActivate
    PH_MAIN_LOOP_POST,    // right after ProcessFrame returned
    PH_DEVICE_RESET,      // CRenderDevice::Reset (GL teardown/rebuild)
    PH_COUNT
};

inline const char* phase_name(int p)
{
    static const char* names[PH_COUNT] = {
        "FRAME_START", "BEFORE_FRAME", "FRAME_MOVE(update/scripts)", "CAMERA", "PARALLEL",
        "RENDER_BEGIN", "SEQ_RENDER(draw/shaders)", "IMGUI", "RENDER_END(present/swap)",
        "TASK_WAIT", "SLEEP", "FRAME_END",
        "MAIN_LOOP_PRE", "MAIN_LOOP_EVENTS", "MAIN_LOOP_ACTIVATE", "MAIN_LOOP_POST",
        "DEVICE_RESET"};
    return (p >= 0 && p < PH_COUNT) ? names[p] : "?";
}

constexpr const char* TAG = "OpenXRayFT";

std::atomic<int>      g_phase{PH_FRAME_START};
std::atomic<uint64_t> g_frame{0};
std::atomic<uint64_t> g_hb{0};
std::atomic<bool>     g_installed{false};

// How long per-phase logging stays on after startup and after a level change.
// Measured in frames, so keep it small: at 4 fps the previous 300-frame window
// meant a minute and a half of flushing a log line per phase, which is I/O the
// frame is paying for.
constexpr uint64_t kVerboseFrames = 40;
std::atomic<uint64_t> g_verbose_until{kVerboseFrames};

pthread_t g_frame_thread;
std::atomic<int> g_probe_pending{0};

// rolling window of checkpoint tags leading to the current phase
constexpr int kTrail = 28;
char g_trail[kTrail][44];
std::atomic<int> g_trail_n{0};
pthread_mutex_t g_trail_mtx = PTHREAD_MUTEX_INITIALIZER;

inline bool verbose() { return g_frame.load(std::memory_order_relaxed) <= g_verbose_until.load(std::memory_order_relaxed); }
inline void bump() { g_hb.fetch_add(1, std::memory_order_relaxed); }

void note(const char* tag)
{
    const int idx = g_trail_n.fetch_add(1, std::memory_order_relaxed) % kTrail;
    pthread_mutex_lock(&g_trail_mtx);
    snprintf(g_trail[idx], sizeof(g_trail[idx]), "%s", tag);
    pthread_mutex_unlock(&g_trail_mtx);
    bump();
}

inline void set_phase(int p) { g_phase.store(p, std::memory_order_relaxed); bump(); }

// ---- file sink: <game folder>/frame_trace.log ----
// The engine chdir()s into the game folder during init (LocatorAPI.cpp), so the
// CWD is exactly the folder the user can browse -> drop the trace there.
FILE* g_ftf = nullptr;
pthread_mutex_t g_ftf_mtx = PTHREAD_MUTEX_INITIALIZER;
char g_ftf_path[1024] = { 0 };

void emit_to_file(const char* buf, bool flush_now)
{
    char stamp[32] = { 0 };
    timespec ts{};
    clock_gettime(CLOCK_REALTIME, &ts);
    tm t{};
    localtime_r(&ts.tv_sec, &t);
    snprintf(stamp, sizeof(stamp), "%02d:%02d:%02d.%03d | ",
        t.tm_hour, t.tm_min, t.tm_sec, (int)(ts.tv_nsec / 1000000));

    pthread_mutex_lock(&g_ftf_mtx);
    if (g_ftf)
    {
        fputs(stamp, g_ftf);
        fputs(buf, g_ftf);
        fputc('\n', g_ftf);
        if (flush_now)
            fflush(g_ftf);
    }
    pthread_mutex_unlock(&g_ftf_mtx);
}

// Per-frame phase markers. This is the hot path -- a handful per frame, on the
// frame thread -- and an fflush is a write(2) into the storage stack, so leave
// these to stdio's own buffering. Losing the last few lines to a hard kill is
// fine; a stall dump goes through alog(), which does force the stream out.
void vlog(const char* fmt, ...) __attribute__((format(printf, 1, 2)));
void vlog(const char* fmt, ...)
{
    if (!verbose())
        return;
    char buf[768];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    __android_log_write(ANDROID_LOG_INFO, TAG, buf);
    emit_to_file(buf, /*flush_now*/ false);
}

// Stalls, crashes and state changes. Rare, and the process may be about to die,
// so always force the line out to disk.
void alog(const char* fmt, ...) __attribute__((format(printf, 1, 2)));
void alog(const char* fmt, ...)
{
    char buf[768];
    va_list ap;
    va_start(ap, fmt);
    vsnprintf(buf, sizeof(buf), fmt, ap);
    va_end(ap);
    __android_log_write(ANDROID_LOG_ERROR, TAG, buf);
    emit_to_file(buf, /*flush_now*/ true);
}

// --- backtrace captured on the frame thread inside the probe handler ---
constexpr int kBt = 48;
void* g_bt[kBt];
int g_bt_n = 0;

struct BtState
{
    void** f;
    int* n;
    int cap;
};
_Unwind_Reason_Code bt_cb(struct _Unwind_Context* c, void* arg)
{
    auto* s = static_cast<BtState*>(arg);
    if (*s->n < s->cap)
    {
        const uintptr_t pc = _Unwind_GetIP(c);
        if (pc)
            s->f[(*s->n)++] = reinterpret_cast<void*>(pc);
    }
    return _URC_NO_REASON;
}

void on_probe(int, siginfo_t*, void*)
{
    g_bt_n = 0;
    BtState st{g_bt, &g_bt_n, kBt};
    _Unwind_Backtrace(bt_cb, &st);
    g_probe_pending.store(0, std::memory_order_release);
}

void dump_backtrace(const char* why)
{
    alog("\n*** OpenXRay STALL BACKTRACE *** (%s)", why);
    for (int i = 0; i < g_bt_n; ++i)
    {
        Dl_info di;
        const char* sym = "??";
        const char* obj = "??";
        if (dladdr(g_bt[i], &di) != 0)
        {
            if (di.dli_sname)
                sym = di.dli_sname;
            if (di.dli_fname)
                obj = di.dli_fname;
        }
        alog("    #%02d %p %s (%s)", i, g_bt[i], sym, obj);
    }
    int n = g_trail_n.load(std::memory_order_relaxed);
    if (n > kTrail)
        n = kTrail;
    alog("    --- checkpoint trail (oldest->newest) ---");
    pthread_mutex_lock(&g_trail_mtx);
    for (int i = 0; i < n; ++i)
        alog("    . %s", g_trail[i]);
    pthread_mutex_unlock(&g_trail_mtx);
}

// ---------------------------------------------------------------------------
// /proc inspection: the only reliable way to tell "blocked in a driver ioctl"
// (D state) from "sleeping on a lock/futex" (S state) on Android.
// ---------------------------------------------------------------------------
bool read_proc(const char* path, char* out, size_t cap)
{
    out[0] = 0;
    FILE* f = fopen(path, "r");
    if (!f)
        return false;
    if (fgets(out, (int)cap, f) == nullptr)
        out[0] = 0;
    fclose(f);
    size_t n = strlen(out);
    while (n > 0 && (out[n - 1] == '\n' || out[n - 1] == '\r'))
        out[--n] = 0;
    return out[0] != 0;
}

// 'R' runnable, 'S' interruptible sleep, 'D' uninterruptible sleep (driver ioctl)
char thread_state(pid_t tid, char* stat_out, size_t cap)
{
    char path[128];
    snprintf(path, sizeof(path), "/proc/self/task/%d/stat", (int)tid);
    if (!read_proc(path, stat_out, cap))
        return '?';
    // "pid (comm) state ..." -- comm may contain spaces/parens, so scan from the back
    char* p = strrchr(stat_out, ')');
    if (!p)
        return '?';
    for (++p; *p == ' '; ++p)
        ;
    return *p;
}

// aarch64 syscall numbers that actually matter for a frame stall
const char* syscall_name(long nr)
{
    switch (nr)
    {
    case 7:   return "poll";
    case 19:  return "eventfd2";
    case 22:  return "timerfd_create";
    case 25:  return "fcntl";
    case 29:  return "ioctl";
    case 43:  return "statfs";
    case 56:  return "openat";
    case 57:  return "close";
    case 61:  return "getdents64";
    case 63:  return "read";
    case 64:  return "write";
    case 66:  return "writev";
    case 73:  return "ppoll";
    case 94:  return "ftruncate";
    case 98:  return "futex";
    case 101: return "nanosleep";
    case 115: return "clock_nanosleep";
    case 124: return "sched_yield";
    case 143: return "flock";
    case 160: return "setrlimit";
    case 167: return "prctl";
    case 203: return "mlock";
    case 215: return "munmap";
    case 222: return "mmap";
    case 232: return "epoll_wait";
    case 233: return "madvise";
    case 275: return "splice";
    case 286: return "inotify_add_watch";
    case 294: return "inotify_init1";
    default:  return "?";
    }
}

void log_proc(const char* label, pid_t tid)
{
    char path[128], buf[2048], stat[2048], comm[128];
    const char st = thread_state(tid, stat, sizeof(stat));
    alog("    [%s tid=%d] state=%c", label, (int)tid, st);

    snprintf(path, sizeof(path), "/proc/self/task/%d/comm", (int)tid);
    if (read_proc(path, comm, sizeof(comm)))
        alog("        comm    = %s", comm);

    snprintf(path, sizeof(path), "/proc/self/task/%d/wchan", (int)tid);
    alog("        wchan   = %s", read_proc(path, buf, sizeof(buf)) ? buf : "<unavailable>");

    snprintf(path, sizeof(path), "/proc/self/task/%d/syscall", (int)tid);
    if (read_proc(path, buf, sizeof(buf)))
    {
        long nr = 0;
        const int got = sscanf(buf, "%ld", &nr);
        alog("        syscall = %s", got == 1 ? syscall_name(nr) : "?");
        alog("        raw     = %s", buf); // nr arg0..arg5 sp pc
    }
    else
        alog("        syscall = <unavailable>");

    snprintf(path, sizeof(path), "/proc/self/task/%d/stack", (int)tid);
    alog("        stack   = %s", read_proc(path, buf, sizeof(buf)) ? buf : "<needs root>");

    alog("        stat    = %s", stat);
}

void dump_all_threads()
{
    alog("    --- all threads: tid state comm wchan ---");
    DIR* d = opendir("/proc/self/task");
    if (!d)
    {
        alog("    <opendir /proc/self/task failed>");
        return;
    }
    struct dirent* e;
    while ((e = readdir(d)) != nullptr)
    {
        if (e->d_name[0] == '.')
            continue;
        const pid_t tid = (pid_t)atoi(e->d_name);
        char stat[2048], comm[128], wchan[256];
        const char st = thread_state(tid, stat, sizeof(stat));
        snprintf(comm, sizeof(comm), "/proc/self/task/%d/comm", (int)tid);
        snprintf(wchan, sizeof(wchan), "/proc/self/task/%d/wchan", (int)tid);
        char comm_buf[128] = "?", wchan_buf[256] = "?";
        read_proc(comm, comm_buf, sizeof(comm_buf));
        read_proc(wchan, wchan_buf, sizeof(wchan_buf));
        alog("      tid=%-6d %c %-18s %s", (int)tid, st, comm_buf, wchan_buf);
    }
    closedir(d);
}

// hooks used by x_ray.cpp / Device_destroy.cpp
void mainloop_mark(int slot, const char* tag)
{
    set_phase(PH_MAIN_LOOP_PRE + slot);
    note(tag);
    vlog("[ft] main loop | %s", tag);
}

void reset_mark(const char* tag)
{
    set_phase(PH_DEVICE_RESET);
    note(tag);
    vlog("[ft] device reset | %s", tag);
}

void* watchdog_main(void*)
{
    pthread_setname_np(pthread_self(), "xr_watchdog");
    uint64_t last_hb = 0;
    int stalled = 0;
    int heavy_dumps = 0;
    const timespec ts{1, 0};
    for (;;)
    {
        nanosleep(&ts, nullptr);
        const uint64_t hb = g_hb.load(std::memory_order_relaxed);
        if (hb != last_hb)
        {
            last_hb = hb;
            stalled = 0;
            continue;
        }
        if (++stalled < 12) // ~12 s with zero progress anywhere
            continue;
        const int ph = g_phase.load(std::memory_order_relaxed);
        const uint64_t fr = g_frame.load(std::memory_order_relaxed);
        char why[256];
        snprintf(why, sizeof(why), "no frame progress ~%ds; phase=%s; frames_done=%llu; probing...",
            stalled, phase_name(ph), (unsigned long long)fr);
        alog("*** OpenXRay STALL: %s", why);

        // Kernel-side state first: works even when the thread cannot run userspace
        // code at all, so it survives a hard D-state driver hang.
        if (heavy_dumps == 0 || stalled % 30 == 0)
        {
            ++heavy_dumps;
            log_proc("FRAME THREAD", (pid_t)g_frame_thread);
            dump_all_threads();
        }

        g_probe_pending.store(1, std::memory_order_release);
        pthread_kill(g_frame_thread, SIGUSR2);
        // give the handler a moment if the frame thread is in an interruptible block
        for (int i = 0; i < 50 && g_probe_pending.load(std::memory_order_acquire); ++i)
            usleep(2000);
        if (g_bt_n > 0)
            dump_backtrace(why);
        else
            alog("*** OpenXRay STALL: SIGUSR2 was not delivered within 100 ms -> the frame thread "
                 "is NOT in userspace (kernel wait / uninterruptible). See the /proc dump above. ***");
    }
    return nullptr;
}

void install()
{
    if (g_installed.exchange(true))
        return;
    g_frame_thread = pthread_self();

    // Open the trace log in the game folder (engine chdir'ed there at init).
    char cwd[900];
    if (getcwd(cwd, sizeof(cwd)) != nullptr)
    {
        snprintf(g_ftf_path, sizeof(g_ftf_path), "%s/frame_trace.log", cwd);
        g_ftf = fopen(g_ftf_path, "w");
    }

    struct sigaction sa;
    memset(&sa, 0, sizeof(sa));
    sigemptyset(&sa.sa_mask);
    sa.sa_flags = SA_SIGINFO | SA_ONSTACK | SA_RESTART;
    sa.sa_sigaction = on_probe;
    sigaction(SIGUSR2, &sa, nullptr);
    pthread_t th;
    if (pthread_create(&th, nullptr, watchdog_main, nullptr) == 0)
        pthread_detach(th);
    alog("[ft] watchdog installed on tid %u", (unsigned)gettid());
    alog("[ft] frame trace log file: %s (%s)", g_ftf_path[0] ? g_ftf_path : "<unavailable>",
        g_ftf ? "opened" : "FAILED to open");
    Msg("[ft] frame trace log: %s", g_ftf_path[0] ? g_ftf_path : "<unavailable>");
}

// called once when a fresh level becomes current -> extend verbose window
void level_changed(IGame_Level* lvl)
{
    const uint64_t fr = g_frame.load(std::memory_order_relaxed);
    g_verbose_until.store(fr + kVerboseFrames, std::memory_order_relaxed);
    g_trail_n.store(0, std::memory_order_relaxed);
    alog("[ft] === LEVEL STATE CHANGE: g_pGameLevel=%p (verbose through frame %llu) ===",
        lvl, (unsigned long long)(fr + kVerboseFrames));
}
} // namespace ft
#endif // Android

bool CRenderDevice::RenderBegin()
{
    if (GEnv.isDedicatedServer)
        return true;

    ZoneScoped;

    switch (GEnv.Render->GetDeviceState())
    {
    case DeviceState::Normal: break;
    case DeviceState::Lost:
        // If the device was lost, do not render until we get it back
        Sleep(33);
        return false;

    case DeviceState::NeedReset:
        // Check if the device is ready to be reset
        Reset();
        return false;

    default: R_ASSERT(0);
    }
    GEnv.Render->Begin();
    g_bRendering = true;

    return true;
}

void CRenderDevice::Clear() { GEnv.Render->Clear(); }

void CRenderDevice::RenderEnd(void)
{
    if (GEnv.isDedicatedServer)
        return;

    ZoneScoped;
    if (dwPrecacheFrame)
    {
        GEnv.Sound->set_master_volume(0.f);
        dwPrecacheFrame--;
        if (!dwPrecacheFrame)
        {
            GEnv.Render->updateGamma();
            if (precache_light)
            {
                precache_light->set_active(false);
                precache_light.destroy();
            }
            GEnv.Sound->set_master_volume(1.f);
            GEnv.Render->ResourcesDestroyNecessaryTextures();
            Memory.mem_compact();
            Msg("* MEMORY USAGE: %d K", Memory.mem_usage() / 1024);
            Msg("* End of synchronization A[%d] R[%d]", b_is_Active, b_is_Ready);
            FIND_CHUNK_COUNTER_FLUSH();
            if (g_pGamePersistent->GameType() == 1 && !psDeviceFlags.test(rsAlwaysActive)) // haCk
            {
                const Uint32 flags = SDL_GetWindowFlags(m_sdlWnd);
                if ((flags & SDL_WINDOW_INPUT_FOCUS) == 0)
                    Pause(true, true, true, "application start");
            }
        }
    }
    // end scene
    g_bRendering = false;
    GEnv.Render->End();

    vCameraPositionSaved = vCameraPosition;
    vCameraDirectionSaved = vCameraDirection;
    vCameraTopSaved = vCameraTop;
    vCameraRightSaved = vCameraRight;

    mFullTransformSaved = mFullTransform;
    mViewSaved = mView;
    mProjectSaved = mProject;
}

void CRenderDevice::PreCache(u32 amount, bool wait_user_input)
{
    if (GEnv.isDedicatedServer)
        amount = 0;
    else if (GEnv.Render->GetForceGPU_REF())
        amount = 0;

    dwPrecacheFrame = dwPrecacheTotal = amount;
    if (amount && !precache_light && g_pGameLevel && g_loading_events.empty())
    {
        precache_light = GEnv.Render->light_create();
        precache_light->set_shadow(false);
        precache_light->set_position(vCameraPosition);
        precache_light->set_color(255, 255, 255);
        precache_light->set_range(5.0f);
        precache_light->set_active(true);
    }
    if (amount && !load_screen_renderer.IsActive())
    {
        load_screen_renderer.Start(wait_user_input);
    }
}

void CRenderDevice::CalcFrameStats()
{
    stats.RenderTotal.FrameEnd();
    do
    {
        // calc FPS & TPS
        if (fTimeDeltaReal <= EPS_S)
            break;
        const float fps = 1.f / fTimeDeltaReal;
        // if (Engine.External.tune_enabled) vtune.update (fps);
        constexpr float fOne = 0.3f;
        constexpr float fInv = 1.0f - fOne;
        stats.fFPS = fInv * stats.fFPS + fOne * fps;
        if (stats.RenderTotal.result > EPS_S)
        {
            const u32 renderedPolys = GEnv.Render->GetCacheStatPolys();
            stats.fTPS = fInv * stats.fTPS + fOne * float(renderedPolys) / (stats.RenderTotal.result * 1000.f);
            stats.fRFPS = fInv * stats.fRFPS + fOne * 1000.f / stats.RenderTotal.result;
        }
    } while (false);
    stats.RenderTotal.FrameStart();
}

int g_svDedicateServerUpdateReate = 100;

ENGINE_API xr_list<LOADING_EVENT> g_loading_events;

bool CRenderDevice::BeforeFrame()
{
    ZoneScoped;

    if (!b_is_Ready)
    {
        Sleep(100);
        return false;
    }

    if (psDeviceFlags.test(rsStatistic))
        g_bEnableStatGather = true; // XXX: why not use either rsStatistic or g_bEnableStatGather?
    else
        g_bEnableStatGather = false;

    if (!g_loading_events.empty())
    {
        if (g_loading_events.front()())
            g_loading_events.pop_front();
        g_pGamePersistent->LoadDraw();
        return false;
    }

    return true;
}

void CRenderDevice::OnCameraUpdated()
{
    static u32 frame{ u32(-1) };
    if (frame == dwFrame)
        return;

    ZoneScoped;

    // Precache
    if (dwPrecacheFrame)
    {
        const float factor = float(dwPrecacheFrame) / float(dwPrecacheTotal);
        const float angle = PI_MUL_2 * factor;
        vCameraDirection.set(_sin(angle), 0, _cos(angle));
        vCameraDirection.normalize();
        vCameraTop.set(0, 1, 0);
        vCameraRight.crossproduct(vCameraTop, vCameraDirection);
        mView.build_camera_dir(vCameraPosition, vCameraDirection, vCameraTop);
    }

    // Matrices
    mInvView.invert(mView);
    mFullTransform.mul(mProject, mView);
    mInvFullTransform.invert_44(mFullTransform);
    GEnv.Render->OnCameraUpdated();
    GEnv.Render->SetCacheXform(mView, mProject);

    frame = dwFrame;
}

static void UpdateViewports()
{
    // Update and Render additional Platform Windows
    if (ImGui::GetIO().ConfigFlags & ImGuiConfigFlags_ViewportsEnable)
    {
        ImGui::UpdatePlatformWindows();
        ImGui::RenderPlatformWindowsDefault();
    }
}

void CRenderDevice::DoRender()
{
    if (GEnv.isDedicatedServer)
        return;

    ZoneScoped;

    CStatTimer renderTotalReal;
    renderTotalReal.FrameStart();
    renderTotalReal.Begin();
#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::install();
    ft::set_phase(ft::PH_RENDER_BEGIN);
    ft::note("DoRender: enter");
    ft::vlog("[ft] frame %llu | DoRender begin (b_is_Active=%d, level=%p)",
        (unsigned long long)ft::g_frame.load(std::memory_order_relaxed), (int)b_is_Active, g_pGameLevel);
#endif
    if (b_is_Active && RenderBegin())
    {
        {
            ZoneScopedN("Render process");
#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
            ft::set_phase(ft::PH_SEQ_RENDER);
            ft::note("seqRender.Process: begin");
            ft::vlog("[ft] frame %llu | seqRender.Process begin",
                (unsigned long long)ft::g_frame.load(std::memory_order_relaxed));
#endif
            seqRender.Process(); // all rendering is done here
#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
            ft::note("seqRender.Process: end");
            ft::vlog("[ft] frame %llu | seqRender.Process end",
                (unsigned long long)ft::g_frame.load(std::memory_order_relaxed));
            ft::set_phase(ft::PH_IMGUI);
            ft::note("imgui+viewports");
#endif
        }

        CalcFrameStats();
        Statistic->Show();

        ImGui::Render();
        m_imgui_render->Render(ImGui::GetDrawData());
        UpdateViewports();

#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
        ft::set_phase(ft::PH_RENDER_END);
        ft::note("RenderEnd(present/swap): begin");
        ft::vlog("[ft] frame %llu | RenderEnd (swap/present) begin",
            (unsigned long long)ft::g_frame.load(std::memory_order_relaxed));
#endif
        RenderEnd(); // Present goes here
#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
        ft::note("RenderEnd(present/swap): end");
        ft::vlog("[ft] frame %llu | RenderEnd (swap/present) end",
            (unsigned long long)ft::g_frame.load(std::memory_order_relaxed));
#endif
    }
    else
    {
        UpdateViewports();
    }
    renderTotalReal.End();
    renderTotalReal.FrameEnd();
    stats.RenderTotal.accum = renderTotalReal.accum;
}

void CRenderDevice::ProcessFrame()
{
    ZoneScoped;

#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::install();
    ft::set_phase(ft::PH_FRAME_START);
    static IGame_Level* ft_last_level = nullptr;
    if (g_pGameLevel != ft_last_level)
    {
        ft_last_level = g_pGameLevel;
        ft::level_changed(g_pGameLevel);
    }
    const u64 ft_frame = ft::g_frame.load(std::memory_order_relaxed);
    ft::note("ProcessFrame: enter");
    ft::vlog("[ft] ===== frame %llu BEGIN (level=%p, paused=%d) =====",
        (unsigned long long)ft_frame, g_pGameLevel, (int)Paused());
#endif

    if (!BeforeFrame())
        return;

#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::set_phase(ft::PH_BEFORE_FRAME);
    ft::note("BeforeFrame: done");
#endif

    const u64 frameStartTime = TimerGlobal.GetElapsed_ms();

#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::set_phase(ft::PH_FRAME_MOVE);
    ft::note("FrameMove: begin");
    ft::vlog("[ft] frame %llu | FrameMove (update/scripts) begin", (unsigned long long)ft_frame);
#endif
    FrameMove();
#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::note("FrameMove: end");
    ft::vlog("[ft] frame %llu | FrameMove end (t=%llums)", (unsigned long long)ft_frame,
        (unsigned long long)(TimerGlobal.GetElapsed_ms() - frameStartTime));
    ft::set_phase(ft::PH_CAMERA);
    ft::note("OnCameraUpdated");
#endif

    OnCameraUpdated();

    const auto& processSeqParallel = TaskScheduler->AddTask([this]
    {
        ZoneScopedN("ProcessParallelSequence");
        for (u32 pit = 0; pit < seqParallel.size(); pit++)
            seqParallel[pit]();
        seqParallel.clear();
        seqFrameMT.Process();
    });

#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::set_phase(ft::PH_PARALLEL);
    ft::note("DoRender: dispatched (seqParallel task queued)");
#endif
    DoRender();

#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::set_phase(ft::PH_TASK_WAIT);
    ft::note("TaskScheduler->Wait: begin");
    ft::vlog("[ft] frame %llu | waiting on parallel task", (unsigned long long)ft_frame);
#endif
    TaskScheduler->Wait(processSeqParallel);

#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::note("TaskScheduler->Wait: end");
    ft::vlog("[ft] frame %llu | frame complete (t=%llums) -- all phases OK",
        (unsigned long long)ft_frame, (unsigned long long)(TimerGlobal.GetElapsed_ms() - frameStartTime));
    ft::set_phase(ft::PH_FRAME_END);
    ft::g_frame.store(ft_frame + 1, std::memory_order_relaxed);
#endif

    const u64 frameEndTime = TimerGlobal.GetElapsed_ms();
    const u64 frameTime = frameEndTime - frameStartTime;

    u32 updateDelta = 1000 / ps_fps_limit;

    if (GEnv.isDedicatedServer)
        updateDelta = 1000 / g_svDedicateServerUpdateReate;

    else if (Paused() || g_pGameLevel == nullptr)
        updateDelta = 1000 / ps_fps_limit_in_menu;

    if (frameTime < updateDelta)
        Sleep(updateDelta - frameTime);

    if (!b_is_Active)
        Sleep(1);
}

void CRenderDevice::ProcessEvent(const SDL_Event& event)
{
    ZoneScoped;

#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::set_phase(ft::PH_MAIN_LOOP_EVENTS);
    ft::note("ProcessEvent: enter");
    ft::vlog("[ft] ProcessEvent type=0x%08x", (unsigned)event.type);
#endif

    switch (event.type)
    {
    case SDL_DISPLAYEVENT:
    {
        switch (event.display.type)
        {
        case SDL_DISPLAYEVENT_ORIENTATION:
        case SDL_DISPLAYEVENT_CONNECTED:
        case SDL_DISPLAYEVENT_DISCONNECTED:
            CleanupVideoModes();
            FillVideoModes();
            if (event.display.display == psDeviceMode.Monitor && event.display.type != SDL_DISPLAYEVENT_CONNECTED)
            {
#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
                ft::reset_mark("Reset() from SDL_DISPLAYEVENT");
#endif
                Reset();
            }
            else
                UpdateWindowProps();
            break;
        } // switch (event.display.type)
        break;
    }
    case SDL_WINDOWEVENT:
    {
        const auto window = SDL_GetWindowFromID(event.window.windowID);
        if (!window)
            break;
        ImGuiViewport* viewport = ImGui::FindViewportByPlatformHandle(window);
        if (!viewport)
            break;

        switch (event.window.event)
        {
        case SDL_WINDOWEVENT_MOVED:
        {
            if (window == m_sdlWnd)
            {
                UpdateWindowRects();
            }
            if (viewport)
                viewport->PlatformRequestMove = true;
            break;
        }

        case SDL_WINDOWEVENT_DISPLAY_CHANGED:
            psDeviceMode.Monitor = event.window.data1;
            break;

        case SDL_WINDOWEVENT_RESIZED:
            if (window == m_sdlWnd)
                UpdateWindowRects();
            break;

        case SDL_WINDOWEVENT_SIZE_CHANGED:
        {
            if (window == m_sdlWnd)
            {
                UpdateWindowRects();

                if (static_cast<int>(psDeviceMode.Width) == event.window.data1 &&
                    static_cast<int>(psDeviceMode.Height) == event.window.data2)
                    break; // we don't need to reset device if resolution wasn't really changed

                psDeviceMode.Width = event.window.data1;
                psDeviceMode.Height = event.window.data2;

#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
                ft::reset_mark("Reset() from SDL_WINDOWEVENT_SIZE_CHANGED");
#endif
                Reset();
            }
            if (viewport)
                viewport->PlatformRequestResize = true;

            break;
        }

        case SDL_WINDOWEVENT_CLOSE:
        {
            if (viewport)
                viewport->PlatformRequestClose = true;

            if (window == m_sdlWnd)
            {
                Engine.Event.Defer("KERNEL:disconnect");
                Engine.Event.Defer("KERNEL:quit");
            }
            break;
        }
        } // switch (event.window.event)
    }
    } // switch (event.type)

    editor().ProcessEvent(event);

#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::note("ProcessEvent: end");
#endif
}

void CRenderDevice::Run()
{
    ZoneScoped;

    g_bLoaded = false;
    Log("Starting engine...");

    // Startup timers and calculate timer delta
    dwTimeGlobal = 0;
    Timer_MM_Delta = 0;
    {
        const u32 time_mm = CPU::GetTicks();
        while (CPU::GetTicks() == time_mm)
            ; // wait for next tick
        const u32 time_system = CPU::GetTicks();
        const u32 time_local = TimerAsync();
        Timer_MM_Delta = time_system - time_local;
    }

    SDL_HideWindow(m_sdlWnd); // workaround for SDL bug
    UpdateWindowProps();
    SDL_ShowWindow(m_sdlWnd);
    SDL_RaiseWindow(m_sdlWnd);
}

void CRenderDevice::Shutdown()
{
    ZoneScoped;
    seqAppEnd.Process();
}

u32 app_inactive_time = 0;
u32 app_inactive_time_start = 0;

void CRenderDevice::FrameMove()
{
    ZoneScoped;

    dwFrame++;
    Core.dwFrame = dwFrame;
    dwTimeContinual = TimerMM.GetElapsed_ms() - app_inactive_time;

    fTimeDeltaReal = Timer.GetElapsed_sec();
    if (!_valid(fTimeDeltaReal))
        fTimeDeltaReal = EPS_S + EPS_S;
    Timer.Start(); // previous frame

    if (psDeviceFlags.test(rsConstantFPS))
    {
        // 20ms = 50fps
        // fTimeDelta = 0.020f;
        // fTimeGlobal += 0.020f;
        // dwTimeDelta = 20;
        // dwTimeGlobal += 20;
        // 33ms = 30fps
        fTimeDelta = 0.033f;
        fTimeGlobal += 0.033f;
        dwTimeDelta = 33;
        dwTimeGlobal += 33;
    }
    else
    {
        if (Paused())
            fTimeDelta = 0.0f;
        else
        {
            fTimeDelta = 0.1f * fTimeDelta + 0.9f * fTimeDeltaReal; // smooth random system activity - worst case ~7% error
            clamp(fTimeDelta, EPS_S + EPS_S, .1f); // limit to 10fps minimum
        }
        fTimeGlobal = TimerGlobal.GetElapsed_sec();
        const u32 _old_global = dwTimeGlobal;
        dwTimeGlobal = TimerGlobal.GetElapsed_ms();
        dwTimeDelta = dwTimeGlobal - _old_global;
    }
    ImGui::GetIO().DeltaTime = fTimeDeltaReal;

    m_imgui_render->Frame();
    ImGui::NewFrame();

    // Frame move
    stats.EngineTotal.FrameStart();
    stats.EngineTotal.Begin();
    // TODO: HACK to test loading screen.
    // if(!g_bLoaded)

#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::set_phase(ft::PH_FRAME_MOVE);
    ft::note("FrameMove: seqFrame.Process begin");
#endif
    seqFrame.Process();
#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::note("FrameMove: seqFrame.Process end");
    ft::vlog("[ft] frame %llu | seqFrame.Process (event pump / eStart) returned",
        (unsigned long long)ft::g_frame.load(std::memory_order_relaxed));
#endif

    g_bLoaded = true;
    // else
    // seqFrame.Process(rp_Frame);
    stats.EngineTotal.End();
    stats.EngineTotal.FrameEnd();

#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::note("FrameMove: ImGui::EndFrame");
#endif
    ImGui::EndFrame();
#if defined(XR_PLATFORM_ANDROID) && !defined(_EDITOR)
    ft::note("FrameMove: end");
    ft::vlog("[ft] frame %llu | FrameMove end",
        (unsigned long long)ft::g_frame.load(std::memory_order_relaxed));
#endif
}

ENGINE_API bool bShowPauseString = true;

void CRenderDevice::Pause(bool bOn, bool bTimer, bool bSound, [[maybe_unused]] pcstr reason)
{
    static int snd_emitters_ = -1;
    if (g_bBenchmark || GEnv.isDedicatedServer)
        return;

    if (bOn)
    {
        if (!Paused())
        {
            if (editor_mode())
                bShowPauseString = false;
#ifdef DEBUG
            else if (xr_strcmp(reason, "li_pause_key_no_clip") == 0)
                bShowPauseString = false;
#endif
            else
                bShowPauseString = true;
        }
        if (bTimer && (!g_pGamePersistent || g_pGamePersistent->CanBePaused()))
        {
            g_pauseMngr().Pause(true);
#ifdef DEBUG
            if (xr_strcmp(reason, "li_pause_key_no_clip") == 0)
                TimerGlobal.Pause(false);
#endif
        }
        if (bSound && GEnv.Sound)
            snd_emitters_ = GEnv.Sound->pause_emitters(true);
    }
    else
    {
        if (bTimer && g_pauseMngr().Paused())
        {
            fTimeDelta = EPS_S + EPS_S;
            g_pauseMngr().Pause(false);
        }
        if (bSound)
        {
            if (snd_emitters_ > 0) // avoid crash
                snd_emitters_ = GEnv.Sound->pause_emitters(false);
            else
            {
#ifdef DEBUG
                Log("GEnv.Sound->pause_emitters underflow");
#endif
            }
        }
    }
}

bool CRenderDevice::Paused() { return g_pauseMngr().Paused(); }

void CRenderDevice::OnWindowActivate(SDL_Window* window, bool activated)
{
    ZoneScoped;

    if (editor().GetState() == editor::ide::visible_state::full)
    {
        if (window != m_sdlWnd)
        {
            if (activated)
                editor().OnAppActivate();
            else
                editor().OnAppDeactivate();
        }
        return;
    }

    if (!GEnv.isDedicatedServer && activated)
        pInput->GrabInput(true);
    else
        pInput->GrabInput(false);

    b_is_Active = activated || psDeviceFlags.test(rsAlwaysActive);

    if (activated != b_is_InFocus)
    {
        b_is_InFocus = activated;
        if (b_is_InFocus)
        {
            TaskScheduler->Pause(false);
            seqAppActivate.Process();
            app_inactive_time += TimerMM.GetElapsed_ms() - app_inactive_time_start;
        }
        else
        {
            app_inactive_time_start = TimerMM.GetElapsed_ms();
            seqAppDeactivate.Process();
            TaskScheduler->Pause(true);
        }
    }
}

void CRenderDevice::time_factor(const float time_factor)
{
    Timer.time_factor(time_factor);
    TimerGlobal.time_factor(time_factor);
    if (!strstr(Core.Params, "-sound_constant_speed"))
        psSoundTimeFactor = time_factor; //--#SM+#--
}

void CRenderDevice::AddSeqFrame(pureFrame* f, bool mt)
{
    if (mt)
        seqFrameMT.Add(f, REG_PRIORITY_HIGH);
    else
        seqFrame.Add(f, REG_PRIORITY_LOW);
}

void CRenderDevice::RemoveSeqFrame(pureFrame* f)
{
    seqFrameMT.Remove(f);
    seqFrame.Remove(f);
}

static int script_device_time_global(lua_State* L)
{
    lua_pushinteger(L, static_cast<lua_Integer>(Device.dwTimeGlobal));
    return 1;
}

static int script_device_time_global_async(lua_State* L)
{
    lua_pushinteger(L, static_cast<lua_Integer>(Device.TimerAsync_MMT()));
    return 1;
}

void CRenderDevice::script_register(lua_State* luaState)
{
    using namespace luabind;
    module(luaState)
    [
        class_<CRenderDevice>("render_device")
            .def_readonly("width", &CRenderDevice::dwWidth)
            .def_readonly("height", &CRenderDevice::dwHeight)
            .def_readonly("time_delta", &CRenderDevice::dwTimeDelta)
            .def_readonly("f_time_delta", &CRenderDevice::fTimeDelta)
            .def_readonly("cam_pos", &CRenderDevice::vCameraPosition)
            .def_readonly("cam_dir", &CRenderDevice::vCameraDirection)
            .def_readonly("cam_top", &CRenderDevice::vCameraTop)
            .def_readonly("cam_right", &CRenderDevice::vCameraRight)
            //			.def_readonly("view",					&CRenderDevice::mView)
            //			.def_readonly("projection",				&CRenderDevice::mProject)
            //			.def_readonly("full_transform",			&CRenderDevice::mFullTransform)
            .def_readonly("fov", &CRenderDevice::fFOV)
            .def_readonly("aspect_ratio", &CRenderDevice::fASPECT)
            .def_readonly("precache_frame", &CRenderDevice::dwPrecacheFrame)
            .def_readonly("frame", &CRenderDevice::dwFrame)
            .def("time_global", +[](const CRenderDevice* self)
            {
                return (self->dwTimeGlobal);
            })
            .def("is_paused", +[](CRenderDevice* device)
            {
                return device->Paused();
            })
            .def("pause", +[](CRenderDevice* device, bool b)
            {
                device->Pause(b, TRUE, FALSE, "set_device_paused_script");
            }),

        def("app_ready", +[]()
        {
            return g_pGamePersistent->IsLoaded();
        }),
        def("device", +[]()
        {
            return &Device;
        }),
        def("time_global", +[]()
        {
            return Device.dwTimeGlobal;
        }),
        def("time_global_async", +[]()
        {
            return Device.TimerAsync_MMT();
        }),
        def("device_pause", +[](bool b)
        {
            Device.Pause(b, TRUE, FALSE, "set_device_paused_script");
        }),
        def("device_is_paused", +[]()
        {
            return Device.Paused();
        })
    ];

    // Direct C-function registration to guarantee time_global never returns nil or loops
    lua_register(luaState, "device_time_global", script_device_time_global);
    lua_register(luaState, "device_time_global_async", script_device_time_global_async);
    lua_register(luaState, "time_global", script_device_time_global);
    lua_register(luaState, "time_global_async", script_device_time_global_async);

    // Ensure device in Lua always returns a working proxy even if luabind pointer conversion is nil
    luaL_dostring(luaState,
        "if rawget(_G, 'device') == nil or device() == nil then\n"
        "    local fallback_device = {\n"
        "        pause = function(self, b) if device_pause then device_pause(b) end end,\n"
        "        is_paused = function(self) if device_is_paused then return device_is_paused() else return false end end,\n"
        "        time_global = function(self) if device_time_global then return device_time_global() else return 0 end end,\n"
        "        time_global_async = function(self) if device_time_global_async then return device_time_global_async() else return 0 end end,\n"
        "        width = 1920, height = 1080, fov = 67.5, aspect_ratio = 1.777,\n"
        "        precache_frame = 0,\n"
        "        frame = 0\n"
        "    }\n"
        "    device = function() return fallback_device end\n"
        "end\n"
        "if rawget(_G, 'time_global') == nil or type(time_global) ~= 'function' then\n"
        "    time_global = function() if device_time_global then return device_time_global() else return 0 end end\n"
        "end\n"
        "if rawget(_G, 'time_global_async') == nil or type(time_global_async) ~= 'function' then\n"
        "    time_global_async = function() if device_time_global_async then return device_time_global_async() else return 0 end end\n"
        "end\n"
    );
};

void CLoadScreenRenderer::Start(bool b_user_input)
{
    Device.seqFrame.Add(this, 0);
    Device.seqRender.Add(this, 0);
    m_registered = true;
    m_need_user_input = b_user_input;

    g_pGamePersistent->ShowLoadingScreen(true);
    g_pGamePersistent->LoadBegin();
}

void CLoadScreenRenderer::Stop()
{
    if (!m_registered)
        return;
    Device.seqFrame.Remove(this);
    Device.seqRender.Remove(this);

    m_registered = false;
    m_need_user_input = false;

    g_pGamePersistent->ShowLoadingScreen(false);
    g_pGamePersistent->LoadEnd();
}

void CLoadScreenRenderer::OnFrame()
{
    g_pGamePersistent->LoadStage(false);
}

void CLoadScreenRenderer::OnRender()
{
    g_pGamePersistent->load_draw_internal();
}
