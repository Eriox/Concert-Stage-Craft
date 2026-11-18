@echo off
rem CSC 1.3.0: exports the LiveVision / Dynamic Truss Blender models to the game format (the .blend files are only read)
cd /d "%~dp0..\.."
set BL=C:\Program Files\Blender Foundation\Blender 5.3\blender.exe
"%BL%" -b --factory-startup --python "tools\v13\export_vision_blender.py" -- "source-assets\livevision_1_3_0\Blender" "tools\v13\export_out" > "tools\v13\export_log.txt" 2>&1
echo exit %errorlevel% >> "tools\v13\export_log.txt"
