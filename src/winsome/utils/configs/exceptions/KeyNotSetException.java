package winsome.utils.configs.exceptions;

public class KeyNotSetException extends RuntimeException {
    public KeyNotSetException(){ super(); }
    public KeyNotSetException(String key){ super(KeyNotSetException.msg(key)); }
    public KeyNotSetException(Throwable err){ super(err); }
    public KeyNotSetException(String key, Throwable err){ super(KeyNotSetException.msg(key), err); }
    
    private static String msg(String key){ return "key \"" + key + "\" has not been initialized"; }

}
