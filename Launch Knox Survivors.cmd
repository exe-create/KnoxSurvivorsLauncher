@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\launch-knox-survivors.ps1"
if errorlevel 1 pause
