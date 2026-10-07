@echo off
setlocal EnableExtensions
set "SCRIPT_DIR=%~dp0"
if defined JAVA_HOME (
    set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_EXE=java.exe"
)
"%JAVA_EXE%" -version >NUL 2>&1
if errorlevel 1 (
    echo ERROR: Java was not found. Install JDK 25 and set JAVA_HOME to it.
    exit /b 1
)
"%JAVA_EXE%" -jar "%SCRIPT_DIR%gradle\wrapper\gradle-wrapper.jar" %*
exit /b %ERRORLEVEL%
