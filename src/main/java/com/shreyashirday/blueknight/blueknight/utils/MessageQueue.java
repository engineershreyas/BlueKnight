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

    public boolean addMessage(Byte[] data, int length){

        String s = assembleChunk(data);

        if(s.length() != length){
            flush();
            return false;
        }

        messageLength += length;
        queue.add(data);

        return true;

    }


    public void flush(){

        while(!queue.isEmpty()){
            queue.poll();
        }

        messageLength = 0;

    }

    private String assembleChunk(Byte[] data){

        byte[] b = new byte[data.length];


        int i = 0;
        for(Byte bte : data){
            b[i] = bte;
            i++;
        }

        String s = new String(b);

        return s;

    }

    public String assembleMessage(){

        StringBuilder sb = new StringBuilder();

        while (!queue.isEmpty()){

            Byte[] bytes = queue.poll();


            String s = assembleChunk(bytes);

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
