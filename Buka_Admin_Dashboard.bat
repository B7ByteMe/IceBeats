@echo off
title IceBeats Admin Dashboard
echo ====================================================
echo   Membuka IceBeats Leaderboard Admin Dashboard...
echo ====================================================

start http://localhost:3000/admin/ 2>nul || start "" "%~dp0web-icebats\admin\index.html"
exit
