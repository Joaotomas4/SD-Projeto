import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class Notificador {
    private final Lock lock;
    // Mapa para minimizar threads acordadas: Produto -> Lista de Conditions de quem espera por ele
    private final Map<String, List<Condition>> interessados = new HashMap<>();
    private boolean diaTerminou = false;

    public Notificador(Lock lock) {
        this.lock = lock;
    }

    /**
     * Acorda apenas as threads que registaram interesse neste produto específico.
     * Chamado pela TSDB dentro do lock de escrita do dia corrente.
     */
    public void notificar(String produto) {
        List<Condition> lista = interessados.get(produto);
        if (lista != null) {
            // Criamos uma cópia para evitar ConcurrentModificationException
            // se a thread acordada remover-se da lista imediatamente
            for (Condition c : new ArrayList<>(lista)) {
                c.signalAll();
            }
        }
    }

    public void prepararNovoDia() {
        lock.lock();
        try {
            this.diaTerminou = false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Chamado pela TSDB quando o dia muda. Acorda todas as threads pendentes.
     */
    public void diaTerminou() {
        lock.lock();
        try {
            this.diaTerminou = true;
            for (List<Condition> lista : interessados.values()) {
                for (Condition c : lista) c.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    // --- REQUISITO 5.1: VENDAS SIMULTÂNEAS ---
    public void esperarSimultaneo(String p1, String p2, Map<String, List<Evento>> dia) throws Exception {
        lock.lock();
        try {
            Condition cond = lock.newCondition();
            registarInteresse(p1, cond);
            registarInteresse(p2, cond);

            while (!diaTerminou && (!dia.containsKey(p1) || !dia.containsKey(p2))) {
                cond.await();
            }

            removerInteresse(p1, cond);
            removerInteresse(p2, cond);

            // Se o loop parou mas um dos produtos não existe, o dia acabou antes
            if (!dia.containsKey(p1) || !dia.containsKey(p2)) {
                throw new Exception("O dia terminou sem que a venda ocorresse.");
            }
        } finally {
            lock.unlock();
        }
    }

    // --- REQUISITO 5.2: VENDAS CONSECUTIVAS ---
    public void esperarConsecutivo(String p, int n, Map<String, List<Evento>> dia) throws Exception {
        lock.lock();
        try {
            Condition cond = lock.newCondition();
            registarInteresse(p, cond);

            while (!diaTerminou && (dia.get(p) == null || dia.get(p).size() < n)) {
                cond.await();
            }

            removerInteresse(p, cond);

            // Validação: verificamos se o produto atingiu de facto a quantidade N
            if (dia.get(p) == null || dia.get(p).size() < n) {
                throw new Exception("O dia terminou antes de atingir as " + n + " vendas.");
            }
        } finally {
            lock.unlock();
        }
    }

    // Métodos auxiliares de gestão do mapa de condições
    private void registarInteresse(String p, Condition c) {
        interessados.putIfAbsent(p, new ArrayList<>());
        interessados.get(p).add(c);
    }

    private void removerInteresse(String p, Condition c) {
        List<Condition> lista = interessados.get(p);
        if (lista != null) {
            lista.remove(c);
            if (lista.isEmpty()) interessados.remove(p);
        }
    }
}