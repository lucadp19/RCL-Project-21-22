package winsome.server.datastructs;

import java.io.IOException;
import java.security.Timestamp;
import java.time.Instant;

import com.google.gson.JsonObject;
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
    
    public void toJson(JsonWriter writer) throws IOException {
        if(writer == null) throw new NullPointerException("null parameter");

        writer.beginObject();
        writer.name("user").value(this.user);
        writer.name("increment").value(this.increment);
        writer.name("instant").value(this.timestamp.toString());
        writer.endObject();
    }

    /**
     * Deserializes a JSON File representing a transaction through a JsonReader
     * @param reader the given json reader
     * @return the transaction obtained from the given json
     * @throws InvalidJSONFileException if the given json reader does not read a valid Transaction
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
                    case "user":
                        user = reader.nextString();
                        break;
                    case "increment":
                        increment = reader.nextDouble();
                        break;
                    case "timestamp":
                        timestamp = Instant.parse(reader.nextString());
                        break;
                    default:
                        throw new InvalidJSONFileException("parse error in json file");
                }
            }
            reader.endObject();

            return new Transaction(user, increment, timestamp);
        } catch (ClassCastException | IllegalStateException | NullPointerException | NumberFormatException ex){
            throw new InvalidJSONFileException("parameter does not represent a valid OriginalPost", ex);
        }
    }
}
