package winsome.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import winsome.api.WinsomeAPI;
import winsome.client.ClientConfig;
import winsome.utils.ConsoleColors;

public class WinsomeClientMain {
    private static String DEFAULT_CONFIG_PATH = "./configs/client-config.yaml";

    public static void main(String[] args) {
        ClientConfig config = null;
        WinsomeAPI api = null;
        try {
            config = new ClientConfig(DEFAULT_CONFIG_PATH); // TODO: catch exceptions

            System.out.println("Config read!");
            // creating API interface
            api = new WinsomeAPI(
                config.getServerAddr(),
                config.getTCPPort(),
                config.getRegHost(),
                config.getRegPort()
            );
            api.connect(); 
        } catch(Exception ex){ 
            ex.printStackTrace(); 
            System.exit(1);
        }

        try (
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        ){
            printPrompt();
            String cmd;
            while((cmd = input.readLine()) != null){
                cmd = cmd.trim();
                if(cmd.isEmpty()) continue;
                if(cmd.startsWith("quit")) System.exit(0);

                // test command
                if(cmd.startsWith("echo")){
                    String msg = cmd.substring(4).trim();
                    
                    String ans = api.echoMsg(msg);
                    System.out.println(ConsoleColors.BLUE_BOLD + " < " + ConsoleColors.RESET + ans);
                }
                // TODO: do things
                printPrompt();
            }
        } catch(IOException ex){ ex.printStackTrace(); }
    }

    private static void printPrompt(){ System.out.print(ConsoleColors.GREEN_BOLD + " > " + ConsoleColors.RESET); }
}
