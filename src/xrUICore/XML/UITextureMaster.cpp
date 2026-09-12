// file:		UITextureMaster.h
// description:	holds info about shared textures. able to initialize external controls
//				through IUITextureControl interface
// created:		11.05.2005
// author:		Serge Vynnychenko
// mail:		narrator@gsc-game.kiev.ua
//
// copyright 2005 GSC Game World

#include "pch.hpp"
#include "UITextureMaster.h"
#include "xrUICore/XML/xrUIXmlParser.h"
#include "Static/UIStaticItem.h"
#include "uiabstract.h"
#include "xrUIXmlParser.h"
#include "Include/xrRender/UIShader.h"
#include "xrCore/Threading/Lock.hpp"
#include "xrCore/Threading/ScopeLock.hpp"
#include <iostream>

xr_map<shared_str, TEX_INFO> CUITextureMaster::m_textures;
xr_map<sh_pair, ui_shader> CUITextureMaster::m_shaders;

void CUITextureMaster::FreeTexInfo()
{
    m_textures.clear();
    FreeCachedShaders();
}

void CUITextureMaster::FreeCachedShaders() { m_shaders.clear(); }
void CUITextureMaster::ParseShTexInfo(pcstr path, pcstr xml_file)
{
    CUIXml xml;
    xml.Load(CONFIG_PATH, path, xml_file);

    ParseShTexInfo(xml, true);
}

void CUITextureMaster::ParseShTexInfo(pcstr xml_file)
{
    CUIXml xml;
    if (!xml.Load(CONFIG_PATH, UI_PATH, UI_PATH_DEFAULT, xml_file, false))
        return;
    const shared_str file = xml.Read("file_name", 0, "");

    const int num = xml.GetNodesNum("", 0, "texture");
    for (int i = 0; i < num; i++)
    {
        TEX_INFO info;

        info.file = file;

        info.rect.x1 = xml.ReadAttribFlt("texture", i, "x");
        info.rect.x2 = xml.ReadAttribFlt("texture", i, "width") + info.rect.x1;
        info.rect.y1 = xml.ReadAttribFlt("texture", i, "y");
        info.rect.y2 = xml.ReadAttribFlt("texture", i, "height") + info.rect.y1;
        shared_str id = xml.ReadAttrib("texture", i, "id");

        if (m_textures.find(id) == m_textures.end())
            m_textures.emplace(id, info);
        else
            m_textures[id] = info;
    }
}

void CUITextureMaster::ParseShTexInfo(CUIXml& xml, bool override)
{
    const int files_num = xml.GetNodesNum("", 0, "file");

    for (int fi = 0; fi < files_num; ++fi)
    {
        XML_NODE root_node = xml.GetLocalRoot();
        shared_str file = xml.ReadAttrib("file", fi, "name");

        XML_NODE node = xml.NavigateToNode("file", fi);

        const int num = xml.GetNodesNum(node, "texture");
        for (int i = 0; i < num; i++)
        {
            TEX_INFO info;

            info.file = file;

            info.rect.x1 = xml.ReadAttribFlt(node, "texture", i, "x");
            info.rect.x2 = xml.ReadAttribFlt(node, "texture", i, "width") + info.rect.x1;
            info.rect.y1 = xml.ReadAttribFlt(node, "texture", i, "y");
            info.rect.y2 = xml.ReadAttribFlt(node, "texture", i, "height") + info.rect.y1;
            shared_str id = xml.ReadAttrib(node, "texture", i, "id");

            if (m_textures.find(id) == m_textures.end())
                m_textures.emplace(id, info);
            else if (override)
                m_textures[id] = info;
        }

        xml.SetLocalRoot(root_node);
    }
}

bool CUITextureMaster::IsSh(const shared_str& texture_name)
{
    return strchr(texture_name.c_str(), _DELIMITER) == nullptr;
}

