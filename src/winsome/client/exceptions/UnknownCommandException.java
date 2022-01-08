package winsome.client.exceptions;

/** Signals that the given command in the Winsome Client CLI is unknown. */
public class UnknownCommandException extends Exception {
    public UnknownCommandException(){ super(); }
    public UnknownCommandException(String msg){ super(msg); }
    public UnknownCommandException(Throwable err){ super(err); }
    public UnknownCommandException(String msg, Throwable err){ super(msg, err); }   
}
