<div align="center">
  <p>
    <a href="https://github.com/OpenXRay">
      <img src="misc/media/OpenXRayCover.png" alt="Open for everyone" />
    </a>
  </p>
</div>

<h1 align="center">
  OpenXRay
</h1>

This rep is alpha for openxray port on android with support soc, cs, cop

Install: each game lives in its own subfolder:
- /storage/emulated/0/OpenXRay/soc <- gamedata.db of Shadow of Chernobyl
- /storage/emulated/0/OpenXRay/cs  <- gamedata.db of Clear Sky
- /storage/emulated/0/OpenXRay/cop <- gamedata.db of Call of Pripyat

Select the game mode in the launcher and press start. The res/ overlay
(gamedata, shaders, configs, fsgame.ltx) is packed inside the APK and copied
automatically into the mode subfolder — manual copying is not needed.

res/ is split per game and packed into the APK:
- res/soc/ <- Shadow of Chernobyl overlay (gamedata/, fsgame.ltx)
- res/cop and cs/ <- Clear Sky / Call of Pripyat overlay (gamedata/, fsgame.ltx)

On launch the matching folder is auto-extracted into the mode subfolder.
The launcher also syncs [compatibility] game_mode in openxray.ltx
(gamedata/config for soc, gamedata/configs for cs/cop) with the selected mode.


thanks: https://github.com/TmLev/xray-16/tree/refs/heads/tmlev/shadow-of-chernobyl

The author is not affiliated with GSC Game world. license use MIT
