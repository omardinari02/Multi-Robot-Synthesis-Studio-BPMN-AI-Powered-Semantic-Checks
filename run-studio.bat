@echo off
title MRS Synthesis Studio - UNIVAQ DISIM
echo =========================================================================
echo  Avvio di Multi-Robot Synthesis Studio...
echo  UNIVAQ DISIM - Tesi e Tirocinio Magistrale ASE
echo =========================================================================
echo.
cd /d "%~dp0"
echo Compilazione e avvio del Web Server su http://localhost:8080 ...
echo.
mvn exec:java -Dexec.args="--web"
pause
