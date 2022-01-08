#!/bin/bash

# useful directories
LIB_DIR="./lib"
BIN_DIR="./bin"

# path to gson.jar
GSON_LIB="${LIB_DIR}/gson-2.8.6.jar"

# necessary files
SERVER_MAIN="winsome.server.WinsomeServerMain"
SERVER_JAR="${BIN_DIR}/winsome-server.jar"
SERVER_CP="${SERVER_JAR}:${GSON_LIB}:."

# executing server
java -cp ${SERVER_CP} ${SERVER_MAIN} "$@"
