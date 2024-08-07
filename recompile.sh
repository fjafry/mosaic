#!/usr/bin/bash

path="./app/custom-applications"

# cd to the app folder
cd $path

# clean and install the app
mvn clean install

echo "App recompiled. Copying jar to shared folder"
# copy the compiled jar from the target folder to a shared folder
cp target/*.jar ~/thesis/mosaic/scenarios/IngolstadtCross/application

echo "Done"