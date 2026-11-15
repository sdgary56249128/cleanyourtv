@echo off
rem Use the repository's existing Gradle wrapper for this independent project.
call "%~dp0..\..\launcher\gradlew.bat" -p "%~dp0." %*
exit /b %ERRORLEVEL%
