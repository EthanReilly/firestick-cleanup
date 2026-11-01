@echo off
title Fire TV settings check
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0SettingsCheck.ps1"
echo.
pause
