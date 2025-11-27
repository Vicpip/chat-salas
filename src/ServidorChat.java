import java.net.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class ServidorChat {
    private static final int PUERTO = 5055;

    // Mapa: sala → (nombre → dirección)
    private static Map<String, Map<String, InetSocketAddress>> usuariosPorSala = new ConcurrentHashMap<>();
    // Almacenamiento de archivos subidos por id
    private static Map<String, File> archivosPorId = new ConcurrentHashMap<>();

    public static void main(String[] args) throws Exception {
        DatagramSocket socket = new DatagramSocket(PUERTO);
        System.out.println("Servidor sincronizado en puerto " + PUERTO);

        // Lanzar servidor TCP para subir/descargar archivos de audio
        new Thread(() -> {
            try (ServerSocket server = new ServerSocket(5056)) {
                System.out.println("Servidor TCP de archivos en puerto 5056");
                File uploads = new File("uploads");
                if (!uploads.exists()) uploads.mkdirs();
                while (true) {
                    Socket s = server.accept();
                    new Thread(() -> manejarConexionTCP(s, uploads, socket)).start();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        byte[] buffer = new byte[8192];
        while (true) {
            DatagramPacket paquete = new DatagramPacket(buffer, buffer.length);
            socket.receive(paquete);
            new Thread(() -> manejarMensaje(socket, paquete)).start();
        }
    }

    private static void manejarMensaje(DatagramSocket socket, DatagramPacket paquete) {
        try {
            String msg = new String(paquete.getData(), 0, paquete.getLength());
            InetSocketAddress direccion = new InetSocketAddress(paquete.getAddress(), paquete.getPort());
            String[] partes = msg.split("\\|", 6);
            String comando = partes[0];

            switch (comando) {
                case "JOIN": {
                    String sala = partes[1];
                    String usuario = partes[2];

                    usuariosPorSala.putIfAbsent(sala, new ConcurrentHashMap<>());
                    usuariosPorSala.get(sala).put(usuario, direccion);

                    broadcast(socket, sala, "--> " + usuario + " se unió a la sala " + sala);
                    actualizarUsuarios(socket, sala);
                    break;
                }

                case "MSG": {
                    String sala = partes[1];
                    String contenido = partes[2];
                    broadcast(socket, sala, contenido);
                    break;
                }

                case "LIST_USERS": {
                    String sala = partes[1];
                    if (!usuariosPorSala.containsKey(sala)) return;

                    StringBuilder lista = new StringBuilder();
                    for (String nombre : usuariosPorSala.get(sala).keySet()) {
                        lista.append(nombre).append(",");
                    }
                    enviar(socket, "USUARIOS|" + lista, direccion);
                    break;
                }

                case "PRIVATE": {
                    String sala = partes[1];
                    String remitente = partes[2];
                    String destinatario = partes[3];
                    String mensaje = partes[4];

                    if (usuariosPorSala.containsKey(sala)) {
                        InetSocketAddress destino = usuariosPorSala.get(sala).get(destinatario);
                        if (destino != null) {
                            enviar(socket, "(Privado de " + remitente + "): " + mensaje, destino);
                            enviar(socket, "(Privado a " + destinatario + "): " + mensaje, direccion);
                        }
                    }
                    break;
                }

                case "LEAVE": {
                    String sala = partes[1];
                    String usuario = partes[2];
                    if (usuariosPorSala.containsKey(sala)) {
                        usuariosPorSala.get(sala).remove(usuario);
                        broadcast(socket, sala, "|| " + usuario + " salió de la sala.");
                        actualizarUsuarios(socket, sala);
                    }
                    break;
                }

                case "LIST_SALAS": {
                    StringBuilder sb = new StringBuilder();
                    if (usuariosPorSala.isEmpty()) {
                        sb.append("NO_SALAS");
                    } else {
                        for (String s : usuariosPorSala.keySet()) {
                            sb.append(s).append(",");
                        }
                    }
                    enviar(socket, "SALAS|" + sb.toString(), direccion);
                    break;
                }

                case "STICKER": {
                    String sala = partes[1];
                    String usuario = partes[2];
                    String nombreArchivo = partes[3];
                    String base64 = partes[4];
                    broadcast(socket, sala, "STICKER|" + usuario + "|" + nombreArchivo + "|" + base64);
                    break;
                }

                

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void manejarConexionTCP(Socket s, File uploadsDir, DatagramSocket udpSocket) {
        try (Socket socket = s;
             InputStream in = socket.getInputStream();
             OutputStream out = socket.getOutputStream()) {

            // Leer la línea de encabezado hasta '\n' manualmente para evitar que un BufferedReader
            // consuma bytes de archivo que luego se pierden al leer directamente desde 'in'.
            ByteArrayOutputStream headerBuf = new ByteArrayOutputStream();
            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') break;
                headerBuf.write(b);
            }
            if (headerBuf.size() == 0) return;
            String line = headerBuf.toString("UTF-8");
            String[] parts = line.split("\\|", 6);
            String cmd = parts[0];

            if ("UPLOAD".equals(cmd)) {
                String sala = parts[1];
                String usuario = parts[2];
                String filename = parts[3];
                String id = parts[4];
                long size = Long.parseLong(parts[5]);

                File outFile = new File(uploadsDir, id + "_" + filename);
                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    byte[] buffer = new byte[8192];
                    long remaining = size;
                    while (remaining > 0) {
                        int read = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                        if (read == -1) break;
                        fos.write(buffer, 0, read);
                        remaining -= read;
                    }
                }
                archivosPorId.put(id, outFile);
                // Notificar por UDP a la sala que hay un audio disponible
                String aviso = "AUDIO_AVAIL|" + sala + "|" + usuario + "|" + filename + "|" + id + "|" + size;
                broadcast(udpSocket, sala, aviso);

            } else if ("DOWNLOAD".equals(cmd)) {
                // DOWNLOAD|id
                String id = parts[1];
                File f = archivosPorId.get(id);
                if (f != null && f.exists()) {
                    try (FileInputStream fis = new FileInputStream(f)) {
                        byte[] buffer = new byte[8192];
                        int r;
                        while ((r = fis.read(buffer)) != -1) {
                            out.write(buffer, 0, r);
                        }
                        out.flush();
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Enviar mensaje a todos los usuarios de una sala
    private static void broadcast(DatagramSocket socket, String sala, String mensaje) throws Exception {
        if (!usuariosPorSala.containsKey(sala)) return;
        for (InetSocketAddress destino : usuariosPorSala.get(sala).values()) {
            enviar(socket, mensaje, destino);
        }
    }

    // Enviar mensaje directo
    private static void enviar(DatagramSocket socket, String mensaje, InetSocketAddress destino) throws Exception {
        byte[] data = mensaje.getBytes();
        DatagramPacket paquete = new DatagramPacket(data, data.length, destino.getAddress(), destino.getPort());
        socket.send(paquete);
    }

    // Actualiza lista de usuarios en todos los clientes de la sala
    private static void actualizarUsuarios(DatagramSocket socket, String sala) throws Exception {
        if (!usuariosPorSala.containsKey(sala)) return;

        StringBuilder lista = new StringBuilder();
        for (String nombre : usuariosPorSala.get(sala).keySet()) {
            lista.append(nombre).append(",");
        }
        String mensaje = "UPDATE_USERS|" + lista;

        for (InetSocketAddress destino : usuariosPorSala.get(sala).values()) {
            enviar(socket, mensaje, destino);
        }
    }
}
