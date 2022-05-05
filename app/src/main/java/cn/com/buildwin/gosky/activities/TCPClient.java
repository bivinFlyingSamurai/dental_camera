package cn.com.buildwin.gosky.activities;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

import cn.com.buildwin.gosky.application.Constants;

public class TCPClient {
    private static TCPClient instance;
    private TCPClient() {
        super();
    }
    public static TCPClient getInstance()
    {
        if (instance == null) {
            synchronized (TCPClient.class) {
                if (instance == null) {
                    instance = new TCPClient();
                }
            }
        }
        return instance;
    }
    public  void getInfo() throws IOException {

        new Thread(new Runnable() {
            @Override
            public void run() {
                //1.Create a TCP client socket service
                Socket client = new Socket();
                //2.Connect with the server
                InetSocketAddress address = new InetSocketAddress(Constants.SERVER_ADDRESS,Constants.SERVER_PORT);
                try {
                    client.connect(address);
                //3.After the connection is successful, get the client Socket output stream
                    OutputStream outputStream = null;
                    outputStream = client.getOutputStream();
                //4.Write data to the server through the output stream
                    outputStream.write("GETINFO /webcam APP0/1.0".getBytes());
                //5.close stream
                    InputStream inputStream = null;
                    inputStream = client.getInputStream();

                    byte[] bt = new byte[1024];
//                Get received bytes and number of bytes
                    int length = inputStream.read(bt);
//                get the correct bytes
                   // if(length < 1)
                     //   continue;
                    byte[] bs = new byte[length];
                    System.arraycopy(bt, 0, bs, 0, length);

                    String str = new String(bs, "UTF-8");
                    Log.e("arsen","rece data"+str);

                    client.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).start();


    }
}