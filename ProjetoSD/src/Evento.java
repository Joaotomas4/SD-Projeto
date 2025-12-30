import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class Evento {
    private final int quantidade;
    private final double preco;
    private final long timestamp; // Essencial para operações de tempo real

    public Evento(int quantidade, double preco, long timestamp) {
        this.quantidade = quantidade;
        this.preco = preco;
        this.timestamp = timestamp;
    }

    // Getters
    public int getQuantidade() { return quantidade; }
    public double getPreco() { return preco; }
    public long getTimestamp() { return timestamp; }

    // Métodos para facilitar a escrita/leitura binária no disco
    public void serialize(DataOutputStream out) throws IOException {
        out.writeInt(quantidade);
        out.writeDouble(preco);
        out.writeLong(timestamp);
    }

    public static Evento deserialize(DataInputStream in) throws IOException {
        int q = in.readInt();
        double p = in.readDouble();
        long t = in.readLong();
        return new Evento(q, p, t);
    }
}