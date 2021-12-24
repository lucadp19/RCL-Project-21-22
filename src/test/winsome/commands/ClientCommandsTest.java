package test.winsome.commands;

import winsome.client.*;
import winsome.client.commands.*;
import winsome.utils.ConsoleColors;
import winsome.utils.exceptions.CommandSyntaxException;

public class ClientCommandsTest {
    public static void main(String[] args) {
        // printing all help commands
        System.out.println(ConsoleColors.GREEN_BOLD + "\tTesting help messages" + ConsoleColors.RESET);
        
        for(Command cmd : Command.values()){
            System.out.println();
            System.out.println(
                ConsoleColors.GREEN_BOLD + "[*] " + ConsoleColors.RESET +
                "Generating \"" + cmd.getCommandName() + "\" command help text..."
                );
            System.out.println();
            System.out.println(Command.help(cmd));
        }
    }

    public static void loginTest(){
        System.out.println(ConsoleColors.GREEN_BOLD + "\tTesting login command" + ConsoleColors.RESET);

        System.out.println();
        System.out.println(
            ConsoleColors.GREEN_BOLD + "[0] " + ConsoleColors.RESET +
            "Generating login command help text..."
            );
        System.out.println(LoginCommand.help());

        System.out.println();
        String args = "user passw";
        try{ 
            System.out.println(
                ConsoleColors.GREEN_BOLD + "[1] " + ConsoleColors.RESET +
                "Parsing command \"login " + args + "\"..."
                );
            LoginCommand cmd = new LoginCommand(args);
            System.out.println("    Command parsed correctly!");
            printLoginCmd(cmd);
        } catch(Exception ex){
            printError("no exception should have been caught");
            ex.printStackTrace();
        }

        System.out.println();
        args = "userbutnopassw";
        try{ 
            System.out.println(
                ConsoleColors.GREEN_BOLD + "[2] " + ConsoleColors.RESET +
                "Parsing command \"login " + args + "\"..."
                );
            LoginCommand cmd = new LoginCommand(args);
            printError("command had no password, it should have thrown an error!");
            printLoginCmd(cmd);
        } 
        catch(CommandSyntaxException ex){
            System.out.println("    Exception correctly caught!");
            ex.printStackTrace();
        }
        catch(Exception ex){
            printError("wrong exception type caught!");
            ex.printStackTrace();
        }


        
    }

    private static void printLoginCmd(LoginCommand cmd){
        printField("username"); System.out.println(cmd.getUser()); 
        printField("password"); System.out.println(cmd.getPass()); 
    }

    private static void printField(String field){
        final int length = 25;
        System.out.print(
            "\t" + 
            ConsoleColors.GREEN_BOLD + 
            String.format("%1$-" + length + "s", field + ":") 
            + ConsoleColors.RESET
            );
    }

    private static void printError(String msg){
        System.err.println(
            ConsoleColors.RED_BOLD + "    ERROR: " + ConsoleColors.RESET + msg
        );
    }

 
}
