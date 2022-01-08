#!/bin/bash

BLUE='\033[1;34m'
RESET='\033[0m'

if [ ! -d bin ] || [ ! -d obj ] || [ ! -d logs ]
then
    echo -e "${BLUE}-> ${RESET}Creating ./bin, ./obj and ./logs directories..."
    mkdir -p bin/ obj/util obj/util/hash obj/api obj/server obj/client
    echo -e "${BLUE}==> Directories created!${RESET}\n"
fi