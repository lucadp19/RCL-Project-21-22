package winsome.server;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import winsome.server.exceptions.InvalidDirectoryException;
import winsome.server.exceptions.InvalidJSONFileException;
import winsome.utils.configs.exceptions.InvalidConfigFileException;

public class WinsomeServerMain {
    private static final String DEFAULT_CONFIG_PATH = "./configs/server-config.yaml";

    public static void main(String[] args) {
        if(args.length > 1) { System.exit(1); } // TODO: error message

        String configPath = (args.length == 1) ? args[0] : DEFAULT_CONFIG_PATH;

        WinsomeServer server;
        try { server = new WinsomeServer(configPath); }
        catch (InvalidConfigFileException | IOException ex){
            System.err.println("Error! " + ex.getMessage());
            System.err.println("Aborting."); 
            System.exit(1);
            return; // otherwise java complains about uninitialized variables
        }

        // initializing server data
        try { 
            System.out.println("Initializing server...");
            server.init();
            System.out.println("Server initialized!");
        } catch (FileNotFoundException ex){
            System.out.println("Persisted data missing: initialized server with empty data.");
        } catch (InvalidJSONFileException ex){
            System.out.println("Persisted data was in an invalid format: initialized server with empty data.");
        } catch (InvalidDirectoryException ex){
            System.err.println("Persisted data directory does not exist: aborting.");
            System.exit(1);
        } catch (IOException ex){
            System.err.println("Some IO error occurred while reading persisted data: aborting.");
            System.exit(1);
        }
        
        // connecting server
        try {
            System.out.println("Starting server...");
            server.start();
            System.out.println("Server started!");
        } catch (IOException ex){
            System.err.println("Some IO error occurred while reading persisted data: aborting.");
            System.exit(1);
        }

        // shutdown hook
        Runtime.getRuntime().addShutdownHook(
            new Thread(
                new Runnable(){
                    @Override
                    public void run(){
                        System.out.println("Server closing! Bye :D");
                    }
                }
            )
        );
        
        // running main loop
        System.out.println("Server running...");
        ExecutorService main = Executors.newSingleThreadExecutor();
        main.execute( () -> {
            try { server.run(); }
            catch(IOException ex){ 
                System.err.println("Error: some IO exception occurred while server was running. Aborting immediately."); 
                System.exit(1); 
            }
        });

        // waiting for 
        try ( BufferedReader in = new BufferedReader(new InputStreamReader(System.in)); ){
            System.out.print("-> Press ENTER to quit: ");
            in.readLine();
        } 
        catch (IOException ex) { System.err.println("Some IO error occurred while reading from standard input."); } 
        finally { server.close(); }

        System.out.println("Started server shutdown...");
        main.shutdown();
        try {
            if(!main.awaitTermination(3, TimeUnit.SECONDS))
                main.shutdownNow();
        } catch (InterruptedException ex) { main.shutdownNow(); }
        System.out.println("Shutdown complete.");
    }
}
