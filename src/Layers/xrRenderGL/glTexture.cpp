// Texture.cpp: implementation of the CTexture class.
//
//////////////////////////////////////////////////////////////////////

#include "stdafx.h"

#include <gli/gli.hpp>

#define BCDEC_IMPLEMENTATION
#include "bcdec.h"

namespace xray::render::RENDER_NAMESPACE
{
namespace
{
// Fast DXT1/3/5 -> RGBA8 decode via bcdec (an order of magnitude faster than
// gli's per-texel convert). Returns an empty texture when the format/target
// combination is not handled; the caller then falls back to gli::convert.
enum class FastDxtKind
{
    None,
    BC1,
    BC2,
    BC3
};

FastDxtKind fast_dxt_kind(gli::format f)
{
    switch (f)
    {
    case gli::FORMAT_RGB_DXT1_UNORM_BLOCK8:
    case gli::FORMAT_RGB_DXT1_SRGB_BLOCK8:
    case gli::FORMAT_RGBA_DXT1_UNORM_BLOCK8:
    case gli::FORMAT_RGBA_DXT1_SRGB_BLOCK8:
        return FastDxtKind::BC1;
    case gli::FORMAT_RGBA_DXT3_UNORM_BLOCK16:
    case gli::FORMAT_RGBA_DXT3_SRGB_BLOCK16:
        return FastDxtKind::BC2;
    case gli::FORMAT_RGBA_DXT5_UNORM_BLOCK16:
    case gli::FORMAT_RGBA_DXT5_SRGB_BLOCK16:
        return FastDxtKind::BC3;
    default:
        return FastDxtKind::None;
    }
}

void fast_dxt_block(FastDxtKind kind, const u8* src, u8* dst, int dstPitch)
{
    switch (kind)
    {
    case FastDxtKind::BC1:
        bcdec_bc1(src, dst, dstPitch);
        break;
    case FastDxtKind::BC2:
        bcdec_bc2(src, dst, dstPitch);
        break;
    case FastDxtKind::BC3:
        bcdec_bc3(src, dst, dstPitch);
        break;
    default:
        break;
    }
}

// Decodes one mip level; handles partial (sub-4x4) tail mips via temp block.
void fast_dxt_level(FastDxtKind kind, const u8* src, u8* dst, u32 w, u32 h, size_t srcRowBlocks, size_t blockBytes, size_t dstPitch)
{
    const u32 bw = (w + 3) / 4;
    const u32 bh = (h + 3) / 4;
    u8 tmp[4 * 4 * 4];
    for (u32 by = 0; by < bh; ++by)
    {
        for (u32 bx = 0; bx < bw; ++bx)
        {
            const u8* s = src + (size_t(by) * srcRowBlocks + bx) * blockBytes;
            const u32 cw = (bx + 1) * 4 <= w ? 4 : w - bx * 4;
            const u32 ch = (by + 1) * 4 <= h ? 4 : h - by * 4;
            u8* d = dst + size_t(by) * 4 * dstPitch + size_t(bx) * 4 * 4;
            if (cw == 4 && ch == 4)
            {
                fast_dxt_block(kind, s, d, (int)dstPitch);
            }
            else
            {
                fast_dxt_block(kind, s, tmp, 16);
                for (u32 row = 0; row < ch; ++row)
                    CopyMemory(d + size_t(row) * dstPitch, tmp + size_t(row) * 16, size_t(cw) * 4);
            }
        }
    }
}

gli::texture fast_dxt_decode(const gli::texture& src)
{
    const FastDxtKind kind = fast_dxt_kind(src.format());
    if (kind == FastDxtKind::None)
        return gli::texture();
    if (src.target() != gli::TARGET_2D && src.target() != gli::TARGET_CUBE)
        return gli::texture();

    const size_t blockBytes = (kind == FastDxtKind::BC1) ? 8 : 16;
    const gli::texture2d::extent_type baseExtent((gli::texture::size_type)src.extent().x, (gli::texture::size_type)src.extent().y);
    gli::texture dst;
    if (src.target() == gli::TARGET_2D)
        dst = gli::texture2d(gli::FORMAT_RGBA8_UNORM_PACK8, baseExtent, src.levels(), src.swizzles());
    else
        dst = gli::texture_cube(gli::FORMAT_RGBA8_UNORM_PACK8, baseExtent, src.levels(), src.swizzles());

    for (gli::texture::size_type face = 0; face < dst.faces(); ++face)
    {
        for (gli::texture::size_type level = 0; level < dst.levels(); ++level)
        {
            const u32 w = (u32)dst.extent(level).x;
            const u32 h = (u32)dst.extent(level).y;
            const size_t dstPitch = size_t(w) * 4;
            const size_t srcRowBlocks = (size_t(w) + 3) / 4;
            fast_dxt_level(kind, (const u8*)src.data(0, face, level), (u8*)dst.data(0, face, level),
                w, h, srcRowBlocks, blockBytes, dstPitch);
        }
    }
    return dst;
}
} // namespace
void fix_texture_name(pstr fn)
{
    pstr _ext = strext(fn);
    if (_ext &&
        (0 == xr_stricmp(_ext, ".tga") ||
            0 == xr_stricmp(_ext, ".dds") ||
            0 == xr_stricmp(_ext, ".ktx") ||
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
                // Prefer ETC2-compressed .ktx override when present (direct GPU
                // upload, no CPU decode); fall back to the original .dds.
                exist = FS.exist(fn, folder, fname, ".ktx");
                if (exist)
                    break;
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

#if defined(XR_PLATFORM_ANDROID) || defined(XRAY_USE_GLES)
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
        // Fast path: bcdec decodes DXT1/3/5 blocks an order of magnitude
        // faster than gli's per-texel convert. Toggle via r__fast_dxt.
        gli::texture fast;
        if (ps_r__fast_dxt)
            fast = fast_dxt_decode(texture);
        if (!fast.empty())
        {
            texture = fast;
        }
        else
        {
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
    }
#endif

#if defined(XR_PLATFORM_ANDROID) || defined(XRAY_USE_GLES)
    gli::gl GL(gli::gl::PROFILE_ES30);
#else
    gli::gl GL(gli::gl::PROFILE_GL33);
#endif

    gli::gl::format const format = GL.translate(texture.format(), texture.swizzles());
    GLenum target = GL.translate(texture.target());

    glGenTextures(1, &pTexture);
    glBindTexture(target, pTexture);

    glTexParameteri(target, GL_TEXTURE_BASE_LEVEL, 0);
    glTexParameteri(target, GL_TEXTURE_MAX_LEVEL, static_cast<GLint>(texture.levels() - 1));
    if (texture.levels() <= 1)
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    else
        glTexParameteri(target, GL_TEXTURE_MIN_FILTER, GL_LINEAR_MIPMAP_LINEAR);
    glTexParameteri(target, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

#if defined(XR_PLATFORM_ANDROID) || defined(XRAY_USE_GLES)
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
