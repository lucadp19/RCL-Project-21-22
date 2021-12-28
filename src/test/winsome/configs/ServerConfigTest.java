package test.winsome.configs;

import winsome.server.ServerConfig;
import winsome.utils.ConsoleColors;
import winsome.utils.configs.exceptions.DuplicateKeyException;
import winsome.utils.configs.exceptions.KeyNotSetException;

public class ServerConfigTest {
    public static void main(String[] args) {
        System.out.println(ConsoleColors.GREEN_BOLD + "\tTesting ServerConfig class" + ConsoleColors.RESET);

        System.out.println();
        String configPath = "configs/server-config.yaml";
        try {
            System.out.println(
                ConsoleColors.GREEN_BOLD + "[1] " + ConsoleColors.RESET + 
                "Parsing correct config..."
                );
            ServerConfig config = new ServerConfig(configPath);

            System.out.println("    Config parsed!");
            printConfig(config);
        } catch(Exception ex) { 
            printError( "exception caught while parsing correct config" );
            ex.printStackTrace(); 
            System.exit(1);
        }

        System.out.println();
        configPath = "src/test/winsome/configs/server_configs/number-formats.yaml";
        try {
            System.out.println(
                ConsoleColors.GREEN_BOLD + "[2] " + ConsoleColors.RESET + 
                "Parsing config with a string in an integer field..."
                );
            ServerConfig config = new ServerConfig(configPath);

            printError("config contains a string in an integer field, should have thrown an error!");
            printConfig(config);
            System.exit(1);
        } 
        catch(NumberFormatException ex){
            System.out.println("    Exception correctly caught!");
            ex.printStackTrace();
        }
        catch(Exception ex) { 
            printError("wrong exception caught!");
            ex.printStackTrace(); 
            System.exit(1);
        }


        System.out.println();
        configPath = "src/test/winsome/configs/server_configs/duplicates.yaml";
        try {
            System.out.println(
                ConsoleColors.GREEN_BOLD + "[3] " + ConsoleColors.RESET + 
                "Parsing config with a duplicate field..."
                );
            ServerConfig config = new ServerConfig(configPath);

            printError("config contains a duplicate field, should have thrown an error!");
            printConfig(config);
            System.exit(1);
        } 
        catch(DuplicateKeyException ex){
            System.out.println("    Exception correctly caught!");
            ex.printStackTrace();
        }
        catch(Exception ex) { 
            printError("wrong exception caught!");
            ex.printStackTrace(); 
            System.exit(1);
        }

        System.out.println();
        configPath = "src/test/winsome/configs/server_configs/missing.yaml";
        try {
            System.out.println(
                ConsoleColors.GREEN_BOLD + "[4] " + ConsoleColors.RESET + 
                "Parsing config with missing fields..."
                );
            ServerConfig config = new ServerConfig(configPath);

            printError("config has some fields missing, should have thrown an error!");
            printConfig(config);
            System.exit(1);
        } 
        catch(KeyNotSetException ex){
            System.out.println("    Exception correctly caught!");
            ex.printStackTrace();
        }
        catch(Exception ex) { 
            printError("wrong exception caught!");
            ex.printStackTrace();
            System.exit(1);
        }

        System.out.println(ConsoleColors.GREEN_BOLD + "\nAll tests succeeded!" + ConsoleColors.RESET);
    }

    private static void printConfig(ServerConfig config){
        printField("Server Address");    System.out.println(config.getServerAddr());
        printField("TCP Port");          System.out.println(config.getTCPPort());
        printField("UDP Port");          System.out.println(config.getUDPPort());
        printField("Multicast Address"); System.out.println(config.getMulticastAddr());
        printField("Multicast Port");    System.out.println(config.getMulticastPort());
        printField("Registry Host");     System.out.println(config.getRegHost());
        printField("Socket Timeout");    System.out.println(config.getSockTimeout());
        printField("Reward Interval");   System.out.println(config.getRewardInterval());
        printField("Reward Percentage"); System.out.println(config.getRewardPerc());
    }

    private static void printField(String field){
        final int length = 25;
        System.out.print(
            "\t" + 
            ConsoleColors.GREEN_BOLD + 
            String.format("%1$-" + length + "s", field + ":") 
            + ConsoleColors.RESET
            );
    }

    private static void printError(String msg){
        System.err.println(
            ConsoleColors.RED_BOLD + "    ERROR: " + ConsoleColors.RESET + msg
        );
    }

}
