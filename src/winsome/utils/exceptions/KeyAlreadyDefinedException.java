package winsome.utils.exceptions;

public class KeyAlreadyDefinedException extends RuntimeException {
    public KeyAlreadyDefinedException(){ super(); }
    public KeyAlreadyDefinedException(String msg){ super(msg); }
    public KeyAlreadyDefinedException(String msg, Throwable err){ super(msg, err); }
}
