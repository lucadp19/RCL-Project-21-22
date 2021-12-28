package winsome.utils.configs.exceptions;

public class MalformedEntryException extends RuntimeException {
    public MalformedEntryException(){ super(); }
    public MalformedEntryException(String msg){ super(msg); }
    public MalformedEntryException(Throwable err){ super(err); }
    public MalformedEntryException(String msg, Throwable err){ super(msg, err); }
}
