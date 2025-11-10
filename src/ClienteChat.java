import java.net.*;

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

    private void escuchar() {
        try {
            byte[] buffer = new byte[4096];
            while (true) {
                DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
                socket.receive(paquete);
                String msg = new String(paquete.getData(), 0, paquete.getLength());

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
}
