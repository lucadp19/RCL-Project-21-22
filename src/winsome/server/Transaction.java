package winsome.server;

import java.time.Instant;

public class Transaction {
    public final String user;
    public final int increment;
    public final Instant timestamp;
    
    public Transaction(String user, int increment){
        this.user = user;
        this.increment = increment;
        this.timestamp = Instant.now();
    }
}
