#pragma once

#include <ode/common.h>
#include <ode/mass.h>
#include <ode/objects.h>
#include <ode/rotation.h>

IC BOOL dV_valid(const dReal* v) { return v && _valid(v[0]) && _valid(v[1]) && _valid(v[2]); }
IC BOOL dM_valid(const dReal* m)
{
    return m && _valid(m[0]) && _valid(m[1]) && _valid(m[2]) && _valid(m[4]) && _valid(m[5]) && _valid(m[6]) &&
        _valid(m[8]) && _valid(m[9]) && _valid(m[10]);
}

IC BOOL dV4_valid(const dReal* v4) { return v4 && _valid(v4[0]) && _valid(v4[1]) && _valid(v4[2]) && _valid(v4[3]); }
IC BOOL dQ_valid(const dReal* q) { return dV4_valid(q); }
IC BOOL dMass_valide(const dMass* m) { return m && _valid(m->mass) && dV_valid(m->c) && dM_valid(m->I); }

IC BOOL dBodyStateValide(const dBodyID body)
{
    if (!body)
        return TRUE;

    const dReal* R = dBodyGetRotation(body);
    const dReal* P = dBodyGetPosition(body);
    const dReal* LV = dBodyGetLinearVel(body);
    const dReal* AV = dBodyGetAngularVel(body);
    const dReal* T = dBodyGetTorque(body);
    const dReal* F = dBodyGetForce(body);

    bool ok = dM_valid(R) && dV_valid(P) && dV_valid(LV) && dV_valid(AV) && dV_valid(T) && dV_valid(F);
    if (!ok)
    {
        if (!dM_valid(R))
        {
            dMatrix3 mIdentity;
            dRSetIdentity(mIdentity);
            dBodySetRotation(body, mIdentity);
        }
        if (!dV_valid(P))
        {
            dBodySetPosition(body, 0.f, 0.f, 0.f);
        }
        if (!dV_valid(LV))
        {
            dBodySetLinearVel(body, 0.f, 0.f, 0.f);
        }
        if (!dV_valid(AV))
        {
            dBodySetAngularVel(body, 0.f, 0.f, 0.f);
        }
        if (!dV_valid(T))
        {
            dBodySetTorque(body, 0.f, 0.f, 0.f);
        }
        if (!dV_valid(F))
        {
            dBodySetForce(body, 0.f, 0.f, 0.f);
        }
    }
    return TRUE;
}
