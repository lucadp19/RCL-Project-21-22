package winsome.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import winsome.api.WinsomeAPI;
import winsome.client.ClientConfig;
import winsome.client.exceptions.UnknownCommandException;
import winsome.utils.ConsoleColors;

public class WinsomeClientMain {
    private static String DEFAULT_CONFIG_PATH = "./configs/client-config.yaml";

    public static void main(String[] args) {
        System.out.println(ConsoleColors.green("\t\tWinsome Social Network\n"));

        System.out.println("Welcome to the Winsome Social Network!\n");

        ClientConfig config = null;
        WinsomeAPI api = null;
        try {
            System.out.println(ConsoleColors.blue("-> ") + "Reading config...");
            config = new ClientConfig(DEFAULT_CONFIG_PATH); // TODO: catch exceptions
            System.out.println(ConsoleColors.blue("==> Config read!"));

            // creating API interface
            System.out.println(ConsoleColors.blue("-> ") + "Connecting to server...");
            api = new WinsomeAPI(
                config.getServerAddr(),
                config.getTCPPort(),
                config.getRegHost(),
                config.getRegPort()
            );
            api.connect(); 
            System.out.println(ConsoleColors.blue("==> Connected!"));
        } catch(Exception ex){ 
            ex.printStackTrace(); 
            System.exit(1);
        }

        System.out.println("\nType a command (or " + ConsoleColors.green("help") + " to print the usage message)...\n");

        try (
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        ){
            String line;

            System.out.print(ConsoleColors.green("-> "));
            while((line = input.readLine().trim()) != null){
                if(line.isEmpty()) continue;
                if(line.startsWith("quit")) {
                    // maybe print message or create termination handler
                    System.exit(0);
                }

                executeCommand(line);

                // test command
                if(line.startsWith("echo")){
                    String msg = line.substring(4).trim();
                    
                    String ans = api.echoMsg(msg);
                    System.out.println(ConsoleColors.BLUE_BOLD + " < " + ConsoleColors.RESET + ans);
                }
                // TODO: do things
                System.out.print(ConsoleColors.green("-> "));
            }
        } catch(IOException ex){ ex.printStackTrace(); }
    }

    private static void executeCommand(String line){
        Command cmd = null;

        try { cmd = Command.fromString(line); }
        catch(UnknownCommandException ex){
            System.out.println(ConsoleColors.red("==> ERROR! ") + ex.getMessage());
            System.out.print(Command.help());
        }

        String argStr = line.substring(cmd.name.length()).trim();

        switch (cmd) {
            case HELP: {
                Command cmd1 = null;
                try { 
                    cmd1 = Command.fromString(argStr); 
                    System.out.println("\n" + cmd1.getHelpString());
                } catch(UnknownCommandException ex){ System.out.println("\n" + Command.help()); }
                break;
            }
            default: {
                System.out.println(ConsoleColors.red("==> Command not yet implemented :("));
                break;
            }
        }

    }
}
