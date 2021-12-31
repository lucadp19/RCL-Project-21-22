package winsome.server.datastructs;

import java.security.Timestamp;
import java.time.Instant;

import com.google.gson.JsonObject;

/** A Transaction created by the Rewards Algorithm */
public class Transaction {
    /** The user involved by this transaction */
    public final String user;
    /** The increment in WinCoins */
    public final double increment;
    /** The timestamp at which this transaction was created */
    public final Instant timestamp;
    
    /**
     * Creates a new transaction.
     * @param user the user involved
     * @param increment the increment in WinCoins
     */
    public Transaction(String user, double increment){
        if(user == null) throw new NullPointerException("null user");

        this.user = user;
        this.increment = increment;
        this.timestamp = Instant.now();
    }

    /**
     * Creates a transaction object with a given timestamp.
     * <p>
     * Useful for deserialization.
     * @param user the user involved
     * @param increment the increment in WinCoins
     * @param timestamp the timestamp of the transaction
     */
    private Transaction(String user, double increment, Instant timestamp) {
        if(user == null || timestamp == null) throw new NullPointerException("null argument");

        this.user = user;
        this.increment = increment;
        this.timestamp = timestamp;
    }

    /**
     * Serializes this transaction into a JsonObject.
     * @return a JsonObject representing this transaction
     */
    public JsonObject toJson(){
        JsonObject json = new JsonObject();
        json.addProperty("user", user);
        json.addProperty("increment", increment);
        json.addProperty("timestamp", timestamp.toString());

        return json;
    }

    /**
     * Deserializes a JsonObject representing a transaction.
     * @param json the given json
     * @return the transaction obtained from the given json
     * @throws IllegalArgumentException if the given json does not represent a valid Transaction
     */
    public static Transaction fromJson(JsonObject json) throws IllegalArgumentException {
        if(json == null) throw new NullPointerException("null parameter");

        try {
            String user = json.get("user").getAsString();
            double increment = json.get("increment").getAsDouble();
            Instant timestamp = Instant.parse(json.get("timestamp").getAsString());

            return new Transaction(user, increment, timestamp);
        } catch (ClassCastException | IllegalStateException | NullPointerException ex){
            throw new IllegalArgumentException("parameter does not represent a valid OriginalPost", ex);
        }
    }
}
