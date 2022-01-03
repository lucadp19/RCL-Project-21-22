package winsome.client;

import java.io.*;
import java.rmi.RemoteException;
import java.util.*;
import java.util.Map.*;

import winsome.api.WinsomeAPI;
import winsome.api.exceptions.FollowException;
import winsome.api.exceptions.MalformedJSONException;
import winsome.api.exceptions.NoLoggedUserException;
import winsome.api.exceptions.NoSuchUserException;
import winsome.api.exceptions.NotImplementedException;
import winsome.api.exceptions.UserAlreadyExistsException;
import winsome.api.exceptions.UserAlreadyLoggedException;
import winsome.api.exceptions.WrongPasswordException;
import winsome.client.ClientConfig;
import winsome.client.exceptions.UnknownCommandException;
import winsome.utils.ConsoleColors;

public class WinsomeClientMain {
    private static String DEFAULT_CONFIG_PATH = "./configs/client-config.yaml";

    public static void main(String[] args) {
        System.out.println(ConsoleColors.green("\t\tWinsome Social Network\n"));

        System.out.println("Welcome to the Winsome Social Network!\n");

        ClientConfig config = null;
        WinsomeAPI api = null;
        try {
            System.out.println(ConsoleColors.blue("-> ") + "Reading config...");
            config = new ClientConfig(DEFAULT_CONFIG_PATH); // TODO: catch exceptions
            System.out.println(ConsoleColors.blue("==> Config read!"));

            // creating API interface
            System.out.println(ConsoleColors.blue("-> ") + "Connecting to server...");
            api = new WinsomeAPI(
                config.getServerAddr(),
                config.getTCPPort(),
                config.getRegHost(),
                config.getRegPort()
            );
            api.connect(); 
            System.out.println(ConsoleColors.blue("==> Connected!"));
        } catch(Exception ex){ 
            ex.printStackTrace(); 
            System.exit(1);
        }

        System.out.println("\nType a command (or " + ConsoleColors.green("help") + " to print the usage message)...\n");

        try (
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        ){
            String line;

            System.out.print(ConsoleColors.green("-> "));
            while((line = input.readLine().trim()) != null){
                if(line.isEmpty()) continue;
                if(line.startsWith("quit")) {
                    // maybe print message or create termination handler
                    System.exit(0);
                }

                // test command; TODO: eliminate it
                if(line.startsWith("echo")){
                    String msg = line.substring(4).trim();
                    
                    String ans = api.echoMsg(msg);
                    System.out.println(ConsoleColors.blue("--> Echoed message: ") + ans);
                    
                } else
                executeCommand(api, line);
                
                System.out.print(ConsoleColors.green("\n-> "));
            }
        } catch(IOException ex){ ex.printStackTrace(); }
    }

