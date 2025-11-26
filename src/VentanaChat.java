import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.Base64;
import javax.sound.sampled.*;
import javax.swing.*;

public class VentanaChat extends JFrame implements ClienteChat.MensajeListener {
    private JTextArea areaMensajes;
    private JTextField campoTexto;
    private JButton botonEnviar, botonSalir, botonUsuarios, botonSticker, botonAudio;
    private ClienteChat cliente;
    private String nombreUsuario;
    private String nombreSala;
    private int puerto = 5055;
    // Buffers temporales para reconstruir audios fragmentados
    private Map<String, StringBuilder> audioBuffers = new HashMap<>();
    private Map<String, Integer> audioExpected = new HashMap<>();

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
        botonAudio = new JButton("Audioo 🎵");
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
            // Subir audio por TCP al servidor (relé fiable)
            cliente.subirAudioTCP(archivo);
            areaMensajes.append("🎧 (Audio subido: " + archivo.getName() + ")\n");
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
                } else if (mensaje.startsWith("AUDIO_START|")) {
                    String[] partes = mensaje.split("\\|", 6);
                    String remitente = partes[2];
                    String nombreArchivo = partes[3];
                    int total = Integer.parseInt(partes[4]);
                    String key = remitente + "|" + nombreArchivo;
                    audioBuffers.put(key, new StringBuilder());
                    audioExpected.put(key, total);
                    areaMensajes.append("(Iniciando recepción de audio de " + remitente + ": " + nombreArchivo + " => " + total + " chunks)\n");

                } else if (mensaje.startsWith("AUDIO_CHUNK|")) {
                    String[] partes = mensaje.split("\\|", 6);
                    String remitente = partes[2];
                    String nombreArchivo = partes[3];
                    // partes[4] = index
                    String chunk = partes.length >= 6 ? partes[5] : "";
                    String key = remitente + "|" + nombreArchivo;
                    StringBuilder sb = audioBuffers.get(key);
                    if (sb == null) {
                        // Si no existe buffer, crear uno (por si se perdió el START)
                        sb = new StringBuilder();
                        audioBuffers.put(key, sb);
                    }
                    sb.append(chunk);

                } else if (mensaje.startsWith("AUDIO_END|")) {
                    String[] partes = mensaje.split("\\|", 5);
                    String remitente = partes[2];
                    String nombreArchivo = partes[3];
                    String key = remitente + "|" + nombreArchivo;
                    StringBuilder sb = audioBuffers.remove(key);
                    audioExpected.remove(key);
                    if (sb == null) {
                        areaMensajes.append("[Recepción incompleta: faltan chunks para " + nombreArchivo + "]\n");
                        // No hay datos suficientes para reconstruir el audio. Evitar excepción.
                    } else {
                        try {
                            byte[] bytes = Base64.getDecoder().decode(sb.toString());
                            String suffix = null;
                            int dot = nombreArchivo.lastIndexOf('.');
                            if (dot >= 0) suffix = nombreArchivo.substring(dot);
                            File archivo = (suffix == null)
                                    ? File.createTempFile("audio_", null)
                                    : File.createTempFile("audio_", suffix);
                            Files.write(archivo.toPath(), bytes);
                            areaMensajes.append("(Audio de " + remitente + "): " + archivo.getName() + "\n");

                            String lower = nombreArchivo.toLowerCase();
                            if (lower.endsWith(".wav") || lower.endsWith(".aiff") || lower.endsWith(".au")) {
                                try {
                                    AudioInputStream ais = AudioSystem.getAudioInputStream(archivo);
                                    Clip clip = AudioSystem.getClip();
                                    clip.open(ais);
                                    clip.start();
                                    clip.addLineListener(evt -> {
                                        if (evt.getType() == LineEvent.Type.STOP) {
                                            clip.close();
                                        }
                                    });
                                } catch (Exception ex) {
                                    JOptionPane.showMessageDialog(this, "Error al reproducir audio WAV: " + ex.getMessage());
                                }
                            } else {
                                // Fallback: intentar abrir con el reproductor por defecto del sistema
                                try {
                                    if (Desktop.isDesktopSupported()) {
                                        Desktop.getDesktop().open(archivo);
                                    } else {
                                        JOptionPane.showMessageDialog(this,
                                                "Nuevo audio recibido de " + remitente + "\nArchivo guardado como " + archivo.getName() + "\nReproducción automática no disponible.");
                                    }
                                } catch (Exception ex) {
                                    JOptionPane.showMessageDialog(this, "Error al abrir audio en el sistema: " + ex.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            areaMensajes.append("[Error al procesar archivo de audio recibido]\n");
                        }
                    }
                } else if (mensaje.startsWith("AUDIO_AVAIL|")) {
                    // AUDIO_AVAIL|sala|usuario|filename|id|size
                    String[] partes = mensaje.split("\\|", 6);
                    String salaRem = partes[1];
                    String remitente = partes[2];
                    String nombreArchivo = partes[3];
                    String id = partes[4];
                    if (!salaRem.equals(nombreSala)) {
                        // no es para esta sala
                        return;
                    }
                    areaMensajes.append("(Nuevo audio disponible de " + remitente + "): " + nombreArchivo + "\n");
                    // Descargar por TCP en background y reproducir cuando termine
                    new Thread(() -> {
                        File archivo = cliente.descargarAudioTCP(id, nombreArchivo);
                        if (archivo != null) {
                            try {
                                String lower = nombreArchivo.toLowerCase();
                                if (lower.endsWith(".wav") || lower.endsWith(".aiff") || lower.endsWith(".au")) {
                                    AudioInputStream ais = AudioSystem.getAudioInputStream(archivo);
                                    Clip clip = AudioSystem.getClip();
                                    clip.open(ais);
                                    clip.start();
                                    clip.addLineListener(evt -> {
                                        if (evt.getType() == LineEvent.Type.STOP) {
                                            clip.close();
                                        }
                                    });
                                } else {
                                    if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(archivo);
                                }
                            } catch (Exception ex) {
                                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "Error al reproducir audio: " + ex.getMessage()));
                            }
                        } else {
                            SwingUtilities.invokeLater(() -> areaMensajes.append("[Error al descargar audio]\n"));
                        }
                    }).start();
                } else {
                    areaMensajes.append(mensaje + "\n");
                }
            } catch (Exception e) {
                areaMensajes.append("[Error al procesar archivo recibido]\n");
            }
        });
    }
}
