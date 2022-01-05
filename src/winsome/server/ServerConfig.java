package winsome.server;

import java.io.*;
import java.util.Optional;

import winsome.utils.configs.AbstractConfig;
import winsome.utils.configs.ConfigEntry;
import winsome.utils.configs.exceptions.*;

public class ServerConfig extends AbstractConfig {
    private enum SCField {
        SERVER_ADDR     ("server-addr"),
        PORT_TCP        ("tcp-port"),
        PORT_UDP        ("udp-port"),
        MCAST_ADDR      ("multicast-addr"),
        MCAST_PORT      ("multicast-port"),
        REG_HOST        ("registry-host"),
        REG_PORT        ("registry-port"),
        SOCK_TIMEOUT    ("socket-timeout"),
        REWARD_INT      ("reward-interval"),
        REWARD_PERC     ("reward-percentage"),
        PERSIST_DIR     ("persistence-dir"),
        MIN_THREADS     ("min-threads"),
        MAX_THREADS     ("max-threads");

        public final String key;
        SCField(String key){ this.key = key; }
        
        static SCField fromKey(String key){
            switch (key) {
                case "server-addr":         return SERVER_ADDR;
                case "tcp-port":            return PORT_TCP;
                case "udp-port":            return PORT_UDP;
                case "multicast-addr":      return MCAST_ADDR;
                case "multicast-port":      return MCAST_PORT;
                case "registry-host":       return REG_HOST;
                case "registry-port":       return REG_PORT;
                case "socket-timeout":      return SOCK_TIMEOUT;
                case "reward-interval":     return REWARD_INT;
                case "reward-percentage":   return REWARD_PERC;
                case "persistence-dir":     return PERSIST_DIR;
                case "min-threads":         return MIN_THREADS;
                case "max-threads":         return MAX_THREADS;
                default: throw new UnknownKeyException(key);
            }
        }
    }

    private String serverAddr = null;
    private int portTCP = -1;
    private int portUDP = -1;
    private String multicastAddr = null;
    private int multicastPort = -1;
    private String regHost = null;
    private int regPort = -1;
    private long sockTimeout = -1;

    private long rewardInterval = -1;
    private RewardsPercentage percentage = null;

    private String persistenceDir = null;

    private int minThreads = -1;
    private int maxThreads = -1;

