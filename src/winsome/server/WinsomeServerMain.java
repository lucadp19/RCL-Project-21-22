package winsome.server;

import java.io.*;

import java.time.Instant;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import winsome.server.exceptions.*;
import winsome.server.logging.WinsomeLogger;

import winsome.utils.ConsoleColors;
import winsome.utils.configs.exceptions.InvalidConfigFileException;

public class WinsomeServerMain {
    /** Command Line Args to this application */
    private static class CLArgs {
        /** Path to the config file */
        public final String configPath;
        /** Path to the log directory */
        public final String logPath;

        public CLArgs(String configPath, String logPath){ 
            this.configPath = Objects.requireNonNull(configPath, "config path is null");
            this.logPath    = Objects.requireNonNull(logPath, "log path is null");
        }
    }

    private static final String DEFAULT_CONFIG_PATH = "./configs/server-config.yaml";
    private static final String DEFAULT_LOG_PATH = "./logs";

    private static final Logger logger = Logger.getLogger("Winsome-Server");

    public static void main(String[] strArgs) {
        System.out.println(ConsoleColors.green("\t\tWinsome Social Network Server\n"));

        // reading arguments from command line
        CLArgs args;
        try { args = getCLArgs(strArgs); }
        catch (IllegalArgumentException ex){
            System.err.println(
                ConsoleColors.red("==> ERROR! ") + "Too many arguments!"
            );
            System.err.println(getHelpString());
            System.exit(1); return;
        }

        // checking that loggin directory exists
        System.out.println(ConsoleColors.blue("-> ") + "Setting up logging...");
        File logDir = new File(args.logPath);
        if(!logDir.exists() || !logDir.isDirectory()){
            System.err.println(
                ConsoleColors.red("==> ERROR! ") + "Log directory (" + args.logPath + ") does not exist: create it before running the Server."
            ); System.exit(1);
        }
        File logFile = new File(logDir, "WinsomeServerLog-" + Instant.now().toString() + ".log");

        // setting up logger
        try { 
            FileHandler fh = new FileHandler(logFile.getPath());
            fh.setFormatter(WinsomeLogger.getCustomFormatter());

            Logger root = Logger.getLogger("");
            for(Handler handler : root.getHandlers())
                root.removeHandler(handler);

            root.addHandler(fh);
            logger.setLevel(Level.INFO);

            System.out.println(ConsoleColors.blue("==> Logger set up!\n"));
        } catch (IOException ex){ 
            System.err.println(
                ConsoleColors.red("==> Error while setting up logging.\n") + "Aborting."
            ); System.exit(1); return;
        }

        WinsomeServer server;
        logger.log(Level.INFO, "Creating instance of WinsomeServer and reading configuration file.");
        try { server = new WinsomeServer(args.configPath); }
        catch (FileNotFoundException ex){
            System.err.println(ConsoleColors.red("==> ERROR!") + " Config file does not exist.");
            logger.log(Level.SEVERE, "Config file does not exist: " + ex.getMessage(), ex);
            System.exit(1);
            return;
        }
        catch (InvalidConfigFileException | IOException ex){
            System.err.println(ConsoleColors.red("==> ERROR!") + " Could not read config file.");
            logger.log(Level.SEVERE, "Could not read config file: " + ex.getMessage(), ex);
            System.exit(1);
            return; // otherwise java complains about uninitialized variables
        }
        catch (Throwable ex){
            System.err.println(ConsoleColors.red("==> Unexpected error!"));
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Unexpected error: " + ex.getMessage(), ex);
            System.exit(1);
            return;
        }

        // initializing server data
        try { 
            System.out.println(ConsoleColors.blue("-> ") + "Initializing server...");
            server.init();
            System.out.println(ConsoleColors.blue("==> Server initialized!\n"));
        } catch (FileNotFoundException ex){
            System.out.println(ConsoleColors.yellow("==> ") + "Persisted data missing: initialized server with empty data.\n");
            logger.log(Level.WARNING, "Persisted data missing: initialized server with empty data: " + ex.getMessage(), ex);
        } catch (InvalidJSONFileException ex){
            System.err.println(ConsoleColors.red("==> ERROR! ") + "Persisted data was in an invalid format: aborting.\n");
            logger.log(Level.SEVERE, "Persisted data was in an invalid format: " + ex.getMessage(), ex);
            System.exit(1);
        } catch (InvalidDirectoryException ex){
            System.err.println(ConsoleColors.red("==> ERROR! ") + "Persisted data directory does not exist: aborting.");
            logger.log(Level.SEVERE, "Persisted data directory does not exist: " + ex.getMessage(), ex);
            System.exit(1);
        } catch (IOException ex){
            System.err.println(ConsoleColors.red("==> ERROR! ") + "Some IO error occurred while reading persisted data: aborting.");
            logger.log(Level.SEVERE, "Some IO error occurred while reading persisted data: " + ex.getMessage(), ex);
            System.exit(1);
        } catch (Throwable ex){
            System.err.println(ConsoleColors.red("==> Unexpected error!"));
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Unexpected error: " + ex.getMessage(), ex);
            System.exit(1);
        }
        
        // connecting server
        try {
            System.out.println(ConsoleColors.blue("-> ") + "Starting server...");
            server.start();
            System.out.println(ConsoleColors.blue("==> Server started!\n"));
        } catch (IOException ex){
            System.err.println(ConsoleColors.red("==> ERROR! ") + "Some IO error occurred while starting the server: aborting.");
            logger.log(Level.SEVERE, "Some IO error occurred while starting the server: " + ex.getMessage(), ex);
            System.exit(1);
        } catch (Throwable ex){
            System.err.println(ConsoleColors.red("==> Unexpected error!"));
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Unexpected error: " + ex.getMessage(), ex);
            System.exit(1);
        }
        
        // running main loop
        ExecutorService main = Executors.newSingleThreadExecutor();
        main.execute( () -> {
            Thread.currentThread().setName("server-main");
            try { server.run(); }
            catch(IOException ex){ 
                System.err.println(ConsoleColors.red("==> ERROR! ") + "Some IO error occurred while the server was running: aborting."); 
                logger.log(Level.SEVERE, "Some IO error occurred while the server was running: " + ex.getMessage(), ex);
                System.exit(1); 
            } catch (Throwable ex){
                System.err.println(ConsoleColors.red("==> Unexpected error!"));
                ex.printStackTrace();
                logger.log(Level.SEVERE, "Unexpected error: " + ex.getMessage(), ex);
                System.exit(1);
            }
        });

        // waiting for poison pill
        Thread.currentThread().setName("poison-pill");
        try ( BufferedReader in = new BufferedReader(new InputStreamReader(System.in)); ){
            System.out.print(ConsoleColors.green("-> ") + "Press ENTER to quit: ");
            in.readLine();
        } catch (IOException ex) { 
            System.err.println(ConsoleColors.red("==> ERROR! ") + "Some IO error occurred while waiting for stdin input: aborting."); 
            logger.log(Level.SEVERE, "Some IO error occurred while waiting for stdin input: " + ex.getMessage(), ex);
        } catch (Throwable ex){
            System.err.println(ConsoleColors.red("==> Unexpected error!"));
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Unexpected error: " + ex.getMessage(), ex);
            System.exit(1);
        } finally { server.close(); } // closing server

        // shutting down server thread
        System.out.println(ConsoleColors.blue("\n-> ") + "Started server shutdown...");
        main.shutdown();
        try {
            if(!main.awaitTermination(3, TimeUnit.SECONDS))
                main.shutdownNow();
        } catch (InterruptedException ex) { main.shutdownNow(); }
        catch (Throwable ex){
            System.err.println(ConsoleColors.red("==> Unexpected error!"));
            ex.printStackTrace();
            logger.log(Level.SEVERE, "Unexpected error: " + ex.getMessage(), ex);
            System.exit(1);
        }
        System.out.println(ConsoleColors.blue("==> Shutdown complete!"));
    }

    /**
     * Parses the command line arguments.
     * @param args the command line arguments
     * @return the parsed command line arguments
     */
    private static CLArgs getCLArgs(String[] args){
        if(args.length > 2) throw new IllegalArgumentException("args must be either 0, 1 or 2");

        String configPath = DEFAULT_CONFIG_PATH;
        String logPath = DEFAULT_LOG_PATH;

        for(String arg : args){
            if(arg.startsWith("--help")) { 
                System.out.println(getHelpString());
                System.exit(0);
            }
            else if(arg.startsWith("--config="))
                configPath = arg.substring("--config=".length());
            else if(arg.startsWith("--logs="))
                logPath = arg.substring("--logs=".length());
        }

        return new CLArgs(configPath, logPath);
    }

    /**
     * Returns the help string for the Command Line Interface.
     * @return the help string
     */
    private static String getHelpString(){
        return "\tServer for the Winsome Social Network\n\n" +
            ConsoleColors.yellow("Available options\n" + "-----------------\n") + 
            ConsoleColors.yellow("--help   ") + "                   " +
                "prints this help message\n" +
            ConsoleColors.yellow("--config=") + "<config-path>      " +
                "optional path to the config file (default: " + DEFAULT_CONFIG_PATH + ")\n" +
            ConsoleColors.yellow("--logs=")   + "<log-path>           " +
                "optional path to the log directory (default: " + DEFAULT_LOG_PATH + ")\n";
    }
}