static pcstr GetTextureFallback(pcstr name)
{
    if (!name || !name[0])
        return nullptr;

    if (strnicmp(name, "ui_ingame2_", 11) == 0 || strnicmp(name, "ui_inGame2_", 11) == 0)
    {
        pcstr sub = name + 11;

        // Big buttons (btn_load, btn_delete, btn_cancel, btn_accept, etc.)
        if (stricmp(sub, "Mp_bigbuttone_e") == 0 || stricmp(sub, "mp_bigbuttone_e") == 0) return "ui_button_main02_e";
        if (stricmp(sub, "Mp_bigbuttone_d") == 0 || stricmp(sub, "mp_bigbuttone_d") == 0) return "ui_button_main02_d";
        if (stricmp(sub, "Mp_bigbuttone_h") == 0 || stricmp(sub, "mp_bigbuttone_h") == 0) return "ui_button_main02_h";
        if (stricmp(sub, "Mp_bigbuttone_t") == 0 || stricmp(sub, "mp_bigbuttone_t") == 0) return "ui_button_main02_t";
        if (stricmp(sub, "Mp_bigbuttone") == 0 || stricmp(sub, "mp_bigbuttone") == 0)   return "ui_button_main02";

        // Tab buttons (video, sound, gameplay, controls, etc.)
        if (stricmp(sub, "opt_button_1_e") == 0) return "ui_button_tablist_e";
        if (stricmp(sub, "opt_button_1_d") == 0) return "ui_button_tablist_d";
        if (stricmp(sub, "opt_button_1_h") == 0) return "ui_button_tablist_h";
        if (stricmp(sub, "opt_button_1_t") == 0) return "ui_button_tablist_t";
        if (stricmp(sub, "opt_button_1") == 0)   return "ui_button_tablist";

        if (stricmp(sub, "opt_button_2_e") == 0) return "ui_button_tablist_e";
        if (stricmp(sub, "opt_button_2_d") == 0) return "ui_button_tablist_d";
        if (stricmp(sub, "opt_button_2_h") == 0) return "ui_button_tablist_h";
        if (stricmp(sub, "opt_button_2_t") == 0) return "ui_button_tablist_t";
        if (stricmp(sub, "opt_button_2") == 0)   return "ui_button_tablist";

        // Ordinary buttons (btn_advanced, etc.)
        if (stricmp(sub, "button_e") == 0) return "ui_button_ordinary_e";
        if (stricmp(sub, "button_d") == 0) return "ui_button_ordinary_d";
        if (stricmp(sub, "button_h") == 0) return "ui_button_ordinary_h";
        if (stricmp(sub, "button_t") == 0) return "ui_button_ordinary_t";
        if (stricmp(sub, "button") == 0)   return "ui_button_ordinary";

        // Server list button
        if (stricmp(sub, "servers_list_button_e") == 0) return "ui_button_ordinary_e";
        if (stricmp(sub, "servers_list_button_d") == 0) return "ui_button_ordinary_d";
        if (stricmp(sub, "servers_list_button_h") == 0) return "ui_button_ordinary_h";
        if (stricmp(sub, "servers_list_button_t") == 0) return "ui_button_ordinary_t";
        if (stricmp(sub, "servers_list_button") == 0)   return "ui_button_ordinary";

        // Checkbox
        if (stricmp(sub, "checkbox_e") == 0) return "ui_checker_e";
        if (stricmp(sub, "checkbox_d") == 0) return "ui_checker_d";
        if (stricmp(sub, "checkbox_h") == 0) return "ui_checker_h";
        if (stricmp(sub, "checkbox_t") == 0) return "ui_checker_t";
        if (stricmp(sub, "checkbox") == 0)   return "ui_checker";

        // Slider
        if (stricmp(sub, "opt_slider_bar") == 0)    return "ui_slider_e_back";
        if (stricmp(sub, "opt_slider_box_e") == 0)  return "ui_slider_button_e";
        if (stricmp(sub, "opt_slider_box_d") == 0)  return "ui_slider_button_d";
        if (stricmp(sub, "opt_slider_box_h") == 0)  return "ui_slider_button_h";
        if (stricmp(sub, "opt_slider_box_t") == 0)  return "ui_slider_button_t";
        if (stricmp(sub, "opt_slider_box") == 0)    return "ui_slider_button";

        // Spin box
        if (stricmp(sub, "spin_box") == 0)                  return "ui_spiner_back";
        if (stricmp(sub, "spin_box_button_top_e") == 0)    return "ui_spiner_button_t_e";
        if (stricmp(sub, "spin_box_button_top_d") == 0)    return "ui_spiner_button_t_d";
        if (stricmp(sub, "spin_box_button_top_h") == 0)    return "ui_spiner_button_t_h";
        if (stricmp(sub, "spin_box_button_top_t") == 0)    return "ui_spiner_button_t_t";
        if (stricmp(sub, "spin_box_button_top") == 0)      return "ui_spiner_button_t";
        if (stricmp(sub, "spin_box_button_bottom_e") == 0) return "ui_spiner_button_b_e";
        if (stricmp(sub, "spin_box_button_bottom_d") == 0) return "ui_spiner_button_b_d";
        if (stricmp(sub, "spin_box_button_bottom_h") == 0) return "ui_spiner_button_b_h";
        if (stricmp(sub, "spin_box_button_bottom_t") == 0) return "ui_spiner_button_b_t";
        if (stricmp(sub, "spin_box_button_bottom") == 0)   return "ui_spiner_button_b";

        // Combobox / Editbox
        if (stricmp(sub, "combobox_linetext") == 0) return "ui_linetext_e_back";
        if (stricmp(sub, "combobox_line_b") == 0)   return "ui_listline_b";
        if (stricmp(sub, "combobox_line_e") == 0)   return "ui_listline_e";
        if (stricmp(sub, "combobox_line") == 0)     return "ui_listline_back";
        if (stricmp(sub, "combobox") == 0)          return "ui_tablist_textbox";
        if (stricmp(sub, "edit_box_1") == 0)        return "ui_linetext_e_back";

        // Windows / Backgrounds
        if (stricmp(sub, "opt_background") == 0)     return "ui_menu_options_dlg";
        if (stricmp(sub, "opt_main_window") == 0)    return "ui_menu_options_dlg";
        if (stricmp(sub, "opt_buttons_frame") == 0)  return "ui_brokenline_back";
        if (stricmp(sub, "background") == 0)         return "ui_menu_options_dlg";
        if (stricmp(sub, "main_window_small") == 0)  return "ui_menu_options_dlg";
        if (stricmp(sub, "servers_list_frame") == 0) return "ui_tablist_textbox";
        if (stricmp(sub, "picture_window") == 0)     return "ui_tablist_textbox";
        if (stricmp(sub, "empty_frameline_15") == 0) return "ui_brokenline_back";
        if (stricmp(sub, "back_01") == 0)            return "ui_menu_options_dlg";
        if (stricmp(sub, "back_02") == 0)            return "ui_menu_options_dlg";
        if (stricmp(sub, "back_03") == 0)            return "ui_menu_options_dlg";
        if (stricmp(sub, "load_info") == 0)          return "ui_tablist_textbox";
        if (stricmp(sub, "left_widepanel") == 0)     return "ui_menu_options_dlg";
        if (stricmp(sub, "right_widepanel") == 0)    return "ui_menu_options_dlg";
    }

    return nullptr;
}

