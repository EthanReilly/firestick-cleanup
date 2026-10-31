@echo off
title Undo Fire Stick clean-up
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0FireStickCleanup.ps1" -Restore
echo.
pause
