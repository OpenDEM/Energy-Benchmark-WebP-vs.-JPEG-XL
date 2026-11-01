@echo off
REM This code was generated using AI and manually reviewed.
REM Compares file size and duration for different effort levels.
REM Usage: benchmark_effort.bat tile.png
set CJXL=%~dp0libjxl_0_12_0\cjxl.exe
set IN=%1

for %%E in (1 3 5 7 9) do (
    echo --- effort %%E ---
    powershell -NoProfile -Command "$t=Measure-Command { & '%CJXL%' '%IN%' 'out_e%%E.jxl' -e %%E -d 0 --num_threads=1 2>$null }; '{0} ms' -f [int]$t.TotalMilliseconds"
    for %%F in (out_e%%E.jxl) do echo %%~zF bytes
)
for %%F in (%IN%) do echo Original: %%~zF bytes
