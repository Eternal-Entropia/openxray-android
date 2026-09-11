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
            Msg(">>> [execute_console] cmd='%s'", cmd ? cmd : "<null>");
            FlushLog();
            if (Console && cmd)
                Console->Execute(cmd);
        }),

        def("execute_console_deferred", +[](pcstr cmd)
        {
            Msg(">>> [execute_console_deferred] cmd='%s'", cmd ? cmd : "<null>");
            FlushLog();
            // Deferred via KERNEL:console event: runs on the next Engine.OnFrame,
            // outside of any Lua dialog callback stack. Required for level-switching
            // commands ("start ...", "disconnect", "main_menu off", "load ...")
            // issued from main-menu UI buttons (synchronous Execute from inside
            // a dialog callback hangs the game on Android).
            if (cmd)
                Engine.Event.Defer("KERNEL:console", size_t(xr_strdup(cmd)));
        }),

        def("renderer_allow_override", +[]()
        {
            return renderer_allow_override;
        })
    ];

    // Ensure get_console in Lua always returns a working console proxy calling execute_console directly
    luaL_dostring(luaState,
        "local real_get_console = get_console\n"
        "local console_proxy = {\n"
        "    execute = function(self, cmd)\n"
        "        if cmd then\n"
        "            if execute_console then\n"
        "                execute_console(cmd)\n"
        "            else\n"
        "                local c = real_get_console and real_get_console()\n"
        "                if c and c.execute then c:execute(cmd) end\n"
        "            end\n"
        "        end\n"
        "    end,\n"
        "    execute_deferred = function(self, cmd)\n"
        "        if cmd then\n"
        "            if execute_console_deferred then\n"
        "                execute_console_deferred(cmd)\n"
        "            elseif execute_console then\n"
        "                execute_console(cmd)\n"
        "            end\n"
        "        end\n"
        "    end,\n"
        "    execute_script = function(self, cmd)\n"
        "        local c = real_get_console and real_get_console()\n"
        "        if c and c.execute_script then c:execute_script(cmd) end\n"
        "    end,\n"
        "    show = function(self)\n"
        "        local c = real_get_console and real_get_console()\n"
        "        if c and c.show then c:show() end\n"
        "    end,\n"
        "    hide = function(self)\n"
        "        local c = real_get_console and real_get_console()\n"
        "        if c and c.hide then c:hide() end\n"
        "    end,\n"
        "    get_string = function(self, cmd)\n"
        "        local c = real_get_console and real_get_console()\n"
        "        return (c and c.get_string) and c:get_string(cmd) or ''\n"
        "    end,\n"
        "    get_integer = function(self, cmd)\n"
        "        local c = real_get_console and real_get_console()\n"
        "        return (c and c.get_integer) and c:get_integer(cmd) or 0\n"
        "    end,\n"
        "    get_bool = function(self, cmd)\n"
        "        local c = real_get_console and real_get_console()\n"
        "        return (c and c.get_bool) and c:get_bool(cmd) or false\n"
        "    end,\n"
        "    get_float = function(self, cmd)\n"
        "        local c = real_get_console and real_get_console()\n"
        "        return (c and c.get_float) and c:get_float(cmd) or 0.0\n"
        "    end,\n"
        "    get_token = function(self, cmd)\n"
        "        local c = real_get_console and real_get_console()\n"
        "        return (c and c.get_token) and c:get_token(cmd) or ''\n"
        "    end\n"
        "}\n"
        "setmetatable(console_proxy, {\n"
        "    __index = function(t, k)\n"
        "        local c = real_get_console and real_get_console()\n"
        "        if c then return c[k] end\n"
        "        return nil\n"
        "    end\n"
        "})\n"
        "get_console = function() return console_proxy end\n"
    );
}
