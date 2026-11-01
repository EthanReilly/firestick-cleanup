@echo off
title Test Home and volume buttons
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0TestButtons.ps1"
echo.
pause
