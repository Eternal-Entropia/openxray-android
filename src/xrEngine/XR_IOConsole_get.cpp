////////////////////////////////////////////////////////////////////////////
// Module : XR_IOConsole_get.cpp
// Created : 17.05.2008
// Author : Evgeniy Sokolov
// Description : Console`s get-functions class implementation
////////////////////////////////////////////////////////////////////////////

#include "stdafx.h"
#include "XR_IOConsole.h"
#include "xr_ioc_cmd.h"

bool CConsole::GetBool(pcstr cmd) const
{
    IConsole_Command* cc = GetCommand(cmd);
    if (!cc)
        return false;

    if (CCC_Mask* cf = dynamic_cast<CCC_Mask*>(cc))
        return (cf->GetValue() != 0);

    if (CCC_Integer* ci = dynamic_cast<CCC_Integer*>(cc))
        return (ci->GetValue() != 0);

    return cc->GetBool();
}

float CConsole::GetFloat(pcstr cmd, float& min, float& max) const
{
    min = 0.0f;
    max = 0.0f;
    IConsole_Command* cc = GetCommand(cmd);
    if (!cc)
        return 0.0f;

    if (CCC_Float* cf = dynamic_cast<CCC_Float*>(cc))
    {
        cf->GetBounds(min, max);
        return cf->GetValue();
    }

    return cc->GetFloat(min, max);
}

IConsole_Command* CConsole::GetCommand(pcstr cmd) const
{
    const auto it = Commands.find(cmd);
    if (it == Commands.end())
        return NULL;
    else
        return it->second;
}

int CConsole::GetInteger(pcstr cmd, int& min, int& max) const
{
    min = 0;
    max = 1;
    IConsole_Command* cc = GetCommand(cmd);
    if (!cc)
        return 0;

    if (CCC_Integer* cf = dynamic_cast<CCC_Integer*>(cc))
    {
        cf->GetBounds(min, max);
        return cf->GetValue();
    }
    if (CCC_Mask* cm = dynamic_cast<CCC_Mask*>(cc))
    {
        min = 0;
        max = 1;
        return (cm->GetValue()) ? 1 : 0;
    }

    return cc->GetInteger(min, max);
}

pcstr CConsole::GetString(pcstr cmd) const
{
    IConsole_Command* cc = GetCommand(cmd);
    if (!cc)
        return NULL;

    static IConsole_Command::TStatus stat;
    cc->GetStatus(stat);
    return stat;
}

pcstr CConsole::GetToken(pcstr cmd) const { return GetString(cmd); }
const xr_token* CConsole::GetXRToken(pcstr cmd) const
{
    IConsole_Command* cc = GetCommand(cmd);
    if (cc)
    {
        const xr_token* tok = cc->GetToken();
        if (tok)
            return tok;

        CCC_Token* cf = dynamic_cast<CCC_Token*>(cc);
        if (cf)
            return cf->GetToken();
    }

    // Safety fallback for quality presets if token not found/registered
    if (xr_strcmp(cmd, "_preset") == 0)
    {
        static const xr_token fallback_qpreset_token[] =
        {
            { "Minimum", 0 },
            { "Low", 1 },
            { "Default", 2 },
            { "High", 3 },
            { "Extreme", 4 },
            { nullptr, 0 }
        };
        return fallback_qpreset_token;
    }

    return NULL;
}

Fvector* CConsole::GetFVectorPtr(pcstr cmd) const
{
    IConsole_Command* cc = GetCommand(cmd);
    if (!cc)
        return NULL;

    CCC_Vector3* cf = dynamic_cast<CCC_Vector3*>(cc);
    if (cf)
    {
        return cf->GetValuePtr();
    }

    return cc->GetFVectorPtr();
}

Fvector CConsole::GetFVector(pcstr cmd) const
{
    Fvector* pV = GetFVectorPtr(cmd);
    if (pV)
    {
        return *pV;
    }
    return Fvector().set(0.0f, 0.0f, 0.0f);
}
