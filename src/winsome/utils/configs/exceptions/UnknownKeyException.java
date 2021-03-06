package winsome.utils.configs.exceptions;

/** Thrown whenever there is an unknown key in the config file */
public class UnknownKeyException extends InvalidConfigFileException {
    public UnknownKeyException(){ super(); }
    public UnknownKeyException(String key){ super(UnknownKeyException.msg(key)); }
    public UnknownKeyException(Throwable err){ super(err); }
    public UnknownKeyException(String key, Throwable err){ super(UnknownKeyException.msg(key), err); }
    
    private static String msg(String key){ return "key \"" + key + "\" is not a known key"; }
}
