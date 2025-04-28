package server;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

public class Server {

    private JFrame frame;
    private JPanel clientsPanel;
    private JLabel statusLabel;
    private ServerSocket serverSocket;

    private ConcurrentHashMap<String, ClientHandler> clientHandlers = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Server().createAndShowGUI());
    }

    public void createAndShowGUI() {
        frame = new JFrame("Screenshare Server");
        clientsPanel = new JPanel();
        clientsPanel.setLayout(new GridLayout(0, 1, 10, 10));
        JScrollPane scrollPane = new JScrollPane(clientsPanel);
        statusLabel = new JLabel("Status: Waiting to start");

        JButton startButton = new JButton("Start Server");
        startButton.addActionListener(e -> {
            if (serverSocket == null) {
                startServer();
            }
        });

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(startButton, BorderLayout.WEST);
        topPanel.add(statusLabel, BorderLayout.CENTER);

        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(scrollPane, BorderLayout.CENTER);

        frame.setSize(1200, 700);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(5000);
                statusLabel.setText("Server started. Waiting for clients...");

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    new ClientHandler(clientSocket).start();
                }
            } catch (IOException e) {
                statusLabel.setText("Error: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    class ClientHandler extends Thread {
        private Socket socket;
        private JLabel imageLabel;
        private JLabel infoLabel;
        private JPanel panel;
        private String nickname;
        private String clientId;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                InputStream is = socket.getInputStream();
                DataInputStream dis = new DataInputStream(is);
                nickname = dis.readUTF();

                clientId = socket.getInetAddress().getHostAddress() + ":" + socket.getPort();
                statusLabel.setText("Client connected: " + nickname);

                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.writeUTF("start_screenshare");

                // Create panel for this client
                SwingUtilities.invokeLater(() -> {
                    panel = new JPanel(new BorderLayout());
                    imageLabel = new JLabel();
                    infoLabel = new JLabel();
                    panel.add(infoLabel, BorderLayout.NORTH);
                    panel.add(imageLabel, BorderLayout.CENTER);
                    clientsPanel.add(panel);
                    frame.revalidate();
                });

                while (true) {
                    int length = dis.readInt();
                    byte[] imageBytes = new byte[length];
                    dis.readFully(imageBytes);

                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
                    if (img != null) {
                        SwingUtilities.invokeLater(() -> {
                            imageLabel.setIcon(new ImageIcon(img));
                            infoLabel.setText("IP: " + socket.getInetAddress().getHostAddress()
                                    + " | Name: " + nickname
                                    + " | Time: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
                        });
                    }
                }

            } catch (IOException e) {
                System.err.println("Client disconnected: " + nickname);
            } finally {
                SwingUtilities.invokeLater(() -> {
                    clientsPanel.remove(panel);
                    frame.revalidate();
                    frame.repaint();
                });
            }
        }
    }
}