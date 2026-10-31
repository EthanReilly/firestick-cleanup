@echo off
title Fire Stick clean-up
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0FireStickCleanup.ps1"
echo.
pause
