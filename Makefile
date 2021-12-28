SRC_DIR=./src
OUT_DIR=./out
LIB_DIR=./lib

GSON_LIB=$(LIB_DIR)/gson-2.8.6.jar

SRC_CP=$(SRC_DIR):$(GSON_LIB):.
OUT_CP=$(OUT_DIR):$(GSON_LIB):.

SRC_FILES:=$(shell find $(SRC_DIR) -name *.java)

.DEFAULT_GOAL:= default

default:
	@echo "Compiling project..."
	@echo "javac -d $(OUT_DIR) -cp $(SRC_CP) $(SRC_DIR)/**/*.java"
	@javac -d $(OUT_DIR) -cp $(SRC_CP) $(SRC_FILES)
	@echo "Project compiled!"

.PHONY: clean runServer

clean:
	@echo "Cleaning $(OUT_DIR)..."
	@rm -rf $(OUT_DIR)/*
	@echo "$(OUT_DIR) clean!"

runServer:
	@java -cp $(OUT_CP) winsome.server.WinsomeServerMain

runClient:
	@java -cp $(OUT_CP) winsome.client.WinsomeClientMain