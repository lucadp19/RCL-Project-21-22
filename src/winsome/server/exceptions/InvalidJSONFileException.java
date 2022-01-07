package winsome.server.exceptions;

/** An exception for invalid JSON files. */
public class InvalidJSONFileException extends Exception {
    public InvalidJSONFileException(){ super(); }
    public InvalidJSONFileException(String msg){ super(msg); }
    public InvalidJSONFileException(Throwable err){ super(err); }
    public InvalidJSONFileException(String msg, Throwable err){ super(msg, err); }
}
