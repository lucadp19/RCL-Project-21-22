package winsome.api.exceptions;

public class TextLengthException extends Exception {
    public TextLengthException(){ super(); }
    public TextLengthException(String msg){ super(msg); }
    public TextLengthException(Throwable err){ super(err); }
    public TextLengthException(String msg, Throwable err){ super(msg, err); }
}
