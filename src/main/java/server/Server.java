package server;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Server {

    private JFrame frame;
    private JTabbedPane tabbedPane;
    private JLabel statusLabel;
    private ServerSocket serverSocket;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Server().createAndShowGUI());
    }

    public void createAndShowGUI() {
        frame = new JFrame("Screenshare Server");
        tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT); // 📜 Scroll tabs if too many

        statusLabel = new JLabel("Status: Waiting to start");

        JButton startButton = new JButton("Start Server");
        startButton.addActionListener(e -> {
            if (serverSocket == null) {
                startServer();
            }
        });

        // Add right-click support on tabbedPane 🖱
        tabbedPane.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger() || SwingUtilities.isRightMouseButton(e)) {
                    int tabIndex = tabbedPane.indexAtLocation(e.getX(), e.getY());
                    if (tabIndex != -1) {
                        showTabPopup(e.getX(), e.getY(), tabIndex);
                    }
                }
            }

            public void mouseReleased(MouseEvent e) {
                mousePressed(e); // handle both pressed and released
            }
        });

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(startButton, BorderLayout.WEST);
        topPanel.add(statusLabel, BorderLayout.CENTER);

        frame.setLayout(new BorderLayout());
        frame.add(topPanel, BorderLayout.NORTH);
        frame.add(tabbedPane, BorderLayout.CENTER);

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

    private void showTabPopup(int x, int y, int tabIndex) {
        JPopupMenu popup = new JPopupMenu();
        JMenuItem closeItem = new JMenuItem("Close Tab");

        closeItem.addActionListener(ev -> {
            tabbedPane.removeTabAt(tabIndex);
        });

        popup.add(closeItem);
        popup.show(tabbedPane, x, y);
    }

    class ClientHandler extends Thread {
        private Socket socket;
        private JLabel imageLabel;
        private JLabel infoLabel;
        private JPanel clientPanel;
        private String nickname;
        private String clientTabTitle;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void run() {
            try {
                InputStream is = socket.getInputStream();
                DataInputStream dis = new DataInputStream(is);

                nickname = dis.readUTF();
                clientTabTitle = nickname + " (" + socket.getInetAddress().getHostAddress() + ")";

                DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                dos.writeUTF("start_screenshare");

                // Create client panel
                SwingUtilities.invokeLater(() -> {
                    clientPanel = new JPanel(new BorderLayout());
                    infoLabel = new JLabel();
                    imageLabel = new JLabel();
                    imageLabel.setHorizontalAlignment(SwingConstants.CENTER);

                    clientPanel.add(infoLabel, BorderLayout.NORTH);
                    clientPanel.add(new JScrollPane(imageLabel), BorderLayout.CENTER);
                    tabbedPane.addTab(clientTabTitle, clientPanel);
                    frame.revalidate();
                    frame.repaint();

                    // 🔔 Show a live popup notification
                    JOptionPane.showMessageDialog(frame,
                            "New client connected:\n" +
                                    "Nickname: " + nickname + "\n" +
                                    "IP: " + socket.getInetAddress().getHostAddress(),
                            "Client Connected",
                            JOptionPane.INFORMATION_MESSAGE);
                });

                while (true) {
                    int length = dis.readInt();
                    byte[] imageBytes = new byte[length];
                    dis.readFully(imageBytes);

                    BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
                    if (img != null) {
                        SwingUtilities.invokeLater(() -> {
                            imageLabel.setIcon(new ImageIcon(img));
                            infoLabel.setText("IP: " + socket.getInetAddress().getHostAddress() +
                                    " | Name: " + nickname +
                                    " | Time: " + new SimpleDateFormat("HH:mm:ss").format(new Date()));
                        });
                    }
                }

            } catch (IOException e) {
                System.err.println("Client disconnected: " + nickname);
            } finally {
                SwingUtilities.invokeLater(() -> {
                    int index = tabbedPane.indexOfTab(clientTabTitle);
                    if (index != -1) {
                        tabbedPane.removeTabAt(index);
                    }
                    frame.revalidate();
                    frame.repaint();
                });
            }
        }
    }
}