@echo off
title Beyond and Tabs setup
rem Double-click to install or update Beyond and Tabs (lovish, Oct 23: "point and click ... it does everything itself").
rem It runs the same setup as the copy-paste line: installer/install.ps1 from the game's GitHub.
echo Setting up Beyond and Tabs - this takes a minute or two...
echo.
powershell -NoProfile -ExecutionPolicy Bypass -Command "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; irm https://raw.githubusercontent.com/IamTheLovish/Beyond-and-Tabs/main/installer/install.ps1 | iex"
if errorlevel 1 (
  echo.
  echo Something went wrong. Take a screenshot of this window and send it to lovish.
)
echo.
pause
