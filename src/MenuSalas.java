import java.awt.*;
import java.net.*;
import javax.swing.*;

public class MenuSalas extends JFrame {
    private DefaultListModel<String> modeloSalas;
    private JList<String> listaSalas;
    private JButton botonActualizar, botonUnirse, botonAgregar;
    private String nombreUsuario;
    private int puerto = 5055;
    private InetAddress servidor;

    public MenuSalas() {
        setTitle("Lobby de Salas 💬");
        setSize(400, 350);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        try {
            servidor = InetAddress.getByName("localhost");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Panel superior (nombre de usuario)
        JPanel panelSuperior = new JPanel(new BorderLayout());
        panelSuperior.add(new JLabel("👤 Tu nombre de usuario:"), BorderLayout.WEST);
        JTextField campoUsuario = new JTextField();
        panelSuperior.add(campoUsuario, BorderLayout.CENTER);
        add(panelSuperior, BorderLayout.NORTH);

        // Lista de salas
        modeloSalas = new DefaultListModel<>();
        listaSalas = new JList<>(modeloSalas);
        listaSalas.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(listaSalas), BorderLayout.CENTER);

        // Panel de botones
        JPanel panelBotones = new JPanel(new GridLayout(1, 3, 10, 0));
        botonActualizar = new JButton("🔄 Actualizar");
        botonUnirse = new JButton("➡️ Unirse");
        botonAgregar = new JButton("➕ Agregar sala");

        panelBotones.add(botonActualizar);
        panelBotones.add(botonUnirse);
        panelBotones.add(botonAgregar);
        add(panelBotones, BorderLayout.SOUTH);

        // Eventos
        botonActualizar.addActionListener(e -> actualizarSalas());

        botonAgregar.addActionListener(e -> {
            String nueva = JOptionPane.showInputDialog(this, "Nombre de la nueva sala:");
            if (nueva != null && !nueva.trim().isEmpty()) {
                // Eliminar mensaje de "no hay salas" si existe
                if (modeloSalas.size() == 1 && modeloSalas.get(0).startsWith("⚠️")) {
                    modeloSalas.clear();
                }

                modeloSalas.addElement(nueva);
                listaSalas.setEnabled(true);
                botonUnirse.setEnabled(true);
            }
        });

        botonUnirse.addActionListener(e -> {
            String usuario = campoUsuario.getText().trim();
            String sala = listaSalas.getSelectedValue();

            if (usuario.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Debes ingresar un nombre de usuario");
                return;
            }

            if (sala == null || sala.startsWith("⚠️")) {
                JOptionPane.showMessageDialog(this, "Selecciona una sala válida para unirte");
                return;
            }

            nombreUsuario = usuario;
            abrirChat(sala);
        });

        actualizarSalas();
        setVisible(true);
    }

    private void actualizarSalas() {
        try {
            DatagramSocket socket = new DatagramSocket();
            byte[] data = "LIST_SALAS|".getBytes();
            DatagramPacket paquete = new DatagramPacket(data, data.length, servidor, puerto);
            socket.send(paquete);

            byte[] buffer = new byte[1024];
            DatagramPacket respuesta = new DatagramPacket(buffer, buffer.length);
            socket.setSoTimeout(1000);
            socket.receive(respuesta);

            String msg = new String(respuesta.getData(), 0, respuesta.getLength());
            modeloSalas.clear();

            if (msg.startsWith("SALAS|")) {
                String contenido = msg.substring(6);
                if (contenido.equals("NO_SALAS")) {
                    modeloSalas.addElement("⚠️ No hay salas disponibles");
                    listaSalas.setEnabled(false);
                    botonUnirse.setEnabled(false);
                } else {
                    String[] nombres = contenido.split(",");
                    for (String n : nombres) {
                        if (!n.trim().isEmpty()) modeloSalas.addElement(n);
                    }
                    listaSalas.setEnabled(true);
                    botonUnirse.setEnabled(true);
                }
            }

            socket.close();
        } catch (SocketTimeoutException e) {
            modeloSalas.clear();
            modeloSalas.addElement("❌ No se pudo conectar al servidor");
            listaSalas.setEnabled(false);
            botonUnirse.setEnabled(false);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void abrirChat(String sala) {
        dispose(); // Cierra el menú
        SwingUtilities.invokeLater(() -> new VentanaChat(sala, nombreUsuario));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(MenuSalas::new);
    }
}
