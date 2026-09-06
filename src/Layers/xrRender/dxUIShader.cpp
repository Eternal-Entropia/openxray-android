#include "stdafx.h"
#include "dxUIShader.h"

namespace xray::render::RENDER_NAMESPACE
{
void dxUIShader::Copy(IUIShader& _in) { *this = *((dxUIShader*)&_in); }
void dxUIShader::create(LPCSTR sh, LPCSTR tex) { hShader.create(sh, tex); }
void dxUIShader::destroy() { hShader.destroy(); }

bool dxUIShader::operator==(const IUIShader& other) const
{
    return hShader == static_cast<const dxUIShader&>(other).hShader;
}

CTexture* dxUIShader::GetBaseTexture() const
{
    if (!hShader || !hShader->E[0] || hShader->E[0]->passes.empty())
        return nullptr;

    const auto& pass_ptr = hShader->E[0]->passes[0];
    if (!pass_ptr || !pass_ptr->T)
        return nullptr;

    const STextureList& textures = *pass_ptr->T;
    if (textures.empty())
        return nullptr;

    const R_constant* sbase = pass_ptr->constants ? pass_ptr->constants->get(baseTexture)._get() : nullptr;
    const size_t idx = (sbase && sbase->samp.index < textures.size()) ? sbase->samp.index : 0;
    if (idx >= textures.size())
        return nullptr;

    return textures[idx].second._get();
}

xrImTextureData dxUIShader::GetImGuiTextureId()
{
    const auto texture = GetBaseTexture();
    if (!texture)
        return {};

    return
    {
        texture->GetImTextureID(),
        {
            (float)texture->get_Width(),
            (float)texture->get_Height()
        }
    };
}

bool dxUIShader::GetBaseTextureResolution(Fvector2& res)
{
    const auto texture = GetBaseTexture();
    if (!texture)
    {
        res = {};
        return false;
    }

    res = { float(texture->get_Width()), float(texture->get_Height()) };
    return true;
}
} // namespace xray::render::RENDER_NAMESPACE
