import java.io.*;
import java.net.Socket;
import java.util.concurrent.locks.ReentrantLock;



//classe para ler e escrever no socket sem problemas de corrida/concorrencia

public class TaggedConnection implements AutoCloseable {
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;

    // Locks independentes permitem ler e escrever ao mesmo tempo
    private final ReentrantLock sendLock = new ReentrantLock();
    private final ReentrantLock receiveLock = new ReentrantLock();

    public TaggedConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        this.out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
    }

    public void send(Frame frame) throws IOException {
        sendLock.lock();
        try {
            // Aqui usamos o método de serialização que desenhámos na classe Frame
            frame.serialize(out);
        } finally {
            sendLock.unlock();
        }
    }

    public void send(int tag, int opCode, byte[] data) throws IOException {
        send(new Frame(tag, opCode, data));
    }

    public Frame receive() throws IOException {
        receiveLock.lock();
        try {
            // Aqui usamos o método de deserialização da classe Frame
            return Frame.deserialize(in);
        } finally {
            receiveLock.unlock();
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}