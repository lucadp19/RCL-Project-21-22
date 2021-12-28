package winsome.server;

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
        try { server.runServer(); }
        catch(Exception ex){ ex.printStackTrace(); }      
    }
}
