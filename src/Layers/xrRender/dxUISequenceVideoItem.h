#pragma once

#include "Include/xrRender/UISequenceVideoItem.h"

namespace xray::render::RENDER_NAMESPACE
{
class dxUISequenceVideoItem : public IUISequenceVideoItem
{
public:
    dxUISequenceVideoItem();
    virtual void Copy(IUISequenceVideoItem& _in);

    virtual bool HasTexture() { return !!m_texture; }
    virtual void CaptureTexture();
    virtual void ResetTexture() { m_texture = nullptr; }
    virtual BOOL video_IsPlaying() { return m_texture ? m_texture->video_IsPlaying() : FALSE; }
    virtual void video_Sync(u32 _time) { if (m_texture) m_texture->video_Sync(_time); }
    virtual void video_Play(BOOL looped, u32 _time = 0xFFFFFFFF) { if (m_texture) m_texture->video_Play(looped, _time); }
    virtual void video_Stop() { if (m_texture) m_texture->video_Stop(); };
private:
    CTexture* m_texture;
};
} // namespace xray::render::RENDER_NAMESPACE
