package winsome.client;

import java.io.*;

import java.rmi.NotBoundException;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import java.util.*;
import java.util.Map.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import winsome.api.PostInfo;
import winsome.api.PostInfo.Comment;
import winsome.api.TransactionInfo;
import winsome.api.Wallet;
import winsome.api.WinsomeAPI;
import winsome.api.exceptions.*;

import winsome.client.exceptions.UnknownCommandException;

import winsome.utils.ConsoleColors;
import winsome.utils.configs.exceptions.InvalidConfigFileException;

public class WinsomeClientMain {
    /** Default config path */
    private static String DEFAULT_CONFIG_PATH = "./configs/client-config.yaml";

    public static void main(String[] args) {
        System.out.println(ConsoleColors.green("\t\tWinsome Social Network\n"));

        System.out.println("Welcome to the Winsome Social Network!\n");

        // parsing config
        ClientConfig config;
        try {
            System.out.println(ConsoleColors.blue("-> ") + "Reading config...");
            config = ClientConfig.fromConfigFile(DEFAULT_CONFIG_PATH);
            System.out.println(ConsoleColors.blue("==> Config read!"));
        } 
        catch (FileNotFoundException ex){
            System.err.println(ConsoleColors.red("==> ERROR!") + " Config file does not exist: aborting.");
            System.exit(1); return;
        }
        catch (InvalidConfigFileException | IOException ex){
            System.err.println(ConsoleColors.red("==> ERROR!") + " Could not read config file: aborting.");
            System.exit(1); return;
        }

        System.out.println(ConsoleColors.blue("-> ") + "Connecting to server...");
        // creating API interface
        WinsomeAPI api = new WinsomeAPI(
            config.serverAddr,
            config.portTCP,
            config.regHost,
            config.regPort
        );

        try {
            api.connect(); 
        } catch(IOException ex){ 
            System.err.println(ConsoleColors.red("==> ERROR! ") + "Some IO error occurred while connecting to the server: aborting.");
            System.exit(1); return;
        } catch(NotBoundException ex){ 
            System.err.println(ConsoleColors.red("==> ERROR! ") + "Could not bind to the server's registry: aborting.");
            System.exit(1); return;
        }
        
        System.out.println(ConsoleColors.blue("==> Connected!"));

        Runtime.getRuntime().addShutdownHook(
            new Thread( () ->
                System.out.println("Thank you for using the " + ConsoleColors.green("Winsome Social Network") + "!") 
            )
        );

        System.out.println("\nType a command (or " + ConsoleColors.green("help") + " to print the usage message)...\n");

        try (
            BufferedReader input = new BufferedReader(new InputStreamReader(System.in));
        ){
            System.out.print(ConsoleColors.green("-> "));
            
            String line;
            while((line = input.readLine()) != null){
                line = line.trim();
                if(line.isEmpty()) continue;

                // tries to execute the command
                try { executeCommand(api, line); }
                catch (MalformedJSONException ex){
                    System.err.println(
                        ConsoleColors.red("==> Server sent malformed response!")
                    );
                }
                catch (NoLoggedUserException ex){
                    printError("No user is currently logged: please log in.");
                }
                catch (UnexpectedServerResponseException ex){
                    System.err.println(
                        ConsoleColors.red("==> Unexpected error from server: ") + ex.getMessage()
                    );
                }

                // getting server "Updated rewards!" message if present
                Optional<String> serverMsg = api.getServerMsg();
                if(serverMsg.isPresent())
                    System.out.println(ConsoleColors.yellow("\n==> SERVER MESSAGE: ") + serverMsg.get());
                
                System.out.print(ConsoleColors.green("\n-> "));

                // printing logged user next to arrow if present
                Optional<String> loggedUser = api.getLoggedUser();
                if(loggedUser.isPresent())
                    System.out.print("[" + ConsoleColors.green(loggedUser.get()) + "] ");
            }
        } catch (IOException ex){ 
            System.err.println(ConsoleColors.red("==> ERROR! ") + "Some IO error occurred while connecting to the server: aborting.");
            ex.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Executes a command.
     * @param api the Winsome API instance
     * @param line the string containing the command
     * @throws IOException if some IO exception occurs
     * @throws MalformedJSONException if the server sends a malformed JSON response
     * @throws NoLoggedUserException if no user is currently logged
     * @throws UnexpectedServerResponseException if the server sent an unexpected error
     */
    private static void executeCommand(WinsomeAPI api, String line)
            throws IOException, MalformedJSONException, NoLoggedUserException, UnexpectedServerResponseException {
        Command cmd;

        // getting command
        try { cmd = Command.fromString(line); }
        catch(UnknownCommandException ex){ // line is not a valid command
            printError(ex.getMessage() + "\n");
            System.out.print(Command.help());
            return;
        }

        // gets the args
        String argStr = line.substring(cmd.name.length()).trim();

        switch (cmd) {
            case QUIT -> { // closes the api and quits
                api.close();
                System.exit(0);
            }

            case HELP -> {
                try { // if the command is "help <cmd>" prints <cmd>'s help message
                    Command cmd1 = Command.fromString(argStr); 
                    System.out.println("\n" + cmd1.getHelpString());
                } catch(UnknownCommandException ex){ // otherwise it simply prints the general help message
                    System.out.println("\n" + Command.help() + "\n"); 
                }
            }

            case REGISTER -> {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length < 3 || args.length > 7){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Signing up " + ConsoleColors.blue(args[0]) + "...");
                Set<String> tags = new HashSet<>(args.length - 2);
                for(int i = 0; i < args.length - 2; i++) tags.add(args[i+2]);

                try { api.register(args[0], args[1], tags); }
                catch (UserAlreadyLoggedException ex) {
                    System.err.println(ConsoleColors.red("==> Error: ") + ex.getMessage());
                    return;
                }
                catch (UserAlreadyExistsException ex) {
                    System.err.println(ConsoleColors.red("==> Error: ") + "this username is not available");
                    return;
                }
                catch (EmptyUsernameException ex) {
                    System.err.println(ConsoleColors.red("==> Error: ") + "username must not be empty!");
                    return;
                }
                catch (EmptyPasswordException ex) {
                    System.err.println(ConsoleColors.red("==> Error: ") + "password must not be empty!");
                    return;
                }
                catch (IllegalTagException ex) {
                    System.err.println(ConsoleColors.red("==> Error: ") + "tags must be non-empty and they must contain only lowercase characters.");
                    return;
                }

                System.out.println(
                    ConsoleColors.blue("==> SUCCESS: ") +
                    ConsoleColors.blue(args[0]) + " has been registered in the Social Network!"
                );
            }

            case LOGIN -> {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 2){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Logging in as " + ConsoleColors.blue(args[0]) + "...");

                try { api.login(args[0], args[1]); }
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

                System.out.println(
                    ConsoleColors.blue("==> SUCCESS: ") + "you are now logged in as " + 
                    ConsoleColors.blue(args[0]) + "!");
            }

            case LOGOUT -> {
                // checking argument number
                if(!argStr.isEmpty()){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Attempting to log out...");
                api.logout();

                System.out.println(ConsoleColors.blue("==> ") + "User successfully logged out!");
            }

            case LIST_USERS -> {
                // checking argument number
                if(!argStr.isEmpty()){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Getting users...");
                Map<String, List<String>> users = api.listUsers();

                String msg = (users.size() == 0) ?
                    "Currently no user shares interests with you." :
                    "Currently the following " + users.size() + " user" +
                        (users.size() == 1 ? " " : "s ") +
                        "share interests with you:\n";

                System.out.println(ConsoleColors.blue("==> SUCCESS! ") + msg);
                if(users.size() > 0) printUsers(users);
            }

            case LIST_FOLLOWERS -> {
                // checking argument number
                if(!argStr.isEmpty()){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }
                
                System.out.println(ConsoleColors.blue("-> ") + "Listing followers...");
                Map<String, List<String>> followers = api.listFollowers();
                
                String msg = (followers.size() == 0) ?
                    "Currently no user is following you." :
                    "You are currently being followed by " + followers.size() + " user" +
                        (followers.size() == 1 ? ".\n" : "s.\n");
                
                System.out.println(
                    ConsoleColors.blue("==> SUCCESS! ") + msg
                );
                printUsers(followers);
            }

            case LIST_FOLLOWING -> {
                // checking argument number
                if(!argStr.isEmpty()){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Listing followed users...");
                Map<String, List<String>> followed = api.listFollowing();

                String msg = (followed.size() == 0) ?
                    "Currently you are not following any users." :
                    "You are currently following " + followed.size() + " user" +
                        (followed.size() == 1 ? ".\n" : "s.\n");
                
                System.out.println(
                    ConsoleColors.blue("==> SUCCESS! ") + msg
                );
                printUsers(followed);
            }

            case FOLLOW -> {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 1){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Following " + ConsoleColors.blue(args[0]) + "...");
                try { api.followUser(args[0]); }
                catch (AlreadyFollowingException ex){
                    printError("You were already following " + ConsoleColors.red(args[0]) + ".");
                    return;
                }
                catch (UserNotVisibleException ex){
                    printError("You have no tags in common with " + ConsoleColors.red(args[0]) + ".");
                    return;
                }
                catch (SelfFollowException ex){
                    printError("You cannot follow yourself!");
                    return;
                }

                System.out.println(ConsoleColors.blue("==> SUCCESS! ") + "You are now following " + ConsoleColors.blue(args[0]) + "!");
            }
            
            case UNFOLLOW -> {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 1){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Unfollowing " + ConsoleColors.blue(args[0]) + "...");
                try { api.unfollowUser(args[0]); }
                catch (NotFollowingException ex){
                    printError("You were not following " + ConsoleColors.red(args[0]) + ".");
                    return;
                }
                catch (UserNotVisibleException ex){
                    printError("You have no tags in common with " + ConsoleColors.red(args[0]) + ".");
                    return;
                }
                catch (SelfFollowException ex){
                    printError("You cannot unfollow yourself!");
                    return;
                }

                System.out.println(ConsoleColors.blue("==> SUCCESS! ") + "You have stopped following " + ConsoleColors.blue(args[0]) + "!");
            }

            case BLOG -> {
                String[] args;

                // checking argument number
                if(argStr.isEmpty()) args = new String[0];
                else {
                    args = argStr.split("\\s+");
                    if(args.length != 1) {
                        printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                        System.err.println(cmd.getHelpString() + "\n");
                        return;
                    }  
                }

                System.out.println(ConsoleColors.blue("-> ") + "Listing " + (args.length == 0 ? "your" : (ConsoleColors.blue(args[0]) + "'s")) + " blog...");
                List<PostInfo> posts;
                try { 
                    if(args.length == 0) posts = api.viewBlog(); 
                    else posts = api.viewBlog(args[0]);
                }
                catch (UserNotVisibleException ex){
                    printError("You have no tags in common with " + ConsoleColors.red(args[0]) + ".");
                    return;
                }

                String msg = (args.length == 0 ? "You have" : ConsoleColors.blue(args[0]) + " has") +
                    " published " + ConsoleColors.blue(Integer.toString(posts.size())) + " post" +
                    ((posts.size() == 1) ? ".\n" : "s.\n");

                System.out.println(
                    ConsoleColors.blue("==> SUCCESS! ") + msg
                );
                for(PostInfo post : posts) printPost(post, false);
            }

            case POST -> {
                List<String> args = splitQuotes(argStr); // splitting on first space

                // checking argument number
                if(args.size() != 2){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Creating new post...");
                int id;
                try { id = api.createPost(args.get(0), args.get(1)); }
                catch (TextLengthException ex){
                    printError("Title and content of post exceeded limits.");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(
                    ConsoleColors.blue("==> SUCCESS! ") + "Post created! (id: " + 
                    ConsoleColors.blue(Integer.toString(id)) + ")"
                );
            }

            case SHOW_FEED -> {
                // checking argument number
                if(!argStr.isEmpty()){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Getting your feed...");
                List<PostInfo> posts = api.showFeed();

                System.out.println(
                    ConsoleColors.blue("==> SUCCESS! ") + "You have " + 
                    ConsoleColors.blue(Integer.toString(posts.size())) + " posts in your feed!\n"
                );
                for(PostInfo post : posts) printPost(post, false);
            }

            case SHOW_POST -> {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 1){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                int id = -1;
                try { id = Integer.parseInt(args[0]); }
                catch(NumberFormatException ex){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "The given post ID is not a positive integer.\n"
                    );
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Showing post with ID " + ConsoleColors.blue(args[0]) + "...");
                PostInfo post;
                try { post = api.showPost(id); }
                catch (NoSuchPostException ex){
                    printError("There is no post with the given ID.");
                    return;
                }

                System.out.println(ConsoleColors.blue("==> SUCCESS!\n"));
                printPost(post, true);
            }

            
            case DELETE -> {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 1){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                int id = -1;
                try { id = Integer.parseInt(args[0]); }
                catch(NumberFormatException ex){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "The given post ID is not a positive integer.\n"
                    );
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Deleting post with ID \"" + ConsoleColors.blue(args[0]) + "\"...");
                try { api.deletePost(id); }
                catch (NoSuchPostException ex){
                    printError("There is no post with the given ID.");
                    return;
                }
                catch (NotPostOwnerException ex){
                    printError("You cannot delete this post because you are not its owner.");
                    return;
                }
                
                System.out.println(ConsoleColors.blue("==> SUCCESS!"));
            }

            case REWIN -> {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 1){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                int id = -1;
                try { id = Integer.parseInt(args[0]); }
                catch(NumberFormatException ex){
                    System.out.println(
                        ConsoleColors.red("==> ERROR! ") + 
                        "The given post ID is not a positive integer.\n"
                    );
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Rewinning post with ID \"" + ConsoleColors.blue(args[0]) + "\"...");
                try { api.rewinPost(id); }
                catch (NoSuchPostException ex){
                    printError("There is no post with the given ID.");
                    return;
                }
                catch (PostOwnerException ex){
                    printError("You cannot rewin your own post.");
                    return;
                }
                catch (AlreadyRewinnedException ex){
                    printError("You are have already rewinned this post.");
                    return;
                }
                catch (NotFollowingException ex){
                    printError("You cannot rewin a post without following its author or rewinner.");
                    return;
                }

                System.out.println(ConsoleColors.blue("==> SUCCESS!"));
            }

            case RATE -> {
                String[] args = argStr.split("\\s+"); // splitting on space

                // checking argument number
                if(args.length != 2){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                int id = -1; int vote = 0;

                try { id = Integer.parseInt(args[0]); }
                catch(NumberFormatException ex){
                    printError("The given post ID is not a positive integer.\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                try { 
                    vote = Integer.parseInt(args[1]); 
                    if(vote != -1 && vote != 1) throw new NumberFormatException("vote must be +1 or -1");
                }
                catch(NumberFormatException ex){
                    printError("The given vote is neither +1 nor -1.\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(
                    ConsoleColors.blue("-> ") + ((vote == +1) ? "Upvoting" : "Downvoting") 
                    + " post with ID \"" + ConsoleColors.blue(args[0]) + "\"..."
                );

                try { api.ratePost(id, vote); }
                catch (NoSuchPostException ex){
                    printError("There is no post with the given ID.");
                    return;
                }
                catch (AlreadyVotedException ex){
                    printError("You have already voted this post.");
                    return;
                }
                catch (WrongVoteFormatException ex){
                    printError("The given vote is neither +1 nor -1.");
                    return;
                }
                catch (PostOwnerException ex){
                    printError("You cannot rate your own posts.");
                    return;
                }
                catch (NotFollowingException ex){
                    printError("You cannot rate a post without following its author or rewinner.");
                    return;
                }
                
                System.out.println(ConsoleColors.blue("==> SUCCESS!"));
            }

            case COMMENT -> {
                List<String> args = splitQuotes(argStr); // splitting on first space

                // checking argument number
                if(args.size() != 2){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                int id = -1;
                try { id = Integer.parseInt(args.get(0)); }
                catch(NumberFormatException ex){
                    printError("The given post ID is not a positive integer.\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Adding comment on post with ID " 
                    + ConsoleColors.blue(args.get(0)) + "...");

                try { api.addComment(id, args.get(1)); }
                catch (NoSuchPostException ex){
                    printError("There is no post with the given ID.");
                    return;
                }
                catch (PostOwnerException ex){
                    printError("You cannot add a comment to your own post.");
                    return;
                }
                catch (NotFollowingException ex){
                    printError(
                        "You cannot add a comment to a post without following its author or rewinner.");
                    return;
                }

                System.out.println(ConsoleColors.blue("==> SUCCESS!"));
            }

            case WALLET -> {
                // checking argument number
                if(!argStr.isEmpty()){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                System.out.println(ConsoleColors.blue("-> ") + "Getting your wallet...");
                Wallet wallet = api.getWallet();
                
                System.out.println(ConsoleColors.blue("==> SUCCESS!") + " Printing your wallet...\n");
                printWallet(wallet);
            }

            case WALLET_BTC -> {
                // checking argument number
                if(!argStr.isEmpty()){
                    printError("Wrong number of arguments to " + ConsoleColors.red(cmd.name) + ".\n");
                    System.err.println(cmd.getHelpString() + "\n");
                    return;
                }

                double total;
                System.out.println(ConsoleColors.blue("-> ") + "Getting your wallet in bitcoin...");
                try { total = api.getWalletInBitcoin(); }
                catch (ExchangeRateException ex){
                    printError("Server could not compute the current exchange rate to BTC.");
                    return;
                }

                System.out.printf(
                    ConsoleColors.blue("==> SUCCESS!") + " Your wallet currently contains " + 
                    ConsoleColors.blue("%.6f") + " bitcoins.\n",
                    total
                );
            }
        }
    }

    /** Prints an error message */
    private static void printError(String msg){
        System.err.println(ConsoleColors.red("==> Error! ") + msg);
    }

    /** Splits a string on single words or quoted sentences */
    private static List<String> splitQuotes(String str){
        List<String> result = new ArrayList<>();

        Matcher matcher = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(str);
        while(matcher.find()) result.add(matcher.group(1).replace("\"", "").trim());

        return result;
    }

    /**
     * Pretty-prints a post.
     * @param post the given post
     * @param includeInfo whether to include content, votes and comments
     */
    private static void printPost(PostInfo post, boolean includeInfo){
        String str = 
            ConsoleColors.yellow("- ID: ") + post.id + "\n"
            + ConsoleColors.yellow("  Author: ") + post.author + "\n"
            + ConsoleColors.yellow("  Title: ") + post.title + "\n";

        if(post.isRewin())
            str += ConsoleColors.yellow("  Rewinner: ") + post.rewinner.get() + "\n";
            
        if(includeInfo){
            str += ConsoleColors.yellow("  Contents: ") + post.contents + "\n"
                +  ConsoleColors.yellow("  Votes: ") + 
                    post.upvotes + " upvotes, " + post.downvotes + " downvotes\n";

            str += ConsoleColors.yellow("  Comments: ") + "there ";
            List<Comment> comments = post.getComments();

            if(comments.size() == 0) str += "are no comments.\n";
            else if(comments.size() == 1) str += "is one comment.\n";
            else str += "are " + comments.size() + " comments.\n";

            for(Comment comment : comments)
                str += "    " + ConsoleColors.blue(comment.author) + ": \"" + comment.contents + "\"\n"; 
        }
        System.out.print(str);
    }

    /** Pretty-prints a user */
    private static void printUsers(Map<String, List<String>> users){
        for(Entry<String, List<String>> userTags : users.entrySet()){
            System.out.println(ConsoleColors.yellow("--> Username: ") + userTags.getKey());
            System.out.print(ConsoleColors.yellow("      Tags:"));
            for(String tag : userTags.getValue())
                System.out.print(" " + tag);
            System.out.println();
        }
    }

    /**
     * Pretty-prints a wallet.
     * @param wallet the given wallet
     */
    private static void printWallet(Wallet wallet){
        System.out.printf(ConsoleColors.yellow("- Total: ") + "%.4f\n", wallet.total);
        System.out.println(
            ConsoleColors.yellow("- Transactions:") + " there are currently " + 
            ConsoleColors.yellow(Integer.toString(wallet.getTransactions().size())) + 
            " transactions in your name."
        );
        for(TransactionInfo transaction : wallet.getTransactions()){
            System.out.printf(ConsoleColors.yellow("    - Amount: ") + "%.4f", transaction.amount);
            System.out.println(
                ConsoleColors.yellow("      Timestamp: ") + 
                ZonedDateTime.ofInstant(transaction.timestamp, ZoneId.systemDefault())
            );
        }
    }
}