bool CUITextureMaster::InitTexture(
    const shared_str& texture_name, const shared_str& shader_name, ui_shader& out_shader, Frect& out_rect)
{
    xr_map<shared_str, TEX_INFO>::iterator it = m_textures.find(texture_name);
    if (it == m_textures.end())
    {
        if (pcstr fb = GetTextureFallback(texture_name.c_str()))
            it = m_textures.find(fb);
    }

    if (it != m_textures.end())
    {
        sh_pair p = {it->second.file, shader_name};
        xr_map<sh_pair, ui_shader>::iterator sh_it = m_shaders.find(p);
        if (sh_it == m_shaders.end())
            m_shaders[p]->create(shader_name.c_str(), it->second.file.c_str());

        out_shader = m_shaders[p];
        out_rect = (*it).second.rect;
        return true;
    }

    out_shader->create(shader_name.c_str(), texture_name.c_str());
    return false;
}

bool CUITextureMaster::InitTexture(const shared_str& texture_name, CUIStaticItem* tc, const shared_str& shader_name)
{
    xr_map<shared_str, TEX_INFO>::iterator it = m_textures.find(texture_name);
    if (it == m_textures.end())
    {
        if (pcstr fb = GetTextureFallback(texture_name.c_str()))
            it = m_textures.find(fb);
    }

    if (it != m_textures.end())
    {
        sh_pair p = {it->second.file, shader_name};
        xr_map<sh_pair, ui_shader>::iterator sh_it = m_shaders.find(p);
        if (sh_it == m_shaders.end())
            m_shaders[p]->create(shader_name.c_str(), it->second.file.c_str());

        tc->SetShader(m_shaders[p]);
        tc->SetTextureRect((*it).second.rect);
        tc->SetSize(Fvector2().set(it->second.rect.width(), it->second.rect.height()));
        return true;
    }

    tc->CreateShader(texture_name.c_str(), shader_name.c_str());
    return false;
}

