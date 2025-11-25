import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.Base64;
import javax.swing.*;

//import para el manejo de reproducción de audio 
import javax.sound.sampled.*;

public class VentanaChat extends JFrame implements ClienteChat.MensajeListener {
    private JTextArea areaMensajes;
    private JTextField campoTexto;
    private JButton botonEnviar, botonSalir, botonUsuarios, botonSticker, botonAudio;
    private ClienteChat cliente;
    private String nombreUsuario;
    private String nombreSala;
    private int puerto = 5055;

    public VentanaChat(String sala, String usuario) {
        this.nombreUsuario = usuario;
        this.nombreSala = sala;
        inicializarUI();
    }

    private void inicializarUI() {
        setTitle("Chat - Sala: " + nombreSala );
        setSize(680, 480);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        areaMensajes = new JTextArea();
        areaMensajes.setEditable(false);
        areaMensajes.setBackground(Color.BLACK);
        areaMensajes.setForeground(Color.GREEN);
        add(new JScrollPane(areaMensajes), BorderLayout.CENTER);

        JPanel panelAbajo = new JPanel(new BorderLayout());
        campoTexto = new JTextField();

        botonEnviar = new JButton("Enviar");
        botonUsuarios = new JButton("Usuarios 👥");
        botonSticker = new JButton("Sticker 😄");
        botonAudio = new JButton("Audio 🎵");
        botonSalir = new JButton("Abandonar 🚪");

        JPanel panelBotones = new JPanel(new GridLayout(1, 5, 5, 0));
        panelBotones.add(botonEnviar);
        panelBotones.add(botonUsuarios);
        panelBotones.add(botonSticker);
        panelBotones.add(botonAudio);
        panelBotones.add(botonSalir);

        panelAbajo.add(campoTexto, BorderLayout.CENTER);
        panelAbajo.add(panelBotones, BorderLayout.EAST);
        add(panelAbajo, BorderLayout.SOUTH);

        try {
            cliente = new ClienteChat("localhost", puerto, nombreSala, nombreUsuario, this);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al conectar con el servidor");
        }

        botonEnviar.addActionListener(e -> enviarMensaje());
        campoTexto.addActionListener(e -> enviarMensaje());
        botonSalir.addActionListener(e -> salirChat());
        botonUsuarios.addActionListener(e -> mostrarUsuarios());
        botonSticker.addActionListener(e -> enviarSticker());
        botonAudio.addActionListener(e -> enviarAudio());

        setVisible(true);
    }

    private void enviarMensaje() {
        String texto = campoTexto.getText().trim();
        if (texto.isEmpty()) return;
        cliente.enviar("MSG|" + nombreSala + "|" + nombreUsuario + ": " + texto);
        campoTexto.setText("");
    }

    private void reproducirAudio(File archivo) {
    try {
        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(archivo);
        Clip clip = AudioSystem.getClip();
        clip.open(audioInputStream);
        clip.start();
        
        // Opcional: Esperar a que la reproducción termine antes de cerrar el stream.
        // En un chat, es mejor no bloquear el hilo de la GUI, por lo que lo dejamos
        // que se reproduzca en segundo plano.
        
        } catch (UnsupportedAudioFileException e) {
            JOptionPane.showMessageDialog(this, "Error de audio: Formato no soportado.", "Error de Reproducción", JOptionPane.ERROR_MESSAGE);
        } catch (LineUnavailableException e) {
            JOptionPane.showMessageDialog(this, "Error de audio: Línea de sonido no disponible.", "Error de Reproducción", JOptionPane.ERROR_MESSAGE);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Error de audio: Error de lectura del archivo.", "Error de Reproducción", JOptionPane.ERROR_MESSAGE);
        } catch (Exception e) {
            // Otros posibles errores
        }
    }

