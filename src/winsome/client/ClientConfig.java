package winsome.client;

import java.io.*;
import java.util.Optional;

import winsome.utils.configs.AbstractConfig;
import winsome.utils.configs.ConfigEntry;
import winsome.utils.configs.exceptions.*;

public class ClientConfig extends AbstractConfig {
    private enum CCField {
        SERVER_ADDR     ("server-addr"),
        PORT_TCP        ("tcp-port"),
        PORT_UDP        ("udp-port"),
        MCAST_ADDR      ("multicast-addr"),
        MCAST_PORT      ("multicast-port"),
        REG_HOST        ("registry-host"),
        REG_PORT        ("registry-port"),
        SOCK_TIMEOUT    ("socket-timeout");

        public final String key;
        CCField(String key){ this.key = key; }
        
        static CCField fromKey(String key){
            switch (key) {
                case "server-addr":         return SERVER_ADDR;
                case "tcp-port":            return PORT_TCP;
                case "udp-port":            return PORT_UDP;
                case "multicast-addr":      return MCAST_ADDR;
                case "multicast-port":      return MCAST_PORT;
                case "registry-host":       return REG_HOST;
                case "registry-port":       return REG_PORT;
                case "socket-timeout":      return SOCK_TIMEOUT;
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

    public ClientConfig(final String configPath) throws 
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
                if(maybeEntry.isEmpty()) continue;

                ConfigEntry entry = maybeEntry.get();

                CCField key = CCField.fromKey(entry.key);

                switch (key) {
                    case SERVER_ADDR:
                        if(serverAddr != null) throw new DuplicateKeyException(CCField.SERVER_ADDR.key);
                        serverAddr = entry.value;
                        break;
                    case PORT_TCP:
                        if(portTCP != -1) throw new DuplicateKeyException(CCField.PORT_TCP.key);
                        try { portTCP = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + CCField.PORT_TCP.key + "\" must be an integer"); }
                        break;
                    case PORT_UDP:
                        if(portUDP != -1) throw new DuplicateKeyException(CCField.PORT_UDP.key);
                        try { portUDP = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + CCField.PORT_UDP.key + "\" must be an integer"); }
                        break;
                    case MCAST_ADDR:
                        if(multicastAddr != null) throw new DuplicateKeyException(CCField.MCAST_ADDR.key);
                        multicastAddr = entry.value;
                        break;
                    case MCAST_PORT:
                        if(multicastPort != -1) throw new DuplicateKeyException(CCField.MCAST_PORT.key);
                        try { multicastPort = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + CCField.MCAST_PORT.key + "\" must be an integer"); }
                        break;
                    case REG_HOST:
                        if(regHost != null) throw new DuplicateKeyException(CCField.REG_HOST.key);
                        regHost = entry.value;
                        break;
                    case REG_PORT:
                        if(regPort != -1) throw new DuplicateKeyException(CCField.REG_PORT.key);
                        try { regPort = Integer.parseInt(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + CCField.REG_PORT.key + "\" must be an integer"); }
                        break;
                    case SOCK_TIMEOUT:
                        if(sockTimeout != -1) throw new DuplicateKeyException(CCField.SOCK_TIMEOUT.key);
                        try { sockTimeout = Long.parseLong(entry.value); }
                        catch(NumberFormatException ex){ throw new NumberFormatException("argument of \"" + CCField.SOCK_TIMEOUT.key + "\" must be an integer"); }
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

    private void checkEmptyFields() throws KeyNotSetException {
        if(serverAddr == null) throw new KeyNotSetException(CCField.SERVER_ADDR.key);
        if(portTCP == -1) throw new KeyNotSetException(CCField.PORT_TCP.key);
        if(portUDP == -1) throw new KeyNotSetException(CCField.PORT_UDP.key);
        if(multicastAddr == null) throw new KeyNotSetException(CCField.MCAST_ADDR.key);
        if(multicastPort == -1) throw new KeyNotSetException(CCField.MCAST_PORT.key);
        if(regHost == null) throw new KeyNotSetException(CCField.REG_HOST.key);
        if(regPort == -1) throw new KeyNotSetException(CCField.REG_PORT.key);
        if(sockTimeout == -1) throw new KeyNotSetException(CCField.SOCK_TIMEOUT.key);
    }
}