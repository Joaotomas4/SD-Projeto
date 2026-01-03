import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientAPI implements AutoCloseable {
    private final Demultiplexer demux;

    public ClientAPI(String host, int port) throws IOException {
        Socket s = new Socket(host, port);
        TaggedConnection conn = new TaggedConnection(s);
        this.demux = new Demultiplexer(conn);
    }

    // --- AUTENTICAÇÃO E REGISTO ---

    public String register(String user, String pass) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF(user);
        out.writeUTF(pass);

        byte[] rep = demux.send(OpCode.REGISTER, baos.toByteArray());
        return new DataInputStream(new ByteArrayInputStream(rep)).readUTF();
    }

    public String login(String user, String pass) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF(user);
        out.writeUTF(pass);

        byte[] rep = demux.send(OpCode.LOGIN, baos.toByteArray());
        return new DataInputStream(new ByteArrayInputStream(rep)).readUTF();
    }

    // --- REGISTO DE EVENTOS (ESCRITA) ---

    public String addEvent(String produto, int qtd, double preco) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF(produto);
        out.writeInt(qtd);
        out.writeDouble(preco);

        byte[] rep = demux.send(OpCode.ADD_EVENT, baos.toByteArray());
        return new DataInputStream(new ByteArrayInputStream(rep)).readUTF();
    }

    // --- CONSULTAS DE AGREGAÇÃO (HISTÓRICO) ---

    public long getQuantity(String produto, int dias) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF(produto);
        out.writeInt(dias);

        byte[] rep = demux.send(OpCode.GET_QUANTITY, baos.toByteArray());
        return new DataInputStream(new ByteArrayInputStream(rep)).readLong();
    }

    public double getVolume(String produto, int dias) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF(produto);
        out.writeInt(dias);

        byte[] rep = demux.send(OpCode.GET_VOLUME, baos.toByteArray());
        return new DataInputStream(new ByteArrayInputStream(rep)).readDouble();
    }

    public double getAvgPrice(String produto, int dias) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF(produto);
        out.writeInt(dias);

        byte[] rep = demux.send(OpCode.GET_AVG_PRICE, baos.toByteArray());
        return new DataInputStream(new ByteArrayInputStream(rep)).readDouble();
    }

    public double getMaxPrice(String produto, int dias) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF(produto);
        out.writeInt(dias);

        byte[] rep = demux.send(OpCode.GET_MAX_PRICE, baos.toByteArray());
        return new DataInputStream(new ByteArrayInputStream(rep)).readDouble();
    }

    // --- NOTIFICAÇÕES (BLOQUEANTES) ---

    public String waitSimultaneous(String p1, String p2) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF(p1);
        out.writeUTF(p2);

        byte[] rep = demux.send(OpCode.SIMULTANEOUS_SALES, baos.toByteArray());
        return new DataInputStream(new ByteArrayInputStream(rep)).readUTF();
    }

    public String waitConsecutive(String p, int n) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);
        out.writeUTF(p);
        out.writeInt(n);

        byte[] rep = demux.send(OpCode.CONSECUTIVE_SALES, baos.toByteArray());
        return new DataInputStream(new ByteArrayInputStream(rep)).readUTF();
    }


    public String filterEvents(List<String> produtos, int dias) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        out.writeInt(dias);
        out.writeInt(produtos.size());
        for (String s : produtos) {
            out.writeUTF(s);
        }

        byte[] rep = demux.send(OpCode.FILTER_EVENTS, baos.toByteArray());

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(rep));
        StringBuilder sb = new StringBuilder();

        Map<Integer, String> dicionario = new HashMap<>();
        int numEntradasDict = in.readInt();

        for (int i = 0; i < numEntradasDict; i++) {
            int id = in.readInt();
            String nome = in.readUTF();
            dicionario.put(id, nome);
        }

        sb.append("--- Resultados do Filtro ---\n");

        for (int i = 0; i < numEntradasDict; i++) {
            int id = in.readInt();
            int numEventos = in.readInt();

            // Traduzir ID para Nome
            String nomeReal = dicionario.get(id);
            sb.append("Produto: ").append(nomeReal).append("\n");

            for (int j = 0; j < numEventos; j++) {
                Evento e = Evento.deserialize(in);
                sb.append(String.format("   -> Qtd: %d | Preço: %.2f €\n",
                        e.getQuantidade(), e.getPreco()));
            }
        }

        if (numEntradasDict == 0) {
            sb.append("Nenhum evento encontrado para os critérios selecionados.");
        }

        return sb.toString();
    }

    @Override
    public void close() throws Exception {
        demux.close();
    }
}