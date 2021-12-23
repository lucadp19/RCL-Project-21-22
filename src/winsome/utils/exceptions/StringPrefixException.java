package winsome.utils.exceptions;

public class StringPrefixException extends IllegalArgumentException {
    public StringPrefixException(){ super(); }
    public StringPrefixException(String msg){ super(msg); }
    public StringPrefixException(String msg, Throwable err){ super(msg, err); }
}
