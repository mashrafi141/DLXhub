@ECHO OFF
setlocal
set DIR=%~dp0
set APP_HOME=%DIR%
if "%JAVA_HOME%"=="" goto noJavaHome
set JAVA_EXE=%JAVA_HOME%\bin\java.exe
goto run
:noJavaHome
set JAVA_EXE=java.exe
:run
"%JAVA_EXE%" -version >NUL 2>&1
if errorlevel 1 goto noJava
if not exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" goto noWrapper
"%JAVA_EXE%" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
:noJavaHome
ECHO ERROR: JAVA_HOME is not set. Please point JAVA_HOME to a JDK 17 installation.
exit /b 1
:noJava
ECHO ERROR: Java was not found. Please install/use JDK 17.
exit /b 1
:noWrapper
ECHO Gradle wrapper JAR is missing. Downloading the official Gradle 9.3.1 wrapper...
if not exist "%APP_HOME%gradle\wrapper" mkdir "%APP_HOME%gradle\wrapper"
powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$u='https://raw.githubusercontent.com/gradle/gradle/v9.3.1/gradle/wrapper/gradle-wrapper.jar'; $o='%APP_HOME%gradle\wrapper\gradle-wrapper.jar'; Invoke-WebRequest -UseBasicParsing -Uri $u -OutFile $o"
if errorlevel 1 (
  ECHO ERROR: Could not download the Gradle 9.3.1 wrapper JAR.
  ECHO Check your internet connection and try again.
  exit /b 1
)
if not exist "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" (
  ECHO ERROR: Gradle wrapper JAR download did not produce the expected file.
  exit /b 1
)
ECHO Gradle wrapper JAR downloaded successfully.
"%JAVA_EXE%" -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
