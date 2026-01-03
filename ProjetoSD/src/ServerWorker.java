import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ServerWorker implements Runnable {
    private final TSDB tsdb;
    private final TaggedConnection conn;
    private String utilizadorLogado = null;

    public ServerWorker(TSDB tsdb, Socket socket) throws IOException {
        this.tsdb = tsdb;
        this.conn = new TaggedConnection(socket);
    }

    @Override
    public void run() {
        try (conn) {
            while (true) {
                Frame frame = conn.receive(); // Recebe o pedido do cliente
                processar(frame);
            }
        } catch (EOFException e) {
            System.out.println("Cliente terminou a ligação.");
        } catch (Exception e) {
            System.err.println("Erro no Worker: " + e.getMessage());
        }
    }

    private void processar(Frame frame) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(frame.payload);
        DataInputStream in = new DataInputStream(bais);

        // Prepara buffers para a resposta
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(baos);

        try {
            switch (frame.opCode) {
                case OpCode.REGISTER:
                    handleRegister(in, out);
                    break;
                case OpCode.LOGIN:
                    handleLogin(in, out);
                    break;
                case OpCode.ADD_EVENT:
                    if (checkAuth(out)) handleAddEvent(in, out);
                    break;
                case OpCode.GET_QUANTITY:
                    if (checkAuth(out)) handleGetQuantity(in, out);
                    break;
                case OpCode.GET_VOLUME:
                    if (checkAuth(out)) handleGetVolume(in, out);
                    break;
                case OpCode.GET_AVG_PRICE:
                    if (checkAuth(out)) handleGetAvgPrice(in, out);
                    break;
                case OpCode.GET_MAX_PRICE:
                    if (checkAuth(out)) handleGetMaxPrice(in, out);
                    break;
                case OpCode.SIMULTANEOUS_SALES:
                    if (checkAuth(out)) handleSimultaneous(in, out, frame.tag);
                    break;
                case OpCode.CONSECUTIVE_SALES:
                    if (checkAuth(out)) handleConsecutive(in, out, frame.tag);
                    break;
                case OpCode.FILTER_EVENTS:
                    if(checkAuth(out)) handleFilter(in, out);
                    break;
                default:
                    throw new Exception("Comando não suportado: " + frame.opCode);
            }
            // Envia resposta de sucesso
            conn.send(frame.tag, OpCode.OK, baos.toByteArray());

        } catch (Exception e) {
            // Em caso de erro, limpa o buffer e envia mensagem de erro
            baos.reset();
            out.writeUTF("ERRO: " + e.getMessage());
            conn.send(frame.tag, OpCode.ERROR, baos.toByteArray());
        }
    }

    // --- HANDLERS ESPECÍFICOS ---

    private void handleRegister(DataInputStream in, DataOutputStream out) throws IOException {
        String user = in.readUTF();
        String pass = in.readUTF();
        if (tsdb.registaUtilizador(user, pass)) {
            out.writeUTF("Utilizador registado com sucesso.");
        } else {
            throw new IOException("Utilizador já existe.");
        }
    }

    private void handleLogin(DataInputStream in, DataOutputStream out) throws IOException {
        String user = in.readUTF();
        String pass = in.readUTF();
        if (tsdb.autentica(user, pass)) {
            this.utilizadorLogado = user;
            out.writeUTF("Login efetuado. Bem-vindo " + user);
        } else {
            throw new IOException("Credenciais inválidas.");
        }
    }

    private void handleAddEvent(DataInputStream in, DataOutputStream out) throws IOException {
        String prod = in.readUTF();
        int qtd = in.readInt();
        double preco = in.readDouble();
        tsdb.registaEvento(prod, qtd, preco);
        out.writeUTF("Evento registado.");
    }

    private void handleGetQuantity(DataInputStream in, DataOutputStream out) throws IOException {
        String prod = in.readUTF();
        int dias = in.readInt();
        long res = tsdb.getQuantidadeTotal(prod, dias);
        out.writeLong(res);
    }

    private void handleGetVolume(DataInputStream in, DataOutputStream out) throws IOException {
        String prod = in.readUTF();
        int dias = in.readInt();
        double res = tsdb.getVolumeTotal(prod, dias);
        out.writeDouble(res);
    }

    private void handleGetAvgPrice(DataInputStream in, DataOutputStream out) throws IOException {
        String prod = in.readUTF();
        int dias = in.readInt();
        double res = tsdb.getPrecoMedio(prod, dias);
        out.writeDouble(res);
    }

    private void handleGetMaxPrice(DataInputStream in, DataOutputStream out) throws IOException {
        String prod = in.readUTF();
        int dias = in.readInt();
        double res = tsdb.getPrecoMaximo(prod, dias);
        out.writeDouble(res);
    }

    private void handleSimultaneous(DataInputStream in, DataOutputStream out, int tag) throws Exception {
        String p1 = in.readUTF();
        String p2 = in.readUTF();
        // Bloqueia a thread até à condição
        tsdb.getNotificador().esperarSimultaneo(p1, p2, tsdb.getDiaCorrente());
        out.writeUTF("Venda simultânea detetada!");
    }

    private void handleConsecutive(DataInputStream in, DataOutputStream out, int tag) throws Exception {
        String p = in.readUTF();
        int n = in.readInt();
        // Bloqueia a thread até à condição
        tsdb.getNotificador().esperarConsecutivo(p, n, tsdb.getDiaCorrente());
        out.writeUTF("Vendas consecutivas detetadas!");
    }

    private boolean checkAuth(DataOutputStream out) throws IOException {
        if (utilizadorLogado == null) {
            throw new IOException("Acesso negado: faça login primeiro.");
        }
        return true;
    }

    private void handleFilter(DataInputStream in, DataOutputStream out) throws IOException {
        int diasAtras = in.readInt();
        int numProdutos = in.readInt();
        List<String> produtosInteresse = new ArrayList<>();
        for (int i = 0; i < numProdutos; i++) {
            produtosInteresse.add(in.readUTF());
        }

        Map<String, List<Evento>> dados = tsdb.getEventosFiltrados(produtosInteresse, diasAtras);

        // A. Primeiro, enviamos o Dicionário (ID -> Nome)
        // Atribui um ID numérico temporário a cada produto encontrado
        Map<String, Integer> dicionario = new HashMap<>();
        int idCounter = 1;

        out.writeInt(dados.size());

        for (String produto : dados.keySet()) {
            dicionario.put(produto, idCounter);

            // Protocolo: [ID] [NOME_PRODUTO]
            out.writeInt(idCounter);
            out.writeUTF(produto);

            idCounter++;
        }

        // B. Agora enviamos os eventos usando APENAS os IDs
        // Protocolo: [ID_PRODUTO] [QTD_EVENTOS] [EV1] [EV2]...

        for (Map.Entry<String, List<Evento>> entry : dados.entrySet()) {
            String nome = entry.getKey();
            List<Evento> lista = entry.getValue();
            int id = dicionario.get(nome);

            out.writeInt(id);            // Identificador curto (4 bytes)
            out.writeInt(lista.size());  // Quantos eventos

            for (Evento e : lista) {
                // Serializa apenas (Qtd, Preco, Timestamp)
                e.serialize(out);
            }
        }
        out.flush();
    }
}