package winsome.utils.configs;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Optional;

import winsome.utils.configs.exceptions.MalformedEntryException;

public abstract class AbstractConfig {
    public static File getConfigFile(String configPath) throws NullPointerException, FileNotFoundException {
        if(configPath == null) throw new NullPointerException("configPath must not be null");

        File configFile = new File(configPath);
        if(!configFile.exists()) throw new FileNotFoundException("config file does not exist");
        if(!configFile.isFile()) throw new FileNotFoundException("config file is not a regular file");

        return configFile;
    }

    public static Optional<ConfigEntry> readEntry(String line) throws MalformedEntryException {
        if(line == null) throw new NullPointerException();
        
        String trimmed = line.trim();
        if(trimmed.startsWith("#") || trimmed.isEmpty()) return Optional.empty();

        String[] sep = trimmed.split(":");
        if(sep.length != 2) throw new MalformedEntryException();
        return Optional.of(
            new ConfigEntry(
                sep[0].trim(),                  // trimmed key
                sep[1].split("#")[0].trim()     // trimmed value, but removing anything that follows a '#'
                ));
    }
}
