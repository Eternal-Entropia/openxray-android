#pragma once

inline const char* get_embedded_ui_main_menu()
{
    return R"lua(-- File:        UI_MAIN_MENU.SCRIPT
-- Description: Main Menu for OpenXRay (Android port)
-- Created:     28.10.2004
-- Author:      Serhiy Vynnychenko, OpenXRay Team
-- Modernized:  2026

local MBOX_MODE_NONE           = 0
local MBOX_MODE_LOAD_LAST_SAVE = 1
local MBOX_MODE_QUIT_WIN       = 2
local MBOX_MODE_QUIT_GAME      = 3

local function log1(msg)
	if _G.log1 then
		_G.log1(tostring(msg))
		if _G.flush1 then _G.flush1() end
	elseif _G.log then
		_G.log(tostring(msg))
		if _G.flush1 then _G.flush1() end
	elseif _G.Msg then
		_G.Msg(tostring(msg))
		if _G.FlushLog then _G.FlushLog() end
	end
end

-- Deferred console execution: queue command via KERNEL:console event so it runs
-- on the next frame, outside of the dialog Lua callback stack. Executing
-- "start ..."/"disconnect"/"main_menu off" synchronously from inside a dialog
-- button callback hangs the game on Android (re-entrant menu teardown).
local function exec_deferred(cmd)
	if not cmd then
		return
	end
	local console = get_console and get_console() or nil
	if console and console.execute_deferred then
		console:execute_deferred(cmd)
	elseif _G.execute_console_deferred then
		_G.execute_console_deferred(cmd)
	elseif console then
		console:execute(cmd)
	elseif execute_console then
		execute_console(cmd)
	end
end

local function get_file_timestamp(fs_item)
	if not fs_item or not fs_item.ModifDigitOnly then
		return ""
	end
	local date_str = fs_item:ModifDigitOnly()
	if not date_str then
		return ""
	end
	local d, m, y, h, min = string.match(date_str, "(%d+)%/(%d+)%/(%d+)%s+(%d+)%:(%d+)")
	if d and m and y and h and min then
		return string.format("%04d%02d%02d%02d%02d", tonumber(y), tonumber(m), tonumber(d), tonumber(h), tonumber(min))
	end
	return date_str
end

class "main_menu" (CUIScriptWnd)

function main_menu:__init() super()
	self.mbox_mode = MBOX_MODE_NONE
	self:InitControls()
	self:InitCallBacks()
	if xr_s and xr_s.on_main_menu_on then
		xr_s.on_main_menu_on()
	end
end

function main_menu:__finalize()
end

function main_menu:InitControls()
	self:SetWndRect(Frect():set(0, 0, 1024, 768))

	local xml = CScriptXmlInit()
	xml:ParseFile("ui_mm_main.xml")

	local is_soc = IsShadowOfChernobylMode and IsShadowOfChernobylMode()
	if is_soc then
		xml:InitStatic("back_movie", self)
	end
	xml:InitStatic("background", self)
	if is_soc then
		xml:InitStatic("fire_movie", self)
	end
	self.shniaga = xml:InitMMShniaga("shniaga_wnd", self)

	self.message_box = CUIMessageBoxEx()
	self:Register(self.message_box, "msg_box")

	local _ver = xml:InitStatic("static_version", self)
	local mm = _G.main_menu and _G.main_menu.get_main_menu and _G.main_menu.get_main_menu()
	if _ver and mm and mm.GetGSVer then
		if _ver.TextControl then
			_ver:TextControl():SetText("ver. " .. mm:GetGSVer())
		elseif _ver.SetText then
			_ver:SetText("ver. " .. mm:GetGSVer())
		end
	end

	if mm then
		self.l_mgr = mm.GetLoginMngr and mm:GetLoginMngr()
		self.acc_mgr = mm.GetAccountMngr and mm:GetAccountMngr()
		self.profile_store = mm.GetProfileStore and mm:GetProfileStore()
		self.gs_profile = (self.l_mgr and self.l_mgr.get_current_profile) and self.l_mgr:get_current_profile() or nil
	end

	if self.gs_profile and not (level and level.present and level.present()) then
		if self.shniaga then
			self.shniaga:ShowPage(CUIMMShniaga.epi_new_network_game) -- fake
			self.shniaga:SetPage(CUIMMShniaga.epi_main, "ui_mm_main.xml", "menu_main_logout")
			self.shniaga:ShowPage(CUIMMShniaga.epi_main)
		end
	end
end

function main_menu:Show(f)
	if self.shniaga then
		self.shniaga:SetVisibleMagnifier(f)
	end
end

function main_menu:InitCallBacks()
	-- new game difficulties
	self:AddCallback("btn_novice",      ui_events.BUTTON_CLICKED, self.OnButton_new_novice_game,    self)
	self:AddCallback("btn_stalker",     ui_events.BUTTON_CLICKED, self.OnButton_new_stalker_game,   self)
	self:AddCallback("btn_veteran",     ui_events.BUTTON_CLICKED, self.OnButton_new_veteran_game,   self)
	self:AddCallback("btn_master",      ui_events.BUTTON_CLICKED, self.OnButton_new_master_game,    self)
	self:AddCallback("btn_spawn",       ui_events.BUTTON_CLICKED, self.OnButton_load_spawn,         self)

	-- options, load, save
	self:AddCallback("btn_options",     ui_events.BUTTON_CLICKED, self.OnButton_options_clicked,    self)
	self:AddCallback("btn_load",        ui_events.BUTTON_CLICKED, self.OnButton_load_clicked,       self)
	self:AddCallback("btn_save",        ui_events.BUTTON_CLICKED, self.OnButton_save_clicked,       self)

	-- multiplayer
	self:AddCallback("btn_net_game",    ui_events.BUTTON_CLICKED, self.OnButton_network_game_clicked, self)
	self:AddCallback("btn_internet",    ui_events.BUTTON_CLICKED, self.OnButton_internet_clicked,   self)
	self:AddCallback("btn_localnet",    ui_events.BUTTON_CLICKED, self.OnButton_localnet_clicked,   self)
	self:AddCallback("btn_multiplayer", ui_events.BUTTON_CLICKED, self.OnButton_multiplayer_clicked, self)
	self:AddCallback("btn_logout",      ui_events.BUTTON_CLICKED, self.OnButton_logout_clicked,     self)

	-- navigation and actions
	self:AddCallback("btn_quit",        ui_events.BUTTON_CLICKED, self.OnButton_quit_clicked,       self)
	self:AddCallback("btn_quit_to_mm",  ui_events.BUTTON_CLICKED, self.OnButton_disconnect_clicked, self)
	self:AddCallback("btn_ret",         ui_events.BUTTON_CLICKED, self.OnButton_return_game,        self)
	self:AddCallback("btn_lastsave",    ui_events.BUTTON_CLICKED, self.OnButton_last_save,          self)
	self:AddCallback("btn_credits",     ui_events.BUTTON_CLICKED, self.OnButton_credits_clicked,    self)

	-- message box
	self:AddCallback("msg_box",         ui_events.MESSAGE_BOX_OK_CLICKED,        self.OnMsgOk,           self)
	self:AddCallback("msg_box",         ui_events.MESSAGE_BOX_CANCEL_CLICKED,    self.OnMsgCancel,       self)
	self:AddCallback("msg_box",         ui_events.MESSAGE_BOX_YES_CLICKED,       self.OnMsgYes,          self)
	self:AddCallback("msg_box",         ui_events.MESSAGE_BOX_NO_CLICKED,        self.OnMsgNo,           self)
	self:AddCallback("msg_box",         ui_events.MESSAGE_BOX_QUIT_GAME_CLICKED, self.OnMessageQuitGame, self)
	self:AddCallback("msg_box",         ui_events.MESSAGE_BOX_QUIT_WIN_CLICKED,  self.OnMessageQuitWin,  self)

	self:Register(self, "self")
	self:AddCallback("self",            ui_events.MAIN_MENU_RELOADED,            self.OnMenuReloaded,    self)
end

function main_menu:OnMsgOk()
	self.mbox_mode = MBOX_MODE_NONE
end

function main_menu:OnMsgCancel()
	self.mbox_mode = MBOX_MODE_NONE
end

function main_menu:OnMsgYes()
	if self.mbox_mode == MBOX_MODE_LOAD_LAST_SAVE then
		self:LoadLastSave()
	elseif self.mbox_mode == MBOX_MODE_QUIT_WIN then
		self:OnMessageQuitWin()
	elseif self.mbox_mode == MBOX_MODE_QUIT_GAME then
		self:OnMessageQuitGame()
	end

	self.mbox_mode = MBOX_MODE_NONE
end

function main_menu:OnMsgNo()
	self.mbox_mode = MBOX_MODE_NONE
end

function main_menu:GetLatestSaveName()
	local f = getFS()
	if not f then
		return nil
	end

	local best_name = nil
	local best_timestamp = ""

	local function process_saves(mask, ext_len)
		local flist = f:file_list_open_ex("$game_saves$", bit_or(FS.FS_ListFiles, FS.FS_RootOnly), mask)
		if flist and flist:Size() > 0 then
			flist:Sort(FS.FS_sort_by_modif_down)
			local file = flist:GetAt(0)
			if file then
				local ts = get_file_timestamp(file)
				if ts >= best_timestamp then
					local name = file:NameFull()
					if name and string.len(name) > ext_len then
						best_name = string.sub(name, 1, string.len(name) - ext_len)
						best_timestamp = ts
					end
				end
			end
		end
	end

	process_saves("*.sav", 4)
	process_saves("*.scop", 5)

	return best_name
end

function main_menu:LoadLastSave()
	log1(">>> [UI_MAIN_MENU] LoadLastSave called")
	local save_name = self:GetLatestSaveName()
	log1(">>> [UI_MAIN_MENU] LoadLastSave: save_name=" .. tostring(save_name))

	self:HideDialog()
	self:Show(false)

	if device and device() then
		device():pause(false)
	end

	exec_deferred("main_menu off")

	if xr_s and xr_s.on_main_menu_off then
		xr_s.on_main_menu_off()
	end

	local exec_fn = function(cmd)
		exec_deferred(cmd)
	end

	if save_name and save_name ~= "" then
		if alife and alife() == nil then
			exec_fn("disconnect")
			local start_cmd = "start server(" .. save_name .. "/single/alife/load) client(localhost)"
			log1(">>> [UI_MAIN_MENU] LoadLastSave: executing " .. start_cmd)
			exec_fn(start_cmd)
		else
			local load_cmd = "load " .. save_name
			log1(">>> [UI_MAIN_MENU] LoadLastSave: executing " .. load_cmd)
			exec_fn(load_cmd)
		end
	else
		log1(">>> [UI_MAIN_MENU] LoadLastSave: executing load_last_save")
		exec_fn("load_last_save")
	end
	log1(">>> [UI_MAIN_MENU] LoadLastSave finished successfully")
end

function main_menu:OnButton_last_save()
	log1(">>> [UI_MAIN_MENU] OnButton_last_save clicked!")
	self:LoadLastSave()
end

function main_menu:OnButton_credits_clicked()
	if game and game.start_tutorial then
		game.start_tutorial("credits_seq")
	end
end

function main_menu:OnButton_quit_clicked()
	log1(">>> [UI_MAIN_MENU] OnButton_quit_clicked!")
	self.mbox_mode = MBOX_MODE_QUIT_WIN
	self.message_box:InitMessageBox("message_box_quit_windows")
	self.message_box:ShowDialog(true)
end

function main_menu:OnButton_disconnect_clicked()
	log1(">>> [UI_MAIN_MENU] OnButton_disconnect_clicked!")
	self.mbox_mode = MBOX_MODE_QUIT_GAME
	self.message_box:InitMessageBox("message_box_quit_game")

	if level and level.game_id and level.game_id() ~= 1 then
		self.message_box:SetText("ui_mm_disconnect_message") -- MultiPlayer
	else
		self.message_box:SetText("ui_mm_quit_game_message")   -- SinglePlayer
	end
	self.message_box:ShowDialog(true)
end

function main_menu:OnMessageQuitGame()
	log1(">>> [UI_MAIN_MENU] OnMessageQuitGame executing disconnect")
	exec_deferred("disconnect")
end

function main_menu:OnMessageQuitWin()
	log1(">>> [UI_MAIN_MENU] OnMessageQuitWin executing quit")
	if device and device() then
		device():pause(false)
	end
	exec_deferred("quit")
end

function main_menu:OnButton_return_game()
	log1(">>> [UI_MAIN_MENU] OnButton_return_game")
	exec_deferred("main_menu off")
	if xr_s and xr_s.on_main_menu_off then
		xr_s.on_main_menu_off()
	end
end

function main_menu:OnButton_new_novice_game()
	log1(">>> [UI_MAIN_MENU] OnButton_new_novice_game clicked!")
	self:StartGame("gd_novice")
end

function main_menu:OnButton_new_stalker_game()
	log1(">>> [UI_MAIN_MENU] OnButton_new_stalker_game clicked!")
	self:StartGame("gd_stalker")
end

function main_menu:OnButton_new_veteran_game()
	log1(">>> [UI_MAIN_MENU] OnButton_new_veteran_game clicked!")
	self:StartGame("gd_veteran")
end

function main_menu:OnButton_new_master_game()
	log1(">>> [UI_MAIN_MENU] OnButton_new_master_game clicked!")
	self:StartGame("gd_master")
end

function main_menu:StartGame(difficulty)
	log1(">>> [UI_MAIN_MENU] StartGame called with difficulty=" .. tostring(difficulty))
	self:HideDialog()
	self:Show(false)

	local console = get_console()
	log1(">>> [UI_MAIN_MENU] StartGame: console=" .. tostring(console))
	if difficulty and console then
		log1(">>> [UI_MAIN_MENU] StartGame: setting difficulty " .. tostring(difficulty))
		console:execute("g_game_difficulty " .. difficulty)
	elseif difficulty and execute_console then
		execute_console("g_game_difficulty " .. difficulty)
	end

	if alife and alife() ~= nil then
		log1(">>> [UI_MAIN_MENU] StartGame: disconnecting active alife")
		exec_deferred("disconnect")
	end

	if device and device() then
		device():pause(false)
	end

	if xr_s and xr_s.on_main_menu_off then
		xr_s.on_main_menu_off()
	end

	local start_cmd = "start server(all/single/alife/new) client(localhost)"
	log1(">>> [UI_MAIN_MENU] StartGame: executing " .. start_cmd)
	exec_deferred(start_cmd)
	log1(">>> [UI_MAIN_MENU] StartGame: executing main_menu off")
	exec_deferred("main_menu off")
	log1(">>> [UI_MAIN_MENU] StartGame finished successfully")
end

function main_menu:OnButton_load_spawn()
	if self.spawn_dlg == nil and ui_spawn_dialog and ui_spawn_dialog.spawn_dialog then
		self.spawn_dlg = ui_spawn_dialog.spawn_dialog()
		self.spawn_dlg.owner = self
	end

	if self.spawn_dlg then
		self.spawn_dlg:ShowDialog(true)
		self:HideDialog()
		self:Show(false)
	end
end

function main_menu:OnButton_save_clicked()
	if self.save_dlg == nil and ui_save_dialog and ui_save_dialog.save_dialog then
		self.save_dlg = ui_save_dialog.save_dialog()
		self.save_dlg.owner = self
	end

	if self.save_dlg then
		if self.save_dlg.FillList then
			self.save_dlg:FillList()
		end
		self.save_dlg:ShowDialog(true)
		self:HideDialog()
		self:Show(false)
	end
end

function main_menu:OnButton_options_clicked()
	if self.opt_dlg == nil and ui_mm_opt_main and ui_mm_opt_main.options_dialog then
		self.opt_dlg = ui_mm_opt_main.options_dialog()
		self.opt_dlg.owner = self
	end

	if self.opt_dlg then
		if self.opt_dlg.SetCurrentValues ~= nil then
			self.opt_dlg:SetCurrentValues()
		elseif self.opt_dlg.UpdateControls ~= nil then
			self.opt_dlg:UpdateControls()
		end
		self.opt_dlg:ShowDialog(true)
		self:HideDialog()
		self:Show(false)
	end
end

function main_menu:OnButton_load_clicked()
	if self.load_dlg == nil and ui_load_dialog and ui_load_dialog.load_dialog then
		self.load_dlg = ui_load_dialog.load_dialog()
		self.load_dlg.owner = self
	end

	if self.load_dlg then
		if self.load_dlg.FillList then
			self.load_dlg:FillList()
		end
		self.load_dlg:ShowDialog(true)
		self:HideDialog()
		self:Show(false)
	end
end

function main_menu:OnButton_network_game_clicked()
	if self.shniaga then
		self.shniaga:ShowPage(CUIMMShniaga.epi_new_network_game)
	end
end

function main_menu:OnButton_multiplayer_clicked()
	if not self.mp_dlg and ui_mp_main and ui_mp_main.mp_main then
		local online = self.gs_profile and self.gs_profile:online() or false
		self.mp_dlg = ui_mp_main.mp_main(online)
		self.mp_dlg.owner = self
		if self.mp_dlg.OnRadio_NetChanged then
			self.mp_dlg:OnRadio_NetChanged()
		end
		if self.mp_dlg.online and self.mp_dlg.dlg_profile then
			if self.mp_dlg.dlg_profile.InitBestScores then
				self.mp_dlg.dlg_profile:InitBestScores()
			end
			if self.mp_dlg.dlg_profile.FillRewardsTable then
				self.mp_dlg.dlg_profile:FillRewardsTable()
			end
		end
	end

	if self.mp_dlg then
		if self.mp_dlg.UpdateControls then
			self.mp_dlg:UpdateControls()
		end
		self.mp_dlg:ShowDialog(true)
		self:HideDialog()
		self:Show(false)
	end

	local console = get_console()
	if console then
		console:execute("check_for_updates 0")
	end
end

function main_menu:OnButton_logout_clicked()
	if self.shniaga then
		self.shniaga:ShowPage(CUIMMShniaga.epi_new_network_game) -- fake
	end
	if self.l_mgr and self.l_mgr.logout then
		self.l_mgr:logout()
	end
	self.gs_profile = nil
	self.mp_dlg = nil
	if self.shniaga then
		self.shniaga:SetPage(CUIMMShniaga.epi_main, "ui_mm_main.xml", "menu_main")
		self.shniaga:ShowPage(CUIMMShniaga.epi_main)
	end
end

function main_menu:OnButton_internet_clicked()
	if not self.gs_dlg and ui_mm_mp_gamespy and ui_mm_mp_gamespy.gamespy_page then
		self.gs_dlg = ui_mm_mp_gamespy.gamespy_page()
		self.gs_dlg.owner = self
	end

	if self.gs_dlg then
		if self.gs_dlg.ShowLoginPage then
			self.gs_dlg:ShowLoginPage()
		end
		self.gs_dlg:ShowDialog(true)
		self:HideDialog()
		self:Show(false)
	end

	local console = get_console()
	if console then
		console:execute("check_for_updates 0")
	end
end

function main_menu:OnButton_localnet_clicked()
	if not self.ln_dlg and ui_mm_mp_localnet and ui_mm_mp_localnet.localnet_page then
		self.ln_dlg = ui_mm_mp_localnet.localnet_page()
		self.ln_dlg.owner = self
		if self.l_mgr then
			if self.ln_dlg.lp_nickname and self.l_mgr.get_nick_from_registry then
				self.ln_dlg.lp_nickname:SetText(self.l_mgr:get_nick_from_registry())
			end
			if self.ln_dlg.lp_check_remember_me and self.l_mgr.get_remember_me_from_registry then
				self.ln_dlg.lp_check_remember_me:SetCheck(self.l_mgr:get_remember_me_from_registry())
			end
		end
	end

	if self.ln_dlg then
		self.ln_dlg:ShowDialog(true)
		self:HideDialog()
		self:Show(false)
	end

	local console = get_console()
	if console then
		console:execute("check_for_updates 0")
	end
end

function main_menu:Dispatch(cmd, param)
	if cmd == 2 then
		self:OnButton_multiplayer_clicked()
	end
	return true
end

function main_menu:OnKeyboard(dik, keyboard_action)
	if CUIScriptWnd.OnKeyboard(self, dik, keyboard_action) then
		return true
	end

	if keyboard_action ~= ui_events.WINDOW_KEY_PRESSED then
		return false
	end

	local bind = dik_to_bind(dik)
	local is_back = (dik == DIK_keys.DIK_ESCAPE) or
	                (bind == key_bindings.kQUIT) or
	                (key_bindings.kUI_BACK and bind == key_bindings.kUI_BACK)

	if is_back then
		if level and level.present and level.present() then
			if db and db.actor and db.actor:alive() then
				self:OnButton_return_game()
				return true
			elseif not IsGameTypeSingle or not IsGameTypeSingle() then
				self:OnButton_return_game()
				return true
			else
				-- Actor dead in singleplayer: prompt to quit
				self:OnButton_quit_clicked()
				return true
			end
		else
			-- Not in game (root main menu): on Android, Back button prompts to exit game
			self:OnButton_quit_clicked()
			return true
		end
	end

	if dik == DIK_keys.DIK_Q then
		self:OnButton_quit_clicked()
		return true
	end

	if dik == DIK_keys.DIK_S and ui_spawn_dialog and ui_spawn_dialog.spawn_dialog then
		self:OnButton_load_spawn()
		return true
	end

	return false
end

function main_menu:OnMenuReloaded()
	self:OnButton_options_clicked()
	if self.opt_dlg and self.opt_dlg.OnMenuReloaded then
		self.opt_dlg:OnMenuReloaded()
	end
end
)lua";
}
