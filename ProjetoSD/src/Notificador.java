import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

public class Notificador {
    private final Lock lock;
    private final Map<String, List<Condition>> interessados = new HashMap<>();

    private int diaCorrente = 0;

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



    // MUDANÇA 2: Método para avançar o dia
    public void avancarDia() {
        lock.lock();
        try {
            this.diaCorrente++; // Incrementamos o dia
            // Acordamos toda a gente. Eles vão verificar o ID e perceber que já passou.
            for (List<Condition> lista : interessados.values()) {
                for (Condition c : lista) c.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    // (Remove o método prepararNovoDia, já não é preciso)

    // MUDANÇA 3: O wait verifica se o dia ainda é o mesmo
    public void esperarSimultaneo(String p1, String p2, int diaDoPedido, Map<String, List<Evento>> dia) throws Exception {
        lock.lock();
        try {
            Condition cond = lock.newCondition();
            registarInteresse(p1, cond);
            registarInteresse(p2, cond);

            // Enquanto for o dia certo E os produtos não existirem...
            while (this.diaCorrente == diaDoPedido && (!dia.containsKey(p1) || !dia.containsKey(p2))) {
                cond.await();
            }

            removerInteresse(p1, cond);
            removerInteresse(p2, cond);

            // Se o loop terminou mas ainda faltam produtos, foi porque o dia mudou!
            if (!dia.containsKey(p1) || !dia.containsKey(p2)) {
                throw new Exception("O dia terminou sem que a venda ocorresse.");
            }
        } finally {
            lock.unlock();
        }
    }

    // --- REQUISITO 5.2: VENDAS CONSECUTIVAS ---
    public void esperarConsecutivo(String p, int n, int diaDoPedido, Map<String, List<Evento>> dia) throws Exception {
        lock.lock();
        try {
            Condition cond = lock.newCondition();
            registarInteresse(p, cond);

            while (this.diaCorrente == diaDoPedido && (dia.get(p) == null || dia.get(p).size() < n)) {
                cond.await();
            }

            removerInteresse(p, cond);

            if (this.diaCorrente != diaDoPedido) {
                throw new Exception("O dia terminou antes de atingir as " + n + " vendas.");
            }

            if (dia.get(p) == null || dia.get(p).size() < n) {
                throw new Exception("Erro de sincronização: condição não cumprida.");
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