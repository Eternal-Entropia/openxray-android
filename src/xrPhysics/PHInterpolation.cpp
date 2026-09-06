#include "StdAfx.h"
#include "PHInterpolation.h"
#include "PHDynamicData.h"
#include "Physics.h"
#include "MathUtils.h"
#include "ph_valid_ode.h"

extern CPHWorld* ph_world;

CPHInterpolation::CPHInterpolation()
{
    m_body = NULL;

    //	fTimeDelta=0.f;
}

void CPHInterpolation::SetBody(dBodyID body)
{
    if (!body)
        return;
    m_body = body;
    const dReal* pos = dBodyGetPosition(m_body);
    Fvector p = {0.f, 0.f, 0.f};
    if (pos && _valid(pos[0]) && _valid(pos[1]) && _valid(pos[2]))
        p = *((const Fvector*)pos);
    qPositions.fill_in(p);

    const dReal* dQ = dBodyGetQuaternion(m_body);
    Fquaternion fQ;
    if (dQ && _valid(dQ[0]) && _valid(dQ[1]) && _valid(dQ[2]) && _valid(dQ[3]))
        fQ.set(-dQ[0], dQ[1], dQ[2], dQ[3]);
    else
        fQ.identity();
    qRotations.fill_in(fQ);
}

void CPHInterpolation::UpdatePositions()
{
    if (!m_body)
        return;
    const dReal* pos = dBodyGetPosition(m_body);
    Fvector p = {0.f, 0.f, 0.f};
    if (pos && _valid(pos[0]) && _valid(pos[1]) && _valid(pos[2]))
        p = *((const Fvector*)pos);
    else
        p = qPositions[0];
    qPositions.push_back(p);
}

void CPHInterpolation::UpdateRotations()
{
    if (!m_body)
        return;
    const dReal* dQ = dBodyGetQuaternion(m_body);
    Fquaternion fQ;
    if (dQ && _valid(dQ[0]) && _valid(dQ[1]) && _valid(dQ[2]) && _valid(dQ[3]))
        fQ.set(-dQ[0], dQ[1], dQ[2], dQ[3]);
    else
        fQ = qRotations[0];
    qRotations.push_back(fQ);
}

void CPHInterpolation::InterpolatePosition(Fvector& pos)
{
    float t = ph_world->m_frame_time / fixed_step;
    clamp(t, 0.f, 1.f);
    pos.lerp(qPositions[0], qPositions[1], t);
}

void CPHInterpolation::InterpolateRotation(Fmatrix& rot)
{
    Fquaternion q;
    float t = ph_world->m_frame_time / fixed_step;
    clamp(t, 0.f, 1.f);
    q.slerp(qRotations[0], qRotations[1], t);
    rot.rotation(q);
}

void CPHInterpolation::ResetPositions()
{
    if (!m_body)
        return;
    const dReal* pos = dBodyGetPosition(m_body);
    if (!pos)
        return;
    Fvector p = *((const Fvector*)pos);
    if (!_valid(p.x) || !_valid(p.y) || !_valid(p.z))
        p.set(0.f, 0.f, 0.f);
    qPositions.fill_in(p);
}

void CPHInterpolation::ResetRotations()
{
    if (!m_body)
        return;
    const dReal* dQ = dBodyGetQuaternion(m_body);
    if (!dQ)
        return;
    Fquaternion fQ;
    if (!_valid(dQ[0]) || !_valid(dQ[1]) || !_valid(dQ[2]) || !_valid(dQ[3]))
        fQ.identity();
    else
        fQ.set(-dQ[0], dQ[1], dQ[2], dQ[3]);
    qRotations.fill_in(fQ);
}

void CPHInterpolation::GetRotation(Fquaternion& q, u16 num)
{
    if (!m_body)
        return;
    q.set(qRotations[num]);
}

void CPHInterpolation::GetPosition(Fvector& p, u16 num)
{
    if (!m_body)
        return;
    p.set(qPositions[num]);
}
void CPHInterpolation::SetPosition(const Fvector& p, u16 num)
{
    if (!m_body)
        return;
    qPositions[num].set(p);
}

void CPHInterpolation::SetRotation(const Fquaternion& q, u16 num)
{
    if (!m_body)
        return;
    qRotations[num] = q;
}
