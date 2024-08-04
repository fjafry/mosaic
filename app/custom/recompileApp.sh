#!/usr/bin/bash


# check if argument is given
if [ -z "$1" ]; then
    echo "Please provide the name of the App you want to recompile"
    exit 1
fi

# cd to the app folder
cd $1

# clean and install the app
mvn clean install

echo "App recompiled. Copying jar to shared folder"
# copy the compiled jar from the target folder to a shared folder
cp target/*.jar ~/thesis/mosaic/scenarios/IngolstadtCross/application/$1.jar

echo "Done"