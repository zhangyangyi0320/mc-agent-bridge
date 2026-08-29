$ErrorActionPreference = "Stop"
$env:PATH = "C:\Program Files\Microsoft\jdk-25.0.2.10-hotspot\bin;$env:PATH"
$root = "D:\code\plugins\mc-agent-bridge"
Set-Location $root

# 1) compile-only stub for bungee BaseComponent (NOT packaged into the jar)
if (Test-Path stubclasses) { Remove-Item -Recurse -Force stubclasses }
New-Item -ItemType Directory -Path stubclasses | Out-Null
javac --release 8 -cp "libs/*" -d stubclasses stubs/net/md_5/bungee/api/chat/BaseComponent.java

# 2) compile plugin sources targeting Java 8 class version (runs on Java 8+ servers,
#    i.e. MC 1.12-1.16 on Java 8 and all newer servers). The paper-api on the
#    classpath is newer but javac only restricts the *output* version, not libs.
if (Test-Path build) { Remove-Item -Recurse -Force build }
New-Item -ItemType Directory -Path build | Out-Null
javac --release 8 -cp "libs/*;stubclasses" -d build src/main/java/mcagent/*.java
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

# 3) copy resources (plugin.yml, config.yml, ...) into the jar root
Copy-Item src/main/resources/* build/ -Recurse -Force

# 4) package (stubclasses excluded)
jar --create --file McAgentBridge.jar -C build .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

Write-Host "BUILD_OK  jar=$(Get-Item McAgentBridge.jar | Select-Object -ExpandProperty Length) bytes"
jar tf McAgentBridge.jar | Select-String "plugin.yml|config.yml|McAgentBridge.class|McAgentCommand.class"