    public ServerConfig(final String configPath) throws 
            NullPointerException, FileNotFoundException, 
            MalformedEntryException, DuplicateKeyException, UnknownKeyException, 
            KeyNotSetException, NumberFormatException, IOException 
    {
        File configFile = getConfigFile(configPath);

        try (
            BufferedReader configIn = new BufferedReader(new FileReader(configFile));
        ) {
            String line;
            while((line = configIn.readLine()) != null){
                Optional<ConfigEntry> maybeEntry = readEntry(line);
                if(!maybeEntry.isPresent()) continue;

                ConfigEntry entry = maybeEntry.get();

                SCField key = SCField.fromKey(entry.key);

                switch (key) {
                    case SERVER_ADDR:
                        if(serverAddr != null) throw new DuplicateKeyException(SCField.SERVER_ADDR.key);
                        serverAddr = entry.value;
                        break;
                    case PORT_TCP:
                        if(portTCP != -1) throw new DuplicateKeyException(SCField.PORT_TCP.key);
                        try { portTCP = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + SCField.PORT_TCP.key + "\" must be an integer"); }
                        break;
                    case PORT_UDP:
                        if(portUDP != -1) throw new DuplicateKeyException(SCField.PORT_UDP.key);
                        try { portUDP = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + SCField.PORT_UDP.key + "\" must be an integer"); }
                        break;
                    case MCAST_ADDR:
                        if(multicastAddr != null) throw new DuplicateKeyException(SCField.MCAST_ADDR.key);
                        multicastAddr = entry.value;
                        break;
                    case MCAST_PORT:
                        if(multicastPort != -1) throw new DuplicateKeyException(SCField.MCAST_PORT.key);
                        try { multicastPort = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + SCField.MCAST_PORT.key + "\" must be an integer"); }
                        break;
                    case REG_HOST:
                        if(regHost != null) throw new DuplicateKeyException(SCField.REG_HOST.key);
                        regHost = entry.value;
                        break;
                    case REG_PORT:
                        if(regPort != -1) throw new DuplicateKeyException(SCField.REG_PORT.key);
                        try { regPort = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + SCField.REG_PORT.key + "\" must be an integer"); }
                        break;
                    case SOCK_TIMEOUT:
                        if(sockTimeout != -1) throw new DuplicateKeyException(SCField.SOCK_TIMEOUT.key);
                        try { sockTimeout = Long.parseLong(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + SCField.SOCK_TIMEOUT.key + "\" must be an integer"); }
                        break;
                    case REWARD_INT:
                        if(rewardInterval != -1) throw new DuplicateKeyException(SCField.REWARD_INT.key);
                        try { rewardInterval = Long.parseLong(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + SCField.REWARD_INT.key + "\" must be an integer"); }
                        break;
                    case REWARD_PERC:
                        if(percentage != null) throw new DuplicateKeyException(SCField.REWARD_PERC.key);
                        try { 
                            double authorReward = Double.parseDouble(entry.value);
                            percentage = RewardsPercentage.fromAuthor(authorReward / 100);
                        }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + SCField.REWARD_PERC.key + "\" must be a double between 0 and 100"); }
                        break;
                    case PERSIST_DIR:
                        if(persistenceDir != null) throw new DuplicateKeyException(SCField.PERSIST_DIR.key);
                        persistenceDir = entry.value;
                        break;
                    case MIN_THREADS:
                        if(minThreads != -1) throw new DuplicateKeyException(SCField.MIN_THREADS.key);
                        try { minThreads = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + SCField.MIN_THREADS.key + "\" must be an integer"); }
                        break;
                    case MAX_THREADS:
                        if(maxThreads != -1) throw new DuplicateKeyException(SCField.MAX_THREADS.key);
                        try { maxThreads = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + SCField.MAX_THREADS.key + "\" must be an integer"); }
                        break;
                }
            }
        } catch (IOException ex) { throw new IOException("IO error while reading config file", ex); }

        checkEmptyFields();
    }

    public String getServerAddr(){ return this.serverAddr; }
    public int getTCPPort(){ return this.portTCP; }
    public int getUDPPort(){ return this.portUDP; }
    public String getMulticastAddr(){ return this.multicastAddr; }
    public int getMulticastPort(){ return this.multicastPort; }
    public String getRegHost(){ return this.regHost; }
    public int getRegPort(){ return this.regPort; }
    public long getSockTimeout(){ return this.sockTimeout; }
    public long getRewardInterval(){ return this.rewardInterval; }
    public RewardsPercentage getRewardPerc(){ return this.percentage; }
    public String getPersistenceDir(){ return this.persistenceDir; }
    public int getMinThreads(){ return this.minThreads; }
    public int getMaxThreads(){ return this.maxThreads; }

    private void checkEmptyFields() throws KeyNotSetException {
        if(serverAddr == null) throw new KeyNotSetException(SCField.SERVER_ADDR.key);
        if(portTCP == -1) throw new KeyNotSetException(SCField.PORT_TCP.key);
        if(portUDP == -1) throw new KeyNotSetException(SCField.PORT_UDP.key);
        if(multicastAddr == null) throw new KeyNotSetException(SCField.MCAST_ADDR.key);
        if(multicastPort == -1) throw new KeyNotSetException(SCField.MCAST_PORT.key);
        if(regHost == null) throw new KeyNotSetException(SCField.REG_HOST.key);
        if(regPort == -1) throw new KeyNotSetException(SCField.REG_PORT.key);
        if(sockTimeout == -1) throw new KeyNotSetException(SCField.SOCK_TIMEOUT.key);
        if(rewardInterval == -1) throw new KeyNotSetException(SCField.REWARD_INT.key);
        if(percentage == null) throw new KeyNotSetException(SCField.REWARD_PERC.key);
        if(persistenceDir == null) throw new KeyNotSetException(SCField.PERSIST_DIR.key);
    }
}