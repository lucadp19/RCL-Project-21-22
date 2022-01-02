package winsome.api.exceptions;

public class WrongPasswordException extends Exception {
    public WrongPasswordException(){ super(); }
    public WrongPasswordException(String msg){ super(msg); }
    public WrongPasswordException(Throwable err){ super(err); }
    public WrongPasswordException(String msg, Throwable err){ super(msg, err); }
}