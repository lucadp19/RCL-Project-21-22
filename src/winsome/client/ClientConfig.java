package winsome.client;

import java.io.*;
import java.util.Objects;
import java.util.Optional;

import winsome.utils.configs.AbstractConfig;
import winsome.utils.configs.ConfigEntry;
import winsome.utils.configs.exceptions.*;

/** The Client configuration */
public class ClientConfig extends AbstractConfig {
    /** A Field in the Client Config */
    private enum CCField {
        /** Address of the Server */
        SERVER_ADDR     ("server-addr"),
        /** Port of the TCP socket */
        PORT_TCP        ("tcp-port"),
        /** Host of the registry */
        REG_HOST        ("registry-host"),
        /** Port of the registry */
        REG_PORT        ("registry-port"),
        /** Timeout of the socket */
        SOCK_TIMEOUT    ("socket-timeout");

        /** Key name */
        public final String key;
        CCField(String key){ this.key = key; }
        
        /**
         * Creates a CCField object from the key name.
         * @param key the key name
         * @return the CCField object with the given key
         * @throws UnknownKeyException if no key with the given name is known
         */
        static CCField fromKey(String key) throws UnknownKeyException {
            return switch (key) {
                case "server-addr" ->    SERVER_ADDR;
                case "tcp-port" ->       PORT_TCP;
                case "registry-host" ->  REG_HOST;
                case "registry-port" ->  REG_PORT;
                case "socket-timeout" -> SOCK_TIMEOUT;
                default -> throw new UnknownKeyException(key);
            };
        }
    }

    public final String serverAddr;
    public final int portTCP;
    public final String regHost;
    public final int regPort;
    public final int sockTimeout;
    
    private ClientConfig(
        String serverAddr, int portTCP, String regHost, int regPort, int sockTimeout
    ) {
        this.serverAddr = Objects.requireNonNull(serverAddr, "server address field is null");
        this.portTCP = portTCP;
        this.regHost = Objects.requireNonNull(regHost, "registry host field is null");
        this.regPort = regPort;
        this.sockTimeout = sockTimeout;
    }

    /**
     * Reads the Server Config from a file.
     * @param configPath path to the config file
     * @return the server config object
     * @throws FileNotFoundException if the file does not exist
     * @throws InvalidConfigFileException if the config file is invalid (i.e. missing/duplicate/unknown keys)
     * @throws IOException if some other IO error occurs
     */
    public static ClientConfig fromConfigFile(final String configPath) throws 
            FileNotFoundException, InvalidConfigFileException, IOException {
        File configFile = getConfigFile(configPath);

        // initializing everything at null
        String serverAddr = null;
        Integer portTCP = null;
        String regHost = null; Integer regPort = null;
        Integer sockTimeout = null;
        
        try (
            BufferedReader configIn = new BufferedReader(new FileReader(configFile));
        ) {
            String line;
            while((line = configIn.readLine()) != null){
                Optional<ConfigEntry> maybeEntry = readEntry(line);
                if(!maybeEntry.isPresent()) continue;

                ConfigEntry entry = maybeEntry.get();

                CCField key = CCField.fromKey(entry.key);

                switch (key) {
                    case SERVER_ADDR -> {
                        if(serverAddr != null) throw new DuplicateKeyException(key.key);
                        serverAddr = entry.value;
                    }
                    case PORT_TCP -> {
                        if(portTCP != null) throw new DuplicateKeyException(key.key);
                        try { portTCP = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new EntryValueFormatException("argument of \"" + key.key + "\" must be an integer"); }
                    }
                    case REG_HOST -> {
                        if(regHost != null) throw new DuplicateKeyException(key.key);
                        regHost = entry.value;
                    }
                    case REG_PORT -> {
                        if(regPort != null) throw new DuplicateKeyException(key.key);
                        try { regPort = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new EntryValueFormatException("argument of \"" + key.key + "\" must be an integer"); }
                    }
                    case SOCK_TIMEOUT -> {
                        if(sockTimeout != null) throw new DuplicateKeyException(key.key);
                        try { sockTimeout = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new EntryValueFormatException("argument of \"" + key.key + "\" must be an integer"); }
                    }
                }
            }
        } catch (IOException ex) { throw new IOException("IO error while reading config file", ex); }

        // if the method throws, some key has not been set
        try { return new ClientConfig( serverAddr, portTCP, regHost, regPort, sockTimeout); } 
        catch (NullPointerException ex){ throw new KeyNotSetException(); }
    }
}