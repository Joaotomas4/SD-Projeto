import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class TSDB {
    private final int D; // Dias de retenção total
    private final int S; // Limite de séries em memória
    private final Notificador notificador;
    private int contadorDias = 0; // Para gerar IDs de ficheiro únicos

    // Controlo de utilizadores
    private final Map<String, String> utilizadores = new HashMap<>();
    private final ReentrantReadWriteLock authLock = new ReentrantReadWriteLock();

    // Dia Atual: Onde ocorrem as escritas
    private Map<String, List<Evento>> diaCorrente = new HashMap<>();
    private final ReentrantReadWriteLock currentLock = new ReentrantReadWriteLock();


    // Dentro da classe TSDB
    private final List<Integer> diasEmMemoria = new ArrayList<>(); // Lista para controlar o limite S

    // Histórico: Dias anteriores (1 a D)
    // Cada SerieDia terá o seu próprio ficheiro e lock interno
    private final Map<Integer, SerieDia> historico = new HashMap<>();
    private final ReentrantReadWriteLock histLock = new ReentrantReadWriteLock();

    public TSDB(int D, int S) {
        this.D = D;
        this.S = S;
        this.notificador = new Notificador(this.currentLock.writeLock());
    }


    public Notificador getNotificador() { return this.notificador; }
    public Map<String, List<Evento>> getDiaCorrente() { return this.diaCorrente; }

    private SerieDia getSerieDia(int diasAtras) {
        histLock.readLock().lock();
        try {
            // Verifica se o dia solicitado está dentro da janela de retenção D
            if (diasAtras < 1 || diasAtras > D) {
                return null;
            }
            return historico.get(diasAtras);
        } finally {
            histLock.readLock().unlock();
        }
    }

    private void garantirSerieNaMemoria(int diaID) {
        histLock.writeLock().lock();
        try {
            SerieDia serie = historico.get(diaID);
            if (serie == null) return;

            if (serie.estaEmMemoria()) {
                diasEmMemoria.remove((Integer) diaID);
                diasEmMemoria.add(diaID);
                return;
            }

            if (diasEmMemoria.size() >= S) {
                int diaParaRemover = diasEmMemoria.remove(0);
                SerieDia antiga = historico.get(diaParaRemover);
                if (antiga != null) antiga.descarregarEventos();
            }

            // AGORA: Não passa o diaID, pois a série já o tem internamente
            try {
                serie.carregarDoDisco();
                diasEmMemoria.add(diaID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } finally {
            histLock.writeLock().unlock();
        }
    }
    public boolean registaUtilizador(String user, String pass) {
        authLock.writeLock().lock();
        try {
            if (utilizadores.containsKey(user)) return false;
            utilizadores.put(user, pass);
            return true;
        } finally {
            authLock.writeLock().unlock();
        }
    }

    public boolean autentica(String user, String pass) {
        authLock.readLock().lock();
        try {
            return pass.equals(utilizadores.get(user));
        } finally {
            authLock.readLock().unlock();
        }
    }

    public void registaEvento(String produto, int qtd, double preco) {
        currentLock.writeLock().lock();
        try {
            // 1. Inserir o evento na lista do dia corrente
            long ts = System.currentTimeMillis();
            Evento e = new Evento(qtd, preco, ts);
            diaCorrente.putIfAbsent(produto, new ArrayList<>());
            diaCorrente.get(produto).add(e);

            // 2. Acordar as threads certas através do notificador
            this.notificador.notificar(produto);

        } finally {
            currentLock.writeLock().unlock();
        }
    }

    public double getPrecoMedio(String produto, int d) {
        long totalQuantidade = 0;
        double totalVolume = 0;

        for (int i = 1; i <= d; i++) {
            // NOVIDADE: Antes de ler, gerimos a memória
            garantirSerieNaMemoria(i);

            SerieDia dia = getSerieDia(i);
            if (dia != null) {
                Stats s = dia.getStats(produto); // Agora o dia tem os eventos na RAM se precisar de calcular
                if (s != null) {
                    totalQuantidade += s.getQuantidadeTotal();
                    totalVolume += s.getVolumeTotal();
                }
            }
        }
        return totalQuantidade == 0 ? 0 : totalVolume / totalQuantidade;
    }

    public double getPrecoMaximo(String produto, int d) {
        double maximoGlobal = 0.0;
        boolean encontrouVendas = false;

        for (int i = 1; i <= d; i++) {
            // 1. Garantir que os dados do dia i estão acessíveis na RAM
            garantirSerieNaMemoria(i);

            SerieDia dia = getSerieDia(i);
            if (dia != null) {
                // 2. Obter o resumo estatístico do produto para esse dia
                Stats s = dia.getStats(produto);

                if (s != null) {
                    // 3. Comparar o máximo desse dia com o máximo global que já temos
                    if (!encontrouVendas || s.getPrecoMaximo() > maximoGlobal) {
                        maximoGlobal = s.getPrecoMaximo();
                        encontrouVendas = true;
                    }
                }
            }
        }

        // Se nunca encontrou o produto em nenhum dia, retorna 0
        return encontrouVendas ? maximoGlobal : 0.0;
    }

    public long getQuantidadeTotal(String produto, int d) {
        long acumuladoQuantidade = 0;

        // Percorre os d dias anteriores solicitados (1 ≤ d ≤ D)
        for (int i = 1; i <= d; i++) {
            // 1. Garante que a série temporal está na RAM (respeita o limite S)
            garantirSerieNaMemoria(i);

            SerieDia dia = getSerieDia(i);
            if (dia != null) {
                // 2. Obtém o resumo estatístico (Lazy Caching)
                Stats s = dia.getStats(produto);

                if (s != null) {
                    // 3. Soma a quantidade vendida nesse dia específico
                    acumuladoQuantidade += s.getQuantidadeTotal();
                }
            }
        }

        return acumuladoQuantidade;
    }

    public double getVolumeTotal(String produto, int d) {
        double acumuladoVolume = 0.0;

        // Percorre os d dias anteriores solicitados (1 ≤ d ≤ D)
        for (int i = 1; i <= d; i++) {
            // 1. Garante que os dados do dia i estão na RAM (respeita o limite S)
            garantirSerieNaMemoria(i);

            SerieDia dia = getSerieDia(i);
            if (dia != null) {
                // 2. Obtém os Stats (Lazy Caching na SerieDia)
                Stats s = dia.getStats(produto);

                if (s != null) {
                    // 3. Acumula o volume (quantidade * preço) desse dia
                    acumuladoVolume += s.getVolumeTotal();
                }
            }
        }

        return acumuladoVolume;
    }

    public void proximoDia() {
        currentLock.writeLock().lock();
        histLock.writeLock().lock();
        try {
            // 1. AVISAR O NOTIFICADOR: O dia acabou
            // Isto acorda todas as threads bloqueadas em await()
            if (this.notificador != null) {
                this.notificador.diaTerminou();
            }

            contadorDias++; // Novo ID único para o ficheiro

            // 2. Lógica de Histórico e Retenção D
            SerieDia novoDiaAnterior = new SerieDia(contadorDias, diaCorrente);

            if (historico.containsKey(D)) {
                SerieDia diaParaEliminar = historico.get(D);
                new File("dia_" + diaParaEliminar.getDiaID() + ".bin").delete();
                diasEmMemoria.remove((Integer) D);
            }

            for (int i = D; i > 1; i--) {
                if (historico.containsKey(i - 1)) {
                    historico.put(i, historico.get(i - 1));
                }
            }
            historico.put(1, novoDiaAnterior);

            // 3. Persistência em disco
            try {
                novoDiaAnterior.persistirParaDisco();
            } catch (IOException e) {
                System.err.println("Erro ao persistir dia " + contadorDias + ": " + e.getMessage());
            }

            // 4. LIMPEZA PARA O NOVO DIA
            this.diaCorrente = new HashMap<>();

            // 5. RESET DO NOTIFICADOR: Preparar para novas vendas
            // Só fazemos isto DEPOIS de o mapa estar limpo, para que as novas
            // esperas não encontrem produtos de "ontem"
            if (this.notificador != null) {
                this.notificador.prepararNovoDia();
            }

            System.out.println("[TSDB] Dia " + (contadorDias-1) + " fechado. Pronto para o dia seguinte.");

        } finally {
            histLock.writeLock().unlock();
            currentLock.writeLock().unlock();
        }
    }
}