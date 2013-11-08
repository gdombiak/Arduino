package com.jivesoftware.arduino.jive;

/**
 * Created with IntelliJ IDEA.
 * User: gaston
 * Date: 11/8/13
 * Time: 11:50 AM
 * To change this template use File | Settings | File Templates.
 */
public abstract class ReplyCommand extends JiveCommand{

    public abstract void execute(String text);

}
