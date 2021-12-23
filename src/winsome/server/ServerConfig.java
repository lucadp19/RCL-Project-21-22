package winsome.server;

import java.io.*;
import winsome.utils.exceptions.*;

public class ServerConfig {
    private String serverAddr = null;
    private int portTCP = -1;
    private int portUDP = -1;
    private String multicastAddr = null;
    private int multicastPort = -1;
    private String regHost = null;
    private int regPort = -1;
    private long sockTimeout = -1;

    private long rewardInterval = -1;
    private int authorRewardPerc = -1;

    public ServerConfig(final String configPath) throws 
            NullPointerException, FileNotFoundException, KeyAlreadyDefinedException, 
            NumberFormatException, IOException 
    {
        if(configPath == null) throw new NullPointerException("configPath must not be null");
        
        File configFile = new File(configPath);
        if(!configFile.exists()) throw new FileNotFoundException("config file does not exist");
        if(!configFile.isFile()) throw new FileNotFoundException("config file is not a regular file");

        try (
            BufferedReader configIn = new BufferedReader(new FileReader(configFile));
        ) {
            String line;
            while((line = configIn.readLine()) != null){
                if(line.isBlank()) continue;
                if(line.charAt(0) == '#') continue;

                try {
                    String trimmed = trimPrefix(line, "SERVER=");
                    if(serverAddr == null){
                        serverAddr = trimmed;
                        continue;
                    } else throw new KeyAlreadyDefinedException("key SERVER was already defined");
                } catch (StringPrefixException ex) { }

                try {
                    String trimmed = trimPrefix(line, "TCPPORT=");
                    if(portTCP == -1){
                        portTCP = Integer.parseInt(trimmed);
                        continue;
                    } else throw new KeyAlreadyDefinedException("key TCPPORT was already defined");
                } catch (StringPrefixException ex) { }
                catch (NumberFormatException ex) { throw new NumberFormatException("argument of TCPPORT must be an integer"); }

                try {
                    String trimmed = trimPrefix(line, "UDPPORT=");
                    if(portUDP == -1){
                        portUDP = Integer.parseInt(trimmed);
                        continue;
                    } else throw new KeyAlreadyDefinedException("key UDPPORT was already defined");
                } catch (StringPrefixException ex) { }
                catch (NumberFormatException ex) { throw new NumberFormatException("argument of UDPPORT must be an integer"); }

                try {
                    String trimmed = trimPrefix(line, "MULTICAST=");
                    if(multicastAddr == null){
                        multicastAddr = trimmed;
                        continue;
                    } else throw new KeyAlreadyDefinedException("key MULTICAST was already defined");
                } catch (StringPrefixException ex) { }

                try {
                    String trimmed = trimPrefix(line, "MCASTPORT=");
                    if(multicastPort == -1){
                        multicastPort = Integer.parseInt(trimmed);
                        continue;
                    } else throw new KeyAlreadyDefinedException("key MCASTPORT was already defined");
                } catch (StringPrefixException ex) { }
                catch (NumberFormatException ex) { throw new NumberFormatException("argument of MCASTPORT must be an integer"); }

                try {
                    String trimmed = trimPrefix(line, "REGHOST=");
                    if(regHost == null){
                        regHost = trimmed;
                        continue;
                    } else throw new KeyAlreadyDefinedException("key REGHOST was already defined");
                } catch (StringPrefixException ex) { }
               
                try {
                    String trimmed = trimPrefix(line, "REGPORT=");
                    if(regPort == -1){
                        regPort = Integer.parseInt(trimmed);
                        continue;
                    } else throw new KeyAlreadyDefinedException("key REGPORT was already defined");
                } catch (StringPrefixException ex) { }
                catch (NumberFormatException ex) { throw new NumberFormatException("argument of REGPORT must be an integer"); }

                try {
                    String trimmed = trimPrefix(line, "TIMEOUT=");
                    if(sockTimeout == -1){
                        sockTimeout = Integer.parseInt(trimmed);
                        continue;
                    } else throw new KeyAlreadyDefinedException("key TIMEOUT was already defined");
                } catch (StringPrefixException ex) { }
                catch (NumberFormatException ex) { throw new NumberFormatException("argument of TIMEOUT must be an integer"); }

                try {
                    String trimmed = trimPrefix(line, "REWARDINTERVAL=");
                    if(rewardInterval == -1){
                        rewardInterval = Integer.parseInt(trimmed);
                        continue;
                    } else throw new KeyAlreadyDefinedException("key REWARDINTERVAL was already defined");
                } catch (StringPrefixException ex) { }
                catch (NumberFormatException ex) { throw new NumberFormatException("argument of REWARDINTERVAL must be an integer"); }                

                try {
                    String trimmed = trimPrefix(line, "REWARDPERC=");
                    if(authorRewardPerc == -1){
                        authorRewardPerc = Integer.parseInt(trimmed);
                        continue;
                    } else throw new KeyAlreadyDefinedException("key REWARDPERC was already defined");
                } catch (StringPrefixException ex) { }
                catch (NumberFormatException ex) { throw new NumberFormatException("argument of REWARDPERC must be an integer"); }                
            }
        } catch (IOException ex) { throw new IOException("IO error while reading config file", ex); }
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
    public int getRewardPerc(){ return this.authorRewardPerc; }

    private static String trimPrefix(String str, String prefix) {
        if(str == null | prefix == null) throw new NullPointerException();
        if(!str.startsWith(prefix)) throw new StringPrefixException();
        return str.substring(prefix.length());
    }
}