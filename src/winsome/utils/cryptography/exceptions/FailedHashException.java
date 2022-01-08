package winsome.utils.cryptography.exceptions;

/** Thrown when the hashing procedure fails. */
public class FailedHashException extends RuntimeException {
    public FailedHashException(){ super(); }
    public FailedHashException(String msg){ super(msg); }
    public FailedHashException(Throwable err){ super(err); }
    public FailedHashException(String msg, Throwable err){ super(msg, err); }
}
