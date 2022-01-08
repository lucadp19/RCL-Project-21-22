package winsome.utils.configs;

/** An entry in a config file. */
public class ConfigEntry {
    public final String key;
    public final String value;

    public ConfigEntry(String key, String value){
        this.key = key; this.value = value;
    }
}