    private static void executeCommand(WinsomeAPI api, String line){
        Command cmd = null;

        try { cmd = Command.fromString(line); }
        catch(UnknownCommandException ex){
            System.out.println(ConsoleColors.red("==> ERROR! ") + ex.getMessage() + "\n");
            System.out.print(Command.help());
            return;
        }

        String argStr = line.substring(cmd.name.length()).trim();

        switch (cmd) {
            case HELP: {
                Command cmd1 = null;
                try { 
                    cmd1 = Command.fromString(argStr); 
                    System.out.println("\n" + cmd1.getHelpString());
                } catch(UnknownCommandException ex){ System.out.println("\n" + Command.help()); }
                break;
            }
            case REGISTER: {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length < 3 || args.length > 7){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Signing up " + ConsoleColors.blue(args[0]) + "...");
                Set<String> tags = new HashSet<>(args.length - 2);
                for(int i = 0; i < args.length - 2; i++) tags.add(args[i+2]);

                try { api.register(args[0], args[1], tags); }
                catch (NullPointerException ex){
                    System.err.println(ConsoleColors.red("==> Internal error: ") + ex.getMessage());
                    return;
                }
                catch (UserAlreadyLoggedException ex){
                    System.err.println(ConsoleColors.red("==> Error: ") + ex.getMessage());
                    return;
                }
                catch (UserAlreadyExistsException ex){
                    System.err.println(ConsoleColors.red("==> Error: ") + "this username is not available");
                    return;
                }
                catch (RemoteException ex){
                    System.err.println(ConsoleColors.red("==> Communication error!"));
                    return;
                }

                System.out.println(
                    ConsoleColors.blue("==> SUCCESS: ") +
                    ConsoleColors.blue(args[0]) + " has been registered in the Social Network!");
                break;
            }
            case LOGIN: {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 2){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Logging in as " + ConsoleColors.blue(args[0]) + "...");

                try { api.login(args[0], args[1]); }
                catch (IOException ex){
                    System.out.println(ConsoleColors.red("==> Fatal error in server communication"));
                    return;
                } 
                catch (MalformedJSONException ex){
                    System.out.println(ConsoleColors.red("==> Error! ") + "Server sent malformed response message.");
                    return;
                }
                catch (UserAlreadyLoggedException ex){
                    System.out.println(ConsoleColors.red("==> Error! User is already logged: ") + ex.getMessage());
                    return;
                }
                catch (NoSuchUserException ex){
                    System.out.println(ConsoleColors.red("==> Error! No such user exists: ") + ex.getMessage());
                    return;
                }
                catch (WrongPasswordException ex){
                    System.out.println(ConsoleColors.red("==> Error! Wrong password: ") + ex.getMessage());
                    return;
                }
                catch (IllegalStateException ex){
                    System.out.println(ConsoleColors.red("==> Unexpected error from server: ") + ex.getMessage());
                }

                System.out.println(
                    ConsoleColors.blue("==> SUCCESS: ") + "you are now logged in as " + 
                    ConsoleColors.blue(args[0]) + "!");
                break;
            }

            case LOGOUT: {
                // checking argument number
                if(!argStr.isEmpty()){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Attempting to log out...");
                try { api.logout(); }
                catch (IOException ex){
                    System.out.println(ConsoleColors.red("==> Fatal error in server communication"));
                    return;
                }
                catch (MalformedJSONException ex){
                    System.out.println(ConsoleColors.red("==> Error! ") + "Server sent malformed response message.");
                    return;
                }
                catch (NoLoggedUserException ex){
                    System.out.println(ConsoleColors.red("==> Error! ") + "No user is currently logged: please log in.");
                    return;
                }
                catch (IllegalStateException ex){
                    System.out.println(ConsoleColors.red("==> Unexpected error from server: ") + ex.getMessage());
                    return;
                }
                System.out.println(ConsoleColors.blue("==> SUCCESS!"));
                break;
            }

            case LIST_USERS: {
                // checking argument number
                if(!argStr.isEmpty()){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Getting users...");
                Map<String, List<String>> users;
                try { users = api.listUsers(); }
                catch(IOException ex){
                    System.out.println(ConsoleColors.red("==> Fatal error in server communication"));
                    return;
                }
                catch(MalformedJSONException ex){
                    System.out.println(ConsoleColors.red("==> Error! ") + "Server sent malformed response message.");
                    return;
                }
                catch(NoLoggedUserException ex){
                    System.out.println(ConsoleColors.red("==> Error! ") + "No user is currently logged: please log in first.");
                    return;
                }
                catch(IllegalStateException ex){
                    System.out.println(ConsoleColors.red("==> Unexpected error from server: ") + ex.getMessage());
                    return;
                }

                String msg;
                if(users.size() == 0)
                    msg = "Currently no user shares interests with you.";
                else msg = "Currently the following " + users.size() + " user" +
                    (users.size() == 1 ? " " : "s ") +
                    "share interests with you:\n";

                System.out.println(ConsoleColors.blue("==> SUCCESS! ") + msg);
                if(users.size() > 0) printUsers(users);
                break;
            }

            case LIST_FOLLOWERS: {
                // checking argument number
                if(!argStr.isEmpty()){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }
                
                Map<String, List<String>> followers;  
                System.out.println(ConsoleColors.blue("-> ") + "Listing followers...");
                try { followers = api.listFollowers(); }
                catch(NoLoggedUserException ex){
                    System.out.println(ConsoleColors.red("==> Error! ") + "No user is currently logged: please log in.");
                    return;
                }

                System.out.println(
                    ConsoleColors.blue("==> SUCCESS! ") + 
                    "You are currently being followed by " + followers.size() + " user"
                    + (followers.size() == 1 ? ".\n" : "s.\n")
                );
                printUsers(followers);
                break;
            }

            case LIST_FOLLOWING: {
                // checking argument number
                if(!argStr.isEmpty()){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                Map<String, List<String>> followed;  
                System.out.println(ConsoleColors.blue("-> ") + "Listing followed users...");
                try { followed = api.listFollowing(); }
                catch(NoLoggedUserException ex){
                    System.out.println(ConsoleColors.red("==> Error! ") + "No user is currently logged: please log in.");
                    return;
                }

                System.out.println(
                    ConsoleColors.blue("==> SUCCESS! ") + 
                    "You are following " + followed.size() + " user" 
                    + (followed.size() == 1 ? ".\n" : "s.\n") 
                );
                printUsers(followed);
                break;
            }

            case FOLLOW: {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 1){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Following " + ConsoleColors.blue(args[0]) + "...");
                try { api.followUser(args[0]); }
                catch (IOException ex){
                    System.out.println(ConsoleColors.red("==> Fatal error in server communication"));
                    return;
                }
                catch (MalformedJSONException ex){
                    System.out.println(ConsoleColors.red("==> Error! ") + "Server sent malformed response message.");
                    return;
                }
                catch (NoLoggedUserException ex){
                    System.out.println(ConsoleColors.red("==> Error! ") + "No user is currently logged: please log in.");
                    return;
                }
                catch (FollowException ex){
                    System.out.println(ConsoleColors.red("==> Error! ") + "You were already following " + ConsoleColors.red(args[0]) + ".");
                    return;
                }
                catch (IllegalStateException ex){
                    System.out.println(ConsoleColors.red("==> Unexpected error from server: ") + ex.getMessage());
                    return;
                }

                System.out.println(ConsoleColors.blue("==> SUCCESS! ") + "You are now following " + ConsoleColors.blue(args[0]) + "!");
                break;
            }
            
            case UNFOLLOW: {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 1){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Unfollowing " + ConsoleColors.blue(args[0]) + "...");
                try { api.unfollowUser(args[0]); }
                catch (IOException ex){
                    System.out.println(ConsoleColors.red("==> Fatal error in server communication"));
                    return;
                }
                catch (MalformedJSONException ex){
                    System.out.println(ConsoleColors.red("==> Error! ") + "Server sent malformed response message.");
                    return;
                }
                catch (NoLoggedUserException ex){
                    System.out.println(ConsoleColors.red("==> Error! ") + "No user is currently logged: please log in.");
                    return;
                }
                catch (FollowException ex){
                    System.out.println(ConsoleColors.red("==> Error! ") + "You were not following " + ConsoleColors.red(args[0]) + ".");
                    return;
                }
                catch (IllegalStateException ex){
                    System.out.println(ConsoleColors.red("==> Unexpected error from server: ") + ex.getMessage());
                    return;
                }

                System.out.println(ConsoleColors.blue("==> SUCCESS! ") + "You have stopped following " + ConsoleColors.blue(args[0]) + "!");
                break;
            }

            case BLOG: {
                // checking argument number
                if(!argStr.isEmpty()){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Listing your blog...");
                try { api.viewBlog(); }
                catch(NotImplementedException ex){
                    System.out.println(ConsoleColors.red("==> Command not yet implemented :("));
                    return;
                }
                break;
            }

            case POST: {
                String[] args = argStr.split("\\s+", 2); // splitting on first space

                // checking argument number
                if(args.length != 2){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Creating new post...");
                try { api.createPost(args[0], args[1]); }
                catch(NotImplementedException ex){
                    System.out.println(ConsoleColors.red("==> Command not yet implemented :("));
                    return;
                }
                break;
            }

            case SHOW_FEED: {
                // checking argument number
                if(!argStr.isEmpty()){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Getting your feed...");
                try { api.showFeed(); }
                catch(NotImplementedException ex){
                    System.out.println(ConsoleColors.red("==> Command not yet implemented :("));
                    return;
                }
                break;
            }

            case SHOW_POST: {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 1){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                int id = -1;
                try { id = Integer.parseInt(args[0]); }
                catch(NumberFormatException ex){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "The given post ID is not a positive integer.\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Showing post with ID \"" + ConsoleColors.blue(args[0]) + "\"...");
                try { api.showPost(id); }
                catch(NotImplementedException ex){
                    System.out.println(ConsoleColors.red("==> Command not yet implemented :("));
                    return;
                } 
                break;
            }

            
            case DELETE: {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 1){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                int id = -1;
                try { id = Integer.parseInt(args[0]); }
                catch(NumberFormatException ex){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "The given post ID is not a positive integer.\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Deleting post with ID \"" + ConsoleColors.blue(args[0]) + "\"...");
                try { api.deletePost(id); }
                catch(NotImplementedException ex){
                    System.out.println(ConsoleColors.red("==> Command not yet implemented :("));
                    return;
                } 
                break;
            }

            case REWIN: {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 1){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                int id = -1;
                try { id = Integer.parseInt(args[0]); }
                catch(NumberFormatException ex){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "The given post ID is not a positive integer.\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Rewinning post with ID \"" + ConsoleColors.blue(args[0]) + "\"...");
                try { api.rewinPost(id); }
                catch(NotImplementedException ex){
                    System.out.println(ConsoleColors.red("==> Command not yet implemented :("));
                    return;
                } 
                break;
            }

            case RATE: {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 2){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                int id = -1; int vote = 0;

                try { id = Integer.parseInt(args[0]); }
                catch(NumberFormatException ex){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "The given post ID is not a positive integer.\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                try { 
                    vote = Integer.parseInt(args[1]); 
                    if(vote != -1 && vote != 1) throw new NumberFormatException("vote must be +1 or -1");
                }
                catch(NumberFormatException ex){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") +
                        "The given vote is neither +1 nor -1.\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(
                    ConsoleColors.blue("-> ") + ((vote == +1) ? "Upvoting" : "Downvoting") 
                    + " post with ID \"" + ConsoleColors.blue(args[0]) + "\"..."
                );
                try { api.ratePost(id, vote); }
                catch(NotImplementedException ex){
                    System.out.println(ConsoleColors.red("==> Command not yet implemented :("));
                    return;
                } 
                break;
            }

            case COMMENT: {
                String[] args = argStr.split("\\s+", 2); // splitting on first space

                // checking argument number
                if(args.length != 2){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                int id = -1;
                try { id = Integer.parseInt(args[0]); }
                catch(NumberFormatException ex){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "The given post ID is not a positive integer.\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Adding comment on post \"" + ConsoleColors.blue(args[0]) + "\"...");
                try { api.addComment(id, args[1]); }
                catch(NotImplementedException ex){
                    System.out.println(ConsoleColors.red("==> Command not yet implemented :("));
                    return;
                }
                break;
            }

            case WALLET: {
                // checking argument number
                if(!argStr.isEmpty()){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Getting your wallet...");
                try { api.getWallet(); }
                catch(NotImplementedException ex){
                    System.out.println(ConsoleColors.red("==> Command not yet implemented :("));
                    return;
                }
                break;
            }

            case WALLET_BTC: {
                // checking argument number
                if(!argStr.isEmpty()){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n"
                    );
                    System.out.println(cmd.getHelpString());
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Getting your wallet in bitcoin...");
                try { api.getWallet(); }
                catch(NotImplementedException ex){
                    System.out.println(ConsoleColors.red("==> Command not yet implemented :("));
                    return;
                }
                break;
            }

            default: {
                System.out.println(ConsoleColors.red("==> Command not yet implemented :("));
                break;
            }
        }

    }

    private static void printUsers(Map<String, List<String>> users){
        for(Entry<String, List<String>> userTags : users.entrySet()){
            System.out.println(ConsoleColors.yellow("--> Username: ") + userTags.getKey());
            System.out.print(ConsoleColors.yellow("      Tags:"));
            for(String tag : userTags.getValue())
                System.out.print(" " + tag);
            System.out.println();
        }
    }
}
