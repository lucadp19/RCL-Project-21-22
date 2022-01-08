#!/bin/bash

# useful directories
LIB_DIR="./lib"
BIN_DIR="./bin"

# path to gson.jar
GSON_LIB="${LIB_DIR}/gson-2.8.6.jar"

# necessary files
CLIENT_MAIN="winsome.client.WinsomeClientMain"
CLIENT_JAR="${BIN_DIR}/winsome-client.jar"
CLIENT_CP="${CLIENT_JAR}:${GSON_LIB}:."

# executing client
java -cp ${CLIENT_CP} ${CLIENT_MAIN} "$@"