package winsome.server.datastructs;

import java.io.IOException;
import java.time.Instant;
import java.util.Objects;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import winsome.server.exceptions.InvalidJSONFileException;

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
     * Serializes this transaction through a JSON stream.
     * @param writer the given JSON stream
     * @throws IOException if some IO error occurs
     */
    public void toJson(JsonWriter writer) throws IOException {
        Objects.requireNonNull(writer, "json writer must not be null")
            .beginObject()
            .name("user").value(this.user)
            .name("increment").value(this.increment)
            .name("timestamp").value(this.timestamp.toString())
            .endObject();
    }

    /**
     * Deserializes a JSON File representing a transaction through a JSON stream.
     * @param reader the given JSON stream
     * @return the transaction obtained from the given JSON
     * @throws InvalidJSONFileException if the given JSON stream does not read a valid Transaction
     * @throws IOException whenever there is an IO error
     */
    public static Transaction fromJson(JsonReader reader) throws InvalidJSONFileException, IOException {
        if(reader == null) throw new NullPointerException("null parameter");

        try {
            String user = null;
            Double increment = null;
            Instant timestamp = null;
            
            reader.beginObject();
            while(reader.hasNext()){
                String property = reader.nextName();

                switch (property) {
                    case "user"      -> user = reader.nextString();
                    case "increment" -> increment = reader.nextDouble();
                    case "timestamp" -> timestamp = Instant.parse(reader.nextString());
                    default -> throw new InvalidJSONFileException("parse error in json file");
                }
            }
            reader.endObject();

            return new Transaction(user, increment, timestamp);
        } catch (ClassCastException | IllegalStateException | NullPointerException | NumberFormatException ex){
            throw new InvalidJSONFileException("parameter does not represent a valid Transaction", ex);
        }
    }
}
