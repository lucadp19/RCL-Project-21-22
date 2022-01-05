package winsome.api.exceptions;

public class ExchangeRateException extends Exception {
    public ExchangeRateException(){ super(); }
    public ExchangeRateException(String msg){ super(msg); }
    public ExchangeRateException(Throwable err){ super(err); }
    public ExchangeRateException(String msg, Throwable err){ super(msg, err); }
}
