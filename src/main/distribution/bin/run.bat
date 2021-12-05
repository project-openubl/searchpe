@REM
@REM Copyright 2019 Project OpenUBL, Inc. and/or its affiliates
@REM and other contributors as indicated by the @author tags.
@REM
@REM Licensed under the Apache License, Version 2.0 (the "License");
@REM you may not use this file except in compliance with the License.
@REM You may obtain a copy of the License at
@REM
@REM http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing, software
@REM distributed under the License is distributed on an "AS IS" BASIS,
@REM WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM See the License for the specific language governing permissions and
@REM limitations under the License.
@REM

@echo off
rem -------------------------------------------------------------------------
rem Searchpe Bootstrap Script for Windows
rem -------------------------------------------------------------------------

@if not "%ECHO%" == ""  echo %ECHO%
setlocal

if "%OS%" == "Windows_NT" (
  set "DIRNAME=%~dp0%"
) else (
  set DIRNAME=.\
)

:MAIN
rem $Id$
)

pushd "%DIRNAME%.."
set "RESOLVED_SEARCHPE_HOME=%CD%"
popd

if "x%SEARCHPE_HOME%" == "x" (
  set "SEARCHPE_HOME=%RESOLVED_SEARCHPE_HOME%"
)

pushd "%SEARCHPE_HOME%"
set "SANITIZED_SEARCHPE_HOME=%CD%"
popd

if /i "%RESOLVED_SEARCHPE_HOME%" NEQ "%SANITIZED_SEARCHPE_HOME%" (
   echo.
   echo   WARNING:  SEARCHPE_HOME may be pointing to a different installation - unpredictable results may occur.
   echo.
   echo       SEARCHPE_HOME: "%SEARCHPE_HOME%"
   echo.
)

rem Find /bin/searchpe.exe, or we can't continue
if exist "%SEARCHPE_HOME%\bin\searchpe.exe" (
    set "RUNJAR=%SEARCHPE_HOME%\bin\searchpe.exe"
) else (
  echo Could not locate "%SEARCHPE_HOME%\bin\searchpe.exe".
  echo Please check that you are in the bin directory when running this script.
  goto END
)

echo ===============================================================================
echo.
echo   Searchpe Bootstrap Environment
echo.
echo   SEARCHPE_HOME: "%SEARCHPE_HOME%"
echo.
echo ===============================================================================
echo.

cd "%SEARCHPE_HOME%"

:RESTART
  "%SEARCHPE_HOME%\bin\run.bat"

if %errorlevel% equ 10 (
	goto RESTART
)

:END
if "x%NOPAUSE%" == "x" pause

:END_NO_PAUSE
