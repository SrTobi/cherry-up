#!/usr/bin/env sh
tempCherryUp=$(mktemp)
curl -L -o $tempCherryUp https://github.com/SrTobi/cherry-up/releases/latest/download/cherry-up.jar
java -jar $tempCherryUp && rm $tempCherryUp &
disown
