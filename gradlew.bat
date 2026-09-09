@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle is not installed. CI provisions Gradle 9.1.0 via gradle/actions/setup-gradle. 1>&2
  exit /b 127
)
gradle %*
