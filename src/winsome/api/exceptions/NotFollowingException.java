package winsome.api.exceptions;

public class NotFollowingException extends Exception {
    public NotFollowingException(){ super(); }
    public NotFollowingException(String msg){ super(msg); }
    public NotFollowingException(Throwable err){ super(err); }
    public NotFollowingException(String msg, Throwable err){ super(msg, err); }
}
