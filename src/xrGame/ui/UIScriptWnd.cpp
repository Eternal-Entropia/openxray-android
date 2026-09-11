#include "pch_script.h"
#include "UIScriptWnd.h"
#include "Common/object_broker.h"
#include "xrUICore/Callbacks/callback_info.h"

CUIDialogWndEx::CUIDialogWndEx() : CUIDialogWnd("CUIDialogWndEx") {}
CUIDialogWndEx::~CUIDialogWndEx() { delete_data(m_callbacks); }

void CUIDialogWndEx::Register(CUIWindow* pChild) { pChild->SetMessageTarget(this); }
void CUIDialogWndEx::Register(CUIWindow* pChild, pcstr name)
{
    pChild->SetWindowName(name);
    pChild->SetMessageTarget(this);
}

void CUIDialogWndEx::SendMessage(CUIWindow* pWnd, s16 msg, void* pData)
{
    Msg(">>> [CUIDialogWndEx] SendMessage: pWnd=%p ('%s'), msg=%d, callbacks_count=%u",
        pWnd, pWnd ? pWnd->WindowName().c_str() : "<null>", (int)msg, (u32)m_callbacks.size());
    FlushLog();

    const auto it = std::find_if(m_callbacks.begin(), m_callbacks.end(), event_comparer{ pWnd, msg });
    if (it == m_callbacks.end())
    {
        Msg(">>> [CUIDialogWndEx] SendMessage: NO MATCHING CALLBACK FOUND for '%s' msg=%d!",
            pWnd ? pWnd->WindowName().c_str() : "<null>", (int)msg);
        FlushLog();
        return inherited::SendMessage(pWnd, msg, pData);
    }

    Msg(">>> [CUIDialogWndEx] SendMessage: Invoking callback for '%s'...", (*it)->m_control_name.c_str());
    FlushLog();
    (*it)->m_callback();
    Msg(">>> [CUIDialogWndEx] SendMessage: Callback for '%s' completed!", (*it)->m_control_name.c_str());
    FlushLog();
}

bool CUIDialogWndEx::Load(pcstr /*xml_name*/) { return true; }

SCallbackInfo* CUIDialogWndEx::NewCallback()
{
    m_callbacks.push_back(xr_new<SCallbackInfo>());
    return m_callbacks.back();
}

void CUIDialogWndEx::AddCallback(LPCSTR control_id, s16 evt, const luabind::functor<void> &lua_function)
{
    Msg(">>> [CUIDialogWndEx] AddCallback(1): control_id='%s', evt=%d", control_id, (int)evt);
    FlushLog();
    SCallbackInfo* c = NewCallback ();
    c->m_callback.set(lua_function);
    c->m_control_name = control_id;
    c->m_event = evt;
}

void CUIDialogWndEx::AddCallback(pcstr control_id, s16 evt, const luabind::functor<void>& functor, const luabind::object& object)
{
    Msg(">>> [CUIDialogWndEx] AddCallback(2): control_id='%s', evt=%d", control_id, (int)evt);
    FlushLog();
    SCallbackInfo* c = NewCallback();
    c->m_callback.set(functor, object);
    c->m_control_name = control_id;
    c->m_event = evt;
}
