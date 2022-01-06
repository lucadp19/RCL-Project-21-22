package winsome.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class WinsomeServerMain {
    private static final String DEFAULT_CONFIG_PATH = "./configs/server-config.yaml";

    public static void main(String[] args) {
        WinsomeServer server = new WinsomeServer();

        try { 
            // initializing server data
            server.initServer(DEFAULT_CONFIG_PATH);
            System.out.println("Server initialized!");

            // connecting server
            server.startServer();
            System.out.println("Server connected!");
        } catch(Exception ex){ ex.printStackTrace(); }

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
        System.out.println("Server listening...");
        ExecutorService main = Executors.newSingleThreadExecutor();
        main.execute( () -> {
            try { server.runServer(); }
            catch(IOException ex){ System.err.println("EXCEPTION"); System.exit(1); }
        });

        try ( BufferedReader in = new BufferedReader(new InputStreamReader(System.in)); ){
            System.out.print("-> Write 'quit' to quit: ");
            String msg = in.readLine();
        } 
        catch (IOException ex) { System.err.println("Error while reading from standard input."); } 
        finally { server.closeServer(); }

        System.out.println("Started server shutdown...");
        main.shutdown();
        try {
            if(!main.awaitTermination(3, TimeUnit.SECONDS))
                main.shutdownNow();
        } catch (InterruptedException ex) { main.shutdownNow(); }
        System.out.println("Shutdown complete.");
    }
}
