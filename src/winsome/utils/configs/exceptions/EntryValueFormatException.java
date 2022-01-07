package winsome.utils.configs.exceptions;

public class EntryValueFormatException extends InvalidConfigFileException {
    public EntryValueFormatException(){ super(); }
    public EntryValueFormatException(String msg){ super(msg); }
    public EntryValueFormatException(Throwable err){ super(err); }
    public EntryValueFormatException(String msg, Throwable err){ super(msg, err); }
}