Frect CUITextureMaster::GetTextureRect(const shared_str& texture_name)
{
    TEX_INFO info = FindItem(texture_name);
    return info.rect;
}

pcstr CUITextureMaster::GetTextureFileName(pcstr texture_name)
{
    TEX_INFO info = FindItem(texture_name);
    return info.file.c_str();
}

float CUITextureMaster::GetTextureHeight(const shared_str& texture_name)
{
    TEX_INFO info = FindItem(texture_name);
    return info.rect.height();
}

float CUITextureMaster::GetTextureWidth(const shared_str& texture_name)
{
    TEX_INFO info = FindItem(texture_name);
    return info.rect.width();
}

bool CUITextureMaster::GetTextureHeight(const shared_str& texture_name, float& outValue)
{
    TEX_INFO info;
    if (FindItem(texture_name, info))
    {
        outValue = info.rect.height();
        return true;
    }
    return false;
}

bool CUITextureMaster::GetTextureWidth(const shared_str& texture_name, float& outValue)
{
    TEX_INFO info;
    if (FindItem(texture_name, info))
    {
        outValue = info.rect.width();
        return true;
    }
    return false;

}

TEX_INFO CUITextureMaster::FindItem(const shared_str& texture_name, pcstr default_texture /*= nullptr*/)
{
    TEX_INFO info;

    VERIFY4(FindItem(texture_name, default_texture, info),
        "Can't find texture", texture_name.c_str(), default_texture);

    return info;
}

bool CUITextureMaster::FindItem(const shared_str& texture_name, TEX_INFO& outValue)
{
    return FindItem(texture_name, nullptr, outValue);
}

bool CUITextureMaster::FindItem(const shared_str& texture_name, pcstr default_texture, TEX_INFO& outValue)
{
    auto it = m_textures.find(texture_name);

    if (it != m_textures.end())
    {
        outValue = it->second;
        return true;
    }

    if (pcstr fb = GetTextureFallback(texture_name.c_str()))
    {
        it = m_textures.find(fb);
        if (it != m_textures.end())
        {
            outValue = it->second;
            return true;
        }
    }

    it = m_textures.find(default_texture);
    if (it != m_textures.end())
    {
        outValue = it->second;
        return true;
    }

    return false;
}

bool CUITextureMaster::ItemExist(const shared_str& texture_name)
{
    auto it = m_textures.find(texture_name);
    if (it != m_textures.end())
        return true;

    if (pcstr fb = GetTextureFallback(texture_name.c_str()))
    {
        it = m_textures.find(fb);
        if (it != m_textures.end())
            return true;
    }

    return false;
}

void CUITextureMaster::GetTextureShader(const shared_str& texture_name, ui_shader& sh)
{
    auto it = m_textures.find(texture_name);
    if (it == m_textures.end())
    {
        if (pcstr fb = GetTextureFallback(texture_name.c_str()))
            it = m_textures.find(fb);
    }
    R_ASSERT3(it != m_textures.end(), "can't find texture", texture_name.c_str());

    sh->create("hud\\default", it->second.file.c_str());
}
