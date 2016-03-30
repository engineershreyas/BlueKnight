package com.shreyashirday.blueknight.blueknight.utils;

import java.util.LinkedList;

/**
 * Created by shreyashirday on 3/29/16.
 */
public class MessageQueue {

    private LinkedList<Byte[]> queue;

    private String message = null;

    private int messageLength = 0;

    public MessageQueue(){
        queue = new LinkedList<>();
    }

    public void addMessage(Byte[] data, int length){

        messageLength += length;
        queue.add(data);

    }


    public void flush(){

        while(!queue.isEmpty()){
            queue.poll();
        }

        messageLength = 0;

    }

    public String assembleMessage(){

        StringBuilder sb = new StringBuilder();

        while (!queue.isEmpty()){

            Byte[] bytes = queue.poll();


            byte[] b = new byte[bytes.length];


            int i = 0;
            for(Byte bte : bytes){
                b[i] = bte;
                i++;
            }

            String s = new String(b);
            sb.append(s);


        }

        message = sb.toString();

        return message;
    }

    public boolean validateMessage(){

        //assemble message first!
        if(message == null) return false;

        return  message.length() == messageLength;

    }

}
