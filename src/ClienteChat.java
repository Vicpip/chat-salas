import java.net.*;
import java.io.*;
import java.nio.file.Files;
import java.util.UUID;

public class ClienteChat {
    private DatagramSocket socket;
    private InetAddress servidor;
    private int puerto;
    private String sala;
    private String nombreUsuario;
    private String listaUsuarios = "";
    

    public interface MensajeListener {
        void onMensajeRecibido(String mensaje);
    }

    private MensajeListener listener;

    public ClienteChat(String host, int puerto, String sala, String nombreUsuario, MensajeListener listener) throws Exception {
        this.servidor = InetAddress.getByName(host);
        this.puerto = puerto;
        this.sala = sala;
        this.nombreUsuario = nombreUsuario;
        this.listener = listener;
        this.socket = new DatagramSocket();

        new Thread(this::escuchar).start();

        enviar("JOIN|" + sala + "|" + nombreUsuario);
    }

    public String getSala() {
        return sala;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void enviar(String mensaje) {
        try {
            byte[] data = mensaje.getBytes();
            DatagramPacket p = new DatagramPacket(data, data.length, servidor, puerto);
            socket.send(p);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // (Removed) chunked UDP audio send - replaced by TCP upload (subirAudioTCP)

    private void escuchar() {
        try {
            byte[] buffer = new byte[65536];
            while (true) {
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);
                String msg = new String(paquete.getData(), 0, paquete.getLength());
                System.out.println("[Cliente] recibido: " + (msg.length() > 80 ? msg.substring(0, 80) + "..." : msg));

                if (msg.startsWith("USUARIOS|")) {
                    listaUsuarios = msg.substring(9);
                } else if (msg.startsWith("UPDATE_USERS|")) {
                    listaUsuarios = msg.substring(13);
                    if (listener != null)
                        listener.onMensajeRecibido("--> Lista de usuarios actualizada: " + listaUsuarios);
                } else if (listener != null) {
                    listener.onMensajeRecibido(msg);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String obtenerUsuarios() {
        return listaUsuarios;
    }

    public void cerrar() {
        enviar("LEAVE|" + sala + "|" + nombreUsuario);
        socket.close();
    }

    // Puerto TCP del servidor para transferencias de archivos
    private static final int TCP_PORT = 5056;

    // Sube un archivo al servidor por TCP. Genera un id y notifica a la sala.
    public void subirAudioTCP(File archivo) {
        new Thread(() -> {
            try (Socket s = new Socket(servidor, TCP_PORT);
                 OutputStream out = s.getOutputStream();
                 InputStream fis = new FileInputStream(archivo)) {

                String id = UUID.randomUUID().toString();
                long size = Files.size(archivo.toPath());
                String header = "UPLOAD|" + sala + "|" + nombreUsuario + "|" + archivo.getName() + "|" + id + "|" + size + "\n";
                out.write(header.getBytes());
                out.flush();

                byte[] buffer = new byte[8192];
                int r;
                while ((r = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, r);
                }
                out.flush();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // Descarga un archivo desde el servidor TCP y lo guarda en un archivo temporal
    public File descargarAudioTCP(String id, String nombreArchivo) {
        try {
            Socket s = new Socket(servidor, TCP_PORT);
            OutputStream out = s.getOutputStream();
            InputStream in = s.getInputStream();
            String req = "DOWNLOAD|" + id + "\n";
            out.write(req.getBytes());
            out.flush();

            String suffix = null;
            int dot = nombreArchivo.lastIndexOf('.');
            if (dot >= 0) suffix = nombreArchivo.substring(dot);
            File archivo = (suffix == null) ? File.createTempFile("audio_down_", null) : File.createTempFile("audio_down_", suffix);
            try (FileOutputStream fos = new FileOutputStream(archivo)) {
                byte[] buffer = new byte[8192];
                int r;
                while ((r = in.read(buffer)) != -1) {
                    fos.write(buffer, 0, r);
                }
            }
            s.close();
            return archivo;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
