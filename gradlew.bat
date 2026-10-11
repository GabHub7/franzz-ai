@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-gradle.ps1" %*
exit /b %ERRORLEVEL%