    private void enviarSticker() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecciona un sticker (imagen PNG o JPG)");
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File archivo = chooser.getSelectedFile();
            try {
                byte[] bytes = Files.readAllBytes(archivo.toPath());
                String base64 = Base64.getEncoder().encodeToString(bytes);
                cliente.enviar("STICKER|" + nombreSala + "|" + nombreUsuario + "|" + archivo.getName() + "|" + base64);
                areaMensajes.append("(Sticker enviado: " + archivo.getName() + ")\n");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error al leer el archivo del sticker.");
            }
        }
    }

    private void enviarAudio() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Selecciona un audio (WAV o MP3)");
        int res = chooser.showOpenDialog(this);
        if (res == JFileChooser.APPROVE_OPTION) {
            File archivo = chooser.getSelectedFile();
            try {
                byte[] bytes = Files.readAllBytes(archivo.toPath());
                String base64 = Base64.getEncoder().encodeToString(bytes);
                cliente.enviar("AUDIO|" + nombreSala + "|" + nombreUsuario + "|" + archivo.getName() + "|" + base64);
                areaMensajes.append("🎧 (Audio enviado: " + archivo.getName() + ")\n");
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error al leer el archivo de audio.");
            }
        }
    }

    private void mostrarUsuarios() {
        try {
            String respuesta = cliente.obtenerUsuarios();

            if (respuesta == null || respuesta.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No hay usuarios disponibles en esta sala.");
                return;
            }

            String[] usuarios = respuesta.split(",");
            DefaultListModel<String> modelo = new DefaultListModel<>();

            for (String u : usuarios) {
                if (!u.trim().isEmpty() && !u.equals(nombreUsuario))
                    modelo.addElement(u);
            }

            if (modelo.isEmpty()) {
                JOptionPane.showMessageDialog(this, "No hay otros usuarios conectados.");
                return;
            }

            JList<String> lista = new JList<>(modelo);
            lista.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            int res = JOptionPane.showConfirmDialog(this, new JScrollPane(lista),
                    "Usuarios conectados", JOptionPane.OK_CANCEL_OPTION);

            if (res == JOptionPane.OK_OPTION) {
                String seleccionado = lista.getSelectedValue();
                if (seleccionado != null) {
                    String mensaje = JOptionPane.showInputDialog(this, "Mensaje privado para " + seleccionado + ":");
                    if (mensaje != null && !mensaje.trim().isEmpty()) {
                        cliente.enviar("PRIVATE|" + nombreSala + "|" + nombreUsuario + "|" + seleccionado + "|" + mensaje);
                        areaMensajes.append("(Privado a " + seleccionado + "): " + mensaje + "\n");
                    }
                }
            }

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al mostrar usuarios conectados.");
        }
    }

    private void salirChat() {
        cliente.enviar("LEAVE|" + nombreSala + "|" + nombreUsuario);
        cliente.cerrar();
        dispose();
        SwingUtilities.invokeLater(MenuSalas::new);
    }

    @Override
    public void onMensajeRecibido(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            try {
                if (mensaje.startsWith("STICKER|")) {
                    String[] partes = mensaje.split("\\|", 5);
                    String remitente = partes[1];
                    String nombreArchivo = partes[2];
                    String base64 = partes[3];
                    byte[] bytes = Base64.getDecoder().decode(base64);
                    File archivo = new File("recibidos_" + nombreArchivo);
                    Files.write(archivo.toPath(), bytes);
                    areaMensajes.append("(Sticker de " + remitente + "): " + archivo.getName() + "\n");
                    ImageIcon icon = new ImageIcon(bytes);
                    JLabel label = new JLabel(icon);
                    JOptionPane.showMessageDialog(this, label, "Sticker de " + remitente, JOptionPane.PLAIN_MESSAGE);

                } else if (mensaje.startsWith("AUDIO|")) {
                String[] partes = mensaje.split("\\|", 5);
                String remitente = partes[1];
                String nombreArchivo = partes[2];
                String base64 = partes[3];
                
                // 1. Decodificar y guardar el archivo
                byte[] bytes = Base64.getDecoder().decode(base64);
                // Usamos un prefijo único para evitar conflictos y facilitar la limpieza.
                File archivo = new File("audio_recibido_" + nombreArchivo); 
                Files.write(archivo.toPath(), bytes);
                
                // 2. Mostrar mensaje en el chat (como solicitaste)
                areaMensajes.append("🔊 (Audio de " + remitente + "): " + nombreArchivo + "\n");
                
                // 3. Reproducir inmediatamente
                reproducirAudio(archivo); 
                } else {
                    areaMensajes.append(mensaje + "\n");
                }
            } catch (Exception e) {
                areaMensajes.append("[Error al procesar archivo recibido]\n");
            }
        });
    }
}
