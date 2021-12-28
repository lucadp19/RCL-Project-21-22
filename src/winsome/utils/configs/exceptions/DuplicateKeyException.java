package winsome.utils.configs.exceptions;

public class DuplicateKeyException extends RuntimeException {
    public DuplicateKeyException(){ super(); }
    public DuplicateKeyException(String key){ super(DuplicateKeyException.msg(key)); }
    public DuplicateKeyException(Throwable err){ super(err); }
    public DuplicateKeyException(String key, Throwable err){ super(DuplicateKeyException.msg(key), err); }
    
    private static String msg(String key){ return "key \"" + key + "\" was defined multiple times"; }
}
