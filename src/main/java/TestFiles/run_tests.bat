@echo off
echo ========================================
echo Cleaning old build output...
echo ========================================

:: Delete and recreate the 'out' folder inside TestFiles
rmdir /s /q out 2>nul
mkdir out

echo ========================================
echo Compiling Java files...
echo ========================================

:: Compile all Java files under src/main/java and output to TestFiles/out
javac -d out ..\..\AbstractSyntax\Program\*.java ..\..\AbstractSyntax\Expressions\*.java ..\..\AbstractSyntax\Statements\*.java ..\..\AbstractSyntax\Types\*.java ..\..\AbstractSyntax\*.java ..\..\boltparser\*.java ..\..\Lib\*.java ..\..\SemanticAnalysis\*.java ..\TestTypeChecker.java

if %ERRORLEVEL% NEQ 0 (
    echo Compilation failed.
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo ========================================
echo Running TestTypeChecker...
echo ========================================
echo.

:: Run the compiled test class
java -cp out TestFiles.TestTypeChecker

echo.
echo ========================================
echo Done running tests.
echo Press any key to exit.
pause
