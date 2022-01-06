package winsome.api.exceptions;

public class SelfFollowException extends Exception {
    public SelfFollowException(){ super(); }
    public SelfFollowException(String msg){ super(msg); }
    public SelfFollowException(Throwable err){ super(err); }
    public SelfFollowException(String msg, Throwable err){ super(msg, err); }
}
