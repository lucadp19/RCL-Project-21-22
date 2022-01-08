package winsome.utils.configs.exceptions;

/** Thrown whenever a key was defined multiple times in the config file */
public class DuplicateKeyException extends InvalidConfigFileException {
    public DuplicateKeyException(){ super(); }
    public DuplicateKeyException(String key){ super(DuplicateKeyException.msg(key)); }
    public DuplicateKeyException(Throwable err){ super(err); }
    public DuplicateKeyException(String key, Throwable err){ super(DuplicateKeyException.msg(key), err); }
    
    private static String msg(String key){ return "key \"" + key + "\" was defined multiple times"; }
}
