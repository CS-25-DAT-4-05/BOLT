@echo off
echo ================================
echo Running all tests...
echo ================================

call run_tests.bat
echo.
call .\run_unit_tests.bat

echo.
echo All tests completed.
pause
