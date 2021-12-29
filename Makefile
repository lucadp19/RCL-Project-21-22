SRC_DIR=./src
OUT_DIR=./out
LIB_DIR=./lib
BIN_DIR=./bin

GSON_LIB=$(LIB_DIR)/gson-2.8.6.jar

SRC_CP=$(SRC_DIR):$(GSON_LIB):.
OUT_CP=$(OUT_DIR):$(GSON_LIB):.

SRC_FILES:=$(shell find $(SRC_DIR) -name *.java)

.DEFAULT_GOAL:= default

default: compile server client

compile:
	@echo "Compiling project..."
	@javac -d $(OUT_DIR) -cp $(SRC_CP) $(SRC_FILES)
	@echo "Project compiled!"

SERVER_MAIN=winsome.server.WinsomeServerMain
SERVER_JAR=$(BIN_DIR)/winsome-server.jar

server:
	@echo "Creating Server .jar file..."
	@jar -cevf $(SERVER_MAIN) $(SERVER_JAR) \
		-C $(OUT_DIR) winsome/server \
		-C $(OUT_DIR) winsome/api \
		-C $(OUT_DIR) winsome/utils > /dev/null
	@echo "Server .jar created!"

CLIENT_MAIN=winsome.client.WinsomeClientMain
CLIENT_JAR=$(BIN_DIR)/winsome-client.jar

client:
	@echo "Creating Client .jar file..."
	@jar -cevf $(CLIENT_MAIN) $(CLIENT_JAR) \
		-C $(OUT_DIR) winsome/client \
		-C $(OUT_DIR) winsome/api \
		-C $(OUT_DIR) winsome/utils > /dev/null
	@echo "Client .jar created!"

.PHONY: clean runServer runClient

clean:
	@echo "Cleaning object files and executables..."
	@rm -rf $(OUT_DIR)/* $(BIN_DIR)/*
	@echo "Done!"

runServer:
	@java -cp $(OUT_CP) -jar $(SERVER_JAR)

runClient:
	@java -cp $(OUT_CP) -jar $(CLIENT_JAR)