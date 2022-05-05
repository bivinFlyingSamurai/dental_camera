package cn.com.buildwin.gosky.activities;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.Arrays;
import java.util.HashMap;


import cn.com.buildwin.gosky.application.Constants;
import cn.com.buildwin.gosky.application.GoSkyApplication;

/**
 * Created by hejiang on 2018/10/17.
 */

public class UdpTaskCenter {

    private static UdpTaskCenter instance;
    private static final String TAG = "UdpTaskCenter";
    //    IP地址
    private String ipAddress;
    //    端口号
    private int port;
    private DatagramSocket socket = null;
    private Thread rcvThread;
    private Thread sendThread;
    private Thread callbackThread;
    private int callBackTick;
    private int nowDegree;
	 public boolean isSendHeartBeat = false;
    private int listenPort = 8990;
    private boolean isStop = false;
    private String lastTakePhotoTick;

    //    构造函数私有化
    private UdpTaskCenter() {
        super();
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(listenPort));
        } catch (SocketException e) {
            e.printStackTrace();
        }

    }

    private int delayTick;
    private int waitTick;
    private OnServerConnectedCallbackBlock connectedCallback;
    //    断开连接回调(连接失败)
    private OnServerDisconnectedCallbackBlock disconnectedCallback;
    //    接收信息回调
    private OnReceiveCallbackBlock receivedCallback;
    private OnReceiveBattery receiveBatteryCallback;
    private OnTakePhoto takePhotoCallBack =null;

    //    提供一个全局的静态方法
    public static UdpTaskCenter sharedCenter() {
        if (instance == null) {
            synchronized (UdpTaskCenter.class) {
                if (instance == null) {
                    instance = new UdpTaskCenter();
                }
            }
        }
        return instance;
    }
    public void setSendHeartBeat(boolean isSendHeartBeat){
        this.isSendHeartBeat = isSendHeartBeat;
    }
    public void heartBeatTask() {
if(sendThread!= null)
    return;
        sendThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while(true) {
                byte[] message = new byte[2048];
                try {
                    if (socket == null) {
                        Log.e(TAG, "create sendThread");
                        socket = new DatagramSocket(null);
                         socket.setReuseAddress(true);
                        socket.bind(new InetSocketAddress(listenPort));
                    }

                    InetAddress address = InetAddress.getByName(Constants.SERVER_ADDRESS);

                        String data = "PLAYSTATE:"+(isSendHeartBeat?1:0);
                    byte dataByte[] = data.getBytes(); //建立数据
                    DatagramPacket packet = new DatagramPacket(dataByte, dataByte.length, address, Constants.SERVER_ADDRESS_PORT); //通过该数据建包
                    socket.send(packet); //开始发送该包
                        try {
                            sendThread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                }
            }
        });
        sendThread.start();
    }
     WifiManager.MulticastLock multicastLock;
    /**
     * 通过IP地址(域名)和端口进行连接
     *
     * @param ipAddress IP地址(域名)
     * @param port      端口
     */
    public void listen(final String ipAddress, final int port) {
        String wserviceName = Context.WIFI_SERVICE;
        WifiManager mWifiManager = (WifiManager) GoSkyApplication.getApplication().getSystemService(wserviceName);
       multicastLock= mWifiManager.createMulticastLock("multicast.test");
        if(rcvThread!= null) {
            Log.e(TAG, "heartBeatTask already exit" );
            return;
        }
        rcvThread = new Thread(new Runnable() {
            @Override
            public void run() {
                multicastLock.acquire();
                byte[] message = new byte[1024];
                try {
                    // 建立Socket连接

                    if (socket == null) {
                        Log.e(TAG, "create listen");
                        socket = new DatagramSocket(null);
                        socket.setReuseAddress(true);
                        socket.bind(new InetSocketAddress(listenPort));
                    }

                   while (true)
                    {
                        // 准备接收数据
                        Arrays.fill(message,(byte)0);
                        DatagramPacket packet = new DatagramPacket(message, message.length);
                        try {
                            socket.receive(packet);//接收数据
                        } catch (InterruptedIOException e) {
                            continue;  //非阻塞循环Operation not permitted
                        }
                        String str_message = convertStandardJSONString(new String(packet.getData(), "UTF-8").trim());
                        if (str_message.length() < 1)
                            continue;
                        String ip_address = packet.getAddress().getHostAddress().toString();
                        int port_int = packet.getPort();
                    // Log.e(TAG,"msg"+str_message);
//                        InetAddress address = InetAddress.getByName("192.168.1.1");

                        HashMap<String, String> map = parseResponseString(str_message);
                        String takePhoto = map.get("TAKE_PHOTO");

                        if(takePhoto!=null && takePhotoCallBack!=null && !takePhoto.equalsIgnoreCase(lastTakePhotoTick)){
                            takePhotoCallBack.takercvPhoto();
                            lastTakePhotoTick = takePhoto;
                        }
                        String power = map.get("POWER_TIME");
                        if(power==null)
                        {
                            power =  map.get("POWER_LEVEL");
                        }
                        if(power !=null)
                        {

                        }

                    }
                } catch (Exception e) {
                    e.printStackTrace();

                }

            }
        });
        isStop = false;
        rcvThread.start();
        //callbackTimer();
    }
   // public String getPower(){

  //  }



    public String convertStandardJSONString(String data_json) {
        data_json = data_json.replaceAll("\\\\r\\\\n", "");
        data_json = data_json.replace("\"{", "{");
        data_json = data_json.replace("}\",", "},");
        data_json = data_json.replace("}\"", "}");
        return data_json;
    }

    /**
     * 回调声明
     */
    public interface OnServerConnectedCallbackBlock {
        void callback();
    }

    public interface OnServerDisconnectedCallbackBlock {
        void callback(IOException e);
    }

    public interface OnReceiveCallbackBlock {
        void callback(int type, String receicedMessage);
    }
    public interface OnReceiveBattery {
        void callback(String battery);
    }
    public interface OnTakePhoto{
        void takercvPhoto();
    }
    public void setOnTakePhoto(OnTakePhoto callback){
        this.takePhotoCallBack = callback;
    }
    public void removeTakePhoto()
    {
        this.takePhotoCallBack = null;
    }
    public void setConnectedCallback(OnServerConnectedCallbackBlock connectedCallback) {
        this.connectedCallback = connectedCallback;
    }

    public void setDisconnectedCallback(OnServerDisconnectedCallbackBlock disconnectedCallback) {
        this.disconnectedCallback = disconnectedCallback;
    }

    public void setReceivedCallback(OnReceiveCallbackBlock receivedCallback) {
        this.receivedCallback = receivedCallback;
    }
    public void setReceiveBatteryCallback(OnReceiveBattery receivedCallback){
        this.receiveBatteryCallback = receivedCallback;
    }


    /**
     * Parse response
     *
     * @param responseString
     * @return
     */
    private HashMap<String, String> parseResponseString(String responseString) {
        HashMap<String, String> map = new HashMap<String, String>();
        if (responseString != null) {
            String[] stringArray = responseString.split("\\r\\n");  // regex
             // Log.e("arsen","stringArray"+stringArray.length+"    "+stringArray[0]+"length "+stringArray[0].length());
                for(int  i = 0 ; i <stringArray.length;i++)
                {
                //    Log.e("arsen",i+"stringArray []"+stringArray[i]);
                }
              if (!stringArray[0].trim().equalsIgnoreCase("{")) {
                return map;
            }
            for(int i = 1;i<stringArray.length-1;i++) {
                String type = stringArray[i];
                type = type.replace(" ", "");
                String[] infoArray = type.split(":");
                if(infoArray.length==2) {
                    map.put(infoArray[0].replace("\"", "").trim(), infoArray[1].replace(",", "").replace("\"", "").trim());
                }
                else if(infoArray.length ==1)
                {
                    map.put(infoArray[0].replace("\"", "").trim(),"1");

                }
            }
        }

        return map;
    }

    /**
     * 移除回调
     */
    public void removeCallback() {
        connectedCallback = null;
        disconnectedCallback = null;
        receivedCallback = null;
        receiveBatteryCallback =null;
    }
    public void UdpTaskrelease()
    {
        Log.e(TAG,"UdpTaskrelease"+isStop);
        if(isStop ==false) {
            isStop = true;
            removeCallback();

        }
    }
}
