package winsome.utils.configs;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Objects;
import java.util.Optional;

import winsome.utils.configs.exceptions.MalformedEntryException;

/** A utility abstract class containing static methods to parse config files. */
public abstract class AbstractConfig {
    /**
     * Checks that the file at the given path exists and is regular.
     * @param configPath the given path
     * @return the file object
     * @throws FileNotFoundException if no file at the given path exists, or if the file is not a regular file
     */
    public static File getConfigFile(String configPath) throws FileNotFoundException {
        Objects.requireNonNull(configPath, "path to config file must not be null");

        File configFile = new File(configPath);
        if(!configFile.exists()) throw new FileNotFoundException("config file does not exist");
        if(!configFile.isFile()) throw new FileNotFoundException("config file is not a regular file");

        return configFile;
    }

    /**
     * Parses a line in the config file.
     * @param line the given line
     * @return the parsed entry, if present, or {@link java.util.Optional#empty()} if the line is empty or is a comment
     * @throws MalformedEntryException if the line is not a valid entry
     */
    public static Optional<ConfigEntry> readEntry(String line) throws MalformedEntryException {
        Objects.requireNonNull(line, "the config line to parse must not be null");
        
        String trimmed = line.trim();
        if(trimmed.startsWith("#") || trimmed.isEmpty()) return Optional.empty();

        String[] sep = trimmed.split(":");
        if(sep.length != 2) throw new MalformedEntryException();
        return Optional.of(
            new ConfigEntry(
                sep[0].trim(),                  // trimmed key
                sep[1].split("#")[0].trim()     // trimmed value, but removing anything that follows a '#'
            )
        );
    }
}
