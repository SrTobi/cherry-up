@echo off
setlocal EnableExtensions

rem get unique file name 
:uniqLoop
set "cherryUpFile=%tmp%\bat~%RANDOM%.tmp.jar"
if exist "%cherryUpFile%" goto :uniqLoop

curl -L -o "%cherryUpFile%" https://github.com/SrTobi/cherry-up/releases/latest/download/cherry-up.jar

%cherryUpFile%