// glHW.cpp: implementation of the OpenGL specialisation of CHW.
//////////////////////////////////////////////////////////////////////

#include "stdafx.h"
#pragma hdrstop

#include "glHW.h"
#include "xrEngine/XR_IOConsole.h"

namespace xray::render::RENDER_NAMESPACE
{
CHW HW;

void CALLBACK OnDebugCallback(GLenum /*source*/, GLenum /*type*/, GLuint id, GLenum severity, GLsizei /*length*/,
    const GLchar* message, const void* /*userParam*/)
{
    static size_t spamCount = 0;
    if (severity == GL_DEBUG_SEVERITY_HIGH || severity == GL_DEBUG_SEVERITY_MEDIUM)
    {
        const std::string_view msg = message ? std::string_view(message) : std::string_view();
        if (msg.find("EsxBufferObject") != std::string_view::npos ||
            msg.find("Namespace collision") != std::string_view::npos ||
            msg.find("Unable to delete shader object") != std::string_view::npos ||
            msg.find("unable to delete shader object") != std::string_view::npos)
        {
            if (++spamCount <= 5)
                Log("~ [spam] ", std::string(msg).c_str());
            return;
        }
        Log(message, id);
    }
}

static_assert(std::is_same_v<decltype(&OnDebugCallback), GLDEBUGPROC>);

void UpdateVSync()
{
    if (psDeviceFlags.test(rsVSync))
    {
        // Try adaptive vsync first
        if (SDL_GL_SetSwapInterval(-1) == -1)
            SDL_GL_SetSwapInterval(1);
    }
    else
    {
        SDL_GL_SetSwapInterval(0);
    }
}

CHW::CHW()
{
    if (!ThisInstanceIsGlobal())
        return;

    Device.seqAppActivate.Add(this);
    Device.seqAppDeactivate.Add(this);
}

CHW::~CHW()
{
    if (!ThisInstanceIsGlobal())
        return;

    Device.seqAppActivate.Remove(this);
    Device.seqAppDeactivate.Remove(this);
}

void CHW::OnAppActivate()
{
    if (m_window)
    {
        SDL_RestoreWindow(m_window);
    }
}

void CHW::OnAppDeactivate()
{
    if (m_window)
    {
        if (psDeviceMode.WindowStyle == rsFullscreen || psDeviceMode.WindowStyle == rsFullscreenBorderless)
            SDL_MinimizeWindow(m_window);
    }
}

//////////////////////////////////////////////////////////////////////
// Construction/Destruction
//////////////////////////////////////////////////////////////////////
void CHW::CreateDevice(SDL_Window* hWnd)
{
    ZoneScoped;

    m_window = hWnd;

    R_ASSERT(m_window);

    // Choose the closest pixel format
    SDL_DisplayMode mode;
    SDL_GetWindowDisplayMode(m_window, &mode);
    mode.format = SDL_PIXELFORMAT_RGBA8888;
    // Apply the pixel format to the device context
    SDL_SetWindowDisplayMode(m_window, &mode);

    Caps.fTarget = D3DFMT_A8R8G8B8;
    Caps.fDepth = D3DFMT_D24S8;

    // Create the context
    m_context = SDL_GL_CreateContext(m_window);
    if (m_context == nullptr)
    {
        Log("! OpenGL: could not create drawing context:", SDL_GetError());
        return;
    }

    if (MakeContextCurrent(IRender::PrimaryContext) != 0)
    {
        Log("! OpenGL: could not make context current:", SDL_GetError());
        return;
    }

    int version;
    {
        ZoneScopedN("gladLoadGL");
#if defined(XR_PLATFORM_ANDROID)
        version = gladLoadGLES2(reinterpret_cast<GLADloadfunc>(SDL_GL_GetProcAddress));
#else
        version = gladLoadGL(reinterpret_cast<GLADloadfunc>(SDL_GL_GetProcAddress));
#endif
    }
    if (version == 0)
    {
        Log("! OpenGL: could not initialize GLAD.");
        if (const auto err = SDL_GetError())
            Log("SDL Error:", err);
        return;
    }

    if (ThisInstanceIsGlobal())
    {
        UpdateVSync();

        if (glDebugMessageCallback)
        {
            CHK_GL(glEnable(GL_DEBUG_OUTPUT));
            CHK_GL(glDebugMessageCallback((GLDEBUGPROC)OnDebugCallback, nullptr));
        }
    }

    int iMaxVTFUnits, iMaxCTIUnits;
    glGetIntegerv(GL_MAX_VERTEX_TEXTURE_IMAGE_UNITS, &iMaxVTFUnits);
    glGetIntegerv(GL_MAX_COMBINED_TEXTURE_IMAGE_UNITS, &iMaxCTIUnits);

    AdapterName = reinterpret_cast<pcstr>(glGetString(GL_RENDERER));
    OpenGLVersionString = reinterpret_cast<pcstr>(glGetString(GL_VERSION));
    ShadingVersion = reinterpret_cast<pcstr>(glGetString(GL_SHADING_LANGUAGE_VERSION));

    Msg("* [GLES] Active Renderer: [%s]", AdapterName ? AdapterName : "Unknown");
    Msg("* [GLES] Active Vendor: [%s]", glGetString(GL_VENDOR) ? reinterpret_cast<pcstr>(glGetString(GL_VENDOR)) : "Unknown");
    Msg("* GPU vendor: [%s] device: [%s]", glGetString(GL_VENDOR), AdapterName);
    Msg("* GPU OpenGL version: %s", OpenGLVersionString);
    Msg("* GPU OpenGL shading language version: %s", ShadingVersion);
    Msg("* GPU OpenGL VTF units: [%d] CTI units: [%d]", iMaxVTFUnits, iMaxCTIUnits);

    ComputeShadersSupported = false; // XXX: Implement compute shaders support

    if (glGenFramebuffers && glBindFramebuffer)
        UpdateViews();
}

void CHW::DestroyDevice()
{
    if (pFB && glDeleteFramebuffers)
    {
        CHK_GL(glDeleteFramebuffers(1, &pFB));
        pFB = 0;
    }

    const auto context = SDL_GL_GetCurrentContext();
    if (context && context == m_context)
        SDL_GL_MakeCurrent(nullptr, nullptr);

    if (m_context)
    {
        SDL_GL_DeleteContext(m_context);
        m_context = nullptr;
    }
}

//////////////////////////////////////////////////////////////////////
// Resetting device
//////////////////////////////////////////////////////////////////////
void CHW::Reset()
{
    ZoneScoped;

    CHK_GL(glDeleteFramebuffers(1, &pFB));
    pFB = 0;
    UpdateViews();

    UpdateVSync();
}

void CHW::SetPrimaryAttributes(u32& windowFlags)
{
    windowFlags |= SDL_WINDOW_OPENGL;

#if defined(XR_PLATFORM_ANDROID)
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_ES);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 3);
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 2);
#else
    SDL_GL_SetAttribute(SDL_GL_CONTEXT_PROFILE_MASK, SDL_GL_CONTEXT_PROFILE_CORE);

    if (!strstr(Core.Params, "-no_gl_context"))
    {
        SDL_GL_SetAttribute(SDL_GL_CONTEXT_MAJOR_VERSION, 4);
        SDL_GL_SetAttribute(SDL_GL_CONTEXT_MINOR_VERSION, 1);
    }
