# Colors
GREEN = \033[32;1m
BLUE  = \033[34;1m
RED   = \033[31;1m
RESET = \033[0m

# Directories
SRC_DIR = ./src
OUT_DIR = ./out
LIB_DIR = ./lib
BIN_DIR = ./bin

# GSON jar file
GSON_LIB = $(LIB_DIR)/gson-2.8.6.jar

# Arguments for -cp
SRC_CP = $(SRC_DIR):$(GSON_LIB):.

# Source files
SRC_FILES := $(shell find $(SRC_DIR) -name *.java)
SERVER_MAIN_SRC = $(SRC_DIR)/winsome/server/WinsomeServerMain.java
CLIENT_MAIN_SRC = $(SRC_DIR)/winsome/client/WinsomeClientMain.java

# Default target
.DEFAULT_GOAL:= default
default: title compile server client

# Just prints a title
title:
	@echo -e "\t\t$(GREEN)Winsome Makefile$(RESET)\n"

# Compiles all files
compile:
	@echo -e "$(BLUE)-> $(RESET)Compiling project..."
	@javac -d $(OUT_DIR) -cp $(SRC_CP) $(SERVER_MAIN_SRC)
	@javac -d $(OUT_DIR) -cp $(SRC_CP) $(CLIENT_MAIN_SRC)
	@javac -d $(OUT_DIR) -cp $(SRC_CP) $(SRC_FILES)
	@echo -e "$(BLUE)==> Project compiled!$(RESET)\n"

# Server JAR creation

SERVER_MAIN=winsome.server.WinsomeServerMain
SERVER_JAR=$(BIN_DIR)/winsome-server.jar
SERVER_CP=$(SERVER_JAR):$(GSON_LIB):.

server:
	@echo -e "$(BLUE)-> $(RESET)Creating Server .jar file..."
	@jar -cevf $(SERVER_MAIN) $(SERVER_JAR) \
		-C $(OUT_DIR) winsome/server \
		-C $(OUT_DIR) winsome/api \
		-C $(OUT_DIR) winsome/utils > /dev/null
	@echo -e "$(BLUE)==> Server .jar created!$(RESET)\n"

# Client JAR creation

CLIENT_MAIN=winsome.client.WinsomeClientMain
CLIENT_JAR=$(BIN_DIR)/winsome-client.jar
CLIENT_CP=$(CLIENT_JAR):$(GSON_LIB):.

client:
	@echo -e "$(BLUE)-> $(RESET)Creating Client .jar file..."
	@jar -cevf $(CLIENT_MAIN) $(CLIENT_JAR) \
		-C $(OUT_DIR) winsome/client \
		-C $(OUT_DIR) winsome/api \
		-C $(OUT_DIR) winsome/utils > /dev/null
	@echo -e "$(BLUE)==> Client .jar created!$(RESET)"

# -------------- CLEAN -------------- #

.PHONY: clean run-server run-client

clean: title
	@echo -e "$(BLUE)-> $(RESET)Cleaning object files and executables..."
	@rm -rf $(OUT_DIR)/* $(BIN_DIR)/*
	@echo -e "$(BLUE)==> Done!$(RESET)"

# -------------- RUN -------------- #

run-server:
	@java -cp $(SERVER_CP) $(SERVER_MAIN)

run-client:
	@java -cp $(CLIENT_CP) $(CLIENT_MAIN)