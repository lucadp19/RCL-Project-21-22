package winsome.client.commands;

import winsome.utils.exceptions.*;

public class LoginCommand {
    private String username;
    private String password;

    public LoginCommand(String cmd) throws CommandSyntaxException {
        String[] args = cmd.split("\\s+"); // removes trailing whitespace
        if(args.length != 2) 
            throw new CommandSyntaxException();
        username = args[0];
        password = args[1];
    }

    public static String help(){
        return "usage: login <username> <password>\n\n"
            + "Arguments\n" + "------------"
            + "<username>     user to log into\n"
            + "<password>     password of the user";
    }

    public String getUser(){ return username; }
    public String getPass(){ return password; }
}