#endif

    SDL_GL_SetAttribute(SDL_GL_RED_SIZE, 8);
    SDL_GL_SetAttribute(SDL_GL_GREEN_SIZE, 8);
    SDL_GL_SetAttribute(SDL_GL_BLUE_SIZE, 8);
    SDL_GL_SetAttribute(SDL_GL_ALPHA_SIZE, 8);

    SDL_GL_SetAttribute(SDL_GL_DOUBLEBUFFER, 1);
    SDL_GL_SetAttribute(SDL_GL_DEPTH_SIZE, 24);
    SDL_GL_SetAttribute(SDL_GL_STENCIL_SIZE, 8);
}

IRender::RenderContext CHW::GetCurrentContext() const
{
    const auto context = SDL_GL_GetCurrentContext();
    if (context == m_context)
        return IRender::PrimaryContext;
    return IRender::NoContext;
}

int CHW::MakeContextCurrent(IRender::RenderContext context) const
{
    switch (context)
    {
    case IRender::NoContext:
        return SDL_GL_MakeCurrent(nullptr, nullptr);

    case IRender::PrimaryContext:
        return SDL_GL_MakeCurrent(m_window, m_context);

    default:
        NODEFAULT;
    }
    return -1;
}

void CHW::UpdateViews()
{
    // Create the default framebuffer
    glGenFramebuffers(1, &pFB);
    CHK_GL(glBindFramebuffer(GL_FRAMEBUFFER, pFB));

    BackBufferCount = 1;
}

