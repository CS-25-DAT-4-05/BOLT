:: run_unit_tests.bat
:: -------------------
:: Compiles and runs all unit tests located in TestFiles/UnitTests/
:: Output .class files are placed in TestFiles/out/
:: Results are printed to the console for each test method (pass/fail)

@echo off
echo ================================
echo Cleaning previous output...
echo ================================
rmdir /s /q out 2>nul
mkdir out

echo ================================
echo Compiling unit test files...
echo ================================

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
UnitTests\TestASTNodes.java ^
UnitTests\TestExprChecker.java ^
UnitTests\TestTypeSystem.java ^
UnitTests\TestStmtChecker.java ^
UnitTests\TestBuiltinFunctions.java

if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed.
    pause
    exit /b
)

echo ================================
echo Running unit tests...
echo ================================
cd out

echo ----------------------------------
java TestFiles.UnitTests.TestASTNodes
echo ----------------------------------
java TestFiles.UnitTests.TestExprChecker
echo ----------------------------------
java TestFiles.UnitTests.TestTypeSystem
echo ----------------------------------
java TestFiles.UnitTests.TestStmtChecker
echo ----------------------------------
java TestFiles.UnitTests.TestBuiltinFunctions
echo ----------------------------------

pause
