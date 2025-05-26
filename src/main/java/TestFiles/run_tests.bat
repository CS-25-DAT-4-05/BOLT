@echo off
echo ========================================
echo Cleaning old build output...
echo ========================================
:: Delete and recreate the out folder at BOLT/src/out/
rmdir /s /q ..\..\..\out 2>nul
mkdir ..\..\..\out

echo ========================================
echo Compiling TypeChecker test...
echo ========================================

:: Compile from Java root using relative source path
javac -d ..\..\..\out -sourcepath .. ..\TestFiles\TestTypeChecker.java

if errorlevel 1 (
    echo Compilation failed.
    pause
    exit /b 1
)

echo.
echo ========================================
echo Running TestTypeChecker...
echo ========================================
echo.

:: Run the compiled class from the correct path
java -cp ..\..\..\out TestFiles.TestTypeChecker

echo.
echo ========================================
echo Done running tests.
echo Press any key to exit.
pause
