:: run_integration_tests.bat
:: -------------------
:: Compiles and runs integration tests for .bolt files using BoltIntegrationTestRunner
:: All test results are reported as [PASS], [FAIL], or [ERROR]
:: Should output .class files are placed in TestFiles/out/

@echo off
echo ================================
echo Cleaning old build output...
echo ================================
rmdir /s /q out 2>nul
mkdir out

echo ================================
echo Compiling Java files...
echo ================================

:: From inside TestFiles, compile all Java files one pass
javac -d out -cp .. ^
..\AbstractSyntax\Definitions\*.java ^
..\AbstractSyntax\Expressions\*.java ^
..\AbstractSyntax\Program\*.java ^
..\AbstractSyntax\SizeParams\*.java ^
..\AbstractSyntax\Statements\*.java ^
..\AbstractSyntax\Types\*.java ^
..\boltparser\*.java ^
..\Lib\*.java ^
..\SemanticAnalysis\*.java ^
BoltIntegrationTestRunner.java

if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed.
    pause
    exit /b %ERRORLEVEL%
)

echo ================================
echo Running BoltIntegrationTestRunner...
echo ================================
cd out
java TestFiles.BoltIntegrationTestRunner

pause
