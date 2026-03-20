@echo off
set CLASSPATH=%~dp0gradle\wrapper\gradle-wrapper.jar
"%JAVA_HOME%\bin\java.exe" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
