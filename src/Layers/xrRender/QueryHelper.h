#pragma once

namespace xray::render::RENDER_NAMESPACE
{
//	Interface
#if defined(USE_DX11)
IC HRESULT CreateQuery(ID3DQuery** ppQuery);
IC HRESULT GetData(ID3DQuery* pQuery, void* pData, u32 DataSize);
IC HRESULT BeginQuery(ID3DQuery* pQuery);
IC HRESULT EndQuery(ID3DQuery* pQuery);
IC HRESULT ReleaseQuery(ID3DQuery *pQuery);
#elif defined(USE_OGL)
IC HRESULT CreateQuery(GLuint* pQuery, D3D_QUERY type);
IC HRESULT GetData(GLuint query, void* pData, u32 DataSize);
IC HRESULT BeginQuery(GLuint query);
IC HRESULT EndQuery(GLuint query);
IC HRESULT ReleaseQuery(GLuint pQuery);
#else
#   error No graphics API selected or enabled!
#endif

//	Implementation

#if defined(USE_DX11)

IC HRESULT CreateQuery(ID3DQuery** ppQuery, D3D_QUERY type)
{
    D3D_QUERY_DESC desc;
    desc.MiscFlags = 0;
    desc.Query = type;
    return HW.pDevice->CreateQuery(&desc, ppQuery);
}

IC HRESULT GetData(ID3DQuery* pQuery, void* pData, u32 DataSize)
{
    //	Use D3Dxx_ASYNC_GETDATA_DONOTFLUSH for prevent flushing
    return HW.get_context(CHW::IMM_CTX_ID)->GetData(pQuery, pData, DataSize, 0); // we can fetch data on imm only
}

IC HRESULT BeginQuery(ID3DQuery* pQuery)
{
    HW.get_context(CHW::IMM_CTX_ID)->Begin(pQuery);
    return S_OK;
}

IC HRESULT EndQuery(ID3DQuery* pQuery)
{
    HW.get_context(CHW::IMM_CTX_ID)->End(pQuery);
    return S_OK;
}

IC HRESULT ReleaseQuery(ID3DQuery* pQuery)
{
    _RELEASE(pQuery);
    return S_OK;
}

#elif defined(USE_OGL)

ICF GLenum GetOccQueryTarget()
{
#if defined(__ANDROID__)
    return GL_ANY_SAMPLES_PASSED;
#else
    if (GLAD_GL_ARB_occlusion_query)
        return GL_SAMPLES_PASSED;
    return GL_ANY_SAMPLES_PASSED;
#endif
}

IC HRESULT CreateQuery(GLuint* pQuery, D3D_QUERY type)
{
    R_ASSERT(type == D3D_QUERY_OCCLUSION);
    if (!pQuery)
        return E_FAIL;
    CHK_GL(glGenQueries(1, pQuery));
    return S_OK;
}

IC HRESULT GetData(GLuint query, void* pData, u32 DataSize)
{
    if (!query)
        return S_FALSE;

    GLuint available = 0;
    CHK_GL(glGetQueryObjectuiv(query, GL_QUERY_RESULT_AVAILABLE, &available));
    if (!available)
        return S_FALSE;

    GLuint res = 0;
    CHK_GL(glGetQueryObjectuiv(query, GL_QUERY_RESULT, &res));
    u64 val = res ? ((res == 1) ? 1000 : res) : 0;
    if (DataSize == sizeof(u64))
        *(u64*)pData = val;
    else
        *(u32*)pData = (u32)val;
    return S_OK;
}

IC HRESULT BeginQuery(GLuint query)
{
    if (!query)
        return S_FALSE;
    CHK_GL(glBeginQuery(GetOccQueryTarget(), query));
    return S_OK;
}

IC HRESULT EndQuery(GLuint query)
{
    if (!query)
        return S_FALSE;
    CHK_GL(glEndQuery(GetOccQueryTarget()));
    return S_OK;
}

IC HRESULT ReleaseQuery(GLuint query)
{
    if (query)
        CHK_GL(glDeleteQueries(1, &query));
    return S_OK;
}

#else
#   error No graphics API selected or enabled!
#endif
} // namespace xray::render::RENDER_NAMESPACE
