package winsome.server;

import java.io.*;
import java.util.Objects;
import java.util.Optional;

import winsome.utils.configs.AbstractConfig;
import winsome.utils.configs.ConfigEntry;
import winsome.utils.configs.exceptions.*;

public class ServerConfig extends AbstractConfig {
    /** A Field in the Server Config */
    private static enum SCField {
        /** Port of the TCP socket */
        PORT_TCP        ("tcp-port"),
        /** Port of the UDP socket */
        PORT_UDP        ("udp-port"),
        /** Multicast address on which clients will receive notifications */
        MCAST_ADDR      ("multicast-addr"),
        /** Multicast port on which clients will receive notifications */
        MCAST_PORT      ("multicast-port"),
        /** Host of the registry */
        REG_HOST        ("registry-host"),
        /** Port of the registry */
        REG_PORT        ("registry-port"),
        /** Waiting time (in seconds) between two iterations of the Rewards Algorithm */
        REWARD_INT      ("reward-interval"),
        /** Percentage of the reward going to the author */
        REWARD_PERC     ("reward-percentage"),
        /** Directory in which the persisted files are found/saved */
        PERSIST_DIR     ("persistence-dir"),
        /** Waiting time (in seconds) between two iterations of the Persistence Thread */
        PERSIST_INT     ("persistence-interval"),
        /** Keep alive time (in seconds) for non-core worker threads */
        KEEP_ALIVE      ("keep-alive"),
        /** Minimum number of worker threads active at the same time */
        MIN_THREADS     ("min-threads"),
        /** Maximum number of worker threads acitve at the same time */
        MAX_THREADS     ("max-threads"),
        /** Timeout (in milliseconds) before forcefully shutting down the server pool */
        POOL_TIMEOUT    ("pool-timeout");

        /** Key name */
        public final String key;
        SCField(String key){ this.key = key; }
        
        /**
         * Creates a SCField object from the key name.
         * @param key the key name
         * @return the SCField object with the given key
         */
        static SCField fromKey(String key){
            return switch (key) {
                case "tcp-port" ->          PORT_TCP;
                case "udp-port" ->          PORT_UDP;
                case "multicast-addr" ->    MCAST_ADDR;
                case "multicast-port" ->    MCAST_PORT;
                case "registry-host" ->     REG_HOST;
                case "registry-port" ->     REG_PORT;
                case "reward-interval" ->   REWARD_INT;
                case "reward-percentage" -> REWARD_PERC;
                case "persistence-dir" ->   PERSIST_DIR;
                case "keep-alive" ->        KEEP_ALIVE;
                case "min-threads" ->       MIN_THREADS;
                case "max-threads" ->       MAX_THREADS;
                case "pool-timeout" ->      POOL_TIMEOUT;
                case "persistence-interval" -> PERSIST_INT;
                default -> throw new UnknownKeyException(key);
            };
        }
    }

    /** Port of the TCP socket */
    public final int portTCP;
    /** Port of the UDP socket */
    public final int portUDP;
    /** Multicast address on which clients will receive notifications */
    public final String multicastAddr;
    /** Multicast port on which clients will receive notifications */
    public final int multicastPort;
    /** Host of the registry */
    public final String regHost;
    /** Port of the registry */
    public final int regPort;

    /** Waiting time (in seconds) between two iterations of the Rewards Algorithm */
    public final long rewardInterval;
    /** Percentage of the reward going to the author/curators */
    public final RewardsPercentage percentage;

    /** Directory in which the persisted files are found/saved */
    public final String persistenceDir;
    /** Waiting time (in seconds) between two iterations of the Persistence Thread */
    public final long persistenceInterval;

    /** Keep alive time (in seconds) for non-core worker threads */
    public final long keepAlive;
    /** Minimum number of worker threads active at the same time */
    public final int minThreads;
    /** Maximum number of worker threads acitve at the same time */
    public final int maxThreads;
    /** Timeout (in milliseconds) before forcefully shutting down the server pool */
    public final long poolTimeout;

    private ServerConfig(
        int portTCP, int portUDP, String multicastAddr,
        int multicastPort, String regHost, int regPort,
        long rewardInterval, RewardsPercentage percentage,
        String persistenceDir, long persistenceInterval,
        long keepAlive, int minThreads, int maxThreads, long poolTimeout
    ) {
        this.portTCP = portTCP;
        this.portUDP = portUDP;
        this.multicastAddr = Objects.requireNonNull(multicastAddr, "multicast address field is null");
        this.multicastPort = multicastPort;
        this.regHost = Objects.requireNonNull(regHost, "registry host field is null");
        this.regPort = regPort;
        this.rewardInterval = rewardInterval;
        this.percentage = Objects.requireNonNull(percentage, "percentage field is null");
        this.persistenceDir = Objects.requireNonNull(persistenceDir, "persistence directory field is null");
        this.persistenceInterval = persistenceInterval;
        this.keepAlive = keepAlive;
        this.minThreads = minThreads;
        this.maxThreads = maxThreads;
        this.poolTimeout = poolTimeout;
    }

