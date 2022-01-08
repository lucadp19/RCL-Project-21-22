#!/bin/bash

BLUE='\033[1;34m'
RESET='\033[0m'

if [ ! -d bin ] || [ ! -d out ] || [ ! -d logs ]
then
    echo -e "${BLUE}-> ${RESET}Creating ./bin, ./obj and ./logs directories..."
    mkdir -p bin/ out/ logs/
    echo -e "${BLUE}==> Directories created!${RESET}\n"
fi