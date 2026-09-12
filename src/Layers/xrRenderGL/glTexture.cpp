// Texture.cpp: implementation of the CTexture class.
//
//////////////////////////////////////////////////////////////////////

#include "stdafx.h"

#include <gli/gli.hpp>

namespace xray::render::RENDER_NAMESPACE
{
void fix_texture_name(pstr fn)
{
    pstr _ext = strext(fn);
    if (_ext &&
        (0 == xr_stricmp(_ext, ".tga") ||
            0 == xr_stricmp(_ext, ".dds") ||
            0 == xr_stricmp(_ext, ".bmp") ||
            0 == xr_stricmp(_ext, ".ogm")))
        *_ext = 0;
}

int get_texture_load_lod(LPCSTR fn)
{
    CInifile::Sect& sect = pSettings->r_section("reduce_lod_texture_list");

    for (const auto& item : sect.Data)
    {
        if (strstr(fn, item.first.c_str()))
        {
            if (psTextureLOD < 1)
                return 0;
            if (psTextureLOD < 3)
                return 1;
            return 2;
        }
    }

    if (psTextureLOD < 2)
        return 0;
    if (psTextureLOD < 4)
        return 1;
    return 2;
}

u32 calc_texture_size(int lod, u32 mip_cnt, size_t orig_size)
{
    if (1 == mip_cnt)
        return orig_size;

    int _lod = lod;
    float res = float(orig_size);

    while (_lod > 0)
    {
        --_lod;
        res -= res / 1.333f;
    }
    return iFloor(res);
}

GLuint CRender::texture_load(LPCSTR fRName, u32& ret_msize, GLenum& ret_desc)
{
    ret_msize = 0;
    R_ASSERT1_CURE(fRName && fRName[0], { return 0; });

    GLuint pTexture = 0;
    string_path fn;
    {
        // make file name
        string_path fname;
        xr_strcpy(fname, fRName);
        fix_texture_name(fname);

        // Call to FS.exist WRITES to fn !

        if (!FS.exist(fn, "$game_textures$", fname, ".dds") && strstr(fname, "_bump"))
        {
            Msg("! Fallback to default bump map: %s", fname);
            if (strstr(fname, "_bump#"))
                R_ASSERT1_CURE(FS.exist(fn, "$game_textures$", "ed\\ed_dummy_bump#", ".dds"), return 0);
            else
                R_ASSERT1_CURE(FS.exist(fn, "$game_textures$", "ed\\ed_dummy_bump", ".dds"), return 0);
        }
        else
        {
            bool exist = false;

            for (cpcstr folder : { "$level$", "$game_saves$", "$game_textures$" })
            {
                exist = FS.exist(fn, folder, fname, ".dds");
                if (exist)
                    break;
            }

            if (!exist)
            {
                Msg("! Can't find texture '%s'", fname);
                if (!FS.exist(fn, "$game_textures$", "ed\\ed_not_existing_texture", ".dds"))
                    return 0;
            }
        }
    }

    // Load and get header
    IReader* S = FS.r_open(fn);
    R_ASSERT2_CURE(S, fn, { return 0; });
    size_t img_size = S->length();
#ifdef DEBUG
    Msg("* Loaded: %s[%d]b", fn, img_size);
#endif // DEBUG
    gli::texture texture = gli::load((char*)S->pointer(), img_size);
    R_ASSERT2(!texture.empty(), fn);

    u32 mip_cnt = u32(-1); // XXX: write to it when reading with GLI!

#if defined(XR_PLATFORM_ANDROID)
    static bool s_has_s3tc = false;
    static bool s_s3tc_checked = false;
    if (!s_s3tc_checked)
    {
        s_s3tc_checked = true;
        GLint num_extensions = 0;
        glGetIntegerv(GL_NUM_EXTENSIONS, &num_extensions);
        if (num_extensions > 0 && glGetStringi != nullptr)
        {
            for (GLint i = 0; i < num_extensions; ++i)
            {
                const char* ext = (const char*)glGetStringi(GL_EXTENSIONS, i);
                if (ext && (strstr(ext, "texture_compression_s3tc") || strstr(ext, "texture_compression_dxt")))
                {
                    s_has_s3tc = true;
                    break;
                }
            }
        }
        else
        {
            const char* extensions = (const char*)glGetString(GL_EXTENSIONS);
            if (extensions)
            {
                s_has_s3tc = (strstr(extensions, "GL_EXT_texture_compression_s3tc") != nullptr) ||
                             (strstr(extensions, "GL_NV_texture_compression_s3tc") != nullptr);
            }
        }
        Msg("* OpenGL: S3TC/DXT texture compression supported: %s", s_has_s3tc ? "yes" : "no");
    }

    // TEMP DIAG: "-forcecpudxt" forces the software decode path to tell HW
    // upload issues apart from data issues (remove after diagnosis).
    static const bool s_force_cpu = (strstr(Core.Params, "-forcecpudxt") != nullptr);
    if ((s_force_cpu || !s_has_s3tc) && gli::is_compressed(texture.format()) && gli::has_decoder(texture.format()))
    {
#ifdef DEBUG
        Msg("* OpenGL: Decompressing texture '%s' on CPU -> RGBA8", fn);
#endif
        gli::format target_format = gli::FORMAT_RGBA8_UNORM_PACK8;
        switch (texture.target())
        {
        case gli::TARGET_2D:
            texture = gli::convert(gli::texture2d(texture), target_format);
            break;
        case gli::TARGET_CUBE:
            texture = gli::convert(gli::texture_cube(texture), target_format);
            break;
        case gli::TARGET_2D_ARRAY:
            texture = gli::convert(gli::texture2d_array(texture), target_format);
            break;
        case gli::TARGET_3D:
            texture = gli::convert(gli::texture3d(texture), target_format);
            break;
        case gli::TARGET_CUBE_ARRAY:
            texture = gli::convert(gli::texture_cube_array(texture), target_format);
            break;
        default:
            break;
        }
    }
#endif

#if defined(XR_PLATFORM_ANDROID)
    gli::gl GL(gli::gl::PROFILE_ES30);
#else
    gli::gl GL(gli::gl::PROFILE_GL33);
#endif

    // TEMP DIAG: dump decoded menu background to TGA to verify CPU decode.
    if (strstr(fn, "ui_mainmenu"))
    {
        glm::tvec3<GLsizei> const dext(texture.extent(0));
        const u8* src = (const u8*)texture.data(0, 0, 0);
        string_path dump_path;
        FS.update_path(dump_path, "$logs$", "texdump_ui_mainmenu.tga");
        IWriter* wr = FS.w_open(dump_path);
        if (wr && src && dext.x > 0 && dext.y > 0)
        {
            wr->w_u8(0); wr->w_u8(0); wr->w_u8(2);
            wr->w_u16(0); wr->w_u16(0); wr->w_u8(0);
            wr->w_u16(0); wr->w_u16(0);
            wr->w_u16((u16)dext.x); wr->w_u16((u16)dext.y);
            wr->w_u8(32); wr->w_u8(0x28);
            for (int y = 0; y < dext.y; ++y)
                for (int x = 0; x < dext.x; ++x)
                {
                    const u8* px = src + (size_t)(y * dext.x + x) * 4;
                    wr->w_u8(px[2]); wr->w_u8(px[1]); wr->w_u8(px[0]); wr->w_u8(px[3]);
                }
            Msg(">>> [glTexture] dumped '%s' to %s", fn, dump_path);
        }
        if (wr) FS.w_close(wr);
    }

    gli::gl::format const format = GL.translate(texture.format(), texture.swizzles());
    GLenum target = GL.translate(texture.target());

    // TEMP DIAG: log detected format mapping to chase DXT artifacts.
    {
        glm::tvec3<GLsizei> const ext0(texture.extent(0));
        Msg(">>> [glTexture] '%s': gli_format=%d compressed=%d levels=%u extent=%dx%d internal=0x%X",
            fn, (int)texture.format(), (int)gli::is_compressed(texture.format()),
            (unsigned)texture.levels(), (int)ext0.x, (int)ext0.y, (unsigned)format.Internal);
    }

    glGenTextures(1, &pTexture);
    glBindTexture(target, pTexture);

    glTexParameteri(target, GL_TEXTURE_BASE_LEVEL, 0);
    glTexParameteri(target, GL_TEXTURE_MAX_LEVEL, static_cast<GLint>(texture.levels() - 1));
    if (texture.levels() <= 1)
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    else
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

#if defined(XR_PLATFORM_ANDROID)
    if (gli::gl::EXTERNAL_RED != format.External)
    {
        glTexParameteri(target, GL_TEXTURE_SWIZZLE_R, format.Swizzles[gli::SWIZZLE_RED]);
        glTexParameteri(target, GL_TEXTURE_SWIZZLE_G, format.Swizzles[gli::SWIZZLE_GREEN]);
        glTexParameteri(target, GL_TEXTURE_SWIZZLE_B, format.Swizzles[gli::SWIZZLE_BLUE]);
        glTexParameteri(target, GL_TEXTURE_SWIZZLE_A, format.Swizzles[gli::SWIZZLE_ALPHA]);
    }
#else
    if (gli::gl::EXTERNAL_RED != format.External) // skip for proper greyscale-alpha font textures
        glTexParameteriv(target, GL_TEXTURE_SWIZZLE_RGBA, &format.Swizzles[gli::SWIZZLE_RED]);
#endif

    glm::tvec3<GLsizei> const tex_extent(texture.extent());

    GLenum err;
    bool use_storage = true;
    switch (texture.target())
    {
    case gli::TARGET_2D:
    case gli::TARGET_CUBE:
        glTexStorage2D(target, static_cast<GLint>(texture.levels()), format.Internal,
                       tex_extent.x, tex_extent.y);
        err = glGetError();
        if (err != GL_NO_ERROR)
        {
            use_storage = false;
            while (glGetError() != GL_NO_ERROR); // clear errors
        }
        break;
    case gli::TARGET_3D:
    case gli::TARGET_CUBE_ARRAY:
        glTexStorage3D(target, static_cast<GLint>(texture.levels()), format.Internal,
                       tex_extent.x, tex_extent.y, tex_extent.z);
        err = glGetError();
        if (err != GL_NO_ERROR)
        {
            use_storage = false;
            while (glGetError() != GL_NO_ERROR); // clear errors
        }
        break;
    default:
        NODEFAULT;
        break;
    }

    for (size_t layer = 0; layer < texture.layers(); ++layer)
    {
        for (size_t face = 0; face < texture.faces(); ++face)
        {
            for (size_t level = 0; level < texture.levels(); ++level)
            {
                glm::tvec3<GLsizei> const tex_level_extent(texture.extent(level));
                GLenum sub_target = gli::is_target_cube(texture.target())
                         ? static_cast<GLenum>(GL_TEXTURE_CUBE_MAP_POSITIVE_X + face)
                         : target;

                switch (texture.target())
                {
                case gli::TARGET_2D:
                case gli::TARGET_CUBE:
                {
                    if (gli::is_compressed(texture.format()))
                    {
                        if (use_storage)
                        {
                            glCompressedTexSubImage2D(sub_target, static_cast<GLint>(level),
                                        0, 0, tex_level_extent.x, tex_level_extent.y,
                                        format.Internal, static_cast<GLsizei>(texture.size(level)),
                                        texture.data(layer, face, level));
                        }
                        else
                        {
                            glCompressedTexImage2D(sub_target, static_cast<GLint>(level),
                                        format.Internal, tex_level_extent.x, tex_level_extent.y,
                                        0, static_cast<GLsizei>(texture.size(level)),
                                        texture.data(layer, face, level));
                        }
                        err = glGetError();
                        if (err != GL_NO_ERROR)
                        {
                            Msg("! OpenGL: 0x%x: Invalid 2D compressed texture: '%s'", err, fn);
                        }
                    }
                    else
                    {
                        if (use_storage)
                        {
                            glTexSubImage2D(sub_target, static_cast<GLint>(level),
                                        0, 0, tex_level_extent.x, tex_level_extent.y,
                                        format.External, format.Type,
                                        texture.data(layer, face, level));
                        }
                        else
                        {
                            glTexImage2D(sub_target, static_cast<GLint>(level),
                                        format.Internal, tex_level_extent.x, tex_level_extent.y,
                                        0, format.External, format.Type,
                                        texture.data(layer, face, level));
                        }
                        err = glGetError();
                        if (err != GL_NO_ERROR)
                        {
                            Msg("! OpenGL: 0x%x: Invalid 2D texture: '%s'", err, fn);
                        }

                    }
                    break;
                }
                case gli::TARGET_3D:
                case gli::TARGET_CUBE_ARRAY:
                {
                    if (gli::is_compressed(texture.format()))
                    {
                        if (use_storage)
                        {
                            glCompressedTexSubImage3D(target, static_cast<GLint>(level),
                                        0, 0, 0, tex_level_extent.x, tex_level_extent.y, tex_level_extent.z,
                                        format.Internal, static_cast<GLsizei>(texture.size(level)),
                                        texture.data(layer, face, level));
                        }
                        else
                        {
                            glCompressedTexImage3D(target, static_cast<GLint>(level),
                                        format.Internal, tex_level_extent.x, tex_level_extent.y, tex_level_extent.z,
                                        0, static_cast<GLsizei>(texture.size(level)),
                                        texture.data(layer, face, level));
                        }
                        err = glGetError();
                        if (err != GL_NO_ERROR)
                        {
                            Msg("! OpenGL: 0x%x: Invalid compressed 3D texture: '%s'", err, fn);
                        }
                    }
                    else
                    {
                        if (use_storage)
                        {
                            glTexSubImage3D(target, static_cast<GLint>(level),
                                        0, 0, 0, tex_level_extent.x, tex_level_extent.y, tex_level_extent.z,
                                        format.External, format.Type,
                                        texture.data(layer, face, level));
                        }
                        else
                        {
                            glTexImage3D(target, static_cast<GLint>(level),
                                        format.Internal, tex_level_extent.x, tex_level_extent.y, tex_level_extent.z,
                                        0, format.External, format.Type,
                                        texture.data(layer, face, level));
                        }
                        err = glGetError();
                        if (err != GL_NO_ERROR)
                        {
                            Msg("! OpenGL: 0x%x: Invalid 3D texture: '%s'", err, fn);
                        }
                    }
                    break;
                }
                default:
                    NODEFAULT;
                    break;
                }
            }
        }
    }

    FS.r_close(S);

    xr_strlwr(fn);
    ret_desc = target;
    int img_loaded_lod = is_target_cube(texture.target()) ? 0 : get_texture_load_lod(fn);
    ret_msize = calc_texture_size(img_loaded_lod, mip_cnt, img_size);
    return pTexture;
}
} // namespace xray::render::RENDER_NAMESPACE
