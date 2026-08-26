@echo off
setlocal
set API=libs\*
set OUT=build
if not exist "%OUT%" mkdir "%OUT%"
javac --release 21 -cp "%API%" -d "%OUT%" src\main\java\mcagent\*.java
if errorlevel 1 exit /b 1
copy src\main\resources\plugin.yml "%OUT%\plugin.yml" >nul
copy src\main\resources\config.yml "%OUT%\config.yml" >nul
jar --create --file McAgentBridge.jar -C "%OUT%" .
echo Built McAgentBridge.jar
