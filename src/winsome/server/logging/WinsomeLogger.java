package winsome.server.logging;

import java.io.PrintWriter;
import java.io.StringWriter;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.util.Optional;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class WinsomeLogger  {
    public static Formatter getCustomFormatter(){
        return new Formatter() {
            @Override
            public String format(LogRecord record){
                ZonedDateTime dateTime = ZonedDateTime.ofInstant(record.getInstant(), ZoneId.systemDefault());

                long threadID = record.getLongThreadID();
                String threadName = getThread(threadID)
                    .map(Thread::getName)
                    .orElse("Thread-" + threadID);

                String formatStr = "%1$tF %1$tT %2$-7s [%3$s] %4$s.%5$s: %6$s %n%7$s";
                return String.format(formatStr, 
                    dateTime,
                    record.getLevel().getName(),
                    threadName,
                    record.getSourceClassName(),
                    record.getSourceMethodName(),
                    record.getMessage(),
                    stackTraceToString(record)
                );
            }
        };
    }

    private static Optional<Thread> getThread(long threadID){
        return Thread.getAllStackTraces().keySet().stream()
            .filter(thread -> thread.getId() == threadID)
            .findFirst();
    }

    private static String stackTraceToString(LogRecord record){
        if(record.getThrown() != null){
            StringWriter strWriter = new StringWriter();
            PrintWriter writer = new PrintWriter(strWriter);

            writer.println();
            record.getThrown().printStackTrace(writer);
            writer.close();

            return strWriter.toString();
        }

        return "";
    }
}
