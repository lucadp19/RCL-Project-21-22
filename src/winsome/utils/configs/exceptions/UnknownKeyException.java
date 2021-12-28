package winsome.utils.configs.exceptions;

public class UnknownKeyException extends RuntimeException {
    public UnknownKeyException(){ super(); }
    public UnknownKeyException(String key){ super(UnknownKeyException.msg(key)); }
    public UnknownKeyException(Throwable err){ super(err); }
    public UnknownKeyException(String key, Throwable err){ super(UnknownKeyException.msg(key), err); }
    
    private static String msg(String key){ return "key \"" + key + "\" is not a known key"; }
}
