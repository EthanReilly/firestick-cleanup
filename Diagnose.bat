@echo off
title Fire Stick diagnose
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0Diagnose.ps1"
echo.
pause
