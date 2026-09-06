#include "stdafx.h"

#include "XR_IOConsole.h"
#include "xr_ioc_cmd.h"

#include "xrScriptEngine/script_space.hpp"

void CConsole::script_register(lua_State* luaState)
{
    using namespace luabind;

    module(luaState)
    [
        class_<CConsole>("CConsole")
            .def("execute", &CConsole::Execute)
            .def("execute_script", &CConsole::ExecuteScript)
            .def("show", &CConsole::Show)
            .def("hide", &CConsole::Hide)

            .def("get_string", &CConsole::GetString)
            .def("get_integer", +[](const CConsole* self, pcstr cmd)
            {
                int min = 0, max = 0;
                const int val = self->GetInteger(cmd, min, max);
                return val;
            })
            .def("get_bool", +[](const CConsole* self, pcstr cmd)
            {
                return self->GetBool(cmd);
            })
            .def("get_float", +[](const CConsole* self, pcstr cmd)
            {
                float min = 0.0f, max = 0.0f;
                const float val = self->GetFloat(cmd, min, max);
                return val;
            })
            .def("get_token", &CConsole::GetToken)
            .def("execute_deferred", +[](CConsole*, pcstr string_to_execute)
            {
                Engine.Event.Defer("KERNEL:console", size_t(xr_strdup(string_to_execute)));
            }),

        def("get_console", +[]() -> CConsole*
        {
            return Console;
        }),

        def("execute_console", +[](pcstr cmd)
        {
            if (Console && cmd)
                Console->Execute(cmd);
        }),

        def("renderer_allow_override", +[]()
        {
            return renderer_allow_override;
        })
    ];

    // Ensure get_console in Lua always returns a working console proxy even if luabind pointer conversion is nil
    luaL_dostring(luaState,
        "if rawget(_G, 'get_console') == nil or get_console() == nil then\n"
        "    local fallback_console = {\n"
        "        execute = function(self, cmd)\n"
        "            if execute_console then\n"
        "                execute_console(cmd)\n"
        "            end\n"
        "        end\n"
        "    }\n"
        "    get_console = function() return fallback_console end\n"
        "end\n"
    );
}