    /**
     * Reads the Server Config from a file.
     * @param configPath path to the config file
     * @return the server config object
     * @throws FileNotFoundException if the file does not exist
     * @throws InvalidConfigFileException if the config file is invalid (i.e. missing/duplicate/unknown keys)
     * @throws IOException if some other IO error occurs
     */
    public static ServerConfig fromConfigFile(final String configPath) throws FileNotFoundException, InvalidConfigFileException, IOException {
        File configFile = getConfigFile(configPath);

        // initializing everything at null
        Integer portTCP = null; Integer portUDP = null; 
        String multicastAddr = null; Integer multicastPort = null; 
        String regHost = null; Integer regPort = null;
        Long rewardInterval = null; 
        RewardsPercentage percentage = null;
        String persistenceDir = null; Long persistenceInterval = null;
        Long keepAlive = null; Long poolTimeout = null;
        Integer minThreads = null; Integer maxThreads = null;

        try (
            BufferedReader configIn = new BufferedReader(new FileReader(configFile));
        ) {
            String line;
            while((line = configIn.readLine()) != null){
                Optional<ConfigEntry> maybeEntry = readEntry(line);
                if(!maybeEntry.isPresent()) continue; // empty line or comment

                ConfigEntry entry = maybeEntry.get(); // reads the entry

                SCField key = SCField.fromKey(entry.key);

                switch (key) {
                    case PORT_TCP ->{
                        if(portTCP != null) throw new DuplicateKeyException(key.key);
                        try { portTCP = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + key.key + "\" must be an integer"); }
                    }
                    case PORT_UDP -> {
                        if(portUDP != null) throw new DuplicateKeyException(key.key);
                        try { portUDP = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + key.key + "\" must be an integer"); }
                    }
                    case MCAST_ADDR -> {
                        if(multicastAddr != null) throw new DuplicateKeyException(key.key);
                        multicastAddr = entry.value;
                    }
                    case MCAST_PORT -> {
                        if(multicastPort != null) throw new DuplicateKeyException(key.key);
                        try { multicastPort = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + key.key + "\" must be an integer"); }
                    }
                    case REG_HOST -> {
                        if(regHost != null) throw new DuplicateKeyException(key.key);
                        regHost = entry.value;
                    }
                    case REG_PORT -> {
                        if(regPort != null) throw new DuplicateKeyException(key.key);
                        try { regPort = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + key.key + "\" must be an integer"); }
                    }
                    case REWARD_INT -> {
                        if(rewardInterval != null) throw new DuplicateKeyException(key.key);
                        try { rewardInterval = Long.parseLong(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + key.key + "\" must be an integer"); }
                    }
                    case REWARD_PERC -> {
                        if(percentage != null) throw new DuplicateKeyException(key.key);
                        try { 
                            double authorReward = Double.parseDouble(entry.value);
                            percentage = RewardsPercentage.fromAuthor(authorReward / 100);
                        }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + key.key + "\" must be a double between 0 and 100"); }
                    }
                    case PERSIST_DIR -> {
                        if(persistenceDir != null) throw new DuplicateKeyException(key.key);
                        persistenceDir = entry.value;
                    }
                    case PERSIST_INT -> {
                        if(persistenceInterval != null) throw new DuplicateKeyException(key.key);
                        try { persistenceInterval = Long.parseLong(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + key.key + "\" must be an integer"); }
                    }
                    case KEEP_ALIVE -> {
                        if(keepAlive != null) throw new DuplicateKeyException(key.key);
                        try { keepAlive = Long.parseLong(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + key.key + "\" must be an integer"); }
                    }
                    case MIN_THREADS -> {
                        if(minThreads != null) throw new DuplicateKeyException(key.key);
                        try { minThreads = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + key.key + "\" must be an integer"); }
                    }
                    case MAX_THREADS -> {
                        if(maxThreads != null) throw new DuplicateKeyException(key.key);
                        try { maxThreads = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + key.key + "\" must be an integer"); }
                    }
                    case POOL_TIMEOUT -> {
                        if(poolTimeout != null) throw new DuplicateKeyException(key.key);
                        try { poolTimeout = Long.parseLong(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + key.key + "\" must be an integer"); }
                    }
                };
            }
        } catch (IOException ex) { throw new IOException("IO error while reading config file", ex); }

        // if the method throws, some key has not been set
        try { return new ServerConfig(
                    portTCP, portUDP, multicastAddr, multicastPort, 
                    regHost, regPort, rewardInterval, percentage, 
                    persistenceDir, persistenceInterval, keepAlive,
                    minThreads, maxThreads, poolTimeout);
        } catch (NullPointerException ex){ throw new KeyNotSetException(); }
    }
}