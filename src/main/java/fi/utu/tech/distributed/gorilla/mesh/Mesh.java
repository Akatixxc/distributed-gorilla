package fi.utu.tech.distributed.gorilla.mesh;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;

public class Mesh extends Thread{
    private ServerSocket serverSocket;
    private Thread thread;
    private MeshHandler clients[] = new MeshHandler[50];
    private int clientCount = 0;
    private ArrayList<Long> tokens;
    /**
     * Luo Mesh-palvelininstanssi
     *
     * @param port Portti, jossa uusien vertaisten liittymispyyntöjä kuunnellaan
     */
    public Mesh(int port) throws IOException {
        serverSocket = new ServerSocket(port);
        this.tokens = new ArrayList<>();
        this.thread = new Thread(this);
        this.thread.start();
    }

    /**
     * Käynnistä uusien vertaisten kuuntelusäie
     */
    public void run() {
        while (thread != null) {
            try {
                System.out.println(serverSocket.getInetAddress());
                addThreadClient(serverSocket.accept());
            } catch (IOException e) {
                e.getMessage();
            }
        }
    }

    /**
     * Sulje mesh-palvelin ja kaikki sen yhteydet
     */
    public void close() {
        if (this.thread != null) {
            thread = null;
        }
    }

    private static class MeshHandler extends Thread {
        private Mesh server;
        private Socket client;
        private SocketAddress ID;
        private ArrayList<Long> tokens;

        InputStream iS;
        OutputStream oS;
        ObjectOutputStream oOut;
        ObjectInputStream oIn;



        public MeshHandler(Mesh server, Socket clientSocket) {
            this.server = server;
            this.client = clientSocket;
            this.ID = client.getRemoteSocketAddress();
            this.tokens = new ArrayList<>();
        }

        public void run() {
            try {
                System.out.println("Client " + client.getRemoteSocketAddress() + " connected to server...");

                iS = client.getInputStream();
                oS = client.getOutputStream();
                oOut = new ObjectOutputStream(oS);
                oIn = new ObjectInputStream(iS);

                try {
                    while (true) {
                        MeshMessage message = (MeshMessage) oIn.readObject();
                        if (!tokenExists(message.getToken())) {
                            server.broadcast(message);
                            if (!server.tokens.contains(message.getToken())) {
                                System.out.println(message.toString());
                                server.tokens.add(message.getToken());
                            }

                            addToken(message.getToken());
                        }
                    }
                } catch (IOException e) {
                    close();
                }
            } catch (Exception e) {
                throw new Error(e.toString());
            }
            System.out.println("... thread done.");
        }

        public void send(MeshMessage msg) {
            try {
                oOut.writeObject(msg);
                oOut.flush();

            } catch (IOException e) {
                System.out.println("Client " + client.getRemoteSocketAddress() + " error sending : " + e.getMessage());
                server.remove(ID);
            }
        }

        public SocketAddress getID() {
            return ID;
        }

        /**
         * Lisää token, eli "viestitunniste"
         * Käytännössä merkkaa viestin tällä tunnisteella luetuksi
         * Määreenä private, koska tätä käyttävä luokka on sisäluokka (inner class)
         * Jos et käytä sisäluokkaa, pitää olla public
         *
         * @param token Viestitunniste
         */
        private void addToken(long token) {
            tokens.add(token);
        }

        /**
         * Tarkista, onko viestitunniste jo olemassa
         * Määreenä private, koska tätä käyttävä luokka on sisäluokka (inner class)
         * Jos et käytä sisäluokkaa, pitää olla public
         *
         * @param token Viestitunniste
         */
        private boolean tokenExists(long token) {
            return tokens.contains(token);
        }

        public void close() throws IOException {
            System.out.println("Client " + client.getRemoteSocketAddress() + " disconnect from server...");
            oIn.close();
            oOut.close();
            client.close();
        }
    }
    /**
     * Lähetä hyötykuorma kaikille vastaanottajille
     *
     * @param msg Lähetettävä hyötykuorma
     */
    public synchronized void broadcast(MeshMessage msg){
        /**if (input.equals("exit")) {
         clients[findClient(ID)].send("exit");
         remove(ID);
         } else {**/
        for (int i = 0; i < clientCount; i++) {
            clients[i].send(msg);
        }
        //}
    }

    /**
     * Lähetä hyötykuorma valitulle vertaiselle
     *
     * @param o         Lähetettävä hyötykuorma
     * @param recipient Vastaanottavan vertaisen tunnus
     */
    public void send(Serializable o, long recipient){

    }

    /**
     * Yhdistä tämä vertainen olemassaolevaan Mesh-verkkoon
     *
     * @param addr Solmun ip-osoite, johon yhdistetään
     * @param port Portti, jota vastapuolinen solmu kuuntelee
     */
    public void connect(String addr, int port) throws IOException {
        addThreadClient(new Socket(addr, port));
    }

    private void addThreadClient(Socket socket) {
        if (clientCount < clients.length) {
            clients[clientCount] = new MeshHandler(this, socket);
            clients[clientCount].start();
            clientCount++;
        } else {
            System.out.println("Client refused : maximum " + clients.length + " reached.");
        }
    }

    public synchronized void remove(SocketAddress ID) {
        int index = findClient(ID);
        if (index >= 0) {
            MeshHandler threadToTerminate = clients[index];
            System.out.println("Removing client thread " + ID + " at " + index);
            if (index < clientCount - 1) {
                for (int i = index + 1; i < clientCount; i++) {
                    clients[i - 1] = clients[i];
                }
            }
            clientCount--;
            try {
                threadToTerminate.close();
            } catch (IOException e) {
                System.out.println("Error closing thread : " + e.getMessage());
            }
        }
    }

    private int findClient(SocketAddress ID) {
        for (int i = 0; i < clientCount; i++) {
            if (clients[i].getID() == ID) {
                return i;
            }
        }
        return -1;
    }
}