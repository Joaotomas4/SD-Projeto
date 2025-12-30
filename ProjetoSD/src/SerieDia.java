import java.io.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SerieDia {
    private final int diaID;
    // Organizamos por produto para que o 'calcularParaProduto' seja instantâneo
    private Map<String, List<Evento>> eventosByProduct;
    private final Map<String, Stats> cacheStats = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public SerieDia(int id, Map<String, List<Evento>> dados) {
        this.diaID = id;
        this.eventosByProduct = dados;
    }


    // Dentro da classe SerieDia.java
    public int getDiaID() {
        return this.diaID;
    }

    // --- MÉTODOS DE GESTÃO DE MEMÓRIA ---

    public boolean estaEmMemoria() {
        lock.readLock().lock();
        try { return this.eventosByProduct != null; }
        finally { lock.readLock().unlock(); }
    }

    public void descarregarEventos() {
        lock.writeLock().lock();
        try { this.eventosByProduct = null; }
        finally { lock.writeLock().unlock(); }
    }

    // --- MÉTODOS DE AGREGAÇÃO (LAZY & CACHING) ---

    public Stats getStats(String produto) {
        // 1. Tentar ler do Cache (Modo Lazy)
        lock.readLock().lock();
        try {
            if (cacheStats.containsKey(produto)) {
                return cacheStats.get(produto);
            }
        } finally {
            lock.readLock().unlock();
        }

        // 2. Se não estiver no cache, calcular (On Demand)
        lock.writeLock().lock();
        try {
            // Dupla verificação (outro thread pode ter calculado entretanto)
            if (cacheStats.containsKey(produto)) return cacheStats.get(produto);

            // IMPORTANTE: A TSDB deve garantir que os dados estão carregados
            // antes de chamar este método, mas por segurança podemos verificar:
            if (eventosByProduct == null) {
                carregarDoDisco();
            }

            Stats s = calcularParaProduto(produto);
            cacheStats.put(produto, s); // Guarda no cache para o futuro
            return s;
        } catch (IOException e) {
            return null; // Tratar erro de leitura de disco
        } finally {
            lock.writeLock().unlock();
        }
    }

    private Stats calcularParaProduto(String produto) {
        List<Evento> lista = eventosByProduct.get(produto);
        if (lista == null || lista.isEmpty()) return null;

        long qtdTotal = 0;
        double volTotal = 0;
        double precoMax = 0;

        for (Evento e : lista) {
            qtdTotal += e.getQuantidade();
            volTotal += (e.getQuantidade() * e.getPreco());
            if (e.getPreco() > precoMax) precoMax = e.getPreco();
        }

        return new Stats(qtdTotal, volTotal, precoMax);
    }

    // --- MÉTODOS DE PERSISTÊNCIA ---

    public void persistirParaDisco() throws IOException {
        lock.readLock().lock();
        try {
            if (eventosByProduct == null) return;
            try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream("dia_" + diaID + ".bin")))) {
                out.writeInt(eventosByProduct.size()); // Número de produtos
                for (Map.Entry<String, List<Evento>> entry : eventosByProduct.entrySet()) {
                    out.writeUTF(entry.getKey()); // Nome do produto
                    out.writeInt(entry.getValue().size()); // Qtd de eventos do produto
                    for (Evento e : entry.getValue()) {
                        e.serialize(out);
                    }
                }
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    public void carregarDoDisco() throws IOException {
        lock.writeLock().lock();
        try {
            File f = new File("dia_" + diaID + ".bin");
            if (!f.exists()) return;
            try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
                int numProdutos = in.readInt();
                this.eventosByProduct = new HashMap<>(numProdutos);
                for (int i = 0; i < numProdutos; i++) {
                    String prod = in.readUTF();
                    int numEventos = in.readInt();
                    List<Evento> lista = new ArrayList<>(numEventos);
                    for (int j = 0; j < numEventos; j++) {
                        lista.add(Evento.deserialize(in));
                    }
                    eventosByProduct.put(prod, lista);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}