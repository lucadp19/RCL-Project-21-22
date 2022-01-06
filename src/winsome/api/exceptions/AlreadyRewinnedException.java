package winsome.api.exceptions;

public class AlreadyRewinnedException extends Exception {
    public AlreadyRewinnedException(){ super(); }
    public AlreadyRewinnedException(String msg){ super(msg); }
    public AlreadyRewinnedException(Throwable err){ super(err); }
    public AlreadyRewinnedException(String msg, Throwable err){ super(msg, err); }
}