void CHW::BeginScene() { }
void CHW::EndScene() { }

void CHW::Present()
{
#if 0 // kept for historical reasons
    RImplementation.Target->phase_flip();
#else
    static size_t presentedFrames = 0;
    const size_t frame = ++presentedFrames;
    if (frame == 1)
        Msg("* Present: first frame blit, w=%d h=%d, pFB=0x%X", Device.dwWidth, Device.dwHeight, pFB);

    glBindFramebuffer(GL_READ_FRAMEBUFFER, pFB);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
    GLenum fbs = glCheckFramebufferStatus(GL_READ_FRAMEBUFFER);
    if (fbs != GL_FRAMEBUFFER_COMPLETE || frame <= 3)
        Msg("! Present: READ FBO 0x%X status 0x%X (complete=%d) frame %d", pFB, fbs, fbs == GL_FRAMEBUFFER_COMPLETE, frame);

    if (frame <= 3 && fbs == GL_FRAMEBUFFER_COMPLETE)
    {
        const int w = Device.dwWidth;
        const int h = Device.dwHeight;
        const auto probe = [&](int x, int y, pcstr tag)
        {
            unsigned char px[4] = {};
            glReadPixels(x, y, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
            Msg("  Present: pFB px[%s](%d,%d)=%02X%02X%02X%02X", tag, x, y, px[0], px[1], px[2], px[3]);
        };
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        probe(-1 + w / 2, h / 2, "c");            // note: (w/2,h/2) is fine since 0-based
        probe(0, 0, "bl");
        probe(w - 1, 0, "br");
        probe(0, h - 1, "tl");
        probe(w - 1, h - 1, "tr");
    }

    glBlitFramebuffer(
        0, 0, Device.dwWidth, Device.dwHeight,
        0, 0, Device.dwWidth, Device.dwHeight,
        GL_COLOR_BUFFER_BIT, GL_NEAREST);
    GLenum err = glGetError();
    if (err != GL_NO_ERROR && frame <= 3)
        Msg("! Present: GL error 0x%X after blitFramebuffer (%d)", err, frame);

    if (frame <= 3)
    {
        glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
        const int w = Device.dwWidth;
        const int h = Device.dwHeight;
        unsigned char px[4] = {};
        glPixelStorei(GL_PACK_ALIGNMENT, 1);
        glReadPixels(w / 2, h / 2, 1, 1, GL_RGBA, GL_UNSIGNED_BYTE, px);
        Msg("  Present: DEFAULT FBO center(%d,%d)=%02X%02X%02X%02X (after blit)", w / 2, h / 2, px[0], px[1], px[2], px[3]);
    }
#endif

    SDL_GL_SwapWindow(m_window);
    CurrentBackBuffer = (CurrentBackBuffer + 1) % BackBufferCount;
}

DeviceState CHW::GetDeviceState() const
{
    //  TODO: OGL: Implement GetDeviceState
    return DeviceState::Normal;
}

std::pair<u32, u32> CHW::GetSurfaceSize()
{
    return
    {
        psDeviceMode.Width,
        psDeviceMode.Height
    };
}

bool CHW::ThisInstanceIsGlobal() const
{
    return this == &HW;
}

void CHW::BeginPixEvent(pcstr name) const
{
    if (glPushDebugGroup)
        glPushDebugGroup(GL_DEBUG_SOURCE_APPLICATION, 0, -1, name);
}

void CHW::EndPixEvent() const
{
    if (glPushDebugGroup)
        glPopDebugGroup();
}
} // namespace xray::render::RENDER_NAMESPACE